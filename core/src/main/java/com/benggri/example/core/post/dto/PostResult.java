package com.benggri.example.core.post.dto;

import com.benggri.example.core.post.entity.Post;
import java.time.Instant;

public record PostResult(Long id, Long memberId, String title, String content, Instant createdAt) {

    public static PostResult from(Post post) {
        return new PostResult(post.getId(), post.getMember().getId(), post.getTitle(),
                post.getContent(), post.getCreatedAt());
    }
}
