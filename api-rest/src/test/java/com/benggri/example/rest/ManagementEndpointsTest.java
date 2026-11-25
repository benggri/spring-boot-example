package com.benggri.example.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.support.PostgresTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real HTTP on both ports: the management port serves only health and prometheus. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ManagementEndpointsTest extends PostgresTestBase {

    private static final String PASSWORD = "password-" + UUID.randomUUID();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int appPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestSecrets.register(registry);
    }

    @AfterEach
    void cleanUp() {
        postRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void prometheus_returnsMetricsOnManagementPort() throws Exception {
        HttpResponse<String> res = get(managementPort, "/actuator/prometheus", null);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Type").orElse("")).startsWith("text/plain");
        assertThat(res.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void actuator_notServedOnMainPort() throws Exception {
        // anonymous: the main chain demands a token (401); with a token there is simply no such path (404)
        for (String path : List.of("/actuator/prometheus", "/actuator/health", "/actuator", "/actuator/")) {
            assertThat(get(appPort, path, null).statusCode()).as("anonymous main %s", path).isEqualTo(401);
            assertThat(get(appPort, path, userToken()).statusCode()).as("authenticated main %s", path)
                    .isEqualTo(404);
        }
    }

    @Test
    void nonExposedActuatorEndpointsAreNotAvailable() throws Exception {
        for (String name : List.of("env", "beans", "metrics", "info", "loggers", "heapdump", "")) {
            // management port: ManagementSecurityConfig denies every non-exposed endpoint (401 anonymous)
            assertThat(get(managementPort, "/actuator/" + name, null).statusCode())
                    .as("management /actuator/%s", name).isEqualTo(401);
            assertThat(get(appPort, "/actuator/" + name, null).statusCode())
                    .as("main anonymous /actuator/%s", name).isEqualTo(401);
            assertThat(get(appPort, "/actuator/" + name, userToken()).statusCode())
                    .as("main authenticated /actuator/%s", name).isEqualTo(404);
        }
    }

    @Test
    void actuatorDiscoveryPage_isNotServed() throws Exception {
        // management.endpoints.web.discovery.enabled=false: no links page, in either form of the path
        for (String path : List.of("/actuator", "/actuator/")) {
            HttpResponse<String> res = get(managementPort, path, null);
            assertThat(res.statusCode()).as("management %s", path).isEqualTo(401);
            assertThat(res.body()).as("management %s", path).doesNotContain("prometheus").doesNotContain("_links");
        }
    }

    @Test
    void managementPort_doesNotServeApplicationEndpoints() throws Exception {
        String token = userToken();
        for (String[] call : List.of(new String[] {"GET", "/api/posts"}, new String[] {"POST", "/auth/login"},
                new String[] {"POST", "/graphql"})) {
            assertThat(send(call[0], managementPort, call[1], token).statusCode())
                    .as("management %s %s with token", call[0], call[1]).isEqualTo(404);
        }
        // anonymous: the inherited chain answers 401, or 404 where the path is public in the main app
        assertThat(send("GET", managementPort, "/api/posts", null).statusCode()).isEqualTo(401);
        assertThat(send("POST", managementPort, "/graphql", null).statusCode()).isEqualTo(401);
        assertThat(send("POST", managementPort, "/auth/login", null).statusCode()).isEqualTo(404);
    }

    @Test
    void health_isUpWithoutDetails() throws Exception {
        HttpResponse<String> res = get(managementPort, "/actuator/health", null);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body().replaceAll("\\s", "")).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void managementEndpoints_requireNoToken_butMainApiStillDoes() throws Exception {
        assertThat(get(managementPort, "/actuator/prometheus", null).statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health", null).statusCode()).isEqualTo(200);
        assertThat(get(appPort, "/api/posts", null).statusCode()).isEqualTo(401);
    }

    @Test
    void serviceCall_recordsInvocationTimer() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@example.com";
        assertThat(register(email).statusCode()).isEqualTo(201);

        assertThat(timerCount("MemberService", "register", "success")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void failedCall_recordsFailureOutcome() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@example.com";
        assertThat(register(email).statusCode()).isEqualTo(201);
        assertThat(register(email).statusCode()).isEqualTo(409);

        assertThat(timerCount("MemberService", "register", "failure")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void metrics_neverContainPasswordOrEmail() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@example.com";
        register(email);
        HttpResponse<String> login = post("/auth/login", json("email", email, "password", PASSWORD));
        assertThat(login.statusCode()).isEqualTo(200);
        String token = login.body().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        String metrics = get(managementPort, "/actuator/prometheus", null).body();

        assertThat(metrics).doesNotContain(email).doesNotContain(PASSWORD).doesNotContain(token)
                .doesNotContain("eyJ");
    }

    private double timerCount(String clazz, String method, String outcome) throws Exception {
        String body = get(managementPort, "/actuator/prometheus", null).body();
        Pattern line = Pattern.compile("^app_service_invocation_seconds_count\\{([^}]*)}\\s+(\\S+)$",
                Pattern.MULTILINE);
        Matcher m = line.matcher(body);
        while (m.find()) {
            String labels = m.group(1);
            if (labels.contains("class=\"" + clazz + "\"") && labels.contains("method=\"" + method + "\"")
                    && labels.contains("outcome=\"" + outcome + "\"")) {
                return Double.parseDouble(m.group(2));
            }
        }
        return 0;
    }

    private HttpResponse<String> register(String email) throws Exception {
        return post("/api/members", json("email", email, "password", PASSWORD, "name", "Tester"));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + appPort + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String userToken() {
        return TokenFactory.token("1", "USER", "access", Duration.ZERO);
    }

    private static HttpResponse<String> send(String method, int port, String path, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString("{}"));
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(int port, String path, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String json(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            sb.append(i > 0 ? "," : "").append('"').append(kv[i]).append("\":\"").append(kv[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }
}
