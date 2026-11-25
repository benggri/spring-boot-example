package com.benggri.example.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RestSecurityIntegrationTest extends IntegrationTestBase {

    private void assertGeneric401(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(content().string(not(containsString("Jwt"))))
                .andExpect(content().string(not(containsString("expired"))));
    }

    @Test
    void listPosts_withoutTokenReturns401() throws Exception {
        assertGeneric401(mvc.perform(get("/api/posts")));
    }

    @Test
    void listPosts_expiredTokenReturns401() throws Exception {
        String expired = TokenFactory.token("1", "USER", "access", Duration.ofHours(1));
        assertGeneric401(mvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(expired))));
    }

    @Test
    void listPosts_refreshTokenAsBearerReturns401() throws Exception {
        String email = uniqueEmail();
        register(email);
        String refresh = login(email)[1];
        assertGeneric401(mvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(refresh))));
    }

    @Test
    void listPosts_malformedBearerReturns401() throws Exception {
        assertGeneric401(mvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")));
    }

    @Test
    void listPosts_tokenWithoutRoleReturns401() throws Exception {
        String noRole = TokenFactory.token("1", null, "access", Duration.ZERO);
        assertGeneric401(mvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(noRole))));
    }

    @Test
    void refresh_withAccessTokenReturns401() throws Exception {
        String email = uniqueEmail();
        register(email);
        String access = login(email)[0];
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", access)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(access))));
    }

    @Test
    void refresh_withRefreshTokenIssuesNewPair() throws Exception {
        String email = uniqueEmail();
        register(email);
        String refresh = login(email)[1];
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void login_wrongPasswordAndUnknownEmailReturnSame401Body() throws Exception {
        String email = uniqueEmail();
        register(email);
        String wrongPassword = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", "wrong-password-1")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", uniqueEmail(), "password", "wrong-password-1")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword).isEqualTo(unknownEmail).doesNotContain(email);
    }

    @Test
    void register_responseNeverContainsPasswordOrHash() throws Exception {
        String body = mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", uniqueEmail(), "password", PASSWORD, "name", "Tester")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD).doesNotContain("$2a$").doesNotContainIgnoringCase("password");
    }

    @Test
    void register_duplicateEmailReturns409() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", PASSWORD, "name", "Again")))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString(email))));
    }

    @Test
    void listPosts_sizeOver100Clamped() throws Exception {
        String email = uniqueEmail();
        register(email);
        String access = login(email)[0];
        mvc.perform(get("/api/posts").param("size", "1000").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void updatePost_byOtherUserReturns403() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        register(emailA);
        register(emailB);
        String tokenA = login(emailA)[0];
        String tokenB = login(emailB)[0];
        String created = mvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content(json("title", "t", "content", "c")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number postId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mvc.perform(put("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON).content(json("title", "hacked", "content", "c")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void swaggerUi_accessibleWithoutToken() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void apiDocs_declaresBearerScheme() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }
}
