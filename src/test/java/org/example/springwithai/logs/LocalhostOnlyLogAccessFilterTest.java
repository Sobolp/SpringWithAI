package org.example.springwithai.logs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LocalhostOnlyLogAccessFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.0.0.2", "::1", "0:0:0:0:0:0:0:1"})
    void loopbackAddressesAreLocal(String address) {
        assertThat(LocalhostOnlyLogAccessFilter.isLoopback(address)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.5", "192.168.1.10", "0.0.0.0", "::", "fe80::1", "::ffff:10.0.0.5"})
    void otherAddressesAreNotLocal(String address) {
        assertThat(LocalhostOnlyLogAccessFilter.isLoopback(address)).isFalse();
    }

    /** Host names are never resolved: "localhost" could be mapped to anything, and a lookup costs time. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"localhost", "127.0.0.1.example.com", "not an address"})
    void anythingButAnIpLiteralIsNotALoopbackAddress(String address) {
        assertThat(LocalhostOnlyLogAccessFilter.isLoopback(address)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LocalHost", "127.0.0.1", "[::1]", "::1"})
    void localHostNamesAreAccepted(String serverName) {
        assertThat(LocalhostOnlyLogAccessFilter.isLocalHostName(serverName)).isTrue();
    }

    /** What a DNS-rebinding page sends as Host: its own domain, or anything that is not this machine by name. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"attacker.example", "localhost.attacker.example", "127.0.0.1.nip.io", "192.168.1.10",
            "[fe80::1]", "[::1"})
    void otherHostNamesAreRefused(String serverName) {
        assertThat(LocalhostOnlyLogAccessFilter.isLocalHostName(serverName)).isFalse();
    }
}
