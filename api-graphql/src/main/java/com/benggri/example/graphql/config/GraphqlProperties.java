package com.benggri.example.graphql.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.graphql.*}: caps that bound how expensive one request can be. {@code maxQueryDepth}
 * limits nesting; {@code maxRootFields} limits root fields per operation, so aliasing cannot
 * multiply login/register (BCrypt) work inside one HTTP request.
 */
@Validated
@ConfigurationProperties("app.graphql")
public record GraphqlProperties(@DefaultValue("10") @Min(1) int maxQueryDepth,
        @DefaultValue("10") @Min(1) int maxRootFields) {
}
