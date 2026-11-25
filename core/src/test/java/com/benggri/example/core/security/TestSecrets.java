package com.benggri.example.core.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates throwaway key material at runtime; no secret-looking literal is ever committed. */
final class TestSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestSecrets() {
    }

    static String randomSecret() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
