package com.benggri.example.core.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.sample.service.CommitFailingService;
import com.benggri.example.core.security.AuthUser;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.support.PostgresTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Audit and timing must wrap the transaction: a failure that only surfaces at commit (after the
 * method body returned) has to be recorded as a failure, never as success.
 */
@SpringBootTest
@Import(AspectOrderingIntegrationTest.Config.class)
class AspectOrderingIntegrationTest extends PostgresTestBase {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        CommitFailingService commitFailingService() {
            return new CommitFailingService();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private CommitFailingService service;

    @Autowired
    private MeterRegistry meters;

    @Test
    void commitTimeFailure_isAuditedAndTimedAsFailure() {
        try (var logs = new LogCapture("audit", Level.INFO)) {
            assertThatThrownBy(() -> service.act(new AuthUser(5L, Role.USER)))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(logs.messages()).containsExactly(
                    "action=sample.commitfail actor=5 outcome=FAILURE(IllegalStateException)");
        }
        assertThat(meters.find(ServiceLoggingAspect.TIMER_NAME).tag("outcome", "failure").timer())
                .isNotNull();
        assertThat(meters.find(ServiceLoggingAspect.TIMER_NAME).tag("outcome", "success").timer())
                .isNull();
    }
}
