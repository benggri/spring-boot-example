package com.benggri.example.grpc;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.entity.Post;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.security.JwtProvider;
import com.benggri.example.core.support.PostgresTestBase;
import com.benggri.example.grpc.proto.AuthServiceGrpc;
import com.benggri.example.grpc.proto.LoginRequest;
import com.benggri.example.grpc.proto.MemberServiceGrpc;
import com.benggri.example.grpc.proto.PostServiceGrpc;
import com.benggri.example.grpc.proto.TokenResponse;
import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Full application (in-process gRPC transport) against real PostgreSQL, called with real tokens. */
@SpringBootTest
@AutoConfigureTestGrpcTransport
abstract class GrpcTestBase extends PostgresTestBase {

    static final String PASSWORD = "password-" + UUID.randomUUID();
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Autowired
    protected GrpcChannelFactory channels;
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

    protected Channel channel() {
        return channels.createChannel("test");
    }

    /** A stub that sends {@code authorization: <headerValue>}; null sends no header at all. */
    protected <T extends AbstractStub<T>> T withAuth(T stub, String headerValue) {
        if (headerValue == null) {
            return stub;
        }
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION, headerValue);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    protected PostServiceGrpc.PostServiceBlockingStub posts(String accessToken) {
        return withAuth(PostServiceGrpc.newBlockingStub(channel()), accessToken == null ? null : "Bearer " + accessToken);
    }

    protected MemberServiceGrpc.MemberServiceBlockingStub members(String accessToken) {
        return withAuth(MemberServiceGrpc.newBlockingStub(channel()), accessToken == null ? null : "Bearer " + accessToken);
    }

    protected AuthServiceGrpc.AuthServiceBlockingStub auth() {
        return AuthServiceGrpc.newBlockingStub(channel());
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

    /** Real Login RPC. */
    protected TokenResponse login(String email) {
        return auth().login(LoginRequest.newBuilder().setEmail(email).setPassword(PASSWORD).build());
    }

    /** Runs the call and returns the status it failed with; fails if it succeeded. */
    protected Status statusOf(Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException e) {
            return e.getStatus();
        }
        throw new AssertionError("expected the call to fail");
    }

    protected void assertFails(Runnable call, Status.Code code, String description) {
        Status status = statusOf(call);
        assertThat(status.getCode()).isEqualTo(code);
        assertThat(status.getDescription()).isEqualTo(description);
    }
}
