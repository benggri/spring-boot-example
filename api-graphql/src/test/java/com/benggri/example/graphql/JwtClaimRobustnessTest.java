package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Own-key tokens with wrong-typed or odd claims are rejected at the Bearer level (HTTP 401), never a 500. */
class JwtClaimRobustnessTest extends GraphqlTestBase {

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

    private static Map<String, Object> claims(Object sub, Object role, Object typ) {
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
    void request_withMalformedClaimsReturnsGeneric401(String name, Map<String, Object> claims) throws Exception {
        Gql res = exec(TokenFactory.withClaims(claims), "query{ posts{ total } }");

        assertThat(res.status()).isEqualTo(401);
        assertThat(res.contentType()).startsWith("application/problem+json");
        assertThat(res.<String>read("$.title")).isEqualTo("Unauthorized");
        assertThat(res.body()).doesNotContain("SUPERUSER").doesNotContain("99999999999999999999");
    }
}
