package com.benggri.example.graphql.controller;

import com.benggri.example.core.common.InvalidRequestException;
import com.benggri.example.core.member.dto.AuthorResult;
import com.benggri.example.core.member.service.MemberService;
import com.benggri.example.core.post.dto.PostResult;
import com.benggri.example.core.post.dto.PostSearchCondition;
import com.benggri.example.core.post.service.PostService;
import com.benggri.example.core.security.CurrentUser;
import com.benggri.example.graphql.dto.PostInput;
import com.benggri.example.graphql.dto.PostPage;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
public class PostGraphqlController {

    private final PostService postService;
    private final MemberService memberService;

    public PostGraphqlController(PostService postService, MemberService memberService) {
        this.postService = postService;
        this.memberService = memberService;
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public PostResult post(@Argument Long id) {
        return postService.get(id);
    }

    /** {@code size} above 100 is clamped by the service; negative page or size below 1 is rejected. */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public PostPage posts(@Argument String keyword, @Argument Long memberId,
            @Argument int page, @Argument int size) {
        if (page < 0 || size < 1) {
            throw new InvalidRequestException("page must be >= 0 and size >= 1");
        }
        return PostPage.from(postService.search(new PostSearchCondition(keyword, memberId),
                PageRequest.of(page, size)));
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PostResult createPost(@Argument @Valid PostInput input) {
        return postService.create(CurrentUser.get(), input.title(), input.content());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PostResult updatePost(@Argument Long id, @Argument @Valid PostInput input) {
        return postService.update(CurrentUser.get(), id, input.title(), input.content());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean deletePost(@Argument Long id) {
        postService.delete(CurrentUser.get(), id);
        return true;
    }

    /**
     * Resolves {@code Post.author} for a whole list of posts with one member query. Only public
     * author data (id, name) is loaded: the schema's Author type has no email or role.
     */
    @BatchMapping(typeName = "Post", field = "author")
    public Map<PostResult, AuthorResult> author(List<PostResult> posts) {
        Set<Long> ids = posts.stream().map(PostResult::memberId).collect(Collectors.toSet());
        Map<Long, AuthorResult> authors = memberService.findAuthors(ids);
        Map<PostResult, AuthorResult> byPost = new HashMap<>();
        posts.forEach(p -> byPost.put(p, authors.get(p.memberId())));
        return byPost;
    }
}
