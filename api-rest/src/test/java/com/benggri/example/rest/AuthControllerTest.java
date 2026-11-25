package com.benggri.example.rest;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benggri.example.core.security.AuthService;
import com.benggri.example.core.security.InvalidTokenException;
import com.benggri.example.core.security.TokenPair;
import com.benggri.example.rest.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends SliceTestBase {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void login_returnsTokens() throws Exception {
        given(authService.login("a@x.com", "password1")).willReturn(new TokenPair("acc", "ref"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"));
    }

    @Test
    void login_badCredentialsReturnsGeneric401() throws Exception {
        given(authService.login(any(), any())).willThrow(new BadCredentialsException("leaky-detail"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(not(containsString("leaky-detail"))));
    }

    @Test
    void login_blankFieldsReturn400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_invalidTokenReturns401() throws Exception {
        given(authService.refresh(any())).willThrow(new InvalidTokenException("leaky-detail"));

        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("leaky-detail"))));
    }
}
