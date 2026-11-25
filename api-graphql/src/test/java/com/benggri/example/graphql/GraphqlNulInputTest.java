package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.benggri.example.core.member.entity.Role;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** U+0000 cannot be stored by PostgreSQL: it must be BAD_REQUEST (HTTP 200 + errors), never INTERNAL_ERROR. */
class GraphqlNulInputTest extends GraphqlTestBase {

    private static final String NUL = "a\u0000b";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;

    @BeforeEach
    void attachAppender() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        root.detachAppender(appender);
    }

    private void assertBadRequest(Gql res) {
        assertThat(res.code()).isEqualTo("BAD_REQUEST");
        assertThat(res.body()).doesNotContain("Zero bytes");
        assertThat(appender.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    void login_emailWithNulIsBadRequest() throws Exception {
        assertBadRequest(exec(null, "mutation($i: LoginInput!){ login(input:$i){ accessToken } }",
                Map.of("i", Map.of("email", "a\u0000@example.com", "password", PASSWORD))));
    }

    @Test
    void register_emailWithNulIsBadRequest() throws Exception {
        assertBadRequest(exec(null, "mutation($i:RegisterInput!){ register(input:$i){ id } }",
                Map.of("i", Map.of("email", "a\u0000@example.com", "password", PASSWORD, "name", "n"))));
    }

    @Test
    void register_nameWithNulIsBadRequest() throws Exception {
        assertBadRequest(exec(null, "mutation($i:RegisterInput!){ register(input:$i){ id } }",
                Map.of("i", Map.of("email", uniqueEmail(), "password", PASSWORD, "name", NUL))));
    }

    @Test
    void posts_keywordWithNulIsBadRequest() throws Exception {
        String token = tokenFor(saveMember(Role.USER));
        assertBadRequest(exec(token, "query($k:String){ posts(keyword:$k){ total } }", Map.of("k", NUL)));
    }

    @Test
    void createPost_titleWithNulIsBadRequest() throws Exception {
        String token = tokenFor(saveMember(Role.USER));
        assertBadRequest(exec(token, "mutation($i:PostInput!){ createPost(input:$i){ id } }",
                Map.of("i", Map.of("title", NUL, "content", "c"))));
    }
}
