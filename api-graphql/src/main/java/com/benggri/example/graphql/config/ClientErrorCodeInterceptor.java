package com.benggri.example.graphql.config;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

/**
 * graphql-java reports malformed requests (syntax, schema validation, null for a non-null
 * argument, bad variables) before any resolver runs, so they bypass the exception resolver and
 * carry no {@code extensions.code}. This gives exactly those errors the project's
 * {@code BAD_REQUEST} code; the message stays graphql-java's own. Those validation/coercion messages can
 * quote client-supplied literals back to the same client; they are not logged by this application.
 */
public class ClientErrorCodeInterceptor implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        return chain.next(request).map(response -> {
            List<GraphQLError> errors = response.getExecutionResult().getErrors();
            if (errors.stream().noneMatch(ClientErrorCodeInterceptor::needsCode)) {
                return response;
            }
            return response.transform(builder ->
                    builder.errors(errors.stream().map(ClientErrorCodeInterceptor::withCode).toList()));
        });
    }

    private static boolean isClientError(GraphQLError error) {
        return error.getErrorType() == ErrorType.ValidationError || error.getErrorType() == ErrorType.InvalidSyntax;
    }

    private static boolean needsCode(GraphQLError error) {
        return isClientError(error) && (error.getExtensions() == null || !error.getExtensions().containsKey("code"));
    }

    private static GraphQLError withCode(GraphQLError error) {
        if (!needsCode(error)) {
            return error;
        }
        Map<String, Object> extensions = new HashMap<>();
        if (error.getExtensions() != null) {
            extensions.putAll(error.getExtensions());
        }
        extensions.put("code", "BAD_REQUEST");
        return GraphqlErrorBuilder.newError().message(error.getMessage()).locations(error.getLocations())
                .path(error.getPath()).errorType(error.getErrorType()).extensions(extensions).build();
    }
}
