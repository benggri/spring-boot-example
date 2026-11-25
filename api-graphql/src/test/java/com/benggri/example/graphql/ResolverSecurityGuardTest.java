package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.benggri.example.graphql.controller.AuthGraphqlController;
import com.benggri.example.graphql.controller.MemberGraphqlController;
import com.benggri.example.graphql.controller.PostGraphqlController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;

/** Deny-by-default guard: a new operation cannot be added without an explicit authorization decision. */
class ResolverSecurityGuardTest extends GraphqlTestBase {

    private static final Set<String> PUBLIC_OPERATIONS = Set.of("register", "login", "refresh");

    @Autowired
    private ApplicationContext context;

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

    /** Every @Controller bean of the real context, not a hard-coded list: a new controller is checked too. */
    @Test
    void everyControllerOperationIsGuardedOrExplicitlyPublic() {
        List<Class<?>> controllers = context.getBeansWithAnnotation(Controller.class).values().stream()
                .<Class<?>>map(bean -> ClassUtils.getUserClass(bean.getClass()))
                .filter(type -> type.getName().startsWith("com.benggri.example.graphql."))
                .toList();

        assertThat(controllers).contains(AuthGraphqlController.class, MemberGraphqlController.class,
                PostGraphqlController.class);
        List<String> unguarded = controllers.stream().flatMap(c -> ResolverGuards.unguarded(c).stream())
                .map(Method::getName).toList();
        assertThat(unguarded).containsExactlyInAnyOrderElementsOf(PUBLIC_OPERATIONS);
    }
}
