package com.benggri.example.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;

/** Own-key tokens with wrong-typed or odd claims must be a generic 401, never a 500. */
class JwtClaimRobustnessTest extends IntegrationTestBase {

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
    void listPosts_withMalformedClaimsReturnsGeneric401(String name, Map<String, Object> claims) throws Exception {
        String token = TokenFactory.withClaims(claims);

        mvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(content().string(not(containsString("SUPERUSER"))))
                .andExpect(content().string(not(containsString("99999999999999999999"))));
    }
}
