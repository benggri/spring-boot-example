package com.benggri.example.rest;

import com.benggri.example.core.security.JwtConfig;
import com.benggri.example.rest.config.SecurityConfig;
import com.benggri.example.rest.error.RestAccessDeniedHandler;
import com.benggri.example.rest.error.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Shared wiring for controller slice tests: the real security chain plus core's JwtConfig. */
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
public abstract class SliceTestBase {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestSecrets.register(registry);
    }
}
