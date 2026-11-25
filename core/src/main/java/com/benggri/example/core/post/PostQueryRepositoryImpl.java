package com.benggri.example.core.post;

import static com.benggri.example.core.post.QPost.post;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

class PostQueryRepositoryImpl implements PostQueryRepository {

    private static final char ESCAPE = '\\';

    private final JPAQueryFactory queryFactory;

    // Builds its own factory so every @DataJpaTest slice works without importing QueryDslConfig.
    PostQueryRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<Post> search(PostSearchCondition cond, Pageable pageable) {
        BooleanExpression[] where = {keywordContains(cond.keyword()), memberIdEq(cond.memberId())};
        List<Post> content = queryFactory.selectFrom(post)
                .where(where)
                .orderBy(post.createdAt.desc(), post.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        return PageableExecutionUtils.getPage(content, pageable, () -> {
            Long total = queryFactory.select(post.count()).from(post).where(where).fetchOne();
            return total == null ? 0L : total;
        });
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.strip()) + "%";
        return post.title.likeIgnoreCase(pattern, ESCAPE).or(post.content.likeIgnoreCase(pattern, ESCAPE));
    }

    private BooleanExpression memberIdEq(Long memberId) {
        return memberId == null ? null : post.member.id.eq(memberId);
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
