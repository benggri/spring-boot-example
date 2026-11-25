package com.benggri.example.rest.controller;

import com.benggri.example.core.post.dto.PostResult;
import com.benggri.example.core.post.dto.PostSearchCondition;
import com.benggri.example.core.post.service.PostService;
import com.benggri.example.core.security.CurrentUser;
import com.benggri.example.rest.dto.PageResponse;
import com.benggri.example.rest.dto.PostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/posts")
@Validated
@Tag(name = "Posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    @Operation(summary = "Search posts (size above 100 is clamped to 100)")
    public PageResponse<PostResult> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return PageResponse.from(postService.search(
                new PostSearchCondition(keyword, memberId), PageRequest.of(page, size)));
    }

    @PostMapping
    @Operation(summary = "Create a post as the caller")
    public ResponseEntity<PostResult> create(@Valid @RequestBody PostRequest request) {
        PostResult created = postService.create(CurrentUser.get(), request.title(), request.content());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a post")
    public PostResult get(@PathVariable Long id) {
        return postService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a post (author or admin)")
    public PostResult update(@PathVariable Long id, @Valid @RequestBody PostRequest request) {
        return postService.update(CurrentUser.get(), id, request.title(), request.content());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a post (author or admin)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(CurrentUser.get(), id);
        return ResponseEntity.noContent().build();
    }
}
