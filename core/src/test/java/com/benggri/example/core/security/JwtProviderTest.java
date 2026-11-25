package com.benggri.example.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtProviderTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private MutableClock clock;
    private JwtProperties properties;
    private JwtProvider provider;
    private Member member;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        properties = new JwtProperties(TestSecrets.randomSecret(), ACCESS_TTL, REFRESH_TTL);
        provider = new JwtProvider(JwtConfig.hs256Encoder(properties), properties, clock);
        member = Member.create("a@example.com", "hash", "Alice", Role.ADMIN);
        ReflectionTestUtils.setField(member, "id", 42L);
    }

    @Test
    void parseAccess_roundTripsIdAndRole() {
        AuthUser user = provider.parseAccess(provider.issueAccess(member));

        assertThat(user).isEqualTo(new AuthUser(42L, Role.ADMIN));
    }

    @Test
    void parseRefresh_roundTripsIdAndRole() {
        AuthUser user = provider.parseRefresh(provider.issueRefresh(member));

        assertThat(user).isEqualTo(new AuthUser(42L, Role.ADMIN));
    }

    @Test
    void parseAccess_expiredTokenThrows() {
        String token = provider.issueAccess(member);

        clock.advance(ACCESS_TTL.plusSeconds(1));

        assertThatThrownBy(() -> provider.parseAccess(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseAccess_validUntilExpiry() {
        String token = provider.issueAccess(member);

        clock.advance(ACCESS_TTL.minusSeconds(1));

        assertThatCode(() -> provider.parseAccess(token)).doesNotThrowAnyException();
    }

    @Test
    void parseAccess_tamperedSignatureThrows() {
        String token = provider.issueAccess(member);
        String[] parts = token.split("\\.");
        char last = parts[2].charAt(0);
        String badSignature = (last == 'A' ? 'B' : 'A') + parts[2].substring(1);

        assertThatThrownBy(() -> provider.parseAccess(parts[0] + "." + parts[1] + "." + badSignature))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseAccess_signedWithOtherSecretThrows() {
        JwtProperties other = new JwtProperties(TestSecrets.randomSecret(), ACCESS_TTL, REFRESH_TTL);
        JwtProvider foreign = new JwtProvider(JwtConfig.hs256Encoder(other), other, clock);

        assertThatThrownBy(() -> provider.parseAccess(foreign.issueAccess(member)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseAccess_rejectsAlgNoneToken() {
        String token = unsignedToken("{\"alg\":\"none\"}", "{\"sub\":\"42\",\"role\":\"ADMIN\",\"typ\":\"access\","
                + "\"exp\":" + clock.instant().plusSeconds(600).getEpochSecond() + "}");

        assertThatThrownBy(() -> provider.parseAccess(token)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> provider.accessDecoder().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseAccess_rejectsRefreshToken() {
        assertThatThrownBy(() -> provider.parseAccess(provider.issueRefresh(member)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseRefresh_rejectsAccessToken() {
        assertThatThrownBy(() -> provider.parseRefresh(provider.issueAccess(member)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void accessDecoder_rejectsRefreshToken() {
        JwtDecoder decoder = provider.accessDecoder();
        String refresh = provider.issueRefresh(member);

        assertThatThrownBy(() -> decoder.decode(refresh)).isInstanceOf(JwtException.class);
        assertThatCode(() -> decoder.decode(provider.issueAccess(member))).doesNotThrowAnyException();
    }

    @Test
    void parse_exceptionMessagesNeverContainTokenOrSecret() {
        String token = provider.issueRefresh(member);

        assertThatThrownBy(() -> provider.parseAccess(token)).satisfies(e -> {
            assertThat(e.getMessage()).doesNotContain(token).doesNotContain(properties.secret());
        });
    }

    @Test
    void parseAccess_garbageThrows() {
        assertThatThrownBy(() -> provider.parseAccess("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
    }

    private static String unsignedToken(String header, String payload) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".";
    }
}
