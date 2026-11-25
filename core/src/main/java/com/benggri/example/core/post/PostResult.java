package com.benggri.example.core.post;

import java.time.Instant;

public record PostResult(Long id, Long memberId, String title, String content, Instant createdAt) {

    static PostResult from(Post post) {
        return new PostResult(post.getId(), post.getMember().getId(), post.getTitle(),
                post.getContent(), post.getCreatedAt());
    }
}
