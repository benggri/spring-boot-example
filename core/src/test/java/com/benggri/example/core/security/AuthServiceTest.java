package com.benggri.example.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final String PASSWORD = "correct-horse";

    private final MemberRepository repository = mock(MemberRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private MutableClock clock;
    private JwtProvider jwtProvider;
    private AuthService service;
    private Member member;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtProperties props = new JwtProperties(TestSecrets.randomSecret(), Duration.ofMinutes(15),
                Duration.ofDays(7));
        jwtProvider = new JwtProvider(JwtConfig.hs256Encoder(props), props, clock);
        service = new AuthService(repository, encoder, jwtProvider);
        member = Member.create("a@example.com", encoder.encode(PASSWORD), "Alice", Role.USER);
        ReflectionTestUtils.setField(member, "id", 1L);
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.of(member));
        when(repository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.of(member));
    }

    @Test
    void login_returnsTokensForValidCredentials() {
        TokenPair pair = service.login("a@example.com", PASSWORD);

        assertThat(jwtProvider.parseAccess(pair.accessToken())).isEqualTo(new AuthUser(1L, Role.USER));
        assertThat(jwtProvider.parseRefresh(pair.refreshToken())).isEqualTo(new AuthUser(1L, Role.USER));
    }

    @Test
    void login_wrongPasswordAndUnknownEmailThrowSameException() {
        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login("a@example.com", "wrong"));
        Throwable unknownEmail = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login("nobody@example.com", "wrong"));

        assertThat(wrongPassword).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail).isInstanceOf(wrongPassword.getClass())
                .hasMessage(wrongPassword.getMessage());
        assertThat(wrongPassword.getMessage()).isEqualTo("Invalid email or password");
    }

    @Test
    void refresh_returnsNewPair() {
        TokenPair first = service.login("a@example.com", PASSWORD);
        ReflectionTestUtils.setField(member, "role", Role.ADMIN);
        clock.advance(Duration.ofMinutes(1));

        TokenPair second = service.refresh(first.refreshToken());

        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        // role comes from the current member state, not from the old token
        assertThat(jwtProvider.parseAccess(second.accessToken())).isEqualTo(new AuthUser(1L, Role.ADMIN));
    }

    @Test
    void refresh_rejectsAccessToken() {
        TokenPair pair = service.login("a@example.com", PASSWORD);

        assertThatThrownBy(() -> service.refresh(pair.accessToken())).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_deletedMemberThrowsInvalidToken() {
        TokenPair pair = service.login("a@example.com", PASSWORD);
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(pair.refreshToken())).isInstanceOf(InvalidTokenException.class);
    }
}
