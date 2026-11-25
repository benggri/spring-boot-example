package com.benggri.example.core.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.entity.Post;
import com.benggri.example.core.post.repository.PostRepository;
import com.benggri.example.core.security.AuthUser;
import com.benggri.example.core.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MemberServiceIntegrationTest extends PostgresTestBase {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        // committed rows would leak into other tests sharing the singleton container
        jdbc.update("DELETE FROM member_post_stats");
        postRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void delete_memberWithPosts_removesPostsAndMember() {
        Member owner = memberRepository.save(Member.create("del-owner@x.com", "h", "O", Role.USER));
        Member other = memberRepository.save(Member.create("del-other@x.com", "h", "X", Role.USER));
        Post p1 = postRepository.save(Post.create(owner, "t1", "c"));
        Post p2 = postRepository.save(Post.create(owner, "t2", "c"));
        Post kept = postRepository.save(Post.create(other, "t3", "c"));

        memberService.delete(new AuthUser(owner.getId(), Role.USER), owner.getId());

        assertThat(memberRepository.existsById(owner.getId())).isFalse();
        assertThat(postRepository.existsById(p1.getId())).isFalse();
        assertThat(postRepository.existsById(p2.getId())).isFalse();
        assertThat(postRepository.existsById(kept.getId())).isTrue();
    }

    @Test
    void delete_memberWithStats_succeeds() {
        Member owner = memberRepository.save(Member.create("del-stats@x.com", "h", "S", Role.USER));
        Member other = memberRepository.save(Member.create("del-stats-o@x.com", "h", "S", Role.USER));
        insertStat(owner.getId());
        insertStat(other.getId());

        memberService.delete(new AuthUser(owner.getId(), Role.USER), owner.getId());

        assertThat(memberRepository.existsById(owner.getId())).isFalse();
        assertThat(statCount(owner.getId())).isZero();
        assertThat(statCount(other.getId())).isEqualTo(1);
    }

    private void insertStat(Long memberId) {
        jdbc.update("INSERT INTO member_post_stats (member_id, stat_date, post_count, created_at)"
                + " VALUES (?, DATE '2026-11-24', 3, now())", memberId);
    }

    private int statCount(Long memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM member_post_stats WHERE member_id = ?",
                Integer.class, memberId);
    }
}
