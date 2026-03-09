/*
 * Ghatana — Event Processing & AI Platform
 * Copyright © 2025 Samujjwal
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ghatana.core.operator.aggregation;

import com.ghatana.core.operator.AbstractStreamOperator;
import com.ghatana.core.operator.OperatorChain;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.core.state.StateStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for composing aggregation pipelines (windowing + aggregation +
 * join).
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a DSL for declaratively building multi-stage aggregation pipelines,
 * combining windowing, aggregation, and join operations with fluent method
 * chaining.
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * // Windowing + aggregation pipeline
 * OperatorChain pipeline = AggregationPipelineBuilder.create()
 *     .withWindowing(
 *         WindowingOperator.WindowingStrategy.tumbling(Duration.ofMinutes(5)),
 *         stateStore,
 *         metrics
 *     )
 *     .withAggregation(
 *         AggregationOperator.AggregationStrategy.SUM,
 *         "transaction_amount",
 *         stateStore,
 *         metrics
 *     )
 *     .build();
 *
 * // Windowing + aggregation + join pipeline
 * OperatorChain enriched = AggregationPipelineBuilder.create()
 *     .withWindowing(...)
 *     .withAggregation(...)
 *     .withJoin(
 *         "transaction_id",
 *         "tx_id",
 *         JoinOperator.JoinStrategy.INNER,
 *         Duration.ofMinutes(5),
 *         stateStore,
 *         metrics
 *     )
 *     .build();
 *
 * // Process events through pipeline
 * event = pipeline.process(event).getResult();
 * }</pre>
 *
 * <p>
 * <b>Pipeline Stages</b><br>
 * - <strong>Windowing:</strong> Partition events into time/count windows -
 * <strong>Aggregation:</strong> Compute aggregates (count, sum, avg, min, max)
 * - <strong>Join:</strong> Correlate with events from secondary stream
 *
 * <p>
 * <b>Fluent API Methods</b><br>
 * -
 * {@link #withWindowing(WindowingOperator.WindowingStrategy, StateStore, MetricsCollector)}
 * -
 * {@link #withAggregation(AggregationOperator.AggregationStrategy, String, StateStore, MetricsCollector)}
 * -
 * {@link #withJoin(String, String, JoinOperator.JoinStrategy, Duration, StateStore, MetricsCollector)}
 * - {@link #build()} - Compose operators into chain - {@link #validate()} -
 * Check pipeline validity - {@link #stageCount()} - Get number of stages
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Builder is not thread-safe; create separate instances per thread or
 * synchronize access.
 *
 * @see WindowingOperator
 * @see AggregationOperator
 * @see JoinOperator
 * @see OperatorChain
 * @see OperatorComposer
 * @doc.type class
 * @doc.purpose Fluent DSL for composing aggregation pipelines
 * @doc.layer core
 * @doc.pattern Builder
 */
public class AggregationPipelineBuilder {

    private final List<AbstractStreamOperator> stages = new ArrayList<>();
    private boolean validated = false;

    /**
     * Creates new aggregation pipeline builder.
     *
     * @return Empty builder ready for configuration
     */
    public static AggregationPipelineBuilder create() {
        return new AggregationPipelineBuilder();
    }

    /**
     * Adds windowing stage to pipeline.
     *
     * @param strategy   Windowing strategy (tumbling, sliding, session, count)
     * @param stateStore State store for buffering windowed events
     * @param metrics    Metrics collector
     * @return This builder for fluent chaining
     */
    public AggregationPipelineBuilder withWindowing(
            WindowingOperator.WindowingStrategy strategy,
            StateStore stateStore,
            MetricsCollector metrics) {
        Objects.requireNonNull(strategy, "Windowing strategy required");
        Objects.requireNonNull(stateStore, "State store required");
        Objects.requireNonNull(metrics, "Metrics collector required");

        stages.add(new WindowingOperator(strategy, stateStore, metrics));
        validated = false;
        return this;
    }

