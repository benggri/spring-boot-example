package com.benggri.example.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benggri.example.core.member.entity.Role;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUser_readsIdAndRoleFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
                .subject("7").claim("role", "ADMIN").claim("typ", "access")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_ADMIN"));

        assertThat(CurrentUser.get()).isEqualTo(new AuthUser(7L, Role.ADMIN));
    }

    @Test
    void currentUser_withoutAuthenticationThrows() {
        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void currentUser_nonJwtPrincipalThrows() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("anonymous", null, "ROLE_X"));

        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void currentUser_jwtWithoutRoleThrowsAuthenticationException() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("7").claim("typ", "access").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_X"));

        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void currentUser_jwtWithNonNumericSubThrowsAuthenticationException() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("abc").claim("role", "USER").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_X"));

        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationException.class);
    }
}
