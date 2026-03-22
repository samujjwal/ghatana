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

package com.ghatana.agent;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Unified, type-safe agent contract for the Ghatana platform.
 *
 * <p>This is the <b>canonical forward-looking agent interface</b> that
 * supersedes the original untyped {@link Agent}. The type parameters
 * {@code I} (input) and {@code O} (output) enable compile-time type
 * safety throughout pipelines, registries, and orchestrators.
 *
 * <p>The original {@link Agent} interface is retained for backward
 * compatibility with existing consumers (virtual-org, yappc); new code
 * should implement {@code TypedAgent} exclusively. Migration of
 * existing implementations is tracked under Task 2.11 of the
 * AEP-EventCloud Stabilization Plan.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   new TypedAgent  →  initialize(config)  →  process(ctx, input)*  →  shutdown()
 *                            │                        │
 *                            │                   healthCheck()
 *                            │                        │
 *                            └── descriptor() ────────┘
 * </pre>
 *
 * <ol>
 *   <li><b>Construction</b>: Agent is created (via DI or factory).</li>
 *   <li><b>Initialization</b>: {@link #initialize(AgentConfig)} is called once
 *       with the agent's configuration.</li>
 *   <li><b>Processing</b>: {@link #process(AgentContext, Object)} is called
 *       for each input. Returns a typed {@link AgentResult}.</li>
 *   <li><b>Batch Processing</b> (optional): {@link #processBatch(AgentContext, List)}
 *       for throughput-optimized bulk operations.</li>
 *   <li><b>Health Check</b>: {@link #healthCheck()} is called periodically.</li>
 *   <li><b>Shutdown</b>: {@link #shutdown()} releases resources.</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations must be safe for concurrent {@code process()} calls.</p>
 *
 * <h2>Relationship to Existing Types</h2>
 * <ul>
 *   <li>{@link Agent} — original untyped interface; retained for backward compat</li>
 *   <li>{@link AgentCapabilities} — original metadata; {@link AgentDescriptor} is the richer replacement</li>
 *   <li>{@link com.ghatana.agent.framework.runtime.BaseAgent} — GAA lifecycle base class (PERCEIVE→REASON→ACT→CAPTURE→REFLECT)</li>
 *   <li>{@link com.ghatana.agent.framework.runtime.AbstractTypedAgent} — new skeletal implementation of TypedAgent</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class ThresholdAgent implements TypedAgent<SensorReading, Alert> {
 *
 *     private double threshold;
 *
 *     @Override
 *     public AgentDescriptor descriptor() {
 *         return AgentDescriptor.builder()
 *             .agentId("threshold-agent")
 *             .name("Threshold Checker")
 *             .type(AgentType.DETERMINISTIC)
 *             .determinism(DeterminismGuarantee.FULL)
 *             .build();
 *     }
 *
 *     @Override
 *     public Promise<AgentResult<Alert>> process(AgentContext ctx, SensorReading input) {
 *         Instant start = Instant.now();
 *         if (input.value() > threshold) {
 *             Alert alert = new Alert("HIGH", "Value exceeds " + threshold);
 *             return Promise.of(AgentResult.success(alert, descriptor().getAgentId(),
 *                 Duration.between(start, Instant.now())));
 *         }
 *         return Promise.of(AgentResult.skipped("Below threshold", descriptor().getAgentId()));
 *     }
 *
 *     @Override
 *     public Promise<Void> initialize(AgentConfig config) {
 *         this.threshold = (double) config.getProperties().getOrDefault("threshold", 100.0);
 *         return Promise.complete();
 *     }
 *
 *     @Override
 *     public Promise<Void> shutdown() { return Promise.complete(); }
 *
 *     @Override
 *     public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.HEALTHY); }
 * }
 * }</pre>
 *
 * @param <I> input type
 * @param <O> output type
 *
 * @doc.type interface
 * @doc.purpose Unified typed agent contract
 * @doc.layer core
 * @doc.pattern Strategy, Lifecycle
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public interface TypedAgent<I, O> {

    // ═══════════════════════════════════════════════════════════════════════════
    // Metadata
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the agent's immutable descriptor with identity, type,
     * SLAs, and capabilities.
     *
     * <p>Must be available immediately after construction — before
     * {@link #initialize(AgentConfig)} is called. The descriptor
     * is used by the registry and scheduler for discovery and routing.
     *
     * @return the agent descriptor (never null)
     */
    @NotNull
    AgentDescriptor descriptor();

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initializes the agent with the given configuration.
     *
     * <p>Called exactly once before any {@code process()} invocation.
     *
     * @param config agent configuration
     * @return a Promise completing when initialization is done
     */
    @NotNull
    Promise<Void> initialize(@NotNull AgentConfig config);

    /**
     * Shuts down the agent and releases all resources.
     * Implementations should be idempotent.
     *
     * @return a Promise completing when shutdown is done
     */
    @NotNull
    Promise<Void> shutdown();

    /**
     * Returns the current health status of the agent.
     * Called periodically by the runtime for monitoring.
     *
     * @return a Promise of the current health status
     */
    @NotNull
    Promise<HealthStatus> healthCheck();

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processes a single input and returns a typed result.
     *
     * @param ctx   execution context with tenant, config, metrics
     * @param input the input to process
     * @return a Promise of the typed result
     */
    @NotNull
    Promise<AgentResult<O>> process(@NotNull AgentContext ctx, @NotNull I input);

    /**
     * Processes a batch of inputs for throughput optimization.
     *
     * <p>Default implementation delegates to individual {@link #process} calls.
     * Override for batch-optimized agents (e.g., batch model inference).
     *
     * @param ctx    execution context
     * @param inputs batch of inputs
     * @return a Promise of a list of results (same order as inputs)
     */
    @NotNull
    default Promise<List<AgentResult<O>>> processBatch(
            @NotNull AgentContext ctx,
            @NotNull List<I> inputs) {

        @SuppressWarnings("unchecked")
        Promise<AgentResult<O>>[] promises = inputs.stream()
                .map(input -> process(ctx, input))
                .toArray(Promise[]::new);

        return Promises.toList(promises);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Optional Hooks
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Called when the agent configuration is hot-reloaded at runtime.
     * Default implementation re-initializes with the new config.
     *
     * @param newConfig updated configuration
     * @return a Promise completing when reconfiguration is done
     */
    @NotNull
    default Promise<Void> reconfigure(@NotNull AgentConfig newConfig) {
        return initialize(newConfig);
    }

    /**
     * Validates whether the given input is acceptable for this agent.
     * Default accepts all input.
     *
     * @param input the input to validate
     * @return true if input is valid
     */
    default boolean validateInput(@NotNull I input) {
        return true;
    }
}
