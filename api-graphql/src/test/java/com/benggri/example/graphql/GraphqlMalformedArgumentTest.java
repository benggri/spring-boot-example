package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Client mistakes are BAD_REQUEST: never INTERNAL_ERROR, and never an ERROR log (log-flooding vector). */
class GraphqlMalformedArgumentTest extends GraphqlTestBase {

    private static final String POSTS = "query($p:Int,$s:Int){ posts(page:$p,size:$s){ total } }";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger root;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(logs);
    }

    private void assertBadRequestWithoutErrorLog(Gql res) {
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.code()).isEqualTo("BAD_REQUEST");
        assertThat(res.body()).doesNotContain("INTERNAL_ERROR");
        assertThat(logs.list).filteredOn(e -> e.getLevel().isGreaterOrEqual(Level.ERROR)).isEmpty();
    }

    private String token() {
        return tokenFor(saveMember(Role.USER));
    }

    @Test
    void post_emptyIdIsBadRequest() throws Exception {
        assertBadRequestWithoutErrorLog(exec(token(), "query{ post(id:\"\"){ id } }"));
    }

    @Test
    void updatePost_emptyIdIsBadRequest() throws Exception {
        assertBadRequestWithoutErrorLog(exec(token(),
                "mutation($i:PostInput!){ updatePost(id:\"\",input:$i){ id } }",
                Map.of("i", Map.of("title", "t", "content", "c"))));
    }

    @Test
    void deletePost_emptyIdIsBadRequest() throws Exception {
        assertBadRequestWithoutErrorLog(exec(token(), "mutation{ deletePost(id:\"\") }"));
    }

    @Test
    void member_emptyIdIsBadRequest() throws Exception {
        assertBadRequestWithoutErrorLog(exec(token(), "query{ member(id:\"\"){ id } }"));
    }

    @Test
    void ids_outOfRangeNumberIsBadRequest() throws Exception {
        assertBadRequestWithoutErrorLog(exec(token(), "query{ post(id:\"99999999999999999999\"){ id } }"));
    }

    @Test
    void posts_explicitNullPageIsBadRequest() throws Exception {
        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("p", null);
        vars.put("s", 10);
        assertBadRequestWithoutErrorLog(exec(token(), POSTS, vars));
        assertBadRequestWithoutErrorLog(exec(null, POSTS, vars));
        assertBadRequestWithoutErrorLog(exec(null, "query{ posts(page:null){ total } }"));
    }

    @Test
    void posts_explicitNullSizeIsBadRequest() throws Exception {
        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("p", 0);
        vars.put("s", null);
        assertBadRequestWithoutErrorLog(exec(token(), POSTS, vars));
        assertBadRequestWithoutErrorLog(exec(null, POSTS, vars));
        assertBadRequestWithoutErrorLog(exec(null, "query{ posts(size:null){ total } }"));
    }

    @Test
    void posts_emptyMemberIdMeansNoFilter() throws Exception {
        Member a = saveMember(Role.USER);
        savePost(a, "one");
        savePost(saveMember(Role.USER), "two");

        Gql res = exec(tokenFor(a), "query{ posts(memberId:\"\"){ total } }");

        assertThat(res.code()).isNull();
        assertThat(res.<Integer>read("$.data.posts.total")).isEqualTo(2);
        assertThat(List.of(res.status())).containsExactly(200);
    }
}
