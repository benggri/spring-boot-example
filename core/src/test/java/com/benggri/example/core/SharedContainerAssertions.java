package com.benggri.example.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import com.benggri.example.core.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Subclasses run in one JVM and must all see the same, still running container. */
@SpringBootTest
abstract class SharedContainerAssertions extends PostgresTestBase {

    private static final Set<String> SEEN_CONTAINER_IDS = ConcurrentHashMap.newKeySet();

    @Autowired
    private DataSource dataSource;

    @Test
    void usesSingleRunningContainer() throws Exception {
        SEEN_CONTAINER_IDS.add(POSTGRES.getContainerId());

        assertThat(SEEN_CONTAINER_IDS).hasSize(1);
        assertThat(POSTGRES.isRunning()).isTrue();
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
        }
    }
}
