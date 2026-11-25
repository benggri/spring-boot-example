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
        for (String name : List.of("env", "beans", "metrics", "info", "loggers", "heapdump", "")) {
            assertThat(get(appPort, "/actuator/" + name, null).statusCode())
                    .as("/actuator/%s", name).isNotEqualTo(200);
        }
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

    private static HttpResponse<String> get(int port, String path, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
}
