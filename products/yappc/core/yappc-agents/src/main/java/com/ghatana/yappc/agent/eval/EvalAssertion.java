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

/**
 * A single assertion to run against an agent's output during evaluation.
 *
 * @doc.type record
 * @doc.purpose Evaluation assertion for agent output verification
 * @doc.layer product
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
@Value
@Builder
public class EvalAssertion {

    /**
     * Assertion type:
     * <ul>
     *   <li>EXACT_MATCH — output equals expected</li>
     *   <li>CONTAINS — output contains substring</li>
     *   <li>JSON_SCHEMA — output validates against JSON schema</li>
     *   <li>REGEX — output matches regex pattern</li>
     *   <li>CONFIDENCE_MIN — result confidence >= threshold</li>
     *   <li>LATENCY_MAX — result latency <= threshold</li>
     *   <li>STATUS — result status equals expected</li>
     *   <li>CUSTOM — delegate to named assertion function</li>
     * </ul>
     */
    String type;

    /** The expected value, pattern, or threshold depending on type. */
    String expected;

    /** Human-readable description of what this assertion checks. */
    String description;

    /** JSON path to extract the value from the output (null = whole output). */
    String jsonPath;
}
