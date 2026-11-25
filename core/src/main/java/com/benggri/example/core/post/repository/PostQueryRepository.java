package com.benggri.example.core.post.repository;

import com.benggri.example.core.post.dto.PostSearchCondition;
import com.benggri.example.core.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostQueryRepository {

    Page<Post> search(PostSearchCondition cond, Pageable pageable);
}
