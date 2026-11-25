package com.benggri.example.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(@NotBlank @Size(max = 50) String name) {
}
