package com.benggri.example.graphql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.entity.Post;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.security.JwtProvider;
import com.benggri.example.core.support.PostgresTestBase;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Full application against real PostgreSQL; requests go through the real security filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class GraphqlTestBase extends PostgresTestBase {

    static final String PASSWORD = "password-" + UUID.randomUUID();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MemberRepository memberRepository;

    @Autowired
    protected PostRepository postRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtProvider jwtProvider;

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

    /** A decoded GraphQL-over-HTTP response. */
    record Gql(int status, String contentType, String body) {

        <T> T read(String path) {
            return JsonPath.read(body, path);
        }

        /** The first error's {@code extensions.code}, or null when there are no errors. */
        String code() {
            try {
                return JsonPath.read(body, "$.errors[0].extensions.code");
            } catch (com.jayway.jsonpath.PathNotFoundException e) {
                return null;
            }
        }
    }

    protected Gql exec(String token, String query, Map<String, Object> variables) throws Exception {
        String payload = JSON.writeValueAsString(Map.of("query", query, "variables", variables));
        MockHttpServletRequestBuilder request = post("/graphql")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(payload);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mvc.perform(request).andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result = mvc.perform(asyncDispatch(result)).andReturn();
        }
        return new Gql(result.getResponse().getStatus(), result.getResponse().getContentType(),
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected Gql exec(String token, String query) throws Exception {
        return exec(token, query, Map.of());
    }

    static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }

    protected Member saveMember(String email, Role role) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode(PASSWORD), "Name-" + role, role));
    }

    protected Member saveMember(Role role) {
        return saveMember(uniqueEmail(), role);
    }

    protected Post savePost(Member author, String title) {
        return postRepository.save(Post.create(author, title, "content of " + title));
    }

    protected String tokenFor(Member member) {
        return jwtProvider.issueAccess(member);
    }

    /** Real login through the mutation; returns {accessToken, refreshToken}. */
    protected String[] login(String email) throws Exception {
        Gql res = exec(null, "mutation($i: LoginInput!){ login(input:$i){ accessToken refreshToken } }",
                Map.of("i", Map.of("email", email, "password", PASSWORD)));
        return new String[] {res.read("$.data.login.accessToken"), res.read("$.data.login.refreshToken")};
    }
}
