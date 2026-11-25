package com.benggri.example.batch.step;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.batch.dto.PostCountRow;
import com.benggri.example.batch.dto.StatRow;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PostCountProcessorTest {

    private final PostCountProcessor processor = new PostCountProcessor(LocalDate.parse("2026-11-25"));

    @Test
    void processor_skipsZeroCountAndMapsOthers() {
        assertThat(processor.process(new PostCountRow(1L, 0))).isNull();
        assertThat(processor.process(new PostCountRow(7L, 3)))
                .isEqualTo(new StatRow(7L, LocalDate.parse("2026-11-25"), 3));
    }
}
