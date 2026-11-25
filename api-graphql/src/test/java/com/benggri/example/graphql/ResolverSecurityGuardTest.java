package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.graphql.controller.AuthGraphqlController;
import com.benggri.example.graphql.controller.MemberGraphqlController;
import com.benggri.example.graphql.controller.PostGraphqlController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;

/** Deny-by-default guard: a new operation cannot be added without an explicit authorization decision. */
class ResolverSecurityGuardTest {

    private static List<Method> operations(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(QueryMapping.class) || m.isAnnotationPresent(MutationMapping.class))
                .toList();
    }

    @Test
    void memberAndPostOperationsRequireAuthentication() {
        for (Class<?> controller : List.of(MemberGraphqlController.class, PostGraphqlController.class)) {
            assertThat(operations(controller)).isNotEmpty().allSatisfy(m -> {
                PreAuthorize guard = m.getAnnotation(PreAuthorize.class);
                assertThat(guard).as(m.toString()).isNotNull();
                assertThat(guard.value()).isEqualTo("isAuthenticated()");
            });
        }
    }

    @Test
    void onlyRegisterLoginAndRefreshAreAnonymous() {
        assertThat(operations(AuthGraphqlController.class)).extracting(Method::getName)
                .containsExactlyInAnyOrder("register", "login", "refresh");
        assertThat(operations(AuthGraphqlController.class))
                .noneMatch(m -> m.isAnnotationPresent(PreAuthorize.class));
    }
}
