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

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Describes the capabilities available in the brain.
 *
 * <p>This is used for feature discovery and API introspection.
 *
 * @doc.type record
 * @doc.purpose Capability discovery
 * @doc.layer core
 * @doc.pattern Capabilities
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder
public class BrainCapabilities {

    /**
     * Brain version.
     */
    @Builder.Default
    String version = "1.0.0";

    /**
     * Whether attention scoring is available.
     */
    @Builder.Default
    boolean attentionScoring = true;

    /**
     * Whether global workspace is available.
     */
    @Builder.Default
    boolean globalWorkspace = true;

    /**
     * Whether tiered memory is available.
     */
    @Builder.Default
    boolean tieredMemory = true;

    /**
     * Whether vector memory is available.
     */
    @Builder.Default
    boolean vectorMemory = true;

    /**
     * Whether semantic search is available.
     */
    @Builder.Default
    boolean semanticSearch = true;

    /**
     * Whether pattern matching is available.
     */
    @Builder.Default
    boolean patternMatching = true;

    /**
     * Whether pattern learning is available.
     */
    @Builder.Default
    boolean patternLearning = true;

    /**
     * Whether reflex processing is available.
     */
    @Builder.Default
    boolean reflexProcessing = true;

    /**
     * Whether autonomy control is available.
     */
    @Builder.Default
    boolean autonomyControl = true;

    /**
     * Whether feedback collection is available.
     */
    @Builder.Default
    boolean feedbackCollection = true;

    /**
     * Supported data record types.
     */
    @Builder.Default
    Set<String> supportedRecordTypes = Set.of(
            "EventRecord", "EntityRecord", "TimeSeriesRecord");

    /**
     * Supported pattern types.
     */
    @Builder.Default
    Set<String> supportedPatternTypes = Set.of(
            "STRUCTURAL", "TEMPORAL", "BEHAVIORAL", "CAUSAL", "ANOMALY");

    /**
     * Supported action types.
     */
    @Builder.Default
    Set<String> supportedActionTypes = Set.of(
            "ALERT", "WEBHOOK", "PUBLISH", "EXECUTE", "LOG", "SUPPRESS");

    /**
     * Embedding models available.
     */
    @Builder.Default
    List<String> embeddingModels = List.of("text-embedding-3-small");

    /**
     * Maximum embedding dimension.
     */
    @Builder.Default
    int maxEmbeddingDimension = 1536;

    /**
     * Maximum batch size.
     */
    @Builder.Default
    int maxBatchSize = 1000;

    /**
     * Extension capabilities.
     */
    @Builder.Default
    Map<String, Object> extensions = Map.of();

    /**
     * Creates default capabilities.
     *
     * @return default capabilities
     */
    public static BrainCapabilities defaults() {
        return BrainCapabilities.builder().build();
    }

    /**
     * Creates minimal capabilities (for testing).
     *
     * @return minimal capabilities
     */
    public static BrainCapabilities minimal() {
        return BrainCapabilities.builder()
                .vectorMemory(false)
                .semanticSearch(false)
                .patternLearning(false)
                .autonomyControl(false)
                .build();
    }

    /**
     * Checks if a capability is available.
     *
     * @param capability capability name
     * @return true if available
     */
    public boolean hasCapability(String capability) {
        return switch (capability.toLowerCase()) {
            case "attention", "attention_scoring" -> attentionScoring;
            case "workspace", "global_workspace" -> globalWorkspace;
            case "tiered_memory", "memory" -> tieredMemory;
            case "vector_memory", "vector" -> vectorMemory;
            case "semantic_search", "search" -> semanticSearch;
            case "pattern_matching", "patterns" -> patternMatching;
            case "pattern_learning", "learning" -> patternLearning;
            case "reflex", "reflexes" -> reflexProcessing;
            case "autonomy" -> autonomyControl;
            case "feedback" -> feedbackCollection;
            default -> extensions.containsKey(capability);
        };
    }
}
