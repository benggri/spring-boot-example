package com.benggri.example.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 50) String name) {

    /** Keeps the raw password out of logs and exception text. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, name=" + name + "]";
    }
}
