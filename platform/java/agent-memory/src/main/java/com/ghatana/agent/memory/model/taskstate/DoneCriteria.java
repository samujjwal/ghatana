package com.ghatana.agent.memory.model.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Criteria that define when a task is considered complete.
 *
 * @doc.type value-object
 * @doc.purpose Task completion criteria
 * @doc.layer agent-memory
 */
@Value
@Builder
public class DoneCriteria {

    @NotNull List<Criterion> criteria;
    @Builder.Default boolean allRequired = true;

    /** Whether all criteria are satisfied. */
    public boolean isSatisfied() {
        if (allRequired) {
            return criteria.stream().allMatch(Criterion::isSatisfied);
        }
        return criteria.stream().anyMatch(Criterion::isSatisfied);
    }

    @Value
    @Builder
    public static class Criterion {
        @NotNull String id;
        @NotNull String description;
        @Builder.Default boolean satisfied = false;
    }
}
