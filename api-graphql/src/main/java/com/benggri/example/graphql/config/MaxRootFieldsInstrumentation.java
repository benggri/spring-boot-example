package com.benggri.example.graphql.config;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphqlErrorBuilder;
import graphql.execution.AbortExecutionException;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caps the number of root fields per operation (aliases and fragments included). Without it one
 * anonymous HTTP request could carry many aliased {@code login}/{@code register} mutations, each
 * costing a BCrypt hash. Rejection happens before any resolver runs, with a fixed message that does
 * not echo the query.
 */
public class MaxRootFieldsInstrumentation extends SimplePerformantInstrumentation {

    static final String MESSAGE = "Too many root fields in one operation";

    private final int maxRootFields;

    public MaxRootFieldsInstrumentation(int maxRootFields) {
        this.maxRootFields = maxRootFields;
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(
            InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        ExecutionContext context = parameters.getExecutionContext();
        int count = countFields(context.getOperationDefinition().getSelectionSet(),
                context.getFragmentsByName(), new HashSet<>());
        if (count > maxRootFields) {
            throw new AbortExecutionException(List.of(GraphqlErrorBuilder.newError()
                    .message(MESSAGE).errorType(ErrorType.ValidationError)
                    .extensions(Map.of("code", "BAD_REQUEST")).build()));
        }
        return super.beginExecuteOperation(parameters, state);
    }

    private static int countFields(SelectionSet selections, Map<String, FragmentDefinition> fragments,
            Set<String> visiting) {
        if (selections == null) {
            return 0;
        }
        int count = 0;
        for (Selection<?> selection : selections.getSelections()) {
            if (selection instanceof Field) {
                count++;
            } else if (selection instanceof InlineFragment inline) {
                count += countFields(inline.getSelectionSet(), fragments, visiting);
            } else if (selection instanceof FragmentSpread spread && visiting.add(spread.getName())) {
                FragmentDefinition definition = fragments.get(spread.getName());
                count += definition == null ? 0 : countFields(definition.getSelectionSet(), fragments, visiting);
                visiting.remove(spread.getName());
            }
        }
        return count;
    }
}
