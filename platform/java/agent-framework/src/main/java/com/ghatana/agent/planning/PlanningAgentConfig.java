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

package com.ghatana.agent.planning;

import com.ghatana.agent.AgentConfig;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a {@link PlanningAgent}.
 *
 * <p>Controls how the agent decomposes goals, manages planning budget,
 * handles replanning on failure, and coordinates with sub-agents.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * PlanningAgentConfig config = PlanningAgentConfig.builder()
 *     .subtype(PlanningSubtype.REACT)
 *     .maxPlanningSteps(20)
 *     .stepTimeout(Duration.ofSeconds(30))
 *     .maxRetries(3)
 *     .enableReplanning(true)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Configuration for planning agents
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle reason
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
@Value
@Builder(toBuilder = true)
public class PlanningAgentConfig {

    /**
     * Planning strategy subtype.
     * Default: {@link PlanningSubtype#REACT} (most flexible for tool-using agents).
     */
    @Builder.Default
    PlanningSubtype subtype = PlanningSubtype.REACT;

    /**
     * Maximum number of reasoning/action steps in a single planning turn.
     * Prevents runaway planning loops. Default: 20.
     */
    @Builder.Default
    int maxPlanningSteps = 20;

    /**
     * Timeout for each individual step in the plan.
     * If a step exceeds this, it fails with {@code AgentResultStatus.TIMEOUT}.
     * Default: 30 seconds.
     */
    @Builder.Default
    Duration stepTimeout = Duration.ofSeconds(30);

    /**
     * Maximum retries for a failed step before the whole plan fails.
     * Default: 2.
     */
    @Builder.Default
    int maxStepRetries = 2;

    /**
     * Whether the agent may replan from the current state if a step fails.
     * If false, failure of any step aborts the whole plan.
     * Default: true.
     */
    @Builder.Default
    boolean enableReplanning = true;

    /**
     * Maximum replanning attempts (only relevant when {@link #enableReplanning} is true).
     * Default: 3.
     */
    @Builder.Default
    int maxReplanningAttempts = 3;

    /**
     * IDs of sub-agents this planning agent may delegate steps to.
     * Leave empty if the agent resolves sub-agents dynamically from the registry.
     */
    @Builder.Default
    List<String> delegateAgentIds = List.of();

    /**
     * Whether to capture intermediate plan steps as episodic memory.
     * Enables reflection and learning from past planning experiences.
     * Default: true.
     */
    @Builder.Default
    boolean capturePlanSteps = true;

    /**
     * Domain-specific parameters passed to the planner (e.g., HTN domain file,
     * BPMN workflow URI, custom cost functions).
     */
    @Builder.Default
    Map<String, Object> plannerParams = Map.of();

    /**
     * Creates a {@link PlanningAgentConfig} from a generic {@link AgentConfig}.
     * Reads well-known keys: {@code subtype}, {@code maxPlanningSteps},
     * {@code stepTimeoutMs}, {@code maxStepRetries}, {@code enableReplanning}.
     *
     * @param config the raw agent configuration
     * @return a typed PlanningAgentConfig
     */
    public static PlanningAgentConfig from(AgentConfig config) {
        Map<String, Object> props = config.getProperties();
        PlanningAgentConfig.PlanningAgentConfigBuilder builder = PlanningAgentConfig.builder();

        Object subtype = props.get("subtype");
        if (subtype instanceof String s) {
            builder.subtype(PlanningSubtype.valueOf(s.toUpperCase()));
        }

        Object maxSteps = props.get("maxPlanningSteps");
        if (maxSteps instanceof Number n) {
            builder.maxPlanningSteps(n.intValue());
        }

        Object stepTimeout = props.get("stepTimeoutMs");
        if (stepTimeout instanceof Number n) {
            builder.stepTimeout(Duration.ofMillis(n.longValue()));
        }

        Object maxRetries = props.get("maxStepRetries");
        if (maxRetries instanceof Number n) {
            builder.maxStepRetries(n.intValue());
        }

        Object replan = props.get("enableReplanning");
        if (replan instanceof Boolean b) {
            builder.enableReplanning(b);
        }

        return builder.build();
    }
}
