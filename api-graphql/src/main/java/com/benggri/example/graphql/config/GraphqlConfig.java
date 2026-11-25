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
}
