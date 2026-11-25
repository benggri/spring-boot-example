package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.post.service.PostService;
import com.benggri.example.grpc.proto.CreatePostRequest;
import com.benggri.example.grpc.proto.RegisterRequest;
import io.grpc.Status;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class GrpcErrorMappingTest extends GrpcTestBase {

    private static final String SECRET_DETAIL = "internal-detail-" + UUID.randomUUID();

    @MockitoSpyBean
    private PostService postService;

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

    @Test
    void error_statusDescriptionsNeverContainExceptionMessage() {
        String email = uniqueEmail();
        RegisterRequest request = RegisterRequest.newBuilder()
                .setEmail(email).setPassword(PASSWORD).setName("n").build();
        members(null).register(request);
        Status status = statusOf(() -> members(null).register(request));
        assertThat(status.getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(status.getDescription()).isEqualTo("Already exists").doesNotContain(email);
    }

    @Test
    void error_unexpectedFailureIsGenericInternalAndLoggedWithoutPayload() {
        Member user = saveMember(Role.USER);
        doThrow(new IllegalStateException(SECRET_DETAIL)).when(postService).create(any(), any(), any());
        Status status = statusOf(() -> posts(tokenFor(user)).create(CreatePostRequest.newBuilder()
                .setTitle("payload-title-marker").setContent("c").build()));
        assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(status.getDescription()).isEqualTo("Internal error").doesNotContain(SECRET_DETAIL);
        assertThat(status.getCause()).isNull();

        assertThat(appender.list).filteredOn(e -> e.getLevel() == Level.ERROR)
                .anySatisfy(e -> assertThat(e.getThrowableProxy().getClassName())
                        .isEqualTo(IllegalStateException.class.getName()))
                .noneSatisfy(e -> assertThat(e.getFormattedMessage()).contains("payload-title-marker"));
    }
}
