package com.benggri.example.core.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from {@code app.jwt.*}. The secret must be supplied externally (e.g.
 * {@code JWT_SECRET}) and has no default. Validation messages never echo the secret.
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.jwt.secret must be configured");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (UTF-8)");
        }
        requirePositive(accessTtl, "app.jwt.access-ttl");
        requirePositive(refreshTtl, "app.jwt.refresh-ttl");
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=***, accessTtl=" + accessTtl + ", refreshTtl=" + refreshTtl + "]";
    }

    private static void requirePositive(Duration ttl, String name) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }
}
