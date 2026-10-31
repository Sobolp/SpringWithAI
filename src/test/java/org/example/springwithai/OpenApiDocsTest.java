package org.example.springwithai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayway.jsonpath.JsonPath;
import org.assertj.core.api.AssertProvider;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.json.AbstractJsonContentAssert;
import org.springframework.test.json.JsonPathValueAssert;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@AppIntegrationTest
class OpenApiDocsTest {

    private static final String REQUEST_SCHEMA = "#/components/schemas/AlienRequest";
    private static final String RESPONSE_SCHEMA = "#/components/schemas/AlienResponse";
    private static final String ERROR_SCHEMA = "#/components/schemas/ApiError";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectProvider<BuildProperties> buildProperties;

    @LocalServerPort
    private int port;

    @Test
    void infoUsesTitleAndBuildVersion() {
        // Same rule as the app: build-info may be absent (e.g. an IDE build), then the version is "unknown".
        String expectedVersion = OpenApiConfig.apiVersion(buildProperties.getIfAvailable());

        apiDocs().isLenientlyEqualTo("""
                {"info": {"title": "SpringWithAI API", "version": "%s"}}
                """.formatted(expectedVersion));
    }

    @Test
    void listDocumentsFiltersAndResponseArray() {
        String list = "$.paths['/api/aliens'].get";
        apiDocs()
                .hasPathSatisfying(list + ".parameters[*].name",
                        names -> assertThat(names).asArray().containsExactlyInAnyOrder("type", "origin"))
                .hasPathSatisfying(list + ".responses['200'].content.*.schema.items.$ref",
                        refs -> assertThat(refs).asArray().containsOnly(RESPONSE_SCHEMA));
    }

    @Test
    void createDocumentsCreatedAndBadRequest() {
        String post = "$.paths['/api/aliens'].post";
        apiDocs()
                .hasPathSatisfying(post + ".requestBody.content.*.schema.$ref",
                        refs -> assertThat(refs).asArray().containsOnly(REQUEST_SCHEMA))
                .hasPathSatisfying(post + ".responses['201'].content.*.schema.$ref",
                        refs -> assertThat(refs).asArray().containsOnly(RESPONSE_SCHEMA))
                .hasPathSatisfying(post + ".responses['400'].content", OpenApiDocsTest::isJsonError)
                .doesNotHavePath(post + ".responses['200']");
    }

    @Test
    void getByIdDocumentsOkAndNotFound() {
        String getById = "$.paths['/api/aliens/{id}'].get";
        apiDocs()
                .hasPathSatisfying(getById + ".responses['200'].content.*.schema.$ref",
                        refs -> assertThat(refs).asArray().containsOnly(RESPONSE_SCHEMA))
                .hasPathSatisfying(getById + ".responses['404'].content", OpenApiDocsTest::isJsonError);
    }

    @Test
    void updateDocumentsOkBadRequestAndNotFound() {
        String put = "$.paths['/api/aliens/{id}'].put";
        apiDocs()
                .hasPathSatisfying(put + ".requestBody.content.*.schema.$ref",
                        refs -> assertThat(refs).asArray().containsOnly(REQUEST_SCHEMA))
                .hasPathSatisfying(put + ".responses['200'].content.*.schema.$ref",
                        refs -> assertThat(refs).asArray().containsOnly(RESPONSE_SCHEMA))
                .hasPathSatisfying(put + ".responses['400'].content", OpenApiDocsTest::isJsonError)
                .hasPathSatisfying(put + ".responses['404'].content", OpenApiDocsTest::isJsonError);
    }

    @Test
    void deleteDocumentsNoContentAndNotFound() {
        String delete = "$.paths['/api/aliens/{id}'].delete";
        apiDocs()
                .hasPath(delete + ".responses['204']")
                .hasPathSatisfying(delete + ".responses['404'].content", OpenApiDocsTest::isJsonError)
                .doesNotHavePath(delete + ".responses['200']");
    }

    @Test
    void requestSchemaHasNoId() {
        apiDocs()
                .hasPath("$.components.schemas.AlienRequest.properties.name")
                .doesNotHavePath("$.components.schemas.AlienRequest.properties.id")
                .hasPath("$.components.schemas.AlienResponse.properties.id");
    }

    @Test
    void errorSchemaRequiresOnlyTheAlwaysPresentFields() {
        apiDocs()
                .hasPathSatisfying("$.components.schemas.ApiError.required",
                        required -> assertThat(required).asArray()
                                .containsExactlyInAnyOrder("timestamp", "status", "error", "path"))
                .hasPathSatisfying("$.components.schemas.ApiError.properties.errors.items.$ref",
                        ref -> assertThat(ref).isEqualTo("#/components/schemas/ApiBindingError"))
                .hasPath("$.components.schemas.ApiError.properties.message")
                .hasPath("$.components.schemas.ApiError.properties.trace");
    }

