package com.benggri.example.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import com.benggri.example.grpc.proto.SearchPostsRequest;
import com.benggri.example.grpc.proto.SearchPostsResponse;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class NPlusOneTest extends GrpcTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void statistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    /**
     * 10 posts by 5 authors. Expected statements: the page query, the count query and ONE batch author
     * lookup (plus at most one more for the author fetch inside the search). The bound of 4 stays constant
     * as posts or authors grow; an N+1 would be 10+.
     */
    @Test
    void search_authorsLoadedWithoutNPlusOne() {
        List<Member> authors = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            authors.add(saveMember(Role.USER));
        }
        for (int i = 0; i < 10; i++) {
            savePost(authors.get(i % 5), "post " + i);
        }
        String token = tokenFor(authors.get(0));
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        SearchPostsResponse res = posts(token).search(SearchPostsRequest.newBuilder().setSize(10).build());
        assertThat(res.getItemsCount()).isEqualTo(10);
        assertThat(res.getItemsList()).allSatisfy(p -> assertThat(p.getAuthor().getName()).isNotBlank());
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }
}
