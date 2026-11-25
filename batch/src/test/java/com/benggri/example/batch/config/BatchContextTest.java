package com.benggri.example.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.batch.support.BatchTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

class BatchContextTest extends BatchTestBase {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void context_startsWithoutJwtSecret() {
        assertThat(environment.getProperty("app.jwt.secret")).isNull();
        assertThat(environment.getProperty("JWT_SECRET")).isNull();
        assertThat(context.getBeansOfType(Job.class)).containsOnlyKeys("memberPostStatsJob");
        // core's services/aspects/JwtConfig are not scanned
        assertThat(context.containsBean("jwtConfig")).isFalse();
        assertThat(context.getBeanNamesForType(com.benggri.example.core.security.JwtProvider.class)).isEmpty();
        assertThat(context.getBeanNamesForType(com.benggri.example.core.member.service.MemberService.class))
                .isEmpty();
    }
}
