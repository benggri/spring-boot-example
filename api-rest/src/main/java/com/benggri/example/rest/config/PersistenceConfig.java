package com.benggri.example.rest.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entities and repositories live in core, outside this module's package tree. Kept off the
 * application class so web slice tests (which have no JPA) do not pick it up.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.benggri.example.core")
@EnableJpaRepositories("com.benggri.example.core")
public class PersistenceConfig {
}
