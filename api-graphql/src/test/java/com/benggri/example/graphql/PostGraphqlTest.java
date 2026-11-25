package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.post.entity.Post;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class PostGraphqlTest extends GraphqlTestBase {

    private static final String LIST =
            "query($p:Int,$s:Int){ posts(page:$p,size:$s){ items{ id title author{ id name } } page size total } }";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void statistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Test
    void posts_returnsPagedItems() throws Exception {
        Member author = saveMember(Role.USER);
        for (int i = 0; i < 5; i++) {
            savePost(author, "title-" + i);
        }

        Gql res = exec(tokenFor(author), LIST, Map.of("p", 1, "s", 2));

        assertThat(res.code()).isNull();
        assertThat(res.<List<?>>read("$.data.posts.items")).hasSize(2);
        assertThat(res.<Integer>read("$.data.posts.page")).isEqualTo(1);
        assertThat(res.<Integer>read("$.data.posts.size")).isEqualTo(2);
        assertThat(res.<Integer>read("$.data.posts.total")).isEqualTo(5);
        assertThat(res.<String>read("$.data.posts.items[0].author.id")).isEqualTo(String.valueOf(author.getId()));
    }

    @Test
    void posts_sizeOver100Clamped() throws Exception {
        Member author = saveMember(Role.USER);
        savePost(author, "only");

        Gql res = exec(tokenFor(author), LIST, Map.of("p", 0, "s", 1000));

        assertThat(res.code()).isNull();
        assertThat(res.<Integer>read("$.data.posts.size")).isEqualTo(100);
    }

    @Test
    void posts_negativePageOrNonPositiveSizeIsBadRequest() throws Exception {
        String token = tokenFor(saveMember(Role.USER));

        assertThat(exec(token, LIST, Map.of("p", -1, "s", 10)).code()).isEqualTo("BAD_REQUEST");
        assertThat(exec(token, LIST, Map.of("p", 0, "s", 0)).code()).isEqualTo("BAD_REQUEST");
        assertThat(exec(token, LIST, Map.of("p", 0, "s", -5)).code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void posts_filtersByKeywordAndMember() throws Exception {
        Member a = saveMember(Role.USER);
        Member b = saveMember(Role.USER);
        savePost(a, "alpha");
        savePost(b, "beta");

        Gql res = exec(tokenFor(a), "query($m:ID){ posts(memberId:$m){ items{ title } total } }",
                Map.of("m", String.valueOf(b.getId())));

        assertThat(res.<List<String>>read("$.data.posts.items[*].title")).containsExactly("beta");
    }

    @Test
    void posts_loadsAuthorsWithoutNPlusOne() throws Exception {
        List<Member> authors = List.of(saveMember(Role.USER), saveMember(Role.USER), saveMember(Role.USER),
                saveMember(Role.USER), saveMember(Role.USER));
        for (int i = 0; i < 10; i++) {
            savePost(authors.get(i % 5), "p" + i);
        }
        String token = tokenFor(authors.get(0));
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().clear();

        Gql res = exec(token, LIST, Map.of("p", 0, "s", 10));

        assertThat(res.<List<?>>read("$.data.posts.items")).hasSize(10);
        assertThat(res.<List<String>>read("$.data.posts.items[*].author.name")).doesNotContainNull();
        // posts page + count + ONE batched author lookup = 3; allow 4. Unbatched it would be 2 + 10 (or 2 + 5).
        assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void post_authorSelectionOfEmailIsRejectedBySchema() throws Exception {
        Member author = saveMember(Role.USER);
        Post post = savePost(author, "t");

        Gql res = exec(tokenFor(author), "query($id:ID!){ post(id:$id){ author{ email } } }",
                Map.of("id", String.valueOf(post.getId())));

        assertThat(res.status()).isEqualTo(200);
        assertThat(res.<List<?>>read("$.errors")).isNotEmpty();
        assertThat(res.body()).doesNotContain("\"data\":{").doesNotContain(author.getEmail());
        assertThat(exec(tokenFor(author), "query($id:ID!){ post(id:$id){ author{ role } } }",
                Map.of("id", "1")).<List<?>>read("$.errors")).isNotEmpty();
    }

    @Test
    void post_missingIdHasNotFoundCode() throws Exception {
        Gql res = exec(tokenFor(saveMember(Role.USER)), "query{ post(id:\"999999\"){ id } }");

        assertThat(res.code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void post_malformedIdIsBadRequest() throws Exception {
        Gql res = exec(tokenFor(saveMember(Role.USER)), "query{ post(id:\"abc\"){ id } }");

        assertThat(res.code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void createPost_returnsPostWithAuthor() throws Exception {
        Member author = saveMember(Role.USER);

        Gql res = exec(tokenFor(author),
                "mutation($i:PostInput!){ createPost(input:$i){ id title content createdAt author{ id name } } }",
                Map.of("i", Map.of("title", "hello", "content", "world")));

        assertThat(res.code()).isNull();
        assertThat(res.<String>read("$.data.createPost.title")).isEqualTo("hello");
        assertThat(res.<String>read("$.data.createPost.author.id")).isEqualTo(String.valueOf(author.getId()));
        assertThat(res.<String>read("$.data.createPost.createdAt")).isNotBlank();
    }

    @Test
    void createPost_overLongTitleIsBadRequest() throws Exception {
        Gql res = exec(tokenFor(saveMember(Role.USER)),
                "mutation($i:PostInput!){ createPost(input:$i){ id } }",
                Map.of("i", Map.of("title", "t".repeat(201), "content", "c")));

        assertThat(res.code()).isEqualTo("BAD_REQUEST");
        assertThat(postRepository.count()).isZero();
    }

    @Test
    void createPost_withoutTokenIsUnauthenticated() throws Exception {
        Gql res = exec(null, "mutation($i:PostInput!){ createPost(input:$i){ id } }",
                Map.of("i", Map.of("title", "t", "content", "c")));

        assertThat(res.status()).isEqualTo(200);
        assertThat(res.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(postRepository.count()).isZero();
    }

    @Test
    void updatePost_byOtherUserHasForbiddenCode() throws Exception {
        Member owner = saveMember(Role.USER);
        Member other = saveMember(Role.USER);
        Post post = savePost(owner, "mine");

        Gql res = exec(tokenFor(other), "mutation($id:ID!,$i:PostInput!){ updatePost(id:$id,input:$i){ id } }",
                Map.of("id", String.valueOf(post.getId()), "i", Map.of("title", "x", "content", "y")));

        assertThat(res.code()).isEqualTo("FORBIDDEN");
        assertThat(postRepository.findById(post.getId()).orElseThrow().getTitle()).isEqualTo("mine");
    }

    @Test
    void updatePost_byOwnerChangesTitle() throws Exception {
        Member owner = saveMember(Role.USER);
        Post post = savePost(owner, "old");

        Gql res = exec(tokenFor(owner), "mutation($id:ID!,$i:PostInput!){ updatePost(id:$id,input:$i){ title } }",
                Map.of("id", String.valueOf(post.getId()), "i", Map.of("title", "new", "content", "y")));

        assertThat(res.<String>read("$.data.updatePost.title")).isEqualTo("new");
    }

    @Test
    void deletePost_byAdminSucceedsAndUserForbidden() throws Exception {
        Member owner = saveMember(Role.USER);
        Member other = saveMember(Role.USER);
        Member admin = saveMember(Role.ADMIN);
        Post post = savePost(owner, "t");
        String mutation = "mutation($id:ID!){ deletePost(id:$id) }";
        Map<String, Object> vars = Map.of("id", String.valueOf(post.getId()));

        assertThat(exec(tokenFor(other), mutation, vars).code()).isEqualTo("FORBIDDEN");
        assertThat(postRepository.existsById(post.getId())).isTrue();
        Gql res = exec(tokenFor(admin), mutation, vars);

        assertThat(res.<Boolean>read("$.data.deletePost")).isTrue();
        assertThat(postRepository.existsById(post.getId())).isFalse();
    }
}
