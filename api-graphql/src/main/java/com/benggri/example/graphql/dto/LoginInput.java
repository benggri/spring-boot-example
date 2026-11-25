package com.benggri.example.graphql.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginInput(@NotBlank String email, @NotBlank String password) {

    @Override
    public String toString() {
        return "LoginInput[email=" + email + ", password=***]";
    }
}
