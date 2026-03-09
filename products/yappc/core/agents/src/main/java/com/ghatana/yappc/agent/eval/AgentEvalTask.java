/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.yappc.agent.eval;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * A single evaluation task in the golden test set. Represents an input/expected-output
 * pair for evaluating agent behavior against known-good outcomes.
 *
 * @doc.type record
 * @doc.purpose Evaluation task definition for agent evaluation flywheel
 * @doc.layer product
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
@Value
@Builder
public class AgentEvalTask {

    /** Unique identifier for this eval case. */
    String id;

    /** Human-readable description of what is being tested. */
    String description;

    /** The agent ID to evaluate. */
    String agentId;

    /** Evaluation category: unit, integration, regression, safety, cost, drift. */
    String category;

    /** The input payload to send to the agent. */
    Object input;

    /** Expected output (for exact-match or structural comparison). */
    Object expectedOutput;

    /** Assertions to run on the agent result. */
    @Builder.Default
    List<EvalAssertion> assertions = List.of();

    /** Maximum allowed latency in milliseconds. */
    @Builder.Default
    long maxLatencyMs = 30000;

    /** Maximum allowed cost (in model tokens or dollars). */
    @Builder.Default
    double maxCost = 0.0;

    /** Tags for filtering eval subsets. */
    @Builder.Default
    List<String> tags = List.of();

    /** Additional context to inject. */
    @Builder.Default
    Map<String, Object> context = Map.of();
}
