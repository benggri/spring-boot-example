package com.benggri.example.graphql;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;

/**
 * Finds GraphQL root operations (Query/Mutation/Subscription handlers) and tells whether each has a
 * method-security guard. {@code @QueryMapping}, {@code @MutationMapping} and {@code @SubscriptionMapping}
 * are meta-annotated {@code @SchemaMapping}, so the merged annotation covers all of them.
 */
final class ResolverGuards {

    private static final Set<String> ROOT_TYPES = Set.of("Query", "Mutation", "Subscription");

    private ResolverGuards() {
    }

    /** Operations declared on the (user) class behind a possibly proxied bean. */
    static List<Method> operations(Class<?> type) {
        Class<?> target = ClassUtils.getUserClass(type);
        return Arrays.stream(target.getDeclaredMethods()).filter(ResolverGuards::isRootOperation).toList();
    }

    static boolean isRootOperation(Method method) {
        SchemaMapping schema = AnnotatedElementUtils.findMergedAnnotation(method, SchemaMapping.class);
        if (schema != null && ROOT_TYPES.contains(schema.typeName())) {
            return true;
        }
        BatchMapping batch = AnnotatedElementUtils.findMergedAnnotation(method, BatchMapping.class);
        return batch != null && ROOT_TYPES.contains(batch.typeName());
    }

    static boolean isGuarded(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), PreAuthorize.class);
    }

    /** Operations of {@code type} without a method-security guard. */
    static List<Method> unguarded(Class<?> type) {
        return operations(type).stream().filter(m -> !isGuarded(m)).toList();
    }
}
