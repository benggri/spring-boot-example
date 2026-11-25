package com.benggri.example.graphql.error;

import com.benggri.example.core.common.DuplicateException;
import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.InvalidRequestException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.security.InvalidTokenException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.method.MethodValidationException;

/**
 * Maps exceptions to a fixed code and a fixed generic message. The exception's own message is
 * never copied into the response (it can contain emails, ids or passwords), and rejected values
 * are never echoed.
 */
@Component
public class GraphqlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphqlExceptionResolver.class);
    private static final int MAX_CAUSE_DEPTH = 5;

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    /** The wire contract: {@code extensions.code} plus a constant message. */
    enum Code {
        NOT_FOUND("Not found", ErrorType.NOT_FOUND),
        FORBIDDEN("Forbidden", ErrorType.FORBIDDEN),
        CONFLICT("Conflict", ErrorType.BAD_REQUEST),
        BAD_REQUEST("Invalid request", ErrorType.BAD_REQUEST),
        UNAUTHENTICATED("Unauthenticated", ErrorType.UNAUTHORIZED),
        INTERNAL_ERROR("Internal error", ErrorType.INTERNAL_ERROR);

        private final String message;
        private final ErrorType type;

        Code(String message, ErrorType type) {
            this.message = message;
            this.type = type;
        }
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        Code code = classify(ex, 0);
        if (code == Code.INTERNAL_ERROR) {
            // server-side only: the exception type and field path, never request variables
            log.error("Unexpected error resolving {} ({})", env.getExecutionStepInfo().getPath(),
                    ex.getClass().getName(), ex);
        }
        return GraphqlErrorBuilder.newError(env)
                .errorType(code.type)
                .message(code.message)
                .extensions(Map.of("code", code.name()))
                .build();
    }

    private Code classify(Throwable ex, int depth) {
        Code direct = direct(ex);
        if (direct != null) {
            return direct;
        }
        boolean wrapped = ex.getCause() != null && ex.getCause() != ex && depth < MAX_CAUSE_DEPTH;
        return wrapped ? classify(ex.getCause(), depth + 1) : Code.INTERNAL_ERROR;
    }

    private Code direct(Throwable ex) {
        Code code = null;
        if (ex instanceof NotFoundException) {
            code = Code.NOT_FOUND;
        } else if (ex instanceof ForbiddenException) {
            code = Code.FORBIDDEN;
        } else if (ex instanceof DuplicateException) {
            code = Code.CONFLICT;
        } else if (isBadRequest(ex)) {
            code = Code.BAD_REQUEST;
        } else if (ex instanceof AuthenticationException || ex instanceof InvalidTokenException) {
            code = Code.UNAUTHENTICATED;
        } else if (ex instanceof AccessDeniedException) {
            code = isAnonymous() ? Code.UNAUTHENTICATED : Code.FORBIDDEN;
        }
        return code;
    }

    private static boolean isBadRequest(Throwable ex) {
        return ex instanceof InvalidRequestException || ex instanceof ConstraintViolationException
                || ex instanceof MethodValidationException || ex instanceof BindException
                || ex instanceof TypeMismatchException;
    }

    private boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || trustResolver.isAnonymous(authentication);
    }
}
