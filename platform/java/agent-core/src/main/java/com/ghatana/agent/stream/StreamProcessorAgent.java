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

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Stateful event stream processor: the base class for event processing operator agents.
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li>Processing ordered event streams with window state (ingestion, routing, transformation)</li>
 *   <li>Complex Event Processing (CEP) with pattern detection across sequences</li>
 *   <li>Windowed aggregations (tumbling, sliding, session)</li>
 *   <li>Event enrichment with external reference data and cache-aside</li>
 * </ul>
 *
 * <h2>Key Distinction from REACTIVE and DETERMINISTIC</h2>
 * <ul>
 *   <li>Unlike {@link com.ghatana.agent.reactive.ReactiveAgent} (stateless trigger→action),
 *       this class maintains checkpoint-recoverable window state.</li>
 *   <li>Unlike {@link com.ghatana.agent.deterministic.DeterministicAgent} (per-record rules),
 *       this class is designed for ordered stream processing with backpressure.</li>
 * </ul>
 *
 * <h2>Subclassing</h2>
 * <p>Override {@link #processEvent(AgentContext, Map)} for event-level logic.
 * Override {@link #onCheckpoint()} to persist custom window state.
 * Override {@link #onRestore(Map)} to restore state after a restart.
 *
 * <pre>{@code
 * public class KafkaIngestionAgent extends StreamProcessorAgent {
 *
 *     @Override
 *     protected Map<String, Object> processEvent(AgentContext ctx, Map<String, Object> event) {
 *         // parse, validate, normalize, emit
 *         return Map.of("type", "event.normalized", "payload", event);
 *     }
 *
 *     @Override
 *     public AgentDescriptor descriptor() {
 *         return AgentDescriptor.builder()
 *             .agentId("kafka-ingestion-agent")
 *             .type(AgentType.STREAM_PROCESSOR)
 *             .subtype(StreamProcessorSubtype.INGESTION.name())
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Stateful event stream processor base class — for event processing operators
 * @doc.layer platform
 * @doc.pattern Template Method
 * @doc.gaa.lifecycle act, capture
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
public abstract class StreamProcessorAgent
        extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(StreamProcessorAgent.class);

    private volatile StreamProcessorAgentConfig streamConfig;

    // ─────────────────────────────────────────────────────────────────────────
    // TypedAgent contract
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected @NotNull Promise<Void> doInitialize(@NotNull AgentConfig config) {
        this.streamConfig = StreamProcessorAgentConfig.from(config);
        log.info("StreamProcessorAgent [{}] initialized: subtype={}, checkpoint={}ms",
                descriptor().getAgentId(),
                streamConfig.getSubtype(),
                streamConfig.getCheckpointInterval().toMillis());
        onStart();
        return Promise.complete();
    }

    @Override
    protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess(
            @NotNull AgentContext ctx,
            @NotNull Map<String, Object> event) {

        Instant start = Instant.now();

        try {
            Map<String, Object> result = processEvent(ctx, event);
            Duration elapsed = Duration.between(start, Instant.now());

            if (result == null || result.isEmpty()) {
                return Promise.of(AgentResult.skipped("Event filtered/dropped", descriptor().getAgentId()));
            }
            return Promise.of(AgentResult.success(result, descriptor().getAgentId(), elapsed));

        } catch (Exception e) {
            log.error("StreamProcessorAgent [{}] failed to process event: {}",
                    descriptor().getAgentId(), event.get("type"), e);
            return Promise.of(AgentResult.failure(e, descriptor().getAgentId(),
                    Duration.between(start, Instant.now())));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Abstract hooks — subclasses must implement
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Processes a single event from the stream and returns the output event.
     *
     * <p>Return {@code null} or an empty map to indicate the event was filtered
     * (i.e., dropped from the output stream). The base class will return
     * {@link AgentResultStatus#SKIPPED} in this case.
     *
     * @param ctx   the agent context (includes tenant, trace ID)
     * @param event the input event as a map
     * @return the output event, or null/empty to drop the event
     */
    protected abstract Map<String, Object> processEvent(
            @NotNull AgentContext ctx,
            @NotNull Map<String, Object> event);

    // ─────────────────────────────────────────────────────────────────────────
    // Optional hooks — subclasses may override
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called after initialization, before any events are processed.
     * Override to set up connections, subscribe to sources, etc.
     */
    protected void onStart() {
        // no-op by default
    }

    /**
     * Called periodically at the configured checkpoint interval.
     * Override to persist window state, position offsets, etc.
     *
     * @return the checkpoint state to persist (key-value map)
     */
    @NotNull
    protected Map<String, Object> onCheckpoint() {
        return Map.of();
    }

    /**
     * Called after a restart to restore processor state from the last checkpoint.
     *
     * @param checkpointState the persisted checkpoint state
     */
    protected void onRestore(@NotNull Map<String, Object> checkpointState) {
        // no-op by default
    }

    /**
     * Returns the current stream processor configuration.
     * Available after {@link #initialize(AgentConfig)} is called.
     *
     * @return the stream processor config
     */
    protected StreamProcessorAgentConfig streamConfig() {
        return streamConfig;
    }
}
