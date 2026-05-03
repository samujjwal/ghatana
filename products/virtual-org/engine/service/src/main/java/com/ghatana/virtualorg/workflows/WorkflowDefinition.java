package com.ghatana.virtualorg.workflows;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Workflow definition with ordered steps, conditional execution, and aggregation.
 *
 * <p><b>Purpose</b><br>
 * Defines a complete workflow pipeline with steps and execution order.
 * Supports plain steps, conditional steps (guard predicate), and
 * aggregation steps (reduce a collection of prior outputs).
 *
 * @doc.type class
 * @doc.purpose Workflow pipeline definition
 * @doc.layer product
 * @doc.pattern Builder
 */
public class WorkflowDefinition {

    /**
     * Internal representation of a step entry.
     * Supports plain steps, conditional steps, and aggregation specs.
     */
    public sealed interface StepEntry
        permits WorkflowDefinition.PlainStep,
                WorkflowDefinition.ConditionalStep,
                WorkflowDefinition.AggregationStep {
        String stepId();
    }

    /** A plain unconditional workflow step backed by an agent executor. */
    public record PlainStep(WorkflowStep step) implements StepEntry {
        public PlainStep {
            Objects.requireNonNull(step, "step must not be null");
        }
        @Override public String stepId() { return step.getStepId(); }
    }

    /**
     * A conditional workflow step.
     * The step's agent executor is invoked only when the condition returns
     * {@code true} for the current execution context object.
     */
    public record ConditionalStep(
            WorkflowStep step,
            Predicate<Object> condition) implements StepEntry {
        public ConditionalStep {
            Objects.requireNonNull(step, "step must not be null");
            Objects.requireNonNull(condition, "condition must not be null");
        }
        @Override public String stepId() { return step.getStepId(); }

        /** Returns true when this step should be executed given {@code context}. */
        public boolean shouldExecute(Object context) { return condition.test(context); }
    }

    /**
     * An aggregation step.
     * No agent executor — the workflow engine applies {@code aggregator} to the
     * accumulated step outputs and stores the result under {@code stepId}.
     *
     * @param <R> type accepted by the aggregator (e.g. {@code Map<String, Object>})
     */
    public record AggregationStep<R>(
            String stepId,
            Function<R, Object> aggregator) implements StepEntry {
        public AggregationStep {
            Objects.requireNonNull(stepId, "stepId must not be null");
            Objects.requireNonNull(aggregator, "aggregator must not be null");
        }
    }

    private final List<StepEntry> stepEntries;

    private WorkflowDefinition(Builder builder) {
        this.stepEntries = List.copyOf(builder.stepEntries);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns all step entries (plain, conditional, and aggregation).
     * Use this when the engine needs the full specification.
     */
    public List<StepEntry> getStepEntries() {
        return stepEntries;
    }

    /**
     * Returns only the agent-executable steps (plain + conditional).
     * Aggregation steps are excluded because they have no agent executor.
     */
    public List<WorkflowStep> getSteps() {
        return stepEntries.stream()
            .flatMap(e -> switch (e) {
                case PlainStep p -> java.util.stream.Stream.of(p.step());
                case ConditionalStep c -> java.util.stream.Stream.of(c.step());
                case AggregationStep<?> ignored -> java.util.stream.Stream.empty();
            })
            .collect(Collectors.toList());
    }

    public static class Builder {
        private final List<StepEntry> stepEntries = new ArrayList<>();

        public Builder addStep(WorkflowStep step) {
            this.stepEntries.add(new PlainStep(step));
            return this;
        }

        /**
         * Adds a conditional step that is executed only when {@code condition} returns
         * {@code true} for the runtime execution context. The engine must check
         * {@link ConditionalStep#shouldExecute(Object)} before dispatching the task.
         */
        public Builder addConditionalStep(
                String stepId,
                Predicate<Object> condition,
                WorkflowStep step) {
            Objects.requireNonNull(stepId, "stepId must not be null");
            this.stepEntries.add(new ConditionalStep(step, condition));
            return this;
        }

        /**
         * Adds an aggregation step. The engine applies {@code aggregator} to the
         * accumulated prior step outputs (typically a {@code Map<String, Object>})
         * and stores the result under {@code stepId}.
         *
         * @param <R>        type of the accumulated value passed to the aggregator
         * @param stepId     unique step identifier; also the output key for the result
         * @param aggregator function applied to the accumulated step results
         */
        public <R> Builder addAggregationStep(
                String stepId,
                Function<R, Object> aggregator) {
            this.stepEntries.add(new AggregationStep<>(stepId, aggregator));
            return this;
        }

        public WorkflowDefinition build() {
            return new WorkflowDefinition(this);
        }
    }
}
