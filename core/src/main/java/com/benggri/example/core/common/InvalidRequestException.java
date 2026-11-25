package com.benggri.example.core.common;

/** Input that violates a business rule; adapters map it to their "bad request" form. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
