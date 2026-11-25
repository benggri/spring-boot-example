package com.benggri.example.grpc.validation;

import lombok.Getter;

/** A request field failed validation. Carries only the field name, never the rejected value. */
@Getter
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field) {
        super(field, null, false, false);
        this.field = field;
    }
}
