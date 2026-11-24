package com.benggri.example.core.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Shared PostgreSQL Testcontainer; subclasses get a DataSource wired automatically. */
@Testcontainers
public abstract class PostgresTestBase {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
}
