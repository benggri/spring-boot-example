package com.benggri.example.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Framework-originated errors (415, 404, 405) must carry fixed details, never client input. */
class FrameworkErrorBodyTest extends IntegrationTestBase {

    @Test
    void unsupportedContentType_returns415WithFixedDetail() throws Exception {
        String contentType = "text/x-evil-" + UUID.randomUUID();
        mvc.perform(post("/auth/login").contentType(contentType).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Unsupported media type"))
                .andExpect(content().string(not(containsString(contentType))))
                .andExpect(content().string(not(containsString("x-evil"))));
    }

    @Test
    void unknownPath_returns404WithoutEchoingPath() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email)[0];
        String path = "/api/does-not-exist-" + UUID.randomUUID();
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Not found"))
                .andExpect(content().string(not(containsString("does-not-exist"))));
    }

    @Test
    void wrongMethod_returns405WithoutEchoingMethod() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email)[0];
        mvc.perform(delete("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Method not allowed"))
                .andExpect(content().string(not(containsString("DELETE"))));
    }

    @Test
    void malformedPathId_returns400WithoutEchoingValue() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email)[0];
        // a non-numeric {id} is a type mismatch; the value must not be echoed
        String junk = "junk-" + UUID.randomUUID();
        mvc.perform(get("/api/members/" + junk).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(junk))));
    }
}
