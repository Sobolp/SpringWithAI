package org.example.springwithai.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.example.springwithai.AppIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Log access on the running app: the log file and log levels through Actuator, the {@code /logs} page, and the
 * localhost-only stopgap. MockMvc sends from {@code 127.0.0.1} unless a test sets another remote address.
 */
@AppIntegrationTest
class LogAccessTest {

    /** A logger of the app's own package, used to write marker lines. */
    private static final Logger probe = LoggerFactory.getLogger("org.example.springwithai.logs.Probe");

    private static final String APP_PACKAGE = "org.example.springwithai";
    private static final String REMOTE = "10.0.0.5";

    /** One of Spring Boot's built-in logger groups. */
    private static final String WEB_GROUP = "web";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private LoggingSystem loggingSystem;

    @Autowired
    private ApplicationContext context;

    @Value("${logging.file.name}")
    private Path logFile;

    @LocalServerPort
    private int port;

    @AfterEach
    void resetAppLogLevel() {
        // Through the endpoint, so a pending revert is cancelled too.
        setLevel(mvc, APP_PACKAGE, null).exchange();
        setLevel(mvc, WEB_GROUP, null).exchange();
    }

    // ---- Log file ----

    @Test
    void logfileReturnsTheLogAsUtf8Text() {
        assertThat(mvc.get().uri("/actuator/logfile"))
                .hasStatusOk()
                .hasContentType("text/plain;charset=UTF-8")
                .bodyText().contains("Tomcat started on port " + port);
    }

    @Test
    void suffixRangeReturnsExactlyTheLastBytes() throws IOException {
        MvcTestResult result = mvc.get().uri("/actuator/logfile").header(HttpHeaders.RANGE, "bytes=-1024").exchange();

        assertThat(result).hasStatus(HttpStatus.PARTIAL_CONTENT);
        long total = totalSize(result);
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(readLog(total - 1024, total));
    }

