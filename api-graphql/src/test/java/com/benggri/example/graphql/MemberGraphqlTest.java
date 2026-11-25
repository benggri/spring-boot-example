package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Member;
import com.benggri.example.core.member.entity.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemberGraphqlTest extends GraphqlTestBase {

    private static final String QUERY = "query($id:ID!){ member(id:$id){ id email name role } }";

    @Test
    void member_bySelfReturnsEmail() throws Exception {
        Member me = saveMember(Role.USER);

        Gql res = exec(tokenFor(me), QUERY, Map.of("id", String.valueOf(me.getId())));

        assertThat(res.code()).isNull();
        assertThat(res.<String>read("$.data.member.email")).isEqualTo(me.getEmail());
        assertThat(res.<String>read("$.data.member.role")).isEqualTo("USER");
    }

    @Test
    void member_byOtherUserHasForbiddenCodeAndNoEmailInBody() throws Exception {
        Member me = saveMember(Role.USER);
        Member other = saveMember(Role.USER);

        Gql res = exec(tokenFor(me), QUERY, Map.of("id", String.valueOf(other.getId())));

        assertThat(res.code()).isEqualTo("FORBIDDEN");
        assertThat(res.body()).doesNotContain(other.getEmail());
        assertThat(res.<Object>read("$.data.member")).isNull();
    }

    @Test
    void member_byAdminReturnsEmail() throws Exception {
        Member user = saveMember(Role.USER);
        String adminEmail = uniqueEmail();
        saveMember(adminEmail, Role.ADMIN);
        String adminToken = login(adminEmail)[0];

        Gql res = exec(adminToken, QUERY, Map.of("id", String.valueOf(user.getId())));

        assertThat(res.code()).isNull();
        assertThat(res.<String>read("$.data.member.email")).isEqualTo(user.getEmail());
    }

    @Test
    void member_withoutTokenIsUnauthenticated() throws Exception {
        Gql res = exec(null, QUERY, Map.of("id", "1"));

        assertThat(res.code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void member_missingIdAsAdminIsNotFound() throws Exception {
        Member admin = saveMember(Role.ADMIN);

        assertThat(exec(tokenFor(admin), QUERY, Map.of("id", "999999")).code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void schema_hasNoMemberListAndNoPasswordHashField() throws Exception {
        Member me = saveMember(Role.USER);
        String token = tokenFor(me);

        assertThat(exec(token, "query{ members{ id } }").<Object>read("$.errors")).isNotNull();
        assertThat(exec(token, "query($id:ID!){ member(id:$id){ passwordHash } }",
                Map.of("id", String.valueOf(me.getId()))).<Object>read("$.errors")).isNotNull();
    }
}
