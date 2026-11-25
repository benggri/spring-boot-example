package com.benggri.example.core.security;

/** Thrown for any unusable JWT. Messages never contain the token or the signing secret. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
