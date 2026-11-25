package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.grpc.proto.RegisterRequest;
import io.grpc.Status;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Real HTTP on the single actuator port; calls go through gRPC (in-process transport) first. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ManagementEndpointsTest extends GrpcTestBase {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int appPort;

    @Test
    void prometheus_returnsMetricsOnActuatorPort() throws Exception {
        HttpResponse<String> res = get(appPort, "/actuator/prometheus", null);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Type").orElse("")).startsWith("text/plain");
        assertThat(res.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void nonExposedActuatorEndpointsAreNotAvailable() throws Exception {
        // names include "" (/actuator/): the links page is disabled, so it is a plain 404 as well
        for (String name : List.of("env", "beans", "metrics", "info", "loggers", "heapdump", "")) {
            assertThat(get(appPort, "/actuator/" + name, null).statusCode())
                    .as("/actuator/%s", name).isEqualTo(404);
        }
    }

    @Test
    void actuatorDiscoveryPage_isNotServed() throws Exception {
        // management.endpoints.web.discovery.enabled=false; this app has no security chain, so 404 not 401
        for (String path : List.of("/actuator", "/actuator/")) {
            HttpResponse<String> res = get(appPort, path, null);
            assertThat(res.statusCode()).as(path).isEqualTo(404);
            assertThat(res.body()).as(path).doesNotContain("_links").doesNotContain("prometheus");
        }
    }

    @Test
    void httpPort_servesNothingButHealthAndPrometheus() throws Exception {
        for (String path : List.of("/api/posts", "/auth/login", "/graphql", "/", "/swagger-ui.html")) {
            assertThat(get(appPort, path, null).statusCode()).as("GET %s", path).isEqualTo(404);
        }
        assertThat(post(appPort, "/actuator/health").statusCode()).isEqualTo(405);
        assertThat(post(appPort, "/graphql").statusCode()).isEqualTo(404);
    }

    @Test
    void errorBodies_carryNoTraceOrMessage() throws Exception {
        // Boot's default error JSON echoes the request path back to the same client; nothing else
        for (String path : List.of("/api/posts", "/actuator/env", "/error")) {
            assertThat(get(appPort, path, null).body()).as(path)
                    .doesNotContain("trace").doesNotContain("exception").doesNotContain("message")
                    .doesNotContain("Exception");
        }
    }

    @Test
    void grpcCall_isRecordedByServerObservation() throws Exception {
        register(uniqueEmail());

        String metrics = get(appPort, "/actuator/prometheus", null).body();

        assertThat(metrics).containsPattern("grpc_server_seconds_count\\{[^}]*rpc_method=\"Register\""
                + "[^}]*rpc_service=\"example.v1.MemberService\"");
    }

    @Test
    void health_isUpWithoutDetails() throws Exception {
        HttpResponse<String> res = get(appPort, "/actuator/health", null);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body().replaceAll("\\s", "")).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void serviceCall_recordsInvocationTimer() throws Exception {
        String email = uniqueEmail();
        register(email);

        assertThat(timerCount("MemberService", "register", "success")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void failedCall_recordsFailureOutcome() throws Exception {
        String email = uniqueEmail();
        register(email);
        assertThat(statusOf(() -> register(email)).getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);

        assertThat(timerCount("MemberService", "register", "failure")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void metrics_neverContainPasswordOrEmail() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email).getAccessToken();

        String metrics = get(appPort, "/actuator/prometheus", null).body();

        assertThat(metrics).doesNotContain(email).doesNotContain(PASSWORD).doesNotContain(token)
                .doesNotContain("eyJ");
    }

    private double timerCount(String clazz, String method, String outcome) throws Exception {
        String body = get(appPort, "/actuator/prometheus", null).body();
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

    private void register(String email) {
        members(null).register(RegisterRequest.newBuilder()
                .setEmail(email).setPassword(PASSWORD).setName("Tester").build());
    }

    private static HttpResponse<String> post(int port, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(int port, String path, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
}
