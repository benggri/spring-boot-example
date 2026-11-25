package com.benggri.example.batch.job;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/** Refuses to launch the job unless runDate is a present, real ISO (yyyy-MM-dd) date string. */
public class RunDateValidator implements JobParametersValidator {

    public static final String RUN_DATE = "runDate";

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        JobParameter<?> parameter = parameters == null ? null : parameters.getParameter(RUN_DATE);
        if (parameter == null || !(parameter.value() instanceof String raw)) {
            throw new InvalidJobParametersException("runDate (yyyy-MM-dd) is required");
        }
        try {
            LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new InvalidJobParametersException("runDate must be an ISO date (yyyy-MM-dd)");
        }
    }
}
