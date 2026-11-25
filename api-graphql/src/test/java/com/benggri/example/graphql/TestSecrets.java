package com.benggri.example.graphql;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Throwaway runtime key material: no secret literal is ever committed, not even in tests. */
public final class TestSecrets {

    public static final String SECRET = randomSecret();

    private TestSecrets() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> SECRET);
        registry.add("app.jwt.access-ttl", () -> "PT15M");
        registry.add("app.jwt.refresh-ttl", () -> "P7D");
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
