package com.benggri.example.batch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benggri.example.batch.support.BatchTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;

class JobRestartTest extends BatchTestBase {

    private static final String RUN_DATE = "2026-11-25";

    @Test
    void job_stepFailureLeavesJobRestartable() throws Exception {
        long a = insertMember("restart-a@x.com");
        insertPost(a, "2026-11-25T01:00:00Z");
        insertPost(a, "2026-11-25T02:00:00Z");
        // identical identifying parameters for every launch of this test
        JobParameters fixed = new JobParametersBuilder()
                .addString("runDate", RUN_DATE)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        // induce a step 1 failure at DB level: the writer's upsert has no table to write to
        jdbc.execute("ALTER TABLE member_post_stats RENAME TO member_post_stats_off");
        JobExecution failed;
        try {
            failed = jobOperatorTestUtils.startJob(fixed);
        } finally {
            jdbc.execute("ALTER TABLE member_post_stats_off RENAME TO member_post_stats");
        }

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failed.getStepExecutions())
                .filteredOn(s -> s.getStatus() == BatchStatus.FAILED)
                .extracting(s -> s.getStepName())
                .containsExactly("aggregatePostCountsStep");
        assertThat(statRowCount()).isZero();

        // plain start() with identical parameters is how the framework restarts a FAILED instance
        JobExecution restarted = jobOperatorTestUtils.startJob(fixed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getId()).isNotEqualTo(failed.getId());
        assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
        assertThat(stats(RUN_DATE)).isEqualTo(Map.of(a, 2));

        // a COMPLETED instance is refused for identical parameters
        assertThatThrownBy(() -> jobOperatorTestUtils.startJob(fixed))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }
}
