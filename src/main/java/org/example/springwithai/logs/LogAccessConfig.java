package org.example.springwithai.logs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Web access to the running app's logs. The file and log levels are served by Actuator ({@code /actuator/logfile},
 * {@code /actuator/loggers}); this adds the localhost-only guard and the level-change audit, and
 * {@link LogsPageConfig} adds the {@code /logs} page.
 * <p>
 * Both filters are registered for their paths only, so the servlet container matches them on the decoded,
 * normalized path without path parameters, the same path the request is dispatched on. They are declared here rather
 * than as {@code @Component}s, so {@code @WebMvcTest} slices leave them out.
 * <p>
 * {@code @EnableScheduling} makes Spring Boot create the {@code TaskScheduler} that {@link LoggerLevelExpiry} uses.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class LogAccessConfig {

    private final String actuatorBasePath;

    LogAccessConfig(@Value(LogPaths.ACTUATOR_BASE_PATH_PROPERTY) String actuatorBasePath) {
        this.actuatorBasePath = actuatorBasePath;
    }

    @Bean
    @ConditionalOnBooleanProperty(name = "app.logs.localhost-only", matchIfMissing = true)
    FilterRegistrationBean<LocalhostOnlyLogAccessFilter> localhostOnlyLogAccessFilter() {
        FilterRegistrationBean<LocalhostOnlyLogAccessFilter> registration =
                new FilterRegistrationBean<>(new LocalhostOnlyLogAccessFilter());
        // "/x/*" matches "/x" itself as well as everything below it.
        registration.addUrlPatterns(
                LogPaths.join(actuatorBasePath, "/logfile/*"),
                LogPaths.join(actuatorBasePath, "/loggers/*"),
                LogPaths.PAGE + "/*",
                LogPaths.PAGE_FILE);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    FilterRegistrationBean<LoggerChangeAuditFilter> loggerChangeAuditFilter(LoggerLevelExpiry expiry) {
        String loggersPath = LogPaths.join(actuatorBasePath, "/loggers");
        FilterRegistrationBean<LoggerChangeAuditFilter> registration =
                new FilterRegistrationBean<>(new LoggerChangeAuditFilter(expiry, loggersPath));
        registration.addUrlPatterns(loggersPath + "/*");
        return registration;
    }
}
