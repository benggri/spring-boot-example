package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benggri.example.core.member.entity.Role;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphqlSecurityIntegrationTest extends GraphqlTestBase {

    private static final String REGISTER =
            "mutation($i:RegisterInput!){ register(input:$i){ id email name role } }";
    private static final String LOGIN = "mutation($i:LoginInput!){ login(input:$i){ accessToken refreshToken } }";
    private static final String REFRESH = "mutation($t:String!){ refresh(refreshToken:$t){ accessToken refreshToken } }";
    private static final String POSTS = "query{ posts{ total } }";

    private void assertGeneric401(Gql res) {
        assertThat(res.status()).isEqualTo(401);
        assertThat(res.contentType()).startsWith("application/problem+json");
        assertThat(res.<String>read("$.title")).isEqualTo("Unauthorized");
        assertThat(res.body()).doesNotContain("Jwt").doesNotContain("expired");
    }

    @Test
    void anonymousCanRegisterLoginAndRefresh() throws Exception {
        String email = uniqueEmail();

        Gql registered = exec(null, REGISTER, Map.of("i", Map.of("email", email, "password", PASSWORD, "name", "N")));
        Gql login = exec(null, LOGIN, Map.of("i", Map.of("email", email, "password", PASSWORD)));
        Gql refreshed = exec(null, REFRESH, Map.of("t", login.<String>read("$.data.login.refreshToken")));

        assertThat(registered.code()).isNull();
        assertThat(registered.<String>read("$.data.register.role")).isEqualTo("USER");
        assertThat(login.<String>read("$.data.login.accessToken")).isNotBlank();
        assertThat(refreshed.<String>read("$.data.refresh.accessToken")).isNotBlank();
    }

    @Test
    void refresh_returnsNewTokens() throws Exception {
        String email = uniqueEmail();
        saveMember(email, Role.USER);
        String[] tokens = login(email);

        Gql res = exec(null, REFRESH, Map.of("t", tokens[1]));

        assertThat(res.<String>read("$.data.refresh.accessToken")).isNotBlank();
        assertThat(res.<String>read("$.data.refresh.refreshToken")).isNotBlank();
        // the new access token really works
        assertThat(exec(res.read("$.data.refresh.accessToken"), POSTS).code()).isNull();
    }

    @Test
    void refresh_withAccessTokenOrGarbageIsUnauthenticated() throws Exception {
        String email = uniqueEmail();
        saveMember(email, Role.USER);
        String access = login(email)[0];

        Gql viaAccess = exec(null, REFRESH, Map.of("t", access));
        Gql garbage = exec(null, REFRESH, Map.of("t", "not.a.jwt"));

        assertThat(viaAccess.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(garbage.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(viaAccess.body()).doesNotContain(access);
    }

    @Test
    void anonymousCannotReachProtectedOperations() throws Exception {
        assertThat(exec(null, POSTS).code()).isEqualTo("UNAUTHENTICATED");
        assertThat(exec(null, "query{ post(id:\"1\"){ id } }").code()).isEqualTo("UNAUTHENTICATED");
        assertThat(exec(null, "mutation{ deletePost(id:\"1\") }").code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void otherPathsStayAuthenticated() throws Exception {
        mvc.perform(get("/graphiql")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
        mvc.perform(get("/graphql")).andExpect(status().isUnauthorized());
        mvc.perform(post("/graphql/other")).andExpect(status().isUnauthorized());
    }

    @Test
    void request_withExpiredTokenReturnsHttp401() throws Exception {
        String expired = TokenFactory.token("1", "USER", "access", Duration.ofHours(1));

        assertGeneric401(exec(expired, POSTS));
        // even the anonymous mutations: a bad token is never ignored
        assertGeneric401(exec(expired, LOGIN, Map.of("i", Map.of("email", "a@b.co", "password", PASSWORD))));
    }

    @Test
    void request_withRefreshTokenAsBearerReturnsHttp401() throws Exception {
        String email = uniqueEmail();
        saveMember(email, Role.USER);
        String refresh = login(email)[1];

        assertGeneric401(exec(refresh, POSTS));
    }

    @Test
    void request_withTokenWithoutRoleReturnsHttp401() throws Exception {
        assertGeneric401(exec(TokenFactory.token("1", null, "access", Duration.ZERO), POSTS));
    }

    @Test
    void request_withMalformedBearerReturnsHttp401() throws Exception {
        assertGeneric401(exec("not.a.jwt", POSTS));
    }

    @Test
    void login_wrongPasswordAndUnknownEmailHaveSameUnauthenticatedError() throws Exception {
        String email = uniqueEmail();
        saveMember(email, Role.USER);

        Gql wrongPassword = exec(null, LOGIN, Map.of("i", Map.of("email", email, "password", "wrong-" + PASSWORD)));
        Gql unknownEmail = exec(null, LOGIN, Map.of("i", Map.of("email", uniqueEmail(), "password", PASSWORD)));

        assertThat(wrongPassword.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(unknownEmail.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(wrongPassword.<String>read("$.errors[0].message"))
                .isEqualTo(unknownEmail.<String>read("$.errors[0].message"));
        assertThat(wrongPassword.body()).doesNotContain(email).doesNotContain(PASSWORD);
    }
}
