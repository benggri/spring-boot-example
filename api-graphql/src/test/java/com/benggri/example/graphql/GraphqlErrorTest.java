package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.core.member.entity.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphqlErrorTest extends GraphqlTestBase {

    private static final String REGISTER = "mutation($i:RegisterInput!){ register(input:$i){ id } }";

    private Gql register(String email, String password, String name) throws Exception {
        return exec(null, REGISTER, Map.of("i", Map.of("email", email, "password", password, "name", name)));
    }

    @Test
    void register_duplicateEmailHasConflictCode() throws Exception {
        String email = uniqueEmail();
        assertThat(register(email, PASSWORD, "N").code()).isNull();

        Gql res = register(email, PASSWORD, "N");

        assertThat(res.code()).isEqualTo("CONFLICT");
    }

    @Test
    void error_responsesNeverContainExceptionMessage() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "N");

        Gql duplicate = register(email, PASSWORD, "N");
        Gql missing = exec(tokenFor(saveMember(Role.USER)), "query{ post(id:\"424242\"){ id } }");

        // core exception texts are "Email already registered: <email>" and "Post not found: <id>"
        assertThat(duplicate.<String>read("$.errors[0].message")).isEqualTo("Conflict");
        assertThat(duplicate.body()).doesNotContain(email).doesNotContain("already registered");
        assertThat(missing.<String>read("$.errors[0].message")).isEqualTo("Not found");
        assertThat(missing.body()).doesNotContain("424242").doesNotContain("StackTrace").doesNotContain("at com.");
    }

    @Test
    void register_multiBytePasswordOver72BytesIsBadRequest() throws Exception {
        String password = "가".repeat(30); // 30 chars passes @Size(max=72) but is 90 UTF-8 bytes

        Gql res = register(uniqueEmail(), password, "N");

        assertThat(res.code()).isEqualTo("BAD_REQUEST");
        assertThat(res.body()).doesNotContain(password);
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    void register_invalidInputsAreBadRequestWithoutEchoingValues() throws Exception {
        String shortPassword = "short1";
        Gql weakPassword = register(uniqueEmail(), shortPassword, "N");
        Gql badEmail = register("not-an-email-address", PASSWORD, "N");
        Gql longName = register(uniqueEmail(), PASSWORD, "n".repeat(51));

        assertThat(weakPassword.code()).isEqualTo("BAD_REQUEST");
        assertThat(weakPassword.body()).doesNotContain(shortPassword);
        assertThat(badEmail.code()).isEqualTo("BAD_REQUEST");
        assertThat(badEmail.body()).doesNotContain("not-an-email-address");
        assertThat(longName.code()).isEqualTo("BAD_REQUEST");
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    void login_blankFieldsAreBadRequest() throws Exception {
        Gql res = exec(null, "mutation($i:LoginInput!){ login(input:$i){ accessToken } }",
                Map.of("i", Map.of("email", "", "password", "")));

        assertThat(res.code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void query_deeperThanLimitIsRejected() throws Exception {
        // the domain schema is acyclic and shallow, so use introspection's recursive __Type.ofType chain
        String deep = "{ __schema { types { " + "ofType { ".repeat(12) + "name" + " }".repeat(12) + " } } }";
        String shallow = "{ __schema { types { name } } }";
        String token = tokenFor(saveMember(Role.USER));

        Gql rejected = exec(token, deep);
        Gql accepted = exec(token, shallow);

        assertThat(rejected.<String>read("$.errors[0].message")).containsIgnoringCase("depth");
        assertThat(rejected.body()).doesNotContain("\"data\":{\"__schema");
        assertThat(accepted.<Object>read("$.data.__schema")).isNotNull();
    }
}
