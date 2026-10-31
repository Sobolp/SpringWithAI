package org.example.springwithai.logs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LogPathsTest {

    @ParameterizedTest
    @CsvSource({
            "/actuator, /loggers, /actuator/loggers",
            "/manage/,  /loggers, /manage/loggers",
            "/,         /loggers, /loggers"})
    void joinUsesExactlyOneSlash(String base, String child, String expected) {
        assertThat(LogPaths.join(base, child)).isEqualTo(expected);
    }

    @Test
    void printableReplacesControlCharacters() {
        assertThat(LogPaths.printable("a.b\r\n2026-10-31 WARN fake\tline")).isEqualTo("a.b??2026-10-31 WARN fake?line");
    }

    @Test
    void printableKeepsOrdinaryText() {
        assertThat(LogPaths.printable("org.example.springwithai;x é")).isEqualTo("org.example.springwithai;x é");
    }
}
