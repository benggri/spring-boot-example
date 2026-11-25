package com.benggri.example.batch.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Entities and Spring Data repositories live in core, outside this module's package tree. */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.benggri.example.core")
@EnableJpaRepositories("com.benggri.example.core")
public class PersistenceConfig {
}
