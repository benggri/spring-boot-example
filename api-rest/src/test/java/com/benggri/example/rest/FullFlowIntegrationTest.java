package com.benggri.example.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class FullFlowIntegrationTest extends IntegrationTestBase {

    private String createPost(String token, String title) throws Exception {
        String body = mvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json("title", title, "content", "hello")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return String.valueOf((Number) JsonPath.read(body, "$.id"));
    }

    @Test
    void registerLoginCreateSearchDelete() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email)[0];
        String keyword = "kw" + System.nanoTime();
        String postId = createPost(token, keyword + " title");

        mvc.perform(get("/api/posts").param("keyword", keyword).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(Long.parseLong(postId)));

        mvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMember_removesTheirPostsAndInvalidatesRefresh() throws Exception {
        String email = uniqueEmail();
        long memberId = register(email);
        String[] tokens = login(email);
        String keyword = "gone" + System.nanoTime();
        createPost(tokens[0], keyword);

        mvc.perform(delete("/api/members/" + memberId).header(HttpHeaders.AUTHORIZATION, bearer(tokens[0])))
                .andExpect(status().isNoContent());

        String other = uniqueEmail();
        register(other);
        String otherToken = login(other)[0];
        mvc.perform(get("/api/posts").param("keyword", keyword).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", tokens[1])))
                .andExpect(status().isUnauthorized());
    }
}
