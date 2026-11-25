package com.benggri.example.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.post.service.PostService;
import com.benggri.example.rest.controller.PostController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PostController.class)
class PostControllerTest extends SliceTestBase {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PostService postService;

    private static RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("1").claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void createPost_blankTitleReturns400() throws Exception {
        mvc.perform(post("/api/posts").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"content\":\"body\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(containsString("title")));
    }

    @Test
    void listPosts_negativePageReturns400() throws Exception {
        mvc.perform(get("/api/posts").with(user()).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPosts_sizeBelowOneReturns400() throws Exception {
        mvc.perform(get("/api/posts").with(user()).param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPosts_nonNumericPageReturns400() throws Exception {
        mvc.perform(get("/api/posts").with(user()).param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPosts_returnsPageResponse() throws Exception {
        given(postService.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 42));

        mvc.perform(get("/api/posts").with(user()).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total").value(42))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void updatePost_forbiddenReturns403() throws Exception {
        given(postService.update(any(), any(), any(), any())).willThrow(new ForbiddenException("not yours"));

        mvc.perform(put("/api/posts/3").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("not yours"))));
    }

    @Test
    void getPost_missingReturns404() throws Exception {
        given(postService.get(99L)).willThrow(new NotFoundException("Post 99 not found"));

        mvc.perform(get("/api/posts/99").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Post 99"))));
    }

    @Test
    void internalError_returnsGenericBodyWithoutDetails() throws Exception {
        given(postService.get(5L)).willThrow(new IllegalStateException("db password=hunter2 at Foo.bar"));

        mvc.perform(get("/api/posts/5").with(user()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("Foo.bar"))))
                .andExpect(content().string(not(containsString("at com."))));
    }
}