    // Default spring.web.error.* settings, as in a packaged jar: the body has exactly the required fields.

    @Test
    void notFoundBodyHasOnlyRequiredErrorFields() throws Exception {
        Map<String, Object> body = errorBody(notFound(port), HttpStatus.NOT_FOUND);

        assertThat(body).containsOnlyKeys(errorSchema(mvc).required());
    }

    @Test
    void validationErrorBodyHasOnlyRequiredErrorFields() throws Exception {
        Map<String, Object> body = errorBody(invalidCreate(port), HttpStatus.BAD_REQUEST);

        assertThat(body).containsOnlyKeys(errorSchema(mvc).required());
    }

    @Test
    void documentedSwaggerUiUrlRedirectsToUi() {
        assertThat(mvc.get().uri("/swagger-ui.html"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/swagger-ui/index.html");
        assertThat(mvc.get().uri("/swagger-ui/index.html"))
                .hasStatusOk();
    }

    /** The content of a documented error response: a JSON body with the {@link ApiError} schema. */
    private static void isJsonError(AssertProvider<JsonPathValueAssert> content) {
        assertThat(content).isEqualTo(
                Map.of(MediaType.APPLICATION_JSON_VALUE, Map.of("schema", Map.of("$ref", ERROR_SCHEMA))));
    }

    private AbstractJsonContentAssert<?> apiDocs() {
        return assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson();
    }

    /** Field names of the ApiError and ApiBindingError schemas, as published in /v3/api-docs. */
    private record ErrorSchema(String[] required, Set<String> properties, Set<String> bindingErrorProperties) {
    }

    private static ErrorSchema errorSchema(MockMvcTester mvc) throws Exception {
        String docs = mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString();
        List<String> required = JsonPath.read(docs, "$.components.schemas.ApiError.required");
        Map<String, Object> properties = JsonPath.read(docs, "$.components.schemas.ApiError.properties");
        Map<String, Object> bindingError = JsonPath.read(docs, "$.components.schemas.ApiBindingError.properties");
        return new ErrorSchema(required.toArray(String[]::new), properties.keySet(), bindingError.keySet());
    }

    private static HttpRequest notFound(int port) {
        return HttpRequest.newBuilder(serverUri(port, "/api/aliens/999999")).GET().build();
    }

    private static HttpRequest invalidCreate(int port) {
        return HttpRequest.newBuilder(serverUri(port, "/api/aliens"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
    }

    private static URI serverUri(int port, String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Sends the request to the real server and returns the JSON error body, after checking the status. */
    private static Map<String, Object> errorBody(HttpRequest request, HttpStatus expectedStatus)
            throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(expectedStatus.value());
            return JsonPath.read(response.body(), "$");
        }
    }

    /** What spring-boot-devtools turns on under bootRun: every optional ApiError field is included. */
    @Nested
    @TestPropertySource(properties = {
            "spring.web.error.include-message=always",
            "spring.web.error.include-stacktrace=always",
            "spring.web.error.include-binding-errors=always"})
    class WithAllErrorDetails {

        @Autowired
        private MockMvcTester detailMvc;

        @LocalServerPort
        private int detailPort;

        @Test
        void notFoundBodyUsesOnlyDocumentedErrorFields() throws Exception {
            ErrorSchema schema = errorSchema(detailMvc);
            Map<String, Object> body = errorBody(notFound(detailPort), HttpStatus.NOT_FOUND);

            assertThat(body).containsKeys(schema.required()).containsKeys("message", "trace");
            assertThat(body.keySet()).isSubsetOf(schema.properties());
        }

        @Test
        void validationErrorBodyUsesOnlyDocumentedErrorFields() throws Exception {
            ErrorSchema schema = errorSchema(detailMvc);
            Map<String, Object> body = errorBody(invalidCreate(detailPort), HttpStatus.BAD_REQUEST);

            assertThat(body).containsKeys(schema.required()).containsKeys("message", "trace", "errors");
            assertThat(body.keySet()).isSubsetOf(schema.properties());
            assertThat(body.get("errors")).asInstanceOf(InstanceOfAssertFactories.list(Map.class))
                    .hasSize(4)
                    .allSatisfy(error -> assertThat(error.keySet()).isSubsetOf(schema.bindingErrorProperties()));
        }
    }

    @Nested
    @ActiveProfiles("prod")
    class WithProdProfile {

        @Autowired
        private MockMvcTester prodMvc;

        @Test
        void apiDocsAndSwaggerUiAreNotServed() {
            assertThat(prodMvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(prodMvc.get().uri("/swagger-ui.html")).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(prodMvc.get().uri("/swagger-ui/index.html")).hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
