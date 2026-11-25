package com.benggri.example.graphql.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** {@code app.graphql.*}: the query depth cap that bounds how expensive one request can be. */
@Validated
@ConfigurationProperties("app.graphql")
public record GraphqlProperties(@DefaultValue("10") @Min(1) int maxQueryDepth) {
}
