package com.benggri.example.batch.step;

import com.benggri.example.batch.dto.StatRow;
import javax.sql.DataSource;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Upserts stat rows, so a rerun for the same runDate overwrites instead of duplicating. */
@Configuration(proxyBeanMethods = false)
public class StatWriterConfig {

    static final String SQL = "INSERT INTO member_post_stats (member_id, stat_date, post_count, created_at)"
            + " VALUES (?, ?, ?, now())"
            + " ON CONFLICT (member_id, stat_date) DO UPDATE SET post_count = EXCLUDED.post_count";

    @Bean
    JdbcBatchItemWriter<StatRow> statWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<StatRow>()
                .dataSource(dataSource)
                .sql(SQL)
                .itemPreparedStatementSetter((row, ps) -> {
                    ps.setLong(1, row.memberId());
                    ps.setObject(2, row.statDate());
                    ps.setInt(3, row.postCount());
                })
                .assertUpdates(true)
                .build();
    }
}
