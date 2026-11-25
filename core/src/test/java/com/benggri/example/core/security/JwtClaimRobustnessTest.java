package com.benggri.example.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Regression protection: tokens signed with our own key but carrying claims of the wrong JSON type or
 * value must end as an invalid-token / authentication failure, never as an unchecked 500.
 */
class JwtClaimRobustnessTest {

    private JwtProperties properties;
    private JwtProvider provider;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(TestSecrets.randomSecret(), Duration.ofMinutes(15), Duration.ofDays(7));
        provider = new JwtProvider(JwtConfig.hs256Encoder(properties), properties, java.time.Clock.systemUTC());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    static Stream<Arguments> badClaims() {
        return Stream.of(
                Arguments.of("roleAsArray", claims("1", List.of("ADMIN"), "access")),
                Arguments.of("roleBlank", claims("1", " ", "access")),
                Arguments.of("roleUnknown", claims("1", "SUPERUSER", "access")),
                Arguments.of("typAsObject", claims("1", "USER", Map.of("a", "b"))),
                Arguments.of("subAsArray", claims(List.of("1"), "USER", "access")),
                Arguments.of("subOverflow", claims("99999999999999999999", "USER", "access")),
                Arguments.of("missingSubAndRole", claims(null, null, "access")));
    }

    static Map<String, Object> claims(Object sub, Object role, Object typ) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (sub != null) {
            map.put("sub", sub);
        }
        if (role != null) {
            map.put("role", role);
        }
        map.put("typ", typ);
        return map;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badClaims")
    void parseAccess_rejectsMalformedClaims(String name, Map<String, Object> claims) {
        String token = sign(claims);

        assertThatThrownBy(() -> provider.parseAccess(token)).isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid token");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badClaims")
    void accessDecoder_rejectsMalformedClaimsWithJwtException(String name, Map<String, Object> claims) {
        String token = sign(claims);

        assertThatThrownBy(() -> provider.accessDecoder().decode(token)).isInstanceOf(JwtException.class);
    }

    /** {@code typ} is the decoder's concern; CurrentUser only reads sub and role from an already verified JWT. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("badClaims")
    void currentUser_rejectsMalformedClaims(String name, Map<String, Object> claims) {
        assumeFalse(name.equals("typAsObject"));
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS256");
        claims.forEach(builder::claim);
        Jwt jwt = builder.claim("exp", Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_X"));

        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badClaims")
    void rejection_neverLeaksClaimValuesInMessage(String name, Map<String, Object> claims) {
        String token = sign(claims);

        assertThatThrownBy(() -> provider.parseAccess(token)).satisfies(e ->
                assertThat(e.getMessage()).doesNotContain("SUPERUSER").doesNotContain("99999999999999999999"));
    }

    private String sign(Map<String, Object> claims) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuedAt(now).expiresAt(now.plusSeconds(600));
        claims.forEach(builder::claim);
        return JwtConfig.hs256Encoder(properties)
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build()))
                .getTokenValue();
    }
}
