package com.benggri.example.core.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.sample.service.SampleService;
import com.benggri.example.core.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class AuditAspectTest {

    private static SampleService proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(new AuditAspect());
        return factory.getProxy();
    }

    @Test
    void audit_logsActorAndSuccess() {
        try (var logs = new LogCapture("audit", Level.INFO)) {
            proxy().act(new AuthUser(42L, Role.USER), "p");

            assertThat(logs.events()).singleElement().satisfies(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.INFO);
                assertThat(e.getFormattedMessage())
                        .isEqualTo("action=sample.act actor=42 outcome=SUCCESS");
            });
        }
    }

    @Test
    void audit_logsFailureAndRethrowsSameException() {
        IllegalStateException original = new IllegalStateException("secret a@b.c");
        try (var logs = new LogCapture("audit", Level.INFO)) {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> proxy().fail(new AuthUser(7L, Role.ADMIN), original));

            assertThat(thrown).isSameAs(original);
            assertThat(logs.messages()).containsExactly(
                    "action=sample.fail actor=7 outcome=FAILURE(IllegalStateException)");
        }
    }

    @Test
    void audit_usesAnonymousWithoutAuthUser() {
        try (var logs = new LogCapture("audit", Level.INFO)) {
            proxy().anon("topsecret");

            assertThat(logs.messages())
                    .containsExactly("action=sample.anon actor=anonymous outcome=SUCCESS");
        }
    }

    @Test
    void aspects_doNotChangeReturnValue() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(new AuditAspect());
        factory.addAspect(new ServiceLoggingAspect(new AopProperties(500), System::nanoTime));
        SampleService service = factory.getProxy();

        assertThat(service.greet("x")).isEqualTo("hello x");
        assertThat(service.act(new AuthUser(1L, Role.USER), "p")).isEqualTo("done:p");
        assertThatThrownBy(() -> service.fail(null, new IllegalArgumentException("e")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