    /**
     * Adds aggregation stage to pipeline.
     *
     * @param strategy   Aggregation strategy (count, sum, avg, min, max)
     * @param fieldName  Field to aggregate (null for COUNT)
     * @param stateStore State store for accumulating aggregates
     * @param metrics    Metrics collector
     * @return This builder for fluent chaining
     */
    public AggregationPipelineBuilder withAggregation(
            AggregationOperator.AggregationStrategy strategy,
            String fieldName,
            StateStore stateStore,
            MetricsCollector metrics) {
        Objects.requireNonNull(strategy, "Aggregation strategy required");
        Objects.requireNonNull(stateStore, "State store required");
        Objects.requireNonNull(metrics, "Metrics collector required");

        stages.add(new AggregationOperator(strategy, fieldName, stateStore, metrics));
        validated = false;
        return this;
    }

    /**
     * Adds join stage to pipeline.
     *
     * @param stream1KeyField Field in stream1 to match on
     * @param stream2KeyField Field in stream2 to match on
     * @param joinStrategy    Join strategy (inner, left, right, outer)
     * @param joinWindow      Time window for matching events
     * @param stateStore      State store for buffering events
     * @param metrics         Metrics collector
     * @return This builder for fluent chaining
     */
    public AggregationPipelineBuilder withJoin(
            String stream1KeyField,
            String stream2KeyField,
            JoinOperator.JoinStrategy joinStrategy,
            Duration joinWindow,
            StateStore stateStore,
            MetricsCollector metrics) {
        Objects.requireNonNull(stream1KeyField, "Stream1 key field required");
        Objects.requireNonNull(stream2KeyField, "Stream2 key field required");
        Objects.requireNonNull(joinStrategy, "Join strategy required");
        Objects.requireNonNull(joinWindow, "Join window required");
        Objects.requireNonNull(stateStore, "State store required");
        Objects.requireNonNull(metrics, "Metrics collector required");

        stages.add(new JoinOperator(
                stream1KeyField,
                stream2KeyField,
                joinStrategy,
                joinWindow,
                stateStore,
                metrics));
        validated = false;
        return this;
    }

    /**
     * Validates pipeline configuration before building.
     *
     * @return This builder for fluent chaining
     * @throws IllegalStateException if pipeline is invalid
     */
    public AggregationPipelineBuilder validate() {
        if (stages.isEmpty()) {
            throw new IllegalStateException("Pipeline must have at least one stage");
        }

        // Validate stage ordering
        boolean hasWindowing = false;
        boolean hasAggregation = false;
        boolean hasJoin = false;

        for (AbstractStreamOperator stage : stages) {
            if (stage instanceof WindowingOperator) {
                hasWindowing = true;
            } else if (stage instanceof AggregationOperator) {
                if (!hasWindowing) {
                    throw new IllegalStateException(
                            "Aggregation stage must follow windowing stage");
                }
                hasAggregation = true;
            } else if (stage instanceof JoinOperator) {
                // Join can follow aggregation or windowing
                hasJoin = true;
            }
        }

        validated = true;
        return this;
    }

    /**
     * Gets the number of pipeline stages.
     *
     * @return Number of stages configured
     */
    public int stageCount() {
        return stages.size();
    }

    /**
     * Gets the operators in this pipeline.
     *
     * @return List of configured operators
     */
    public List<AbstractStreamOperator> getOperators() {
        return new ArrayList<>(stages);
    }

    /**
     * Builds the operator pipeline.
     *
     * @return Composed operator chain ready for processing
     * @throws IllegalStateException if pipeline configuration is invalid
     */
    public OperatorChain build() {
        if (!validated) {
            validate();
        }

        List<UnifiedOperator> operators = new ArrayList<>(stages.size());
        operators.addAll(stages);
        return OperatorChain.of(operators);
    }
}
