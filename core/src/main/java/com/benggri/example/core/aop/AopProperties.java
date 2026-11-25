package com.benggri.example.core.aop;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** slowThresholdMs: service calls strictly slower than this are logged at WARN. */
@ConfigurationProperties("app.aop")
public record AopProperties(@DefaultValue("500") long slowThresholdMs) {

    public AopProperties {
        if (slowThresholdMs < 0) {
            throw new IllegalArgumentException("app.aop.slow-threshold-ms must not be negative");
        }
    }
}
