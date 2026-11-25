package com.benggri.example.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = JwtConfig.class)
class JwtConfigContextTest {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestSecrets::randomSecret);
        registry.add("app.jwt.access-ttl", () -> "15m");
        registry.add("app.jwt.refresh-ttl", () -> "7d");
    }

    @Autowired
    JwtDecoder jwtDecoder;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void contextStartsWithRealBeans() {
        assertThat(passwordEncoder.matches("pw", passwordEncoder.encode("pw"))).isTrue();
    }

    @Test
    void jwtDecoderBean_rejectsRefreshTokenButAcceptsAccess() {
        Member member = Member.create("a@example.com", "hash", "Alice", Role.USER);
        ReflectionTestUtils.setField(member, "id", 3L);

        assertThat(jwtDecoder.decode(jwtProvider.issueAccess(member)).getSubject()).isEqualTo("3");
        assertThatThrownBy(() -> jwtDecoder.decode(jwtProvider.issueRefresh(member)))
                .isInstanceOf(JwtException.class);
    }
}
