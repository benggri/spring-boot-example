package com.benggri.example.batch.step;

import java.time.LocalDate;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** Deletes statistics older than runDate - retentionDays; that boundary day itself is kept. */
public class StatsPurgeTasklet implements Tasklet {

    private final JdbcTemplate jdbc;
    private final LocalDate runDate;
    private final int retentionDays;

    public StatsPurgeTasklet(JdbcTemplate jdbc, LocalDate runDate, int retentionDays) {
        this.jdbc = jdbc;
        this.runDate = runDate;
        this.retentionDays = retentionDays;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution,
            ChunkContext chunkContext) {
        int deleted = jdbc.update("DELETE FROM member_post_stats WHERE stat_date < ?",
                runDate.minusDays(retentionDays));
        contribution.incrementWriteCount(deleted);
        return RepeatStatus.FINISHED;
    }
}
