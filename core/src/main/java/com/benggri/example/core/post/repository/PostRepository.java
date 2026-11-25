package com.benggri.example.core.post.repository;

import com.benggri.example.core.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long>, PostQueryRepository {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Post p where p.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
