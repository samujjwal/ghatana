/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.client;

import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Pipeline for executing AI aspects in order.
 *
 * <p>The pipeline manages aspect registration, ordering, and execution.
 * Aspects are sorted by priority and executed in phases:
 *
 * <h2>Execution Flow</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                     AIAspectPipeline                            │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  1. PRE Aspects (sorted by priority)                            │
 * │     ├── Validation aspects                                      │
 * │     ├── Enrichment aspects                                      │
 * │     └── Pre-processing aspects                                  │
 * │                                                                 │
 * │  2. CORE OPERATION (user-provided)                              │
 * │                                                                 │
 * │  3. POST Aspects (sorted by priority)                           │
 * │     ├── Embedding generation                                    │
 * │     ├── Classification                                          │
 * │     └── Anomaly detection                                       │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * var pipeline = AIAspectPipeline.builder()
 *     .add(new EmbeddingAspect())
 *     .add(new ClassificationAspect())
 *     .add(new ValidationAspect())
 *     .build();
 *
 * Promise<Record> result = pipeline.execute(
 *     record,
 *     context,
 *     () -> repository.save(record)
 * );
 * }</pre>
 *
 * @param <T> the data type flowing through the pipeline
 *
 * @see AIAspect
 * @see AIAspectContext
 * @doc.type class
 * @doc.purpose AI aspect pipeline executor
 * @doc.layer core
 * @doc.pattern Pipeline, Chain of Responsibility
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class AIAspectPipeline<T> {

    private final List<AIAspect<T, T>> preAspects;
    private final List<AIAspect<T, T>> postAspects;
    private final List<AIAspect<T, T>> asyncAspects;

    private AIAspectPipeline(Builder<T> builder) {
        List<AIAspect<T, T>> pre = new ArrayList<>();
        List<AIAspect<T, T>> post = new ArrayList<>();
        List<AIAspect<T, T>> async = new ArrayList<>();

        for (AIAspect<T, T> aspect : builder.aspects) {
            if (aspect.isAsync()) {
                async.add(aspect);
            } else {
                switch (aspect.phase()) {
                    case PRE -> pre.add(aspect);
                    case POST -> post.add(aspect);
                    case BOTH -> {
                        pre.add(aspect);
                        post.add(aspect);
                    }
                }
            }
        }

        // Sort by priority
        Comparator<AIAspect<?, ?>> byPriority = Comparator.comparingInt(AIAspect::priority);
        pre.sort(byPriority);
        post.sort(byPriority);
        async.sort(byPriority);

        this.preAspects = Collections.unmodifiableList(pre);
        this.postAspects = Collections.unmodifiableList(post);
        this.asyncAspects = Collections.unmodifiableList(async);
    }

    /**
     * Executes the pipeline with the given input.
     *
     * @param input the input data
     * @param context the aspect context
     * @param coreOperation the core operation to execute between phases
     * @return promise of the final result
     */
    public Promise<T> execute(T input, AIAspectContext context, Supplier<Promise<T>> coreOperation) {
        // Execute PRE aspects
        return executePhase(input, context, preAspects, AIAspect.OperationType.CREATE)
                // Execute core operation
                .then(preResult -> coreOperation.get())
                // Execute POST aspects
                .then(coreResult -> executePhase(coreResult, context, postAspects, context.operationType()))
                // Fire-and-forget async aspects
                .then(postResult -> {
                    fireAsyncAspects(postResult, context);
                    return Promise.of(postResult);
                });
    }

    /**
     * Executes only PRE aspects.
     *
     * @param input input data
     * @param context aspect context
     * @return promise of processed data
     */
    public Promise<T> executePreAspects(T input, AIAspectContext context) {
        return executePhase(input, context, preAspects, context.operationType());
    }

    /**
     * Executes only POST aspects.
     *
     * @param input input data
     * @param context aspect context
     * @return promise of processed data
     */
    public Promise<T> executePostAspects(T input, AIAspectContext context) {
        return executePhase(input, context, postAspects, context.operationType());
    }

    private Promise<T> executePhase(
            T input,
            AIAspectContext context,
            List<AIAspect<T, T>> aspects,
            AIAspect.OperationType operationType
    ) {
        Promise<T> result = Promise.of(input);

        for (AIAspect<T, T> aspect : aspects) {
            if (!aspect.isApplicable(operationType, context)) {
                continue;
            }

            result = result.then(current -> {
                long startTime = System.currentTimeMillis();
                return aspect.process(current, context)
                        .then(output -> {
                            long duration = System.currentTimeMillis() - startTime;
                            context.recordResult(aspect.name(),
                                    AIAspectContext.AIAspectResult.success(aspect.name(), output, duration));
                            return Promise.of(output);
                        })
                        .then(
                                Promise::of,
                                error -> {
                                    long duration = System.currentTimeMillis() - startTime;
                                    context.recordResult(aspect.name(),
                                            AIAspectContext.AIAspectResult.failure(aspect.name(), error.getMessage(), duration));
                                    // Continue with current value on error (graceful degradation)
                                    return Promise.of(current);
                                }
                        );
            });
        }

        return result;
    }

    private void fireAsyncAspects(T input, AIAspectContext context) {
        for (AIAspect<T, T> aspect : asyncAspects) {
            if (aspect.isApplicable(context.operationType(), context)) {
                // Fire and forget - don't wait for completion
                aspect.process(input, context)
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                // Log async error (would use proper logging in production)
                                System.err.println("Async aspect " + aspect.name() + " failed: " + error.getMessage());
                            }
                        });
            }
        }
    }

    /**
     * Returns the number of registered aspects.
     *
     * @return total aspect count
     */
    public int aspectCount() {
        return preAspects.size() + postAspects.size() + asyncAspects.size();
    }

    /**
     * Returns registered PRE aspects.
     *
     * @return list of PRE aspects
     */
    public List<AIAspect<T, T>> preAspects() {
        return preAspects;
    }

    /**
     * Returns registered POST aspects.
     *
     * @return list of POST aspects
     */
    public List<AIAspect<T, T>> postAspects() {
        return postAspects;
    }

    /**
     * Returns registered async aspects.
     *
     * @return list of async aspects
     */
    public List<AIAspect<T, T>> asyncAspects() {
        return asyncAspects;
    }

    /**
     * Creates a new builder.
     *
     * @param <T> data type
     * @return new builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Creates an empty pipeline (no aspects).
     *
     * @param <T> data type
     * @return empty pipeline
     */
    public static <T> AIAspectPipeline<T> empty() {
        return new Builder<T>().build();
    }

    /**
     * Builder for AIAspectPipeline.
     *
     * @param <T> data type
     */
    public static final class Builder<T> {
        private final List<AIAspect<T, T>> aspects = new CopyOnWriteArrayList<>();

        private Builder() {
        }

        /**
         * Adds an aspect to the pipeline.
         *
         * @param aspect the aspect to add
         * @return this builder
         */
        public Builder<T> add(AIAspect<T, T> aspect) {
            aspects.add(aspect);
            return this;
        }

        /**
         * Adds multiple aspects.
         *
         * @param aspects aspects to add
         * @return this builder
         */
        @SafeVarargs
        public final Builder<T> addAll(AIAspect<T, T>... aspects) {
            Collections.addAll(this.aspects, aspects);
            return this;
        }

        /**
         * Builds the pipeline.
         *
         * @return configured pipeline
         */
        public AIAspectPipeline<T> build() {
            return new AIAspectPipeline<>(this);
        }
    }
}
