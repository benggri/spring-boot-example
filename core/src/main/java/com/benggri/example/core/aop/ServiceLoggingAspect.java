package com.benggri.example.core.aop;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Times service calls. Only the class and method name are logged, never argument or return values
 * (they carry emails, passwords and hashes). AuthService is matched by the second pattern.
 */
@Slf4j
@Aspect
@Component
public class ServiceLoggingAspect {

    static final String TIMER_NAME = "app.service.invocation";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";

    private final AopProperties properties;
    private final LongSupplier nanoTime;

    private final Supplier<MeterRegistry> registry;

    /** The registry is optional (apps without actuator, e.g. batch) and resolved lazily per call. */
    @Autowired
    public ServiceLoggingAspect(AopProperties properties, ObjectProvider<MeterRegistry> registry) {
        this(properties, System::nanoTime, registry::getIfAvailable);
    }

    /** Lets tests own the clock instead of sleeping. */
    ServiceLoggingAspect(AopProperties properties, LongSupplier nanoTime) {
        this(properties, nanoTime, () -> null);
    }

    ServiceLoggingAspect(AopProperties properties, LongSupplier nanoTime, MeterRegistry registry) {
        this(properties, nanoTime, () -> registry);
    }

    private ServiceLoggingAspect(AopProperties properties, LongSupplier nanoTime,
            Supplier<MeterRegistry> registry) {
        this.properties = properties;
        this.nanoTime = nanoTime;
        this.registry = registry;
    }

    @Around("(execution(* com.benggri.example.core..service.*Service.*(..))"
            + " || execution(* com.benggri.example.core.security.*Service.*(..)))"
            + " && !within(com.benggri.example.core.aop..*)")
    public Object logTiming(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = nanoTime.getAsLong();
        String outcome = OUTCOME_FAILURE;
        try {
            Object result = joinPoint.proceed();
            outcome = OUTCOME_SUCCESS;
            return result;
        } finally {
            long elapsedNanos = nanoTime.getAsLong() - start;
            record(joinPoint, TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
            recordTimer(joinPoint, outcome, elapsedNanos);
        }
    }

    /** Tags are fixed code identifiers only (never arguments, ids or exception data) to stay low-cardinality. */
    private void recordTimer(ProceedingJoinPoint joinPoint, String outcome, long elapsedNanos) {
        MeterRegistry meters = registry.get();
        if (meters == null) {
            return;
        }
        Timer.builder(TIMER_NAME)
                .tag("class", joinPoint.getSignature().getDeclaringType().getSimpleName())
                .tag("method", joinPoint.getSignature().getName())
                .tag("outcome", outcome)
                .register(meters)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private void record(ProceedingJoinPoint joinPoint, long elapsedMs) {
        String method = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        long threshold = properties.slowThresholdMs();
        if (elapsedMs > threshold) {
            log.warn("{} took {}ms (slow, threshold {}ms)", method, elapsedMs, threshold);
        } else {
            log.debug("{} took {}ms", method, elapsedMs);
        }
    }
}
