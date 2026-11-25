package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Role;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Aliasing one operation N times would otherwise multiply BCrypt/DB work inside a single HTTP request. */
class GraphqlRootFieldLimitTest extends GraphqlTestBase {

    private static String aliasedPosts(int count) {
        return "{ " + IntStream.range(0, count).mapToObj(i -> "a" + i + ": posts { total }")
                .collect(Collectors.joining(" ")) + " }";
    }

    private static String aliasedLogins(int count) {
        return "mutation { " + IntStream.range(0, count)
                .mapToObj(i -> "a" + i + ": login(input:{email:\"x@example.com\",password:\"whatever-1\"}){ accessToken }")
                .collect(Collectors.joining(" ")) + " }";
    }

    @Test
    void query_withMoreRootFieldsThanLimitIsRejected() throws Exception {
        String token = tokenFor(saveMember(Role.USER));

        Gql res = exec(token, aliasedPosts(11));

        assertThat(res.code()).isEqualTo("BAD_REQUEST");
        assertThat(res.body()).doesNotContain("\"total\"").doesNotContain("a0");
    }

    @Test
    void mutation_withMoreAliasedLoginsThanLimitIsRejectedBeforeAnyLogin() throws Exception {
        Gql res = exec(null, aliasedLogins(11));

        assertThat(res.code()).isEqualTo("BAD_REQUEST");
        // had the logins run, each would report its own INVALID_CREDENTIALS-style error with a path
        assertThat(res.body()).doesNotContain("\"path\"").doesNotContain("x@example.com");
    }

    @Test
    void query_withFragmentsCannotSmuggleExtraRootFields() throws Exception {
        String token = tokenFor(saveMember(Role.USER));
        String fragments = IntStream.range(0, 11).mapToObj(i -> "b" + i + ": posts { total }")
                .collect(Collectors.joining(" "));

        Gql res = exec(token, "{ ...F } fragment F on Query { " + fragments + " }");

        assertThat(res.code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void query_atTheLimitIsAccepted() throws Exception {
        String token = tokenFor(saveMember(Role.USER));

        Gql res = exec(token, aliasedPosts(10));

        assertThat(res.code()).isNull();
        assertThat(res.<Object>read("$.data.a9.total")).isNotNull();
    }
}
