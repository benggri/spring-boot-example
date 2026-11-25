package com.benggri.example.graphql.config;

import graphql.analysis.MaxQueryDepthInstrumentation;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GraphqlProperties.class)
public class GraphqlConfig {

    /** Spring GraphQL registers every Instrumentation bean; deeper queries fail before execution. */
    @Bean
    MaxQueryDepthInstrumentation maxQueryDepthInstrumentation(GraphqlProperties properties) {
        return new MaxQueryDepthInstrumentation(properties.maxQueryDepth());
    }

    /** Bounds aliased root fields (e.g. many login mutations in one request); see the class comment. */
    @Bean
    MaxRootFieldsInstrumentation maxRootFieldsInstrumentation(GraphqlProperties properties) {
        return new MaxRootFieldsInstrumentation(properties.maxRootFields());
    }

    @Bean
    ClientErrorCodeInterceptor clientErrorCodeInterceptor() {
        return new ClientErrorCodeInterceptor();
    }
}
