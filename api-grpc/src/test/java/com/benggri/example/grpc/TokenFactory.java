package com.benggri.example.grpc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Hand-builds tokens (expired, wrong type, missing claims) signed with the test secret. */
public final class TokenFactory {

    private static final JwtEncoder ENCODER = NimbusJwtEncoder
            .withSecretKey(new SecretKeySpec(TestSecrets.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .algorithm(MacAlgorithm.HS256).build();

    private TokenFactory() {
    }

    /** A signed token issued {@code age} ago and valid for 15 minutes from then. */
    public static String token(String subject, String role, String type, Duration age) {
        Instant issued = Instant.now().minus(age);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .claim("typ", type).issuedAt(issued).expiresAt(issued.plus(Duration.ofMinutes(15)));
        if (subject != null) {
            claims.subject(subject);
        }
        if (role != null) {
            claims.claim("role", role);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /** A signed token whose claims are exactly {@code claims} (plus iat/exp): for wrong-typed or odd values. */
    public static String withClaims(Map<String, Object> claims) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuedAt(now).expiresAt(now.plusSeconds(900));
        claims.forEach(builder::claim);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }
}
