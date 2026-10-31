package org.example.springwithai.logs;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;

/** Keeps configured levels in a map, without touching Logback; a logger without one inherits INFO. */
final class FakeLoggingSystem extends LoggingSystem {

    private final Map<String, LogLevel> levels = new HashMap<>();

    @Override
    public void beforeInitialize() {
    }

    @Override
    public void setLogLevel(@Nullable String name, @Nullable LogLevel level) {
        if (level == null) {
            levels.remove(name);
        } else {
            levels.put(name, level);
        }
    }

    @Override
    public LoggerConfiguration getLoggerConfiguration(String name) {
        LogLevel configured = levels.get(name);
        return new LoggerConfiguration(name, configured, (configured != null) ? configured : LogLevel.INFO);
    }
}
