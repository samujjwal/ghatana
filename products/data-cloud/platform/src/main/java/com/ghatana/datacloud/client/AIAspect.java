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

/**
 * Base interface for AI aspects in the AI-first data layer.
 *
 * <p>AI aspects are composable processing units that apply AI/ML
 * capabilities to data operations. They form a pipeline that runs
 * on every data operation (read, write, query).
 *
 * <h2>Aspect Types</h2>
 * <pre>
 * ┌────────────────────┬─────────────────────────────────────────────┐
 * │  Aspect Type       │  Purpose                                    │
 * ├────────────────────┼─────────────────────────────────────────────┤
 * │  Embedding         │  Generate vector embeddings for search      │
 * │  Classification    │  Auto-classify/tag incoming data            │
 * │  Anomaly           │  Detect anomalies and outliers              │
 * │  Enrichment        │  Add derived fields via AI                  │
 * │  Validation        │  AI-powered data quality checks             │
 * │  Summarization     │  Generate summaries for documents           │
 * └────────────────────┴─────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * Request → [Pre-aspects] → Core Operation → [Post-aspects] → Response
 *              ↓                                   ↓
 *        Validate/Enrich                    Embed/Classify
 * </pre>
 *
 * @param <I> input type
 * @param <O> output type
 *
 * @see AIAspectPipeline
 * @see AIAspectContext
 * @doc.type interface
 * @doc.purpose Base AI aspect interface
 * @doc.layer core
 * @doc.pattern Aspect, Pipeline Element
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface AIAspect<I, O> {

    /**
     * Returns the unique name of this aspect.
     *
     * @return aspect name (e.g., "embedding", "classification")
     */
    String name();

    /**
     * Returns the execution phase for this aspect.
     *
     * @return phase (PRE, POST, or BOTH)
     */
    Phase phase();

    /**
     * Returns the priority for ordering (lower = earlier).
     *
     * @return priority value (default 100)
     */
    default int priority() {
        return 100;
    }

    /**
     * Processes the input and produces output.
     *
     * @param input the input data
     * @param context the aspect context
     * @return processed output
     */
    Promise<O> process(I input, AIAspectContext context);

    /**
     * Returns true if this aspect is applicable to the given operation.
     *
     * @param operation the operation type
     * @param context the aspect context
     * @return true if applicable
     */
    default boolean isApplicable(OperationType operation, AIAspectContext context) {
        return true; // Applicable to all by default
    }

    /**
     * Returns true if this aspect should run asynchronously.
     *
     * <p>Async aspects don't block the main operation flow.
     *
     * @return true for async execution
     */
    default boolean isAsync() {
        return false;
    }

    /**
     * Execution phase for aspects.
     */
    enum Phase {
        /** Runs before the core operation */
        PRE,
        /** Runs after the core operation */
        POST,
        /** Runs both before and after */
        BOTH
    }

    /**
     * Data operation types.
     */
    enum OperationType {
        /** Create/Insert operation */
        CREATE,
        /** Read/Get operation */
        READ,
        /** Update operation */
        UPDATE,
        /** Delete operation */
        DELETE,
        /** Query/Search operation */
        QUERY,
        /** Batch operation */
        BATCH
    }
}
