package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.graphql.config.GraphqlProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class GraphqlPropertiesTest {

    @Test
    void maxQueryDepthBelowOneIsInvalid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new GraphqlProperties(0, 10))).hasSize(1);
            assertThat(factory.getValidator().validate(new GraphqlProperties(1, 10))).isEmpty();
        }
    }

    @Test
    void maxRootFieldsBelowOneIsInvalid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new GraphqlProperties(10, 0))).hasSize(1);
            assertThat(factory.getValidator().validate(new GraphqlProperties(10, 1))).isEmpty();
        }
    }
}
