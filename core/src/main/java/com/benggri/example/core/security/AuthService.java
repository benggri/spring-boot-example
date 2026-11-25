package com.benggri.example.core.security;

import com.benggri.example.core.common.TextGuard;
import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.repository.MemberRepository;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login and refresh. Refresh tokens are stateless: there is no rotation or revocation list
 * (documented non-goal), so a refresh token stays valid until it expires.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    // matched against when the email is unknown so both failure paths cost one BCrypt check
    private final String dummyHash;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public TokenPair login(String email, String rawPassword) {
        // a NUL would make the lookup throw a driver error; reject it before any query
        TextGuard.requireClean(email);
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) {
            passwordEncoder.matches(String.valueOf(rawPassword), dummyHash);
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
        return issuePair(member);
    }

    public TokenPair refresh(String refreshToken) {
        AuthUser user = jwtProvider.parseRefresh(refreshToken);
        // the member may have been deleted, and the role is re-read so demotions take effect
        Member member = memberRepository.findById(user.id())
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));
        return issuePair(member);
    }

    private TokenPair issuePair(Member member) {
        return new TokenPair(jwtProvider.issueAccess(member), jwtProvider.issueRefresh(member));
    }
}
