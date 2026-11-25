package com.benggri.example.batch.support;

import com.benggri.example.core.support.PostgresTestBase;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Real PostgreSQL (shared Testcontainer). The job never auto-runs on startup in tests, because
 * without a runDate parameter the validator would refuse it.
 */
@SpringBootTest(properties = "spring.batch.job.enabled=false")
@Import(BatchTestBase.UtilsConfig.class)
public abstract class BatchTestBase extends PostgresTestBase {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected JobOperatorTestUtils jobOperatorTestUtils;

    /**
     * Explicit wiring instead of @SpringBatchTest: its JobScopeTestExecutionListener treats any
     * test method returning a JobExecution (such as run below) as a job scope factory.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class UtilsConfig {

        @Bean
        JobOperatorTestUtils jobOperatorTestUtils(JobOperator operator, JobRepository repository, Job job) {
            JobOperatorTestUtils utils = new JobOperatorTestUtils(operator, repository);
            utils.setJob(job);
            return utils;
        }
    }

    @AfterEach
    void cleanUpRows() {
        // committed rows would leak into other tests sharing the singleton container
        jdbc.update("DELETE FROM member_post_stats");
        jdbc.update("DELETE FROM posts");
        jdbc.update("DELETE FROM members");
    }

    protected JobParameters params(String runDate) {
        return new JobParametersBuilder()
                .addString("runDate", runDate)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();
    }

    protected JobExecution run(String runDate) throws Exception {
        return jobOperatorTestUtils.startJob(params(runDate));
    }

    protected long insertMember(String email) {
        return jdbc.queryForObject("INSERT INTO members (email, password_hash, name, role, created_at)"
                + " VALUES (?, 'h', 'n', 'USER', now()) RETURNING id", Long.class, email);
    }

    protected void insertPost(long memberId, String createdAtUtc) {
        OffsetDateTime at = OffsetDateTime.parse(createdAtUtc);
        jdbc.update("INSERT INTO posts (member_id, title, content, created_at, updated_at)"
                + " VALUES (?, 't', 'c', ?, ?)", memberId, at, at);
    }

    protected void insertStat(long memberId, String statDate, int count) {
        jdbc.update("INSERT INTO member_post_stats (member_id, stat_date, post_count, created_at)"
                + " VALUES (?, ?::date, ?, now())", memberId, statDate, count);
    }

    /** member id to post count for one stat date, ordered by member id. */
    protected Map<Long, Integer> stats(String statDate) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbc.query("SELECT member_id, post_count FROM member_post_stats WHERE stat_date = ?::date"
                + " ORDER BY member_id", rs -> {
                    result.put(rs.getLong(1), rs.getInt(2));
                }, statDate);
        return result;
    }

    protected int statRowCount() {
        return jdbc.queryForObject("SELECT count(*) FROM member_post_stats", Integer.class);
    }
}
