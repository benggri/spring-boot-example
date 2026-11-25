package com.benggri.example.core.post.repository;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.member.repository.MemberRepository;
import com.benggri.example.core.post.dto.PostSearchCondition;
import com.benggri.example.core.post.entity.Post;
import com.benggri.example.core.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostQueryRepositoryTest extends PostgresTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TestEntityManager em;

    private Member alice;
    private Member bob;

    @BeforeEach
    void setUp() {
        alice = memberRepository.save(Member.create("alice@example.com", "hash", "Alice", Role.USER));
        bob = memberRepository.save(Member.create("bob@example.com", "hash", "Bob", Role.USER));
    }

    private Post save(Member author, String title, String content) {
        Post post = postRepository.saveAndFlush(Post.create(author, title, content));
        em.clear();
        return post;
    }

    private List<String> titles(Page<Post> page) {
        return page.getContent().stream().map(Post::getTitle).toList();
    }

    @Test
    void search_keywordMatchesTitleOrContent() {
        save(alice, "Spring Boot", "intro");
        save(alice, "other", "about SPRING data");
        save(alice, "unrelated", "nothing");

        Page<Post> result = postRepository.search(new PostSearchCondition("spring", null), PageRequest.of(0, 10));

        assertThat(titles(result)).containsExactlyInAnyOrder("Spring Boot", "other");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_memberIdFiltersAuthor() {
        save(alice, "a1", "x");
        save(bob, "b1", "x");

        Page<Post> result = postRepository.search(new PostSearchCondition(null, bob.getId()), PageRequest.of(0, 10));

        assertThat(titles(result)).containsExactly("b1");
    }

    @Test
    void search_nullConditionReturnsAllPaged() {
        for (int i = 0; i < 5; i++) {
            save(alice, "t" + i, "c");
        }

        Page<Post> result = postRepository.search(new PostSearchCondition(null, null), PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(titles(result)).containsExactly("t4", "t3");
    }

    @Test
    void search_percentInKeywordIsLiteral() {
        save(alice, "100% done", "x");
        save(alice, "plain title", "x");
        save(alice, "a_b", "x");
        save(alice, "axb", "x");

        Page<Post> percent = postRepository.search(new PostSearchCondition("%", null), PageRequest.of(0, 10));
        Page<Post> underscore = postRepository.search(new PostSearchCondition("a_b", null), PageRequest.of(0, 10));

        assertThat(titles(percent)).containsExactly("100% done");
        assertThat(titles(underscore)).containsExactly("a_b");
    }

    @Test
    void search_blankKeywordIgnored() {
        save(alice, "one", "x");
        save(alice, "two", "x");

        Page<Post> result = postRepository.search(new PostSearchCondition("   ", null), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
