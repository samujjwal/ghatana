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

package com.ghatana.datacloud.brain;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for the DataCloudBrain.
 *
 * <p>Contains settings that control the behavior of all brain subsystems.
 *
 * @doc.type record
 * @doc.purpose Brain configuration
 * @doc.layer core
 * @doc.pattern Configuration
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class BrainConfig {

    // ═══════════════════════════════════════════════════════════════════════════
    // General Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Unique identifier for this brain instance.
     */
    @Builder.Default
    String brainId = "brain-default";

    /**
     * Human-readable name.
     */
    @Builder.Default
    String name = "DataCloud Brain";

    /**
     * Whether the brain is in learning mode.
     */
    @Builder.Default
    boolean learningEnabled = true;

    /**
     * Whether reflexes are enabled.
     */
    @Builder.Default
    boolean reflexesEnabled = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // Attention Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Default salience threshold for attention.
     */
    @Builder.Default
    float salienceThreshold = 0.5f;

    /**
     * Maximum items in attention focus.
     */
    @Builder.Default
    int maxAttentionItems = 100;

    /**
     * Attention decay rate per second.
     */
    @Builder.Default
    float attentionDecayRate = 0.1f;

    // ═══════════════════════════════════════════════════════════════════════════
    // Workspace Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum items in global workspace.
     */
    @Builder.Default
    int maxWorkspaceItems = 50;

    /**
     * Broadcast batch size.
     */
    @Builder.Default
    int broadcastBatchSize = 10;

    /**
     * Timeout for workspace processing.
     */
    @Builder.Default
    Duration workspaceTimeout = Duration.ofSeconds(5);

    // ═══════════════════════════════════════════════════════════════════════════
    // Memory Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether tiered memory is enabled.
     */
    @Builder.Default
    boolean tieredMemoryEnabled = true;

    /**
     * Hot tier capacity.
     */
    @Builder.Default
    int hotTierCapacity = 10000;

    /**
     * Warm tier capacity.
     */
    @Builder.Default
    int warmTierCapacity = 100000;

    /**
     * Memory reconciliation interval.
     */
    @Builder.Default
    Duration reconciliationInterval = Duration.ofMinutes(5);

    // ═══════════════════════════════════════════════════════════════════════════
    // Pattern Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Minimum confidence for pattern matching.
     */
    @Builder.Default
    float minPatternConfidence = 0.7f;

    /**
     * Maximum patterns to evaluate per record.
     */
    @Builder.Default
    int maxPatternsPerRecord = 20;

    /**
     * Pattern expiration window.
     */
    @Builder.Default
    Duration patternExpirationWindow = Duration.ofDays(30);

    // ═══════════════════════════════════════════════════════════════════════════
    // Reflex Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum reflex rules per tenant.
     */
    @Builder.Default
    int maxReflexRules = 1000;

    /**
     * Reflex execution timeout.
     */
    @Builder.Default
    Duration reflexTimeout = Duration.ofSeconds(5);

    /**
     * Whether high-risk reflexes require approval.
     */
    @Builder.Default
    boolean requireApprovalForHighRisk = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // Autonomy Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Default autonomy level.
     */
    @Builder.Default
    String defaultAutonomyLevel = "GUIDED";

    /**
     * Maximum risk for autonomous actions.
     */
    @Builder.Default
    float maxAutonomousRisk = 0.3f;

    /**
     * Whether all actions should be audited.
     */
    @Builder.Default
    boolean auditAllActions = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // Learning Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Learning rate for reinforcement.
     */
    @Builder.Default
    float learningRate = 0.01f;

    /**
     * Discount factor for future rewards.
     */
    @Builder.Default
    float discountFactor = 0.95f;

    /**
     * Minimum samples for pattern learning.
     */
    @Builder.Default
    int minSamplesForPattern = 100;

    // ═══════════════════════════════════════════════════════════════════════════
    // Vector Memory Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Embedding dimension.
     */
    @Builder.Default
    int embeddingDimension = 384;

    /**
     * Embedding model name.
     */
    @Builder.Default
    String embeddingModel = "text-embedding-3-small";

    /**
     * Whether to enable semantic search.
     */
    @Builder.Default
    boolean semanticSearchEnabled = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // Observability Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether metrics are enabled.
     */
    @Builder.Default
    boolean metricsEnabled = true;

    /**
     * Whether tracing is enabled.
     */
    @Builder.Default
    boolean tracingEnabled = true;

    /**
     * Log level for brain operations.
     */
    @Builder.Default
    String logLevel = "INFO";

    // ═══════════════════════════════════════════════════════════════════════════
    // Extension Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Additional configuration properties.
     */
    @Builder.Default
    Map<String, Object> extensions = Map.of();

    /**
     * Creates a default configuration.
     *
     * @return default configuration
     */
    public static BrainConfig defaults() {
        return BrainConfig.builder().build();
    }

    /**
     * Creates a minimal configuration for testing.
     *
     * @return minimal configuration
     */
    public static BrainConfig minimal() {
        return BrainConfig.builder()
                .learningEnabled(false)
                .reflexesEnabled(false)
                .tieredMemoryEnabled(false)
                .semanticSearchEnabled(false)
                .metricsEnabled(false)
                .tracingEnabled(false)
                .build();
    }

    /**
     * Gets an extension property.
     *
     * @param key the property key
     * @param <T> the expected type
     * @return the property value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key) {
        return (T) extensions.get(key);
    }

    /**
     * Gets an extension property with default.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @param <T> the expected type
     * @return the property value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key, T defaultValue) {
        Object value = extensions.get(key);
        return value != null ? (T) value : defaultValue;
    }
}
