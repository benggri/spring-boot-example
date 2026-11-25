package com.benggri.example.grpc.error;

import com.benggri.example.core.common.DuplicateException;
import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.InvalidRequestException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.security.InvalidTokenException;
import com.benggri.example.grpc.validation.InvalidFieldException;
import io.grpc.Status;
import io.grpc.StatusException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * The single place where exceptions become gRPC statuses (Boot wires every {@link GrpcExceptionHandler}
 * bean into its global exception interceptor). Descriptions are fixed per code: exception messages can
 * contain emails or other user input, so they are never sent; no trailers or details are attached.
 */
@Slf4j
@Component
public class GrpcExceptionMapper implements GrpcExceptionHandler {

    static final String NOT_FOUND = "Not found";
    static final String PERMISSION_DENIED = "Permission denied";
    static final String ALREADY_EXISTS = "Already exists";
    static final String INVALID_ARGUMENT = "Invalid argument";
    static final String UNAUTHENTICATED = "Unauthenticated";
    static final String INTERNAL = "Internal error";

    @Override
    public StatusException handleException(Throwable exception) {
        return toStatus(exception).asException();
    }

    Status toStatus(Throwable exception) {
        return switch (exception) {
            case InvalidFieldException e -> Status.INVALID_ARGUMENT.withDescription(e.getField() + ": invalid");
            case NotFoundException e -> Status.NOT_FOUND.withDescription(NOT_FOUND);
            case ForbiddenException e -> Status.PERMISSION_DENIED.withDescription(PERMISSION_DENIED);
            case DuplicateException e -> Status.ALREADY_EXISTS.withDescription(ALREADY_EXISTS);
            case InvalidRequestException e -> Status.INVALID_ARGUMENT.withDescription(INVALID_ARGUMENT);
            case AuthenticationException e -> Status.UNAUTHENTICATED.withDescription(UNAUTHENTICATED);
            case InvalidTokenException e -> Status.UNAUTHENTICATED.withDescription(UNAUTHENTICATED);
            default -> internal(exception);
        };
    }

    private Status internal(Throwable exception) {
        // server-side only; the request payload is never logged
        log.error("Unexpected failure while handling a gRPC call", exception);
        return Status.INTERNAL.withDescription(INTERNAL);
    }
}
