package com.benggri.example.core.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared PostgreSQL Testcontainer, started once per JVM (singleton pattern) so cached
 * Spring contexts never point at a stopped container. Ryuk removes it when the JVM exits.
 */
@Import(PostgresTestBase.ContainerConfig.class)
public abstract class PostgresTestBase {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {

        @Bean(destroyMethod = "")
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return POSTGRES;
        }
    }
}
