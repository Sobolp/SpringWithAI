package org.example.springwithai.logs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Audits runtime log level changes ({@code POST /actuator/loggers/{name}}): after a successful change it logs, at
 * {@code WARN}, the logger or group, the old and new level, and who changed it. That is the logged-in user once
 * authentication exists, and the client's address until then. The change runs inside a
 * {@link LoggerLevelExpiry.Change}, which also starts the revert timer. {@link LogAccessConfig} registers it for the
 * loggers path only.
 */
class LoggerChangeAuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggerChangeAuditFilter.class);

    /** Decodes the path like Spring MVC does: path parameters ({@code ;x=1}) removed first, then decoded. */
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    static {
        PATHS.setDefaultEncoding(StandardCharsets.UTF_8.name());
    }

    private final LoggerLevelExpiry expiry;
    private final String loggersPrefix;

    /** @param loggersPath the loggers endpoint, e.g. {@code /actuator/loggers} */
    LoggerChangeAuditFilter(LoggerLevelExpiry expiry, String loggersPath) {
        this.expiry = expiry;
        this.loggersPrefix = loggersPath + "/";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || loggerName(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String name = loggerName(request).orElseThrow();
        try (LoggerLevelExpiry.Change change = expiry.begin(name)) {
            chain.doFilter(request, response);
            if (response.getStatus() != HttpServletResponse.SC_NO_CONTENT) {
                return;  // rejected (400, 403, ...): nothing changed
            }
            LoggerLevelExpiry.Outcome outcome = change.applied();
            log.warn("Log level of {} changed from {} to {} by {}{}", LogPaths.printable(name),
                    LoggerLevelExpiry.describe(outcome.before()), LoggerLevelExpiry.describe(outcome.after()),
                    changedBy(request), outcome.revertsAt().map(at -> "; reverts at " + at).orElse(""));
        }
    }

    private Optional<String> loggerName(HttpServletRequest request) {
        String path = PATHS.getPathWithinApplication(request);
        return (path.startsWith(loggersPrefix) && path.length() > loggersPrefix.length())
                ? Optional.of(path.substring(loggersPrefix.length()))
                : Optional.empty();
    }

    private static String changedBy(HttpServletRequest request) {
        Principal user = request.getUserPrincipal();
        return (user != null)
                ? "user " + LogPaths.printable(user.getName())
                : "remote address " + request.getRemoteAddr();
    }
}
