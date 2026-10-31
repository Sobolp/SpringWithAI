package org.example.springwithai.logs;

import java.util.regex.Pattern;

/** Request paths of the log access feature, and a helper for logging values taken from requests. */
final class LogPaths {

    /** The live log page; {@link LogsPageConfig} forwards it to {@link #PAGE_FILE}. */
    static final String PAGE = "/logs";

    /** The static page itself, in {@code src/main/resources/static}. */
    static final String PAGE_FILE = "/logs.html";

    /** Default of {@code management.endpoints.web.base-path}. */
    static final String ACTUATOR_BASE_PATH_PROPERTY = "${management.endpoints.web.base-path:/actuator}";

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private LogPaths() {
    }

    /** {@code base} and {@code child} joined with exactly one slash, so a base path of {@code /} also works. */
    static String join(String base, String child) {
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + child;
    }

    /** Replaces control characters, so a value taken from a request cannot forge extra log lines. */
    static String printable(String value) {
        return CONTROL_CHARACTERS.matcher(value).replaceAll("?");
    }
}
