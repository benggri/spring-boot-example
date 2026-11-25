package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.grpc.proto.CreatePostRequest;
import com.benggri.example.grpc.proto.LoginRequest;
import com.benggri.example.grpc.proto.RegisterRequest;
import com.benggri.example.grpc.proto.SearchPostsRequest;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** U+0000 is legal in proto3 strings but PostgreSQL cannot store it: it must be a client error, not INTERNAL. */
class GrpcInputSanitationTest extends GrpcTestBase {

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

    private void assertInvalidArgumentWithoutErrorLog(Runnable call) {
        Status status = statusOf(call);
        assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(status.getDescription()).doesNotContain("\u0000").doesNotContain("Zero bytes");
        assertThat(appender.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    void login_emailWithNulIsInvalidArgument() {
        assertInvalidArgumentWithoutErrorLog(() -> auth().login(LoginRequest.newBuilder()
                .setEmail("a\u0000@example.com").setPassword(PASSWORD).build()));
    }

    @Test
    void register_emailWithNulIsInvalidArgument() {
        assertInvalidArgumentWithoutErrorLog(() -> members(null).register(RegisterRequest.newBuilder()
                .setEmail("a\u0000@example.com").setPassword(PASSWORD).setName("n").build()));
    }

    @Test
    void register_nameWithNulIsInvalidArgument() {
        assertInvalidArgumentWithoutErrorLog(() -> members(null).register(RegisterRequest.newBuilder()
                .setEmail(uniqueEmail()).setPassword(PASSWORD).setName("n\u0000").build()));
    }

    @Test
    void register_blankPasswordIsInvalidArgument() {
        for (String blank : new String[] {"", "         "}) {
            assertInvalidArgumentWithoutErrorLog(() -> members(null).register(RegisterRequest.newBuilder()
                    .setEmail(uniqueEmail()).setPassword(blank).setName("n").build()));
        }
    }

    @Test
    void search_keywordWithNulIsInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        assertInvalidArgumentWithoutErrorLog(() -> posts(token).search(SearchPostsRequest.newBuilder()
                .setKeyword("a\u0000b").build()));
    }

    @Test
    void search_keywordLongerThan200IsInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        assertInvalidArgumentWithoutErrorLog(() -> posts(token).search(SearchPostsRequest.newBuilder()
                .setKeyword("k".repeat(201)).build()));
    }

    @Test
    void createPost_titleWithNulIsInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        assertInvalidArgumentWithoutErrorLog(() -> posts(token).create(CreatePostRequest.newBuilder()
                .setTitle("t\u0000").setContent("c").build()));
    }
}
