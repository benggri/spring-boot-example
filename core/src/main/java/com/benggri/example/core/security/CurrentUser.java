package com.benggri.example.core.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves the caller from the JWT in the {@code SecurityContext} (shared by REST and GraphQL). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated JWT principal");
        }
        try {
            return JwtProvider.toAuthUser(jwt);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationCredentialsNotFoundException("JWT lacks a valid subject or role", e);
        }
    }
}
