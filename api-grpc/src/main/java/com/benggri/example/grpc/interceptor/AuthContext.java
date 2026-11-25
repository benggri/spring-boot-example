package com.benggri.example.grpc.interceptor;

import com.benggri.example.core.security.AuthUser;
import io.grpc.Context;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/**
 * The authenticated caller of the current gRPC call. It lives in the gRPC {@link Context} (not in
 * SecurityContextHolder): gRPC callbacks may run on different threads, and Context is what gRPC propagates.
 */
public final class AuthContext {

    static final Context.Key<AuthUser> KEY = Context.key("authUser");

    private AuthContext() {
    }

    /** The caller; fails closed (UNAUTHENTICATED) if a protected handler runs without one. */
    public static AuthUser current() {
        AuthUser user = KEY.get();
        if (user == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated caller");
        }
        return user;
    }
}
