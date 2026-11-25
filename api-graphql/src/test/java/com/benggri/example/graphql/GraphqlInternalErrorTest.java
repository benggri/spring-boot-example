package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.benggri.example.core.member.entity.Role;
import com.benggri.example.core.post.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** A failing core service must surface as the generic INTERNAL_ERROR, never its own message. */
class GraphqlInternalErrorTest extends GraphqlTestBase {

    private static final String LEAKY = "leaky-internal-detail-" + System.nanoTime();

    @MockitoBean
    private PostService postService;

    @Test
    void unexpectedExceptionBecomesGenericInternalError() throws Exception {
        when(postService.get(anyLong())).thenThrow(new IllegalStateException(LEAKY));

        Gql res = exec(tokenFor(saveMember(Role.USER)), "query{ post(id:\"1\"){ id } }");

        assertThat(res.status()).isEqualTo(200);
        assertThat(res.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(res.<String>read("$.errors[0].message")).isEqualTo("Internal error");
        assertThat(res.body()).doesNotContain(LEAKY).doesNotContain("IllegalStateException");
    }
}
