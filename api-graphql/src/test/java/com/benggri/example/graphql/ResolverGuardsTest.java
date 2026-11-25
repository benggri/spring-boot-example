package com.benggri.example.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.prepost.PreAuthorize;

/** The guard checker itself, proven on synthetic classes that are never part of the application. */
class ResolverGuardsTest {

    static class Sloppy {
        @QueryMapping
        String q() {
            return "";
        }

        @MutationMapping
        String m() {
            return "";
        }

        @SubscriptionMapping
        String s() {
            return "";
        }

        @SchemaMapping(typeName = "Mutation", field = "viaSchemaMapping")
        String sm() {
            return "";
        }

        @BatchMapping(typeName = "Query")
        String bm() {
            return "";
        }

        @SchemaMapping(typeName = "Post", field = "author")
        String notAnOperation() {
            return "";
        }

        @QueryMapping
        @PreAuthorize("isAuthenticated()")
        String guarded() {
            return "";
        }
    }

    @PreAuthorize("isAuthenticated()")
    static class GuardedByClass {
        @QueryMapping
        String q() {
            return "";
        }
    }

    @Test
    void unguarded_findsEveryKindOfRootOperationWithoutAGuard() {
        List<String> names = ResolverGuards.unguarded(Sloppy.class).stream().map(Method::getName).toList();

        assertThat(names).containsExactlyInAnyOrder("q", "m", "s", "sm", "bm");
    }

    @Test
    void operations_ignoresFieldResolversOfNonRootTypes() {
        assertThat(ResolverGuards.operations(Sloppy.class)).extracting(Method::getName)
                .doesNotContain("notAnOperation");
    }

    @Test
    void classLevelGuardCoversItsOperations() {
        assertThat(ResolverGuards.operations(GuardedByClass.class)).hasSize(1);
        assertThat(ResolverGuards.unguarded(GuardedByClass.class)).isEmpty();
    }
}
