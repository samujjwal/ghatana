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

package com.ghatana.agent.stream;

/**
 * Subtypes of stream processor agent behaviour.
 *
 * <p>All subtypes share: stateful event stream processing with checkpoint/recovery
 * support, backpressure handling, and ordered event processing guarantees.
 *
 * <h2>Type Boundary vs REACTIVE and DETERMINISTIC</h2>
 * <ul>
 *   <li><b>STREAM_PROCESSOR</b>: Stateful — maintains window state, counters, position.
 *       Survives restarts via checkpoint. Processes ordered event streams.</li>
 *   <li><b>REACTIVE</b>: Stateless — simple trigger→action with no persistent state.
 *       Fast path for immediate reflexes (alerts, circuit-breakers).</li>
 *   <li><b>DETERMINISTIC</b>: Stateless business logic — rules, policies, templates.
 *       Applicable to individual events, not streams.</li>
 * </ul>
 *
 * @since 2.1.0
 *
 * @doc.type enum
 * @doc.purpose Subtypes of stream processor agent strategies
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum StreamProcessorSubtype {

    /**
     * Event ingestion: reads from external sources (Kafka, HTTP, files, databases)
     * and emits canonicalized internal events. Handles parsing, deserialization,
     * schema validation, and back-off on source failures.
     */
    INGESTION,

    /**
     * Content-based routing: inspects event content and dispatches to
     * topic-specific or partition-specific downstream channels.
     * Supports fan-out (one event → multiple channels) and conditional routing.
     */
    ROUTING,

    /**
     * Event transformation: maps, enriches, normalizes, or converts event structure.
     * Stateless per-event transformation (no window). For windowed transformations,
     * use {@link #WINDOW_AGGREGATION}.
     */
    TRANSFORMATION,

    /**
     * Complex Event Processing: detects patterns across event sequences
     * (e.g., sequence detection, absence patterns, within-time correlations).
     * Maintains pattern-match state across multiple events.
     */
    CEP,

    /**
     * Event enrichment: joins incoming events with reference data (Redis, database,
     * lookup tables, external API calls) and emits enriched events.
     * Caches reference data with configurable TTL and cache-aside patterns.
     */
    ENRICHMENT,

    /**
     * Windowed aggregation: accumulates events over time or count windows
     * (tumbling, sliding, session) and emits aggregate results.
     * Supports SUM, COUNT, AVG, MIN, MAX, and custom aggregate functions.
     */
    WINDOW_AGGREGATION,

    /**
     * Event filtering: drops events that do not match a predicate.
     * Equivalent to a DETERMINISTIC rule but operating on a stateful stream
     * (e.g., deduplication filter with seen-ID tracking).
     */
    FILTER
}
