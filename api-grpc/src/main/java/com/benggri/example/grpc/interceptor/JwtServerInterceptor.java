package com.benggri.example.grpc.interceptor;

import com.benggri.example.core.security.AuthUser;
import com.benggri.example.core.security.JwtProvider;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Authenticates every call from the {@code authorization: Bearer <access token>} metadata entry and puts
 * the {@link AuthUser} into the gRPC {@link Context}. Everything is protected unless it is listed in
 * {@link #PUBLIC_METHODS}, so a newly added RPC is secure by default. A credential that is present but
 * invalid is always rejected, even on a public method: it is never silently treated as anonymous.
 * The rejection is generic; neither the token nor any parser message is ever echoed.
 */
@Component
@GlobalServerInterceptor
@Order(10)
@RequiredArgsConstructor
public class JwtServerInterceptor implements ServerInterceptor {

    /** The ONLY methods callable without a token (full gRPC method names). */
    public static final Set<String> PUBLIC_METHODS = Set.of(
            "example.v1.AuthService/Login",
            "example.v1.AuthService/Refresh",
            "example.v1.MemberService/Register");

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "bearer ";

    private final JwtProvider jwtProvider;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        List<String> values = authorizationValues(headers);
        if (values.isEmpty()) {
            return isPublic(call) ? next.startCall(call, headers) : reject(call);
        }
        AuthUser user = authenticate(values);
        if (user == null) {
            return reject(call);
        }
        return Contexts.interceptCall(Context.current().withValue(AuthContext.KEY, user), call, headers, next);
    }

    /** Metadata.getAll returns null (not an empty iterable) when the key is absent. */
    private static List<String> authorizationValues(Metadata headers) {
        List<String> values = new ArrayList<>();
        Iterable<String> all = headers.getAll(AUTHORIZATION);
        if (all != null) {
            all.forEach(values::add);
        }
        return values;
    }

    private static boolean isPublic(ServerCall<?, ?> call) {
        return PUBLIC_METHODS.contains(call.getMethodDescriptor().getFullMethodName());
    }

    /** The caller, or null for anything but exactly one well-formed, valid access token. */
    private AuthUser authenticate(List<String> values) {
        if (values.size() != 1) {
            return null;
        }
        String value = values.get(0);
        if (value.length() <= BEARER_PREFIX.length()
                || !value.substring(0, BEARER_PREFIX.length()).toLowerCase(Locale.ROOT).equals(BEARER_PREFIX)) {
            return null;
        }
        try {
            return jwtProvider.parseAccess(value.substring(BEARER_PREFIX.length()).trim());
        } catch (RuntimeException e) {
            return null; // fail closed on any parse failure
        }
    }

    private static <ReqT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, ?> call) {
        call.close(Status.UNAUTHENTICATED.withDescription("Unauthenticated"), new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
