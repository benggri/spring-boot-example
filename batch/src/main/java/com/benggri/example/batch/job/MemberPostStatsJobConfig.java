package com.benggri.example.batch.job;

import com.benggri.example.batch.config.BatchProperties;
import com.benggri.example.batch.dto.PostCountRow;
import com.benggri.example.batch.dto.StatRow;
import com.benggri.example.batch.step.PostCountProcessor;
import com.benggri.example.batch.step.StatsPurgeTasklet;
import java.time.LocalDate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** memberPostStatsJob: aggregate the run date's posts per member, then purge old statistics. */
@Configuration(proxyBeanMethods = false)
public class MemberPostStatsJobConfig {

    @Bean
    Job memberPostStatsJob(JobRepository repo, Step aggregatePostCountsStep, Step purgeOldStatsStep) {
        return new JobBuilder("memberPostStatsJob", repo)
                .validator(new RunDateValidator())
                .start(aggregatePostCountsStep)
                .next(purgeOldStatsStep)
                .build();
    }

    @Bean
    Step aggregatePostCountsStep(JobRepository repo, PlatformTransactionManager tm, BatchProperties properties,
            ItemReader<PostCountRow> postCountReader, PostCountProcessor postCountProcessor,
            ItemWriter<StatRow> statWriter) {
        return new StepBuilder("aggregatePostCountsStep", repo)
                .<PostCountRow, StatRow>chunk(properties.chunkSize(), tm)
                .reader(postCountReader)
                .processor(postCountProcessor)
                .writer(statWriter)
                .build();
    }

    @Bean
    Step purgeOldStatsStep(JobRepository repo, PlatformTransactionManager tm, StatsPurgeTasklet purgeTasklet) {
        return new StepBuilder("purgeOldStatsStep", repo).tasklet(purgeTasklet, tm).build();
    }

    @Bean
    @StepScope
    PostCountProcessor postCountProcessor(@Value("#{jobParameters['runDate']}") String runDate) {
        return new PostCountProcessor(LocalDate.parse(runDate));
    }

    @Bean
    @StepScope
    StatsPurgeTasklet purgeTasklet(JdbcTemplate jdbc, BatchProperties properties,
            @Value("#{jobParameters['runDate']}") String runDate) {
        return new StatsPurgeTasklet(jdbc, LocalDate.parse(runDate), properties.retentionDays());
    }
}
