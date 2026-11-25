package com.benggri.example.graphql.dto;

import com.benggri.example.core.post.dto.PostResult;
import java.util.List;
import org.springframework.data.domain.Page;

/** Mirrors the schema's {@code PostPage}; GraphQL {@code Int} is 32-bit, so the total is saturated. */
public record PostPage(List<PostResult> items, int page, int size, int total) {

    public static PostPage from(Page<PostResult> page) {
        int total = (int) Math.min(page.getTotalElements(), Integer.MAX_VALUE);
        return new PostPage(page.getContent(), page.getNumber(), page.getSize(), total);
    }
}
