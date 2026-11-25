package com.benggri.example.grpc.mapper;

import com.benggri.example.core.member.dto.AuthorResult;
import com.benggri.example.core.post.dto.PostResult;
import com.benggri.example.core.post.dto.PostSearchCondition;
import com.benggri.example.grpc.proto.Author;
import com.benggri.example.grpc.proto.PostResponse;
import com.benggri.example.grpc.proto.SearchPostsRequest;
import com.benggri.example.grpc.proto.SearchPostsResponse;
import com.benggri.example.grpc.validation.RequestValidator;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Post mapping, including the proto3 zero-default rules for search (see post.proto). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PostMapper {

    /** Unset member_id means no filter, blank keyword means no keyword. Assumes the request is validated. */
    public static PostSearchCondition toCondition(SearchPostsRequest request) {
        String keyword = request.getKeyword().isBlank() ? null : request.getKeyword();
        Long memberId = request.hasMemberId() ? request.getMemberId() : null;
        return new PostSearchCondition(keyword, memberId);
    }

    /** Size 0 (unset) means the default; larger than the maximum is clamped by the service. */
    public static Pageable toPageable(SearchPostsRequest request) {
        int size = request.getSize() == 0 ? RequestValidator.DEFAULT_PAGE_SIZE : request.getSize();
        return PageRequest.of(request.getPage(), size);
    }

    public static PostResponse toResponse(PostResult post, Map<Long, AuthorResult> authors) {
        return PostResponse.newBuilder()
                .setId(post.id())
                .setTitle(post.title())
                .setContent(post.content())
                .setCreatedAt(post.createdAt().toString())
                .setAuthor(toAuthor(post.memberId(), authors))
                .build();
    }

    public static SearchPostsResponse toResponse(Page<PostResult> page, Map<Long, AuthorResult> authors) {
        List<PostResponse> items = page.getContent().stream().map(post -> toResponse(post, authors)).toList();
        return SearchPostsResponse.newBuilder()
                .addAllItems(items)
                .setPage(page.getNumber())
                .setSize(page.getSize())
                .setTotal(page.getTotalElements())
                .build();
    }

    private static Author toAuthor(Long memberId, Map<Long, AuthorResult> authors) {
        AuthorResult author = authors.get(memberId);
        return Author.newBuilder()
                .setId(memberId)
                .setName(author == null ? "" : author.name())
                .build();
    }
}
