package com.benggri.example.batch.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

class RunDateValidatorTest {

    private final RunDateValidator validator = new RunDateValidator();

    private JobParameters of(String runDate) {
        return new JobParametersBuilder().addString("runDate", runDate).toJobParameters();
    }

    @Test
    void runDateValidator_rejectsMissingAndMalformedRunDate() {
        assertThatThrownBy(() -> validator.validate(new JobParameters()))
                .isInstanceOf(InvalidJobParametersException.class);
        for (String bad : new String[] {"", "  ", "2026-13-01", "2026-02-30", "20261125", "2026/11/25",
            "2026-11-25T00:00:00Z", "yesterday"}) {
            assertThatThrownBy(() -> validator.validate(of(bad)))
                    .as("runDate '%s'", bad)
                    .isInstanceOf(InvalidJobParametersException.class);
        }
        assertThatThrownBy(() -> validator.validate(
                new JobParametersBuilder().addLong("runDate", 20261125L).toJobParameters()))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void runDateValidator_acceptsIsoDate() {
        assertThatCode(() -> validator.validate(of("2026-11-25"))).doesNotThrowAnyException();
    }
}
