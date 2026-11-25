package com.benggri.example.core.post.repository;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.entity.Post;
import com.benggri.example.core.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryTest extends PostgresTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void save_persistsAuthorRelation() {
        Member author = memberRepository.save(Member.create("b@example.com", "hash", "Bob", Role.USER));
        Post saved = postRepository.save(Post.create(author, "title", "content"));
        em.flush();
        em.clear();

        Post found = postRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getMember().getId()).isEqualTo(author.getId());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
