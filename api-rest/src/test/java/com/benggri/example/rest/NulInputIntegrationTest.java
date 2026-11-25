package com.benggri.example.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** U+0000 (JSON "\u0000" or %00) cannot be stored by PostgreSQL: it must be a 400, never a 500 or ERROR log. */
class NulInputIntegrationTest extends IntegrationTestBase {

    /** A JSON escape sequence, so the request body really contains backslash-u-0-0-0-0. */
    private static final String NUL_ESCAPE = "\\u0000";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;

    @BeforeEach
    void attachAppender() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        root.detachAppender(appender);
    }

    private void assertGeneric400(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(not(containsString("Zero bytes"))));
        assertThat(appender.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    void login_emailWithNulReturns400() throws Exception {
        assertGeneric400(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", "a" + NUL_ESCAPE + "@example.com", "password", PASSWORD))));
    }

    @Test
    void register_emailWithNulReturns400() throws Exception {
        assertGeneric400(mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", "a" + NUL_ESCAPE + "@example.com", "password", PASSWORD, "name", "n"))));
    }

    @Test
    void register_nameWithNulReturns400() throws Exception {
        assertGeneric400(mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", uniqueEmail(), "password", PASSWORD, "name", "n" + NUL_ESCAPE))));
    }

    @Test
    void listPosts_keywordWithNulReturns400() throws Exception {
        // MockMvc does not reject %00 in the container; the parameter is decoded to a real NUL character
        String email = uniqueEmail();
        register(email);
        String token = login(email)[0];
        assertGeneric400(mvc.perform(get("/api/posts").param("keyword", "a\u0000b")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))));
    }

    @Test
    void createPost_titleWithNulReturns400() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email)[0];
        assertGeneric400(mvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .content(json("title", "t" + NUL_ESCAPE, "content", "c"))));
    }
}
