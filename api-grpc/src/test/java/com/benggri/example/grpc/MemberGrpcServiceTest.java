package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.grpc.proto.CreatePostRequest;
import com.benggri.example.grpc.proto.DeleteMemberRequest;
import com.benggri.example.grpc.proto.GetMemberRequest;
import com.benggri.example.grpc.proto.GetPostRequest;
import com.benggri.example.grpc.proto.LoginRequest;
import com.benggri.example.grpc.proto.MemberResponse;
import com.benggri.example.grpc.proto.PostResponse;
import com.benggri.example.grpc.proto.RefreshRequest;
import com.benggri.example.grpc.proto.RegisterRequest;
import com.benggri.example.grpc.proto.TokenResponse;
import com.benggri.example.grpc.proto.UpdateMemberRequest;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

class MemberGrpcServiceTest extends GrpcTestBase {

    private MemberResponse register(String email) {
        return members(null).register(RegisterRequest.newBuilder()
                .setEmail(email).setPassword(PASSWORD).setName("Tester").build());
    }

    private static GetMemberRequest idOf(Member member) {
        return GetMemberRequest.newBuilder().setId(member.getId()).build();
    }

    @Test
    void registerLoginCreateGetDelete() {
        String email = uniqueEmail();
        MemberResponse registered = register(email);
        assertThat(registered.getEmail()).isEqualTo(email);
        assertThat(registered.getRole()).isEqualTo("USER");

        String token = login(email).getAccessToken();
        PostResponse post = posts(token).create(CreatePostRequest.newBuilder()
                .setTitle("flow").setContent("body").build());
        assertThat(posts(token).get(GetPostRequest.newBuilder().setId(post.getId()).build()).getTitle())
                .isEqualTo("flow");
        assertThat(members(token).get(GetMemberRequest.newBuilder().setId(registered.getId()).build()).getEmail())
                .isEqualTo(email);

        members(token).delete(DeleteMemberRequest.newBuilder().setId(registered.getId()).build());
        assertThat(memberRepository.findById(registered.getId())).isEmpty();
        assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    @Test
    void register_duplicateIsAlreadyExists() {
        String email = uniqueEmail();
        register(email);
        assertFails(() -> register(email), Status.Code.ALREADY_EXISTS, "Already exists");
    }

    @Test
    void register_invalidEmailIsInvalidArgument() {
        for (String bad : new String[] {"", "no-at-sign", "a@b", "a b@example.com", "a@@example.com"}) {
            assertFails(() -> register(bad), Status.Code.INVALID_ARGUMENT, "email: invalid");
        }
        assertFails(() -> register("a".repeat(250) + "@example.com"), Status.Code.INVALID_ARGUMENT, "email: invalid");
    }

    @Test
    void register_passwordLengthAndNameLimits() {
        RegisterRequest.Builder valid = RegisterRequest.newBuilder()
                .setEmail(uniqueEmail()).setPassword(PASSWORD).setName("n");
        assertFails(() -> members(null).register(valid.setPassword("short").build()),
                Status.Code.INVALID_ARGUMENT, "password: invalid");
        assertFails(() -> members(null).register(valid.setPassword("p".repeat(73)).build()),
                Status.Code.INVALID_ARGUMENT, "password: invalid");
        assertFails(() -> members(null).register(valid.setPassword(PASSWORD).setName(" ").build()),
                Status.Code.INVALID_ARGUMENT, "name: invalid");
        assertFails(() -> members(null).register(valid.setName("n".repeat(51)).build()),
                Status.Code.INVALID_ARGUMENT, "name: invalid");
    }

    @Test
    void register_multiBytePasswordOver72BytesIsInvalidArgumentAndNotEchoed() {
        String password = "비밀".repeat(15) + "abc"; // 33 characters but 93 UTF-8 bytes
        Status status = statusOf(() -> members(null).register(RegisterRequest.newBuilder()
                .setEmail(uniqueEmail()).setPassword(password).setName("n").build()));
        assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(status.getDescription()).doesNotContain(password).doesNotContain("비밀");
    }

    @Test
    void login_wrongPasswordAndUnknownEmailHaveSameStatus() {
        Member user = saveMember(Role.USER);
        Status wrongPassword = statusOf(() -> auth().login(LoginRequest.newBuilder()
                .setEmail(user.getEmail()).setPassword("wrong-" + PASSWORD).build()));
        Status unknownEmail = statusOf(() -> auth().login(LoginRequest.newBuilder()
                .setEmail(uniqueEmail()).setPassword(PASSWORD).build()));
        assertThat(wrongPassword.getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(unknownEmail.getCode()).isEqualTo(wrongPassword.getCode());
        assertThat(unknownEmail.getDescription()).isEqualTo(wrongPassword.getDescription());
    }

    @Test
    void refresh_returnsNewTokens() {
        Member user = saveMember(Role.USER);
        TokenResponse tokens = login(user.getEmail());
        TokenResponse refreshed = auth().refresh(RefreshRequest.newBuilder()
                .setRefreshToken(tokens.getRefreshToken()).build());
        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getRefreshToken()).isNotBlank();
        // the new access token really works
        assertThat(members(refreshed.getAccessToken()).get(idOf(user)).getId()).isEqualTo(user.getId());
    }

    @Test
    void refresh_withAccessTokenOrGarbageIsUnauthenticated() {
        Member user = saveMember(Role.USER);
        String access = login(user.getEmail()).getAccessToken();
        assertFails(() -> auth().refresh(RefreshRequest.newBuilder().setRefreshToken(access).build()),
                Status.Code.UNAUTHENTICATED, "Unauthenticated");
        assertFails(() -> auth().refresh(RefreshRequest.newBuilder().setRefreshToken("garbage").build()),
                Status.Code.UNAUTHENTICATED, "Unauthenticated");
        assertFails(() -> auth().refresh(RefreshRequest.getDefaultInstance()),
                Status.Code.INVALID_ARGUMENT, "refresh_token: invalid");
    }

    @Test
    void getMember_bySelfReturnsEmail() {
        Member user = saveMember(Role.USER);
        assertThat(members(login(user.getEmail()).getAccessToken()).get(idOf(user)).getEmail())
                .isEqualTo(user.getEmail());
    }

    @Test
    void getMember_byAdminReturnsEmail() {
        Member user = saveMember(Role.USER);
        Member admin = saveMember(Role.ADMIN);
        assertThat(members(login(admin.getEmail()).getAccessToken()).get(idOf(user)).getEmail())
                .isEqualTo(user.getEmail());
    }

    @Test
    void getMember_byOtherUserIsPermissionDeniedAndNoEmailInStatus() {
        Member victim = saveMember(Role.USER);
        String other = tokenFor(saveMember(Role.USER));
        Status status = statusOf(() -> members(other).get(idOf(victim)));
        assertThat(status.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(status.getDescription()).isEqualTo("Permission denied").doesNotContain(victim.getEmail());
    }

    @Test
    void memberRpcs_idZeroIsInvalidArgumentAndUpdateWorksForSelf() {
        Member user = saveMember(Role.USER);
        String token = tokenFor(user);
        assertFails(() -> members(token).get(GetMemberRequest.getDefaultInstance()),
                Status.Code.INVALID_ARGUMENT, "id: invalid");
        assertFails(() -> members(token).delete(DeleteMemberRequest.getDefaultInstance()),
                Status.Code.INVALID_ARGUMENT, "id: invalid");
        assertFails(() -> members(token).update(UpdateMemberRequest.newBuilder().setName("x").build()),
                Status.Code.INVALID_ARGUMENT, "id: invalid");
        MemberResponse updated = members(token).update(UpdateMemberRequest.newBuilder()
                .setId(user.getId()).setName("Renamed").build());
        assertThat(updated.getName()).isEqualTo("Renamed");
    }
}
