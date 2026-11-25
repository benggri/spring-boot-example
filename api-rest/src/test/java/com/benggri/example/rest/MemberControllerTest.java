package com.benggri.example.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benggri.example.core.common.DuplicateException;
import com.benggri.example.core.member.dto.MemberResult;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.service.MemberService;
import com.benggri.example.rest.controller.MemberController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
class MemberControllerTest extends SliceTestBase {

    private static final String BODY = "{\"email\":\"dup@x.com\",\"password\":\"password1\",\"name\":\"Dup\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MemberService memberService;

    @Test
    void register_createsAndReturnsLocation() throws Exception {
        given(memberService.register("dup@x.com", "password1", "Dup"))
                .willReturn(new MemberResult(7L, "dup@x.com", "Dup", Role.USER));

        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/members/7")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void register_duplicateEmailReturns409() throws Exception {
        given(memberService.register(any(), any(), any()))
                .willThrow(new DuplicateException("Email already registered: dup@x.com"));

        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(not(containsString("dup@x.com"))));
    }

    @Test
    void validationError_doesNotEchoRejectedPassword() throws Exception {
        String shortPassword = "short-pw";
        String body = "{\"email\":\"ok@x.com\",\"password\":\"abc\",\"name\":\"N\"}";

        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("password")))
                .andExpect(content().string(not(containsString("abc"))))
                .andExpect(content().string(not(containsString("rejected"))));
        // the 8+ char variant must not leak either
        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"" + shortPassword + "\",\"name\":\"N\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(shortPassword))))
                .andExpect(content().string(not(containsString("not-an-email"))));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
