package com.benggri.example.grpc.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entities and repositories live in core, outside this module's package tree.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.benggri.example.core")
@EnableJpaRepositories("com.benggri.example.core")
public class PersistenceConfig {
}
