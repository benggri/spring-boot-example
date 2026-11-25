package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.post.entity.Post;
import com.benggri.example.grpc.proto.CreatePostRequest;
import com.benggri.example.grpc.proto.DeletePostRequest;
import com.benggri.example.grpc.proto.GetPostRequest;
import com.benggri.example.grpc.proto.PostResponse;
import com.benggri.example.grpc.proto.SearchPostsRequest;
import com.benggri.example.grpc.proto.SearchPostsResponse;
import com.benggri.example.grpc.proto.UpdatePostRequest;
import io.grpc.Status;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostGrpcServiceTest extends GrpcTestBase {

    private SearchPostsResponse search(String token, SearchPostsRequest.Builder request) {
        return posts(token).search(request.build());
    }

    @Test
    void createGetUpdate_returnPostWithAuthorNameOnly() {
        Member user = saveMember(Role.USER);
        String token = tokenFor(user);
        PostResponse created = posts(token).create(CreatePostRequest.newBuilder()
                .setTitle("hello").setContent("world").build());
        assertThat(created.getId()).isPositive();
        assertThat(created.getAuthor().getId()).isEqualTo(user.getId());
        assertThat(created.getAuthor().getName()).isEqualTo(user.getName());
        assertThat(Instant.parse(created.getCreatedAt())).isNotNull();
        assertThat(created.toString()).doesNotContain(user.getEmail());

        PostResponse got = posts(token).get(GetPostRequest.newBuilder().setId(created.getId()).build());
        assertThat(got.getTitle()).isEqualTo("hello");
        PostResponse updated = posts(token).update(UpdatePostRequest.newBuilder()
                .setId(created.getId()).setTitle("t2").setContent("c2").build());
        assertThat(updated.getTitle()).isEqualTo("t2");
    }

    @Test
    void updatePost_byOtherUserIsPermissionDenied() {
        Post post = savePost(saveMember(Role.USER), "mine");
        String other = tokenFor(saveMember(Role.USER));
        assertFails(() -> posts(other).update(UpdatePostRequest.newBuilder()
                .setId(post.getId()).setTitle("x").setContent("y").build()),
                Status.Code.PERMISSION_DENIED, "Permission denied");
    }

    @Test
    void deletePost_byAdminSucceedsAndUserPermissionDenied() {
        Post post = savePost(saveMember(Role.USER), "to delete");
        DeletePostRequest request = DeletePostRequest.newBuilder().setId(post.getId()).build();
        String other = tokenFor(saveMember(Role.USER));
        assertFails(() -> posts(other).delete(request), Status.Code.PERMISSION_DENIED, "Permission denied");
        posts(tokenFor(saveMember(Role.ADMIN))).delete(request);
        assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    @Test
    void getPost_unknownIdIsNotFound() {
        String token = tokenFor(saveMember(Role.USER));
        assertFails(() -> posts(token).get(GetPostRequest.newBuilder().setId(Long.MAX_VALUE).build()),
                Status.Code.NOT_FOUND, "Not found");
    }

    @Test
    void getPost_idZeroIsInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        assertFails(() -> posts(token).get(GetPostRequest.getDefaultInstance()),
                Status.Code.INVALID_ARGUMENT, "id: invalid");
    }

    @Test
    void updatePost_idZeroAndNegativeAreInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        UpdatePostRequest.Builder valid = UpdatePostRequest.newBuilder().setTitle("t").setContent("c");
        assertFails(() -> posts(token).update(valid.setId(0).build()), Status.Code.INVALID_ARGUMENT, "id: invalid");
        assertFails(() -> posts(token).update(valid.setId(-5).build()), Status.Code.INVALID_ARGUMENT, "id: invalid");
    }

    @Test
    void deletePost_idZeroIsInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        assertFails(() -> posts(token).delete(DeletePostRequest.getDefaultInstance()),
                Status.Code.INVALID_ARGUMENT, "id: invalid");
    }

    @Test
    void createPost_titleTooLongIsInvalidArgumentWithoutEchoingIt() {
        String token = tokenFor(saveMember(Role.USER));
        String title = "T".repeat(201);
        Status status = statusOf(() -> posts(token).create(CreatePostRequest.newBuilder()
                .setTitle(title).setContent("c").build()));
        assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(status.getDescription()).isEqualTo("title: invalid").doesNotContain(title);
    }

    @Test
    void createPost_blankTitleOrContentAndTooLongContentAreInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        assertFails(() -> posts(token).create(CreatePostRequest.newBuilder().setTitle(" ").setContent("c").build()),
                Status.Code.INVALID_ARGUMENT, "title: invalid");
        assertFails(() -> posts(token).create(CreatePostRequest.newBuilder().setTitle("t").build()),
                Status.Code.INVALID_ARGUMENT, "content: invalid");
        assertFails(() -> posts(token).create(CreatePostRequest.newBuilder()
                .setTitle("t").setContent("c".repeat(10001)).build()), Status.Code.INVALID_ARGUMENT, "content: invalid");
    }

    @Test
    void search_returnsPage() {
        Member author = saveMember(Role.USER);
        for (int i = 0; i < 5; i++) {
            savePost(author, "post " + i);
        }
        SearchPostsResponse res = search(tokenFor(author), SearchPostsRequest.newBuilder().setPage(1).setSize(2));
        assertThat(res.getItemsCount()).isEqualTo(2);
        assertThat(res.getTotal()).isEqualTo(5);
        assertThat(res.getPage()).isEqualTo(1);
        assertThat(res.getSize()).isEqualTo(2);
        assertThat(res.getItems(0).getAuthor().getName()).isEqualTo(author.getName());
    }

    @Test
    void search_sizeOver100Clamped() {
        String token = tokenFor(saveMember(Role.USER));
        assertThat(search(token, SearchPostsRequest.newBuilder().setSize(500)).getSize()).isEqualTo(100);
    }

    @Test
    void search_unsetSizeUsesDefault20AndUnsetMemberIdMeansNoFilter() {
        Member a = saveMember(Role.USER);
        Member b = saveMember(Role.USER);
        for (int i = 0; i < 12; i++) {
            savePost(a, "a" + i);
            savePost(b, "b" + i);
        }
        String token = tokenFor(a);
        SearchPostsResponse all = search(token, SearchPostsRequest.newBuilder());
        assertThat(all.getSize()).isEqualTo(20);
        assertThat(all.getItemsCount()).isEqualTo(20);
        assertThat(all.getTotal()).isEqualTo(24);
        assertThat(all.getPage()).isZero();

        SearchPostsResponse onlyB = search(token, SearchPostsRequest.newBuilder().setMemberId(b.getId()).setKeyword("  "));
        assertThat(onlyB.getTotal()).isEqualTo(12);
        assertThat(onlyB.getItemsList()).allSatisfy(p -> assertThat(p.getAuthor().getId()).isEqualTo(b.getId()));
    }

    @Test
    void search_keywordFilters() {
        Member a = saveMember(Role.USER);
        savePost(a, "needle in title");
        savePost(a, "other");
        SearchPostsResponse res = search(tokenFor(a), SearchPostsRequest.newBuilder().setKeyword("needle"));
        assertThat(res.getTotal()).isEqualTo(1);
    }

    @Test
    void search_negativePageIsInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        assertFails(() -> search(token, SearchPostsRequest.newBuilder().setPage(-1)),
                Status.Code.INVALID_ARGUMENT, "page: invalid");
    }

    @Test
    void search_negativeSizeIsInvalidArgument() {
        String token = tokenFor(saveMember(Role.USER));
        assertFails(() -> search(token, SearchPostsRequest.newBuilder().setSize(-1)),
                Status.Code.INVALID_ARGUMENT, "size: invalid");
    }

    @Test
    void search_withoutTokenIsUnauthenticated() {
        assertFails(() -> search(null, SearchPostsRequest.newBuilder()), Status.Code.UNAUTHENTICATED, "Unauthenticated");
    }
}
