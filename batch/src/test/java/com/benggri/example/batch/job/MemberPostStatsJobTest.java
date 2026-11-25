package com.benggri.example.batch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benggri.example.batch.support.BatchTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;

class MemberPostStatsJobTest extends BatchTestBase {

    private static final String RUN_DATE = "2026-11-25";

    @Test
    void job_aggregatesPostsOfRunDateOnly() throws Exception {
        long a = insertMember("job-a@x.com");
        long b = insertMember("job-b@x.com");
        long outsider = insertMember("job-c@x.com");
        insertPost(a, "2026-11-25T00:00:00Z");
        insertPost(a, "2026-11-25T23:59:59.999999Z");
        insertPost(b, "2026-11-25T12:00:00Z");
        insertPost(a, "2026-11-24T23:59:59.999999Z");
        insertPost(b, "2026-11-26T00:00:00Z");
        // 00:30 in UTC+9 is the previous UTC day: proves the window is UTC, not the JVM zone
        insertPost(outsider, "2026-11-25T00:30:00+09:00");

        var execution = run(RUN_DATE);

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(stats(RUN_DATE)).isEqualTo(Map.of(a, 2, b, 1));
        assertThat(statRowCount()).isEqualTo(2);
    }

    @Test
    void job_rerunForSameRunDateIsIdempotent() throws Exception {
        long a = insertMember("rerun-a@x.com");
        long b = insertMember("rerun-b@x.com");
        insertPost(a, "2026-11-25T01:00:00Z");
        insertPost(b, "2026-11-25T02:00:00Z");

        run(RUN_DATE);
        run(RUN_DATE);

        assertThat(statRowCount()).isEqualTo(2);
        assertThat(stats(RUN_DATE)).isEqualTo(Map.of(a, 1, b, 1));

        insertPost(a, "2026-11-25T03:00:00Z");
        run(RUN_DATE);

        assertThat(statRowCount()).isEqualTo(2);
        assertThat(stats(RUN_DATE)).isEqualTo(Map.of(a, 2, b, 1));
    }

    @Test
    void job_purgesStatsOlderThanRetention() throws Exception {
        long a = insertMember("purge-a@x.com");
        // default retention 90 days: runDate - 90 = 2026-08-27 is the oldest day kept
        insertStat(a, "2026-08-26", 5);
        insertStat(a, "2026-08-27", 6);
        insertStat(a, "2026-11-24", 7);

        run(RUN_DATE);

        assertThat(stats("2026-08-26")).isEmpty();
        assertThat(stats("2026-08-27")).isEqualTo(Map.of(a, 6));
        assertThat(stats("2026-11-24")).isEqualTo(Map.of(a, 7));
    }

    @Test
    void job_failsOnMalformedRunDateWithoutWritingStats() {
        long a = insertMember("bad-a@x.com");
        insertPost(a, "2026-11-25T01:00:00Z");
        insertStat(a, "2020-01-01", 9);

        for (String bad : new String[] {"2026-13-40", "not-a-date"}) {
            assertThatThrownBy(() -> run(bad)).isInstanceOf(InvalidJobParametersException.class);
        }
        assertThatThrownBy(() -> jobOperatorTestUtils.startJob(new JobParameters()))
                .isInstanceOf(InvalidJobParametersException.class);

        assertThat(statRowCount()).isEqualTo(1);
        assertThat(stats("2020-01-01")).isEqualTo(Map.of(a, 9));
    }

    @Test
    void job_deletingMemberDoesNotBreakStats() throws Exception {
        long a = insertMember("gone-a@x.com");
        long b = insertMember("gone-b@x.com");
        insertPost(a, "2026-11-25T01:00:00Z");
        insertPost(b, "2026-11-25T01:00:00Z");
        run(RUN_DATE);
        assertThat(statRowCount()).isEqualTo(2);

        jdbc.update("DELETE FROM posts WHERE member_id = ?", a);
        int deleted = jdbc.update("DELETE FROM members WHERE id = ?", a);

        assertThat(deleted).isEqualTo(1);
        assertThat(stats(RUN_DATE)).isEqualTo(Map.of(b, 1));
    }
}
