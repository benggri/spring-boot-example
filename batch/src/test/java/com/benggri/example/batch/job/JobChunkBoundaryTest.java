package com.benggri.example.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.batch.support.BatchTestBase;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.batch.job.enabled=false", "app.batch.chunk-size=1"})
class JobChunkBoundaryTest extends BatchTestBase {

    @Test
    void job_chunkSizeOneStillProcessesAllRows() throws Exception {
        Map<Long, Integer> expected = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            long member = insertMember("chunk-" + i + "@x.com");
            for (int j = 0; j <= i; j++) {
                insertPost(member, "2026-11-25T0" + j + ":00:00Z");
            }
            expected.put(member, i + 1);
        }

        var execution = run("2026-11-25");

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(stats("2026-11-25")).isEqualTo(expected);
    }
}
