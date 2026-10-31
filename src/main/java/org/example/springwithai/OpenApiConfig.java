package org.example.springwithai;

import java.util.Optional;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * API metadata for the generated OpenAPI document ({@code /v3/api-docs}) and Swagger UI ({@code /swagger-ui.html}).
 * Both are served only when springdoc is enabled, which is the {@code dev} profile by default.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Shown when the build version is not available: the app was built without Gradle's build-info
     * (e.g. a plain IDE compile), or the build-info has no version.
     */
    static final String UNKNOWN_VERSION = "unknown";

    @Bean
    OpenAPI springWithAiOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        return new OpenAPI().info(new Info()
                .title("SpringWithAI API")
                .description("CRUD and search for aliens.")
                .version(apiVersion(buildProperties.getIfAvailable())));
    }

    /** The project version from build-info, or {@link #UNKNOWN_VERSION}; never null, as OpenAPI requires a version. */
    static String apiVersion(BuildProperties buildProperties) {
        return Optional.ofNullable(buildProperties)
                .map(BuildProperties::getVersion)
                .filter(StringUtils::hasText)
                .orElse(UNKNOWN_VERSION);
    }
}
