package com.benggri.example.core.security;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Issues and verifies self-signed HS256 JWTs. Access and refresh tokens differ only by the
 * {@code typ} claim, which each decoder enforces, so one can never stand in for the other.
 */
public class JwtProvider {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final JwtDecoder accessDecoder;
    private final JwtDecoder refreshDecoder;

    public JwtProvider(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.accessDecoder = decoder(TYPE_ACCESS);
        this.refreshDecoder = decoder(TYPE_REFRESH);
    }

    public String issueAccess(Member member) {
        return issue(member, TYPE_ACCESS, properties.accessTtl());
    }

    public String issueRefresh(Member member) {
        return issue(member, TYPE_REFRESH, properties.refreshTtl());
    }

    public AuthUser parseAccess(String token) {
        return parse(accessDecoder, token);
    }

    public AuthUser parseRefresh(String token) {
        return parse(refreshDecoder, token);
    }

    /** Decoder for incoming API calls; rejects every token whose {@code typ} is not access. */
    public JwtDecoder accessDecoder() {
        return accessDecoder;
    }

    private String issue(Member member, String type, Duration ttl) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(member.getId()))
                .claim(CLAIM_ROLE, member.getRole().name())
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private AuthUser parse(JwtDecoder decoder, String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return toAuthUser(jwt);
        } catch (RuntimeException e) {
            // JwtException, IllegalArgumentException and anything else a malformed claim can raise (never a 500);
            // never echo the token, claim values or parser text that could quote them: the cause stays local
            throw new InvalidTokenException("Invalid token", e);
        }
    }

    /** Maps verified claims to the caller; throws IllegalArgumentException for a missing or malformed sub/role. */
    static AuthUser toAuthUser(Jwt jwt) {
        String role = jwt.getClaimAsString(CLAIM_ROLE);
        if (jwt.getSubject() == null || jwt.getSubject().isBlank() || role == null || role.isBlank()) {
            throw new IllegalArgumentException("Token lacks subject or role");
        }
        return new AuthUser(Long.valueOf(jwt.getSubject()), Role.valueOf(role));
    }

    private JwtDecoder decoder(String expectedType) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(JwtConfig.secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, typeValidator(expectedType), identityValidator()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> typeValidator(String expectedType) {
        OAuth2Error error = new OAuth2Error("invalid_token", "Unexpected token type", null);
        return jwt -> {
            try {
                return expectedType.equals(jwt.getClaimAsString(CLAIM_TYPE))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(error);
            } catch (RuntimeException e) {
                return OAuth2TokenValidatorResult.failure(error); // typ of an unconvertible JSON type
            }
        };
    }

    private static OAuth2TokenValidator<Jwt> identityValidator() {
        OAuth2Error error = new OAuth2Error("invalid_token", "Token lacks subject or role", null);
        return jwt -> {
            try {
                toAuthUser(jwt);
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException e) {
                return OAuth2TokenValidatorResult.failure(error);
            }
        };
    }
}
