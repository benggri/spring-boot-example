package com.benggri.example.batch.step;

import com.benggri.example.batch.config.BatchProperties;
import com.benggri.example.batch.dto.PostCountRow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Counts the posts of one UTC day per member; every value is bound, never concatenated. */
@Configuration(proxyBeanMethods = false)
public class PostCountReaderConfig {

    static final String SQL = "SELECT member_id, count(*) AS post_count FROM posts"
            + " WHERE created_at >= ? AND created_at < ? GROUP BY member_id ORDER BY member_id";

    @Bean
    @StepScope
    JdbcCursorItemReader<PostCountRow> postCountReader(DataSource dataSource, BatchProperties properties,
            @Value("#{jobParameters['runDate']}") String runDate) {
        // window [runDate 00:00Z, runDate+1d 00:00Z): UTC, independent of the JVM time zone
        LocalDate day = LocalDate.parse(runDate);
        OffsetDateTime from = day.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return new JdbcCursorItemReaderBuilder<PostCountRow>()
                .name("postCountReader")
                .dataSource(dataSource)
                .sql(SQL)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, from);
                    ps.setObject(2, to);
                })
                .rowMapper((rs, rowNum) -> new PostCountRow(rs.getLong("member_id"), rs.getLong("post_count")))
                .fetchSize(properties.chunkSize())
                .build();
    }
}
