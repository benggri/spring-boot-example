package com.benggri.example.grpc.service;

import com.benggri.example.core.member.dto.AuthorResult;
import com.benggri.example.core.member.service.MemberService;
import com.benggri.example.core.post.dto.PostResult;
import com.benggri.example.core.post.service.PostService;
import com.benggri.example.grpc.interceptor.AuthContext;
import com.benggri.example.grpc.mapper.PostMapper;
import com.benggri.example.grpc.proto.CreatePostRequest;
import com.benggri.example.grpc.proto.DeletePostRequest;
import com.benggri.example.grpc.proto.DeletePostResponse;
import com.benggri.example.grpc.proto.GetPostRequest;
import com.benggri.example.grpc.proto.PostResponse;
import com.benggri.example.grpc.proto.PostServiceGrpc;
import com.benggri.example.grpc.proto.SearchPostsRequest;
import com.benggri.example.grpc.proto.SearchPostsResponse;
import com.benggri.example.grpc.proto.UpdatePostRequest;
import com.benggri.example.grpc.validation.RequestValidator;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.grpc.server.service.GrpcService;

/** Authors are public data (id and name); they are resolved with one batch lookup per request. */
@GrpcService
@RequiredArgsConstructor
public class PostGrpcService extends PostServiceGrpc.PostServiceImplBase {

    private final PostService postService;
    private final MemberService memberService;

    @Override
    public void create(CreatePostRequest request, StreamObserver<PostResponse> observer) {
        RequestValidator.post(request.getTitle(), request.getContent());
        PostResult post = postService.create(AuthContext.current(), request.getTitle(), request.getContent());
        respond(observer, post);
    }

    @Override
    public void get(GetPostRequest request, StreamObserver<PostResponse> observer) {
        RequestValidator.id(request.getId());
        respond(observer, postService.get(request.getId()));
    }

    @Override
    public void search(SearchPostsRequest request, StreamObserver<SearchPostsResponse> observer) {
        RequestValidator.search(request);
        Page<PostResult> page = postService.search(PostMapper.toCondition(request), PostMapper.toPageable(request));
        Set<Long> memberIds = page.getContent().stream().map(PostResult::memberId).collect(Collectors.toSet());
        observer.onNext(PostMapper.toResponse(page, memberService.findAuthors(memberIds)));
        observer.onCompleted();
    }

    @Override
    public void update(UpdatePostRequest request, StreamObserver<PostResponse> observer) {
        RequestValidator.id(request.getId());
        RequestValidator.post(request.getTitle(), request.getContent());
        respond(observer, postService.update(AuthContext.current(), request.getId(), request.getTitle(),
                request.getContent()));
    }

    @Override
    public void delete(DeletePostRequest request, StreamObserver<DeletePostResponse> observer) {
        RequestValidator.id(request.getId());
        postService.delete(AuthContext.current(), request.getId());
        observer.onNext(DeletePostResponse.getDefaultInstance());
        observer.onCompleted();
    }

    private void respond(StreamObserver<PostResponse> observer, PostResult post) {
        Map<Long, AuthorResult> authors = memberService.findAuthors(Set.of(post.memberId()));
        observer.onNext(PostMapper.toResponse(post, authors));
        observer.onCompleted();
    }
}
