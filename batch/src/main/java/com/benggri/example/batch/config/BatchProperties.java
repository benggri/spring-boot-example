package com.benggri.example.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

/**
 * Tunables of the member post stats job.
 *
 * @param chunkSize     items per transaction in the aggregation step
 * @param retentionDays how many days of statistics are kept (the boundary day itself is kept)
 */
@ConfigurationProperties("app.batch")
public record BatchProperties(
        @DefaultValue("100") int chunkSize,
        @DefaultValue("90") int retentionDays) {

    public BatchProperties {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("app.batch.chunk-size must be >= 1");
        }
        if (retentionDays < 1) {
            throw new IllegalArgumentException("app.batch.retention-days must be >= 1");
        }
    }

    /** Registers the properties record as a bean. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BatchProperties.class)
    static class Registration {
    }
}
