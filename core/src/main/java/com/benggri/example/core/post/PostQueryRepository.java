package com.benggri.example.core.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostQueryRepository {

    Page<Post> search(PostSearchCondition cond, Pageable pageable);
}
