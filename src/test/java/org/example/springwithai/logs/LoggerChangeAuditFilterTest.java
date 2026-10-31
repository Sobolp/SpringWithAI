package org.example.springwithai.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.TaskScheduler;

/**
 * The audit filter around a stand-in for the Actuator endpoint: the chain sets the level and the response status, as
 * {@code POST /actuator/loggers/{name}} would.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LoggerChangeAuditFilterTest {

    private static final String NAME = "org.example.springwithai";
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Mock
    private TaskScheduler scheduler;

    private final FakeLoggingSystem logging = new FakeLoggingSystem();

    private LoggerChangeAuditFilter filter;

    @BeforeEach
    void setUp() {
        LoggerLevelExpiry expiry =
                new LoggerLevelExpiry(logging, new LoggerGroups(), scheduler, Duration.ofMinutes(30));
        filter = new LoggerChangeAuditFilter(expiry, "/actuator/loggers");
    }

    @Test
    void successfulChangeIsLoggedWithTheClientAddressAndScheduledToRevert(CapturedOutput output) throws Exception {
        allowScheduling();

        filter.doFilter(post(NAME), new MockHttpServletResponse(), endpointSetting(NAME, LogLevel.DEBUG, 204));

        assertThat(output).contains("Log level of org.example.springwithai changed from inherited to DEBUG "
                + "by remote address 10.0.0.5; reverts at 2026-10-31T12:30:00Z");
    }

    /** Once authentication exists, the request carries the logged-in user; the audit names the user instead. */
    @Test
    void loggedInUserIsNamedInsteadOfTheAddress(CapturedOutput output) throws Exception {
        allowScheduling();
        MockHttpServletRequest request = post(NAME);
        request.setUserPrincipal(() -> "alice");

        filter.doFilter(request, new MockHttpServletResponse(), endpointSetting(NAME, LogLevel.DEBUG, 204));

        assertThat(output).contains("changed from inherited to DEBUG by user alice;").doesNotContain("10.0.0.5");
    }

    @Test
    void rejectedChangeIsNeitherLoggedNorScheduled(CapturedOutput output) throws Exception {
        filter.doFilter(post(NAME), new MockHttpServletResponse(), endpointSetting(NAME, null, 400));

        assertThat(output).doesNotContain("Log level of");
        verifyNoInteractions(scheduler);
    }

    @Test
    void changeBackToTheOriginalIsLoggedWithoutARevert(CapturedOutput output) throws Exception {
        logging.setLogLevel(NAME, LogLevel.WARN);

        filter.doFilter(post(NAME), new MockHttpServletResponse(), endpointSetting(NAME, LogLevel.WARN, 204));

        assertThat(output).contains("Log level of org.example.springwithai changed from WARN to WARN by")
                .doesNotContain("reverts at");
        verifyNoInteractions(scheduler);
    }

    @Test
    void controlCharactersInTheLoggerNameCannotForgeLogLines(CapturedOutput output) throws Exception {
        allowScheduling();
        String forged = "a\n2026-10-31 ERROR fake";

        filter.doFilter(post(forged), new MockHttpServletResponse(), endpointSetting(forged, LogLevel.DEBUG, 204));

        assertThat(output).contains("Log level of a?2026-10-31 ERROR fake changed").doesNotContain("a\n2026");
    }

    /** The name is decoded like Spring MVC does: path parameters dropped first, then %-escapes decoded. */
    @ParameterizedTest
    @CsvSource({
            "/actuator/loggers/com.acme%3Bx,           com.acme;x",
            "/actuator/loggers/com.acme;jsessionid=1,  com.acme",
            "/actuator/loggers/com.%61cme,             com.acme"})
    void loggerNameIsTakenFromTheDecodedPath(String uri, String name, CapturedOutput output) throws Exception {
        allowScheduling();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);

        filter.doFilter(request, new MockHttpServletResponse(), endpointSetting(name, LogLevel.DEBUG, 204));

        assertThat(output).contains("Log level of " + name + " changed from inherited to DEBUG");
    }

    @ParameterizedTest
    @CsvSource({
            "GET,  /actuator/loggers/org.example",
            "POST, /actuator/loggers/",
            "POST, /actuator/loggers",
            "POST, /actuator/logfile",
            "POST, /api/aliens"})
    void otherRequestsPassThroughUntouched(String method, String path, CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);

        filter.doFilter(request, new MockHttpServletResponse(), endpointSetting(NAME, LogLevel.DEBUG, 204));

        assertThat(logging.getLoggerConfiguration(NAME).getConfiguredLevel()).isEqualTo(LogLevel.DEBUG);
        assertThat(output).doesNotContain("Log level of");
        verifyNoInteractions(scheduler);
    }

    private void allowScheduling() {
        when(scheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(invocation -> mock(ScheduledFuture.class));
    }

    private static MockHttpServletRequest post(String logger) {
        String path = "/actuator/loggers/" + logger;
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("10.0.0.5");
        return request;
    }

    /** Stands in for the loggers endpoint: sets the level (unless rejected) and the status. */
    private FilterChain endpointSetting(String logger, LogLevel level, int status) {
        return (request, response) -> {
            if (status == HttpServletResponse.SC_NO_CONTENT) {
                logging.setLogLevel(logger, level);
            }
            ((HttpServletResponse) response).setStatus(status);
        };
    }
}