    @Test
    void openRangeReturnsOnlyWhatWasWrittenSince() throws IOException {
        long size = Files.size(logFile);
        String marker = marker();
        probe.info(marker);

        MvcTestResult result = mvc.get().uri("/actuator/logfile").header(HttpHeaders.RANGE, "bytes=" + size + "-")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.PARTIAL_CONTENT);
        // Nothing older than the marker; other threads may legitimately log after it.
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8).lines())
                .first().asString().contains(" INFO ", marker);
    }

    /** The /logs page reloads the tail when this happens, which is how it notices that the file rolled. */
    @Test
    void rangePastTheEndIsNotSatisfiable() throws IOException {
        long pastTheEnd = Files.size(logFile) + 1_000_000;

        assertThat(mvc.get().uri("/actuator/logfile").header(HttpHeaders.RANGE, "bytes=" + pastTheEnd + "-"))
                .hasStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    // ---- Page ----

    @Test
    void logsForwardsToTheStaticPage() {
        assertThat(mvc.get().uri("/logs")).hasStatusOk().hasForwardedUrl("/logs.html");
        assertThat(mvc.get().uri("/logs.html"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
                .bodyText().contains("class RangePollingLogStream", "function createLogStream()");
    }

    // ---- Log levels ----

    @Test
    void loggersListsLevels() {
        assertThat(mvc.get().uri("/actuator/loggers"))
                .hasStatusOk()
                .bodyJson().hasPath("$.loggers.ROOT.effectiveLevel");
        assertThat(mvc.get().uri("/actuator/loggers/" + APP_PACKAGE))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("{\"effectiveLevel\": \"INFO\"}");
    }

    @Test
    void levelChangeTakesEffectAndIsAudited() throws IOException {
        long size = Files.size(logFile);
        String marker = marker();

        assertThat(setLevel(mvc, APP_PACKAGE, LogLevel.DEBUG)).hasStatus(HttpStatus.NO_CONTENT);
        probe.debug(marker);

        assertThat(loggingSystem.getLoggerConfiguration(APP_PACKAGE).getConfiguredLevel()).isEqualTo(LogLevel.DEBUG);
        assertThat(readLogFrom(size))
                .contains(" DEBUG ", marker)
                .containsPattern(" WARN .* Log level of org\\.example\\.springwithai changed from inherited to DEBUG "
                        + "by remote address 127\\.0\\.0\\.1; reverts at \\S+Z");
    }

    /** A group has no logger of its own: the audit and the revert must follow the group, as Actuator does. */
    @Test
    void groupChangeIsAuditedAndScheduledToRevert() throws IOException {
        long size = Files.size(logFile);

        assertThat(setLevel(mvc, WEB_GROUP, LogLevel.WARN)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(loggingSystem.getLoggerConfiguration("org.springframework.web").getConfiguredLevel())
                .isEqualTo(LogLevel.WARN);
        assertThat(readLogFrom(size)).containsPattern("Log level of web changed from inherited to WARN "
                + "by remote address 127\\.0\\.0\\.1; reverts at \\S+Z");
    }

    @Test
    void levelResetIsAuditedWithoutARevert() throws IOException {
        setLevel(mvc, APP_PACKAGE, LogLevel.DEBUG).exchange();
        long size = Files.size(logFile);

        assertThat(setLevel(mvc, APP_PACKAGE, null)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(loggingSystem.getLoggerConfiguration(APP_PACKAGE).getConfiguredLevel()).isNull();
        assertThat(readLogFrom(size))
                .contains("Log level of org.example.springwithai changed from DEBUG to inherited by remote address")
                .doesNotContain("reverts at");
    }

    @Test
    void invalidLevelIsRejectedAndNotAudited() throws IOException {
        long size = Files.size(logFile);

        assertThat(mvc.post().uri("/actuator/loggers/" + APP_PACKAGE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\": \"LOUD\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(readLogFrom(size)).doesNotContain("Log level of");
    }

    // ---- Exposure ----

    @Test
    void healthIsPublicAndShowsOnlyTheStatus() {
        assertThat(mvc.get().uri("/actuator/health").with(remoteAddress(REMOTE)))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("{\"status\": \"UP\"}");
    }

    /** Readiness counts towards health, so a load balancer sends no traffic before startup has finished. */
    @Test
    void healthIsOutOfServiceWhileTheAppIsNotReady() {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        try {
            assertThat(mvc.get().uri("/actuator/health"))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .bodyJson().isStrictlyEqualTo("{\"status\": \"OUT_OF_SERVICE\"}");
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }

        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"env", "beans", "heapdump", "threaddump", "configprops", "mappings", "metrics",
            "shutdown"})
    void otherActuatorEndpointsAreNotExposed(String endpoint) {
        assertThat(mvc.get().uri("/actuator/" + endpoint)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- Localhost-only stopgap ----

    @ParameterizedTest
    @CsvSource({
            "GET,  /actuator/logfile",
            "GET,  /actuator/loggers",
            "GET,  /actuator/loggers/ROOT",
            "POST, /actuator/loggers/ROOT",
            "GET,  /logs",
            "GET,  /logs/",
            "GET,  /logs.html",
            "GET,  /actuator/%6Cogfile",
            "GET,  /actuator/logfile;x=1"})
    void logPathsRefuseRemoteClients(String method, String path) {
        assertThat(request(method, path).with(remoteAddress(REMOTE))).hasStatus(HttpStatus.FORBIDDEN);
    }

    /**
     * DNS rebinding: a page on another domain, re-pointed at 127.0.0.1, connects from this machine but sends its own
     * domain as Host.
     */
    @ParameterizedTest
    @ValueSource(strings = {"attacker.example", "attacker.example:8080", "localhost.attacker.example",
            "192.168.1.10:8080"})
    void foreignHostIsRefusedEvenFromLoopback(String host) {
        assertThat(mvc.get().uri("/actuator/logfile").header(HttpHeaders.HOST, host))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(setLevel(mvc, APP_PACKAGE, LogLevel.TRACE).header(HttpHeaders.HOST, host))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(loggingSystem.getLoggerConfiguration(APP_PACKAGE).getConfiguredLevel()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "localhost:8080", "127.0.0.1:8080", "[::1]:8080"})
    void localHostNamesAreAccepted(String host) {
        assertThat(mvc.get().uri("/actuator/logfile").header(HttpHeaders.HOST, host)).hasStatusOk();
    }

    @Test
    void forwardedForHeaderDoesNotMakeARemoteClientLocal() {
        assertThat(mvc.get().uri("/actuator/logfile").with(remoteAddress(REMOTE))
                .header("X-Forwarded-For", "127.0.0.1").header("Forwarded", "for=127.0.0.1"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void remoteLevelChangeIsRefusedAndChangesNothing() {
        assertThat(setLevel(mvc, APP_PACKAGE, LogLevel.TRACE).with(remoteAddress(REMOTE)))
                .hasStatus(HttpStatus.FORBIDDEN);

        assertThat(loggingSystem.getLoggerConfiguration(APP_PACKAGE).getConfiguredLevel()).isNull();
    }

    @Test
    void ipv6LoopbackIsLocal() {
        assertThat(mvc.get().uri("/actuator/logfile").with(remoteAddress("0:0:0:0:0:0:0:1"))).hasStatusOk();
    }

    @Test
    void apiStaysOpenToRemoteClients() {
        assertThat(mvc.get().uri("/api/aliens").with(remoteAddress(REMOTE))).hasStatusOk();
    }

    /**
     * Over a real socket from this machine's own network address, which is not a loopback address: the 403 goes
     * through Spring Boot's error handling and has the documented {@code ApiError} body.
     */
    @Test
    void refusalOverTheNetworkHasTheApiErrorBody() throws Exception {
        Optional<InetAddress> ownAddress = nonLoopbackAddress();
        assumeTrue(ownAddress.isPresent(), "needs a network interface with a non-loopback IPv4 address");
        String base = "http://" + ownAddress.get().getHostAddress() + ":" + port;

        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpResponse<String> refused = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/actuator/logfile")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(refused.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
            Map<String, Object> body = JsonPath.read(refused.body(), "$");
            assertThat(body).containsOnlyKeys("timestamp", "status", "error", "path")
                    .containsEntry("status", 403)
                    .containsEntry("path", "/actuator/logfile");

            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/actuator/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(HttpStatus.OK.value());
        }
    }

    /** Settings other than the defaults, in a context of their own. */
    @Nested
    @TestPropertySource(properties = {
            "app.logs.level-ttl=1s",
            "app.logs.localhost-only=false",
            "management.endpoint.logfile.external-file=build/test-logs/does-not-exist.log",
            "logging.group.probes=org.example.springwithai.logs.Probe"})
    class WithOtherSettings {

        private static final String PROBE = "org.example.springwithai.logs.Probe";

        @Autowired
        private MockMvcTester otherMvc;

        @AfterEach
        void resetProbeLevel() {
            setLevel(otherMvc, PROBE, null).exchange();
        }

        @Test
        void missingLogFileIsNotFound() {
            assertThat(otherMvc.get().uri("/actuator/logfile")).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void changedLevelRevertsAfterTheTtl() throws IOException {
            long size = Files.size(logFile);

            assertThat(setLevel(otherMvc, PROBE, LogLevel.DEBUG)).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(loggingSystem.getLoggerConfiguration(PROBE).getConfiguredLevel()).isEqualTo(LogLevel.DEBUG);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(loggingSystem.getLoggerConfiguration(PROBE).getConfiguredLevel()).isNull());
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(readLogFrom(size))
                    .contains("Log level of " + PROBE + " reverted to inherited after PT1S (expired)"));
        }

        @Test
        void changedGroupRevertsAfterTheTtl() {
            assertThat(setLevel(otherMvc, "probes", LogLevel.DEBUG)).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(loggingSystem.getLoggerConfiguration(PROBE).getConfiguredLevel()).isEqualTo(LogLevel.DEBUG);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(loggingSystem.getLoggerConfiguration(PROBE).getConfiguredLevel()).isNull());
        }

        @Test
        void remoteClientsAreAllowedWhenTheStopgapIsOff() {
            assertThat(otherMvc.get().uri("/actuator/loggers").with(remoteAddress(REMOTE))).hasStatusOk();
        }
    }

    // ---- Helpers ----

    private MockMvcRequestBuilder request(String method, String path) {
        URI uri = URI.create(path);  // already encoded: a String template would encode the % again
        return "POST".equals(method)
                ? mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\": \"DEBUG\"}")
                : mvc.get().uri(uri);
    }

    private static MockMvcRequestBuilder setLevel(MockMvcTester mvc, String logger, LogLevel level) {
        String json = (level != null) ? "\"" + level + "\"" : "null";
        return mvc.post().uri("/actuator/loggers/" + logger)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuredLevel\": " + json + "}");
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String marker() {
        return "marker-" + UUID.randomUUID();
    }

    /** "Content-Range: bytes 100-199/200" gives 200. */
    private static long totalSize(MvcTestResult result) {
        String contentRange = result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE);
        assertThat(contentRange).startsWith("bytes ");
        return Long.parseLong(contentRange.substring(contentRange.indexOf('/') + 1));
    }

    private byte[] readLog(long from, long to) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            byte[] bytes = new byte[Math.toIntExact(to - from)];
            file.seek(from);
            file.readFully(bytes);
            return bytes;
        }
    }

    private String readLogFrom(long from) throws IOException {
        return new String(readLog(from, Files.size(logFile)), StandardCharsets.UTF_8);
    }

    private static Optional<InetAddress> nonLoopbackAddress() throws SocketException {
        return NetworkInterface.networkInterfaces()
                .filter(LogAccessTest::isUp)
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> address instanceof Inet4Address)
                .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                .findFirst();
    }

    private static boolean isUp(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp() && !networkInterface.isLoopback();
        } catch (SocketException e) {
            return false;
        }
    }
}
