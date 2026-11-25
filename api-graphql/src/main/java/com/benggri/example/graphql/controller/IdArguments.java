package com.benggri.example.graphql.controller;

import com.benggri.example.core.common.InvalidRequestException;

/** The {@code ID} scalar accepts "", which the argument binder turns into null. */
final class IdArguments {

    private IdArguments() {
    }

    static Long requireId(Long id) {
        if (id == null) {
            throw new InvalidRequestException("Invalid id");
        }
        return id;
    }
}
