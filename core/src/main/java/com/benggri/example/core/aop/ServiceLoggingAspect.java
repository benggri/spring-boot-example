package com.benggri.example.core.aop;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
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

    private final AopProperties properties;
    private final LongSupplier nanoTime;

    @Autowired
    public ServiceLoggingAspect(AopProperties properties) {
        this(properties, System::nanoTime);
    }

    /** Lets tests own the clock instead of sleeping. */
    ServiceLoggingAspect(AopProperties properties, LongSupplier nanoTime) {
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    @Around("(execution(* com.benggri.example.core..service.*Service.*(..))"
            + " || execution(* com.benggri.example.core.security.*Service.*(..)))"
            + " && !within(com.benggri.example.core.aop..*)")
    public Object logTiming(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = nanoTime.getAsLong();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - start);
            record(joinPoint, elapsedMs);
        }
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
