package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.support.PostgresTestBase;
import com.benggri.example.grpc.proto.CreatePostRequest;
import com.benggri.example.grpc.proto.LoginRequest;
import com.benggri.example.grpc.proto.AuthServiceGrpc;
import com.benggri.example.grpc.proto.PostServiceGrpc;
import com.benggri.example.grpc.proto.SearchPostsRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.repository.PostRepository;

/** The real Netty/TCP server (random port, plaintext): auth and the inbound size bound work end to end. */
@SpringBootTest(properties = "spring.grpc.server.port=0")
class GrpcNettyTransportTest extends PostgresTestBase {

    private static final String PASSWORD = "password-" + java.util.UUID.randomUUID();

    @LocalGrpcServerPort
    private int port;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private ManagedChannel channel;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestSecrets.register(registry);
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        postRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private ManagedChannel open() {
        channel = NettyChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        return channel;
    }

    @Test
    void overTcp_noTokenIsUnauthenticatedAndLoginThenSearchWorks() {
        Member user = memberRepository.save(Member.create("u-" + java.util.UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(PASSWORD), "Tcp", Role.USER));
        ManagedChannel ch = open();
        StatusRuntimeException denied = org.junit.jupiter.api.Assertions.assertThrows(StatusRuntimeException.class,
                () -> PostServiceGrpc.newBlockingStub(ch).search(SearchPostsRequest.getDefaultInstance()));
        assertThat(denied.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);

        String token = AuthServiceGrpc.newBlockingStub(ch)
                .login(LoginRequest.newBuilder().setEmail(user.getEmail()).setPassword(PASSWORD).build())
                .getAccessToken();
        assertThat(authed(ch, token).search(SearchPostsRequest.getDefaultInstance()).getSize()).isEqualTo(20);
    }

    @Test
    void overTcp_messageOver256KbIsRejectedBeforeTheHandler() {
        Member user = memberRepository.save(Member.create("u-" + java.util.UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(PASSWORD), "Big", Role.USER));
        ManagedChannel ch = open();
        String token = AuthServiceGrpc.newBlockingStub(ch)
                .login(LoginRequest.newBuilder().setEmail(user.getEmail()).setPassword(PASSWORD).build())
                .getAccessToken();
        CreatePostRequest huge = CreatePostRequest.newBuilder()
                .setTitle("t").setContent("c".repeat(300 * 1024)).build();
        StatusRuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(StatusRuntimeException.class,
                () -> authed(ch, token).create(huge));
        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(postRepository.count()).isZero();
    }

    private static PostServiceGrpc.PostServiceBlockingStub authed(ManagedChannel ch, String token) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return PostServiceGrpc.newBlockingStub(ch)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
