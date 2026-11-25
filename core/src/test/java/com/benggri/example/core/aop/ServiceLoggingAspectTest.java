package com.benggri.example.core.aop;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.benggri.example.core.sample.service.SampleService;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class ServiceLoggingAspectTest {

    private static final long MS = TimeUnit.MILLISECONDS.toNanos(1);

    /** Returns 0 on the first read and elapsedMs on the next, so the test owns the clock. */
    private static LongSupplier clockElapsing(long elapsedMs) {
        long[] readings = {0, elapsedMs * MS};
        int[] next = {0};
        return () -> readings[Math.min(next[0]++, 1)];
    }

    private static SampleService proxy(ServiceLoggingAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    void logging_recordsMethodAndDuration() {
        var aspect = new ServiceLoggingAspect(new AopProperties(500), clockElapsing(5));
        try (var logs = new LogCapture(ServiceLoggingAspect.class, Level.DEBUG)) {
            proxy(aspect).greet("x");

            assertThat(logs.events()).singleElement().satisfies(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(e.getFormattedMessage()).isEqualTo("SampleService.greet took 5ms");
            });
        }
    }

    @Test
    void logging_warnsWhenOverThreshold() {
        var aspect = new ServiceLoggingAspect(new AopProperties(0), clockElapsing(1));
        try (var logs = new LogCapture(ServiceLoggingAspect.class, Level.DEBUG)) {
            proxy(aspect).greet("x");

            assertThat(logs.events()).singleElement().satisfies(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage())
                        .isEqualTo("SampleService.greet took 1ms (slow, threshold 0ms)");
            });
        }
    }

    @Test
    void logging_doesNotWarnWhenExactlyAtThreshold() {
        var aspect = new ServiceLoggingAspect(new AopProperties(5), clockElapsing(5));
        try (var logs = new LogCapture(ServiceLoggingAspect.class, Level.DEBUG)) {
            proxy(aspect).greet("x");

            assertThat(logs.events()).singleElement()
                    .satisfies(e -> assertThat(e.getLevel()).isEqualTo(Level.DEBUG));
        }
    }

    @Test
    void logging_neverContainsArgumentValues() {
        var logging = new ServiceLoggingAspect(new AopProperties(0), clockElapsing(1));
        var audit = new AuditAspect();
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(logging);
        factory.addAspect(audit);
        SampleService service = factory.getProxy();

        try (var appLogs = new LogCapture(ServiceLoggingAspect.class, Level.DEBUG);
                var auditLogs = new LogCapture("audit", Level.INFO)) {
            service.register("a@b.c", "s3cretPw!", "n");

            String text = String.join("\n", appLogs.messages()) + String.join("\n", auditLogs.messages());
            assertThat(appLogs.messages()).isNotEmpty();
            assertThat(auditLogs.messages()).isNotEmpty();
            assertThat(text).doesNotContain("s3cretPw!").doesNotContain("a@b.c");
        }
    }

    @Test
    void logging_logsDurationEvenWhenCallFails() {
        var aspect = new ServiceLoggingAspect(new AopProperties(500), clockElapsing(2));
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        SampleService service = factory.getProxy();
        try (var logs = new LogCapture(ServiceLoggingAspect.class, Level.DEBUG)) {
            RuntimeException boom = new IllegalStateException("x");
            try {
                service.fail(null, boom);
            } catch (IllegalStateException expected) {
                assertThat(expected).isSameAs(boom);
            }
            assertThat(logs.messages()).containsExactly("SampleService.fail took 2ms");
        }
    }

    private static final String TIMER = "app.service.invocation";

    @Test
    void recordsTimerWithClassMethodOutcomeTags() {
        var registry = new SimpleMeterRegistry();
        var aspect = new ServiceLoggingAspect(new AopProperties(500), clockElapsing(5), registry);

        proxy(aspect).greet("secret-arg");

        Timer timer = registry.find(TIMER)
                .tags("class", "SampleService", "method", "greet", "outcome", "success").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(5.0);
    }

    @Test
    void recordsFailureOutcomeForThrowingCall() {
        var registry = new SimpleMeterRegistry();
        var aspect = new ServiceLoggingAspect(new AopProperties(500), clockElapsing(2), registry);
        RuntimeException boom = new IllegalStateException("boom-message");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> proxy(aspect).fail(null, boom))
                .isSameAs(boom);

        assertThat(registry.find(TIMER).tags("method", "fail", "outcome", "failure").timer())
                .isNotNull().satisfies(t -> assertThat(t.count()).isEqualTo(1));
        assertThat(registry.find(TIMER).tags("outcome", "success").timer()).isNull();
    }

    @Test
    void timerHasNoTagsOtherThanThree() {
        var registry = new SimpleMeterRegistry();
        var aspect = new ServiceLoggingAspect(new AopProperties(500), clockElapsing(1), registry);

        proxy(aspect).register("a@b.c", "s3cretPw!", "n");

        Timer timer = registry.find(TIMER).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTags().stream().map(t -> t.getKey()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("class", "method", "outcome");
        String values = timer.getId().getTags().stream().map(t -> t.getValue())
                .collect(Collectors.joining("|"));
        assertThat(values).doesNotContain("a@b.c").doesNotContain("s3cretPw!");
    }

    @Test
    void aspect_worksWithoutMeterRegistry() {
        var aspect = new ServiceLoggingAspect(new AopProperties(500),
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));

        assertThat(proxy(aspect).greet("x")).isNotNull();
    }

    @Test
    void aopProperties_defaultsAndRejectsNegative() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AopProperties(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AopProperties(0).slowThresholdMs()).isZero();
    }
}
