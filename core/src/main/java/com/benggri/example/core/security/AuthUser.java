package com.benggri.example.core.security;

import com.benggri.example.core.member.entity.Role;

public record AuthUser(Long id, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
