package com.benggri.example.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final Duration TTL = Duration.ofMinutes(1);

    @Test
    void jwtProperties_shortSecretFailsStartup() {
        String shortSecret = TestSecrets.randomSecret().substring(0, 31);

        assertThatThrownBy(() -> new JwtProperties(shortSecret, TTL, TTL))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(shortSecret));
    }

    @Test
    void jwtProperties_countsBytesNotCharacters() {
        // 16 two-byte characters = 32 bytes is accepted; 15 of them = 30 bytes is not
        assertThatCode(() -> new JwtProperties("é".repeat(16), TTL, TTL)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new JwtProperties("é".repeat(15), TTL, TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jwtProperties_blankOrMissingSecretFails() {
        assertThatThrownBy(() -> new JwtProperties(null, TTL, TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("   ", TTL, TTL)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jwtProperties_nonPositiveTtlFails() {
        String secret = TestSecrets.randomSecret();

        assertThatThrownBy(() -> new JwtProperties(secret, null, TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(secret, TTL, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(secret, Duration.ofSeconds(-1), TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
