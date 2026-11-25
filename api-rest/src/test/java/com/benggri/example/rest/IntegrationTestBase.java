package com.benggri.example.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.support.PostgresTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Full application against real PostgreSQL; helpers drive registration and login over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestBase extends PostgresTestBase {

    static final String PASSWORD = "password-" + UUID.randomUUID();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    protected MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestSecrets.register(registry);
    }

    @AfterEach
    void cleanUp() {
        // committed rows would leak into other tests sharing the singleton container
        postRepository.deleteAll();
        memberRepository.deleteAll();
    }

    static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }

    protected long register(String email) throws Exception {
        String body = mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", PASSWORD, "name", "Tester")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Inserts an ADMIN directly (registration only creates USERs); logs in with {@link #PASSWORD}. */
    protected void createAdmin(String email) {
        memberRepository.save(Member.create(email, passwordEncoder.encode(PASSWORD), "Admin", Role.ADMIN));
    }

    /** Returns {accessToken, refreshToken}. */
    protected String[] login(String email) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken")};
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            sb.append(i > 0 ? "," : "").append('"').append(keyValues[i]).append("\":\"")
                    .append(keyValues[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }
}
