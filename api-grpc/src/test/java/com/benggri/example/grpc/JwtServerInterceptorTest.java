package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.grpc.proto.AuthServiceGrpc;
import com.benggri.example.grpc.proto.CreatePostRequest;
import com.benggri.example.grpc.proto.LoginRequest;
import com.benggri.example.grpc.proto.PostServiceGrpc;
import com.benggri.example.grpc.proto.SearchPostsRequest;
import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JwtServerInterceptorTest extends GrpcTestBase {

    private static final CreatePostRequest VALID = CreatePostRequest.newBuilder()
            .setTitle("title").setContent("content").build();
    private static final Set<String> PUBLIC = Set.of("example.v1.AuthService/Login",
            "example.v1.AuthService/Refresh", "example.v1.MemberService/Register");

    private static final String HEALTH_CHECK = "grpc.health.v1.Health/Check";

    @Autowired
    private List<io.grpc.BindableService> services;

    private void assertUnauthenticated(Runnable call) {
        assertFails(call, Status.Code.UNAUTHENTICATED, "Unauthenticated");
    }

    /**
     * Search never reads the caller, so only the interceptor can reject it; Create would also fail closed
     * in AuthContext.current() and so could not prove the interceptor works on its own.
     */
    private void assertRejectedEverywhere(String token) {
        assertUnauthenticated(() -> posts(token).create(VALID));
        assertUnauthenticated(() -> posts(token).search(SearchPostsRequest.getDefaultInstance()));
    }

    @Test
    void createPost_withoutMetadataIsUnauthenticated() {
        assertRejectedEverywhere(null);
    }

    @Test
    void createPost_expiredTokenIsUnauthenticated() {
        String expired = TokenFactory.token("1", "USER", "access", Duration.ofHours(1));
        assertRejectedEverywhere(expired);
    }

    @Test
    void request_refreshTokenAsBearerIsUnauthenticated() {
        String refresh = TokenFactory.token("1", "USER", "refresh", Duration.ZERO);
        assertRejectedEverywhere(refresh);
    }

    @Test
    void request_tokenWithoutRoleIsUnauthenticated() {
        String noRole = TokenFactory.token("1", null, "access", Duration.ZERO);
        assertRejectedEverywhere(noRole);
    }

    @Test
    void request_malformedTokenIsUnauthenticated() {
        assertRejectedEverywhere("not-a-jwt");
    }

    @Test
    void request_nonBearerSchemeAndEmptyBearerAreUnauthenticated() {
        Member user = saveMember(Role.USER);
        String token = tokenFor(user);
        assertUnauthenticated(() -> withAuth(PostServiceGrpc.newBlockingStub(channel()), "Basic " + token).create(VALID));
        assertUnauthenticated(() -> withAuth(PostServiceGrpc.newBlockingStub(channel()), token).create(VALID));
        assertUnauthenticated(() -> withAuth(PostServiceGrpc.newBlockingStub(channel()), "Bearer ").create(VALID));
    }

    @Test
    void request_twoAuthorizationHeadersAreUnauthenticated() {
        String token = tokenFor(saveMember(Role.USER));
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "Bearer " + token);
        metadata.put(key, "Bearer " + token);
        var stub = PostServiceGrpc.newBlockingStub(channel())
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
        assertUnauthenticated(() -> stub.create(VALID));
    }

    @Test
    void publicMethod_withInvalidTokenIsUnauthenticated() {
        Member user = saveMember(Role.USER);
        LoginRequest login = LoginRequest.newBuilder().setEmail(user.getEmail()).setPassword(PASSWORD).build();
        // control: the same valid request succeeds without a token, so only the bad token can fail it
        assertThat(auth().login(login).getAccessToken()).isNotBlank();
        String expired = TokenFactory.token("1", "USER", "access", Duration.ofHours(1));
        assertUnauthenticated(() -> withAuth(AuthServiceGrpc.newBlockingStub(channel()), "Bearer " + expired)
                .login(login));
    }

    @Test
    void validToken_reachesTheHandlerAsTheCaller() {
        Member user = saveMember(Role.USER);
        var created = posts(tokenFor(user)).create(VALID);
        assertThat(created.getAuthor().getId()).isEqualTo(user.getId());
    }

    @Test
    void onlyThreePublicMethodsAreAccessibleWithoutToken() {
        Set<String> accessible = new TreeSet<>();
        Set<String> all = new TreeSet<>();
        int methods = 0;
        for (io.grpc.BindableService service : services) {
            for (ServerMethodDefinition<?, ?> def : service.bindService().getMethods()) {
                methods++;
                String name = def.getMethodDescriptor().getFullMethodName();
                all.add(name);
                if (!callWithoutToken(name).getCode().equals(Status.Code.UNAUTHENTICATED)) {
                    accessible.add(name);
                }
            }
        }
        assertThat(methods).isGreaterThanOrEqualTo(11);
        // Boot's health service is registered too, so the enumeration cannot pass vacuously
        assertThat(all).contains(HEALTH_CHECK);
        assertThat(accessible).containsExactlyInAnyOrderElementsOf(PUBLIC);
    }

    @Test
    void healthCheck_withoutTokenIsUnauthenticated() {
        assertThat(callWithoutToken(HEALTH_CHECK).getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    /** Sends an empty request without any credentials and returns the resulting status. */
    private Status callWithoutToken(String fullMethodName) {
        MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethodName)
                .setRequestMarshaller(RAW).setResponseMarshaller(RAW).build();
        return statusOf(() -> ClientCalls.blockingUnaryCall(channel(), descriptor, CallOptions.DEFAULT, new byte[0]));
    }

    private static final MethodDescriptor.Marshaller<byte[]> RAW = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    };
}
