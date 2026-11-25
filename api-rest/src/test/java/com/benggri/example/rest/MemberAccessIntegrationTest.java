package com.benggri.example.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Self-or-admin rules exercised with real tokens, so the role claim flows through decoder and converter. */
class MemberAccessIntegrationTest extends IntegrationTestBase {

    @Test
    void getMember_bySelfReturns200WithoutPasswordHash() throws Exception {
        String email = uniqueEmail();
        long id = register(email);
        String token = login(email)[0];

        String body = mvc.perform(get("/api/members/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("$2a$").doesNotContainIgnoringCase("password");
    }

    @Test
    void getMember_byOtherUserReturns403AndBodyHasNoEmail() throws Exception {
        String emailA = uniqueEmail();
        long idA = register(emailA);
        String emailB = uniqueEmail();
        register(emailB);
        String tokenB = login(emailB)[0];

        mvc.perform(get("/api/members/" + idA).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(not(containsString(emailA))));
    }

    @Test
    void getMember_byAdminReturns200() throws Exception {
        String emailA = uniqueEmail();
        long idA = register(emailA);
        String adminEmail = uniqueEmail();
        createAdmin(adminEmail);
        String adminToken = login(adminEmail)[0];

        mvc.perform(get("/api/members/" + idA).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailA));
    }

    @Test
    void getMember_missingReturns404() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email)[0];

        mvc.perform(get("/api/members/999999999").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePost_byAdminReturns204AndUserForbidden() throws Exception {
        String ownerEmail = uniqueEmail();
        register(ownerEmail);
        String ownerToken = login(ownerEmail)[0];
        String otherEmail = uniqueEmail();
        register(otherEmail);
        String otherToken = login(otherEmail)[0];
        String adminEmail = uniqueEmail();
        createAdmin(adminEmail);
        String adminToken = login(adminEmail)[0];
        Number postId = createPost(ownerToken);

        mvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    private Number createPost(String token) throws Exception {
        String body = mvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json("title", "t", "content", "c")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
