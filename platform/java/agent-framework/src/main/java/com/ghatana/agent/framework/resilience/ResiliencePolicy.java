/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.framework.resilience;

import java.time.Duration;

/**
 * Immutable policy record that governs retry, timeout, and backoff behavior for agent execution.
 *
 * <p>A {@code ResiliencePolicy} is loaded from the agent's YAML definition and passed
 * to {@code executeWithPolicy()} on {@link com.ghatana.agent.framework.runtime.AgentTurnPipeline}.
 * When no policy is provided, {@link #defaultPolicy()} applies.
 *
 * <h2>Example agent YAML snippet</h2>
 * <pre>{@code
 * resilience:
 *   max_attempts: 3
 *   backoff_ms: 500
 *   timeout_ms: 30000
 * }</pre>
 *
 * @param maxAttempts maximum number of execution attempts (1 = no retry)
 * @param backoffMs   initial backoff in milliseconds between retries (exponential)
 * @param timeoutMs   per-attempt timeout in milliseconds
 *
 * @doc.type record
 * @doc.purpose Configures retry, timeout, and backoff for agent execution
 * @doc.layer framework
 * @doc.pattern ValueObject
 */
public record ResiliencePolicy(int maxAttempts, long backoffMs, long timeoutMs) {

    /**
     * Default policy: one attempt, no retry, 30-second timeout.
     * Applied when no explicit policy is configured for an agent.
     *
     * @return the default resilience policy
     */
    public static ResiliencePolicy defaultPolicy() {
        return new ResiliencePolicy(1, 0L, 30_000L);
    }

    /**
     * Creates a policy with aggressive retry settings for non-deterministic agents.
     *
     * @return a policy with 3 attempts, 500ms exponential backoff, and 60s timeout
     */
    public static ResiliencePolicy retrying() {
        return new ResiliencePolicy(3, 500L, 60_000L);
    }

    /**
     * Returns the per-attempt timeout as a {@link Duration}.
     *
     * @return timeout duration
     */
    public Duration timeout() {
        return Duration.ofMillis(timeoutMs);
    }

    /**
     * Returns {@code true} if this policy permits more than one attempt.
     *
     * @return {@code true} when retries are enabled
     */
    public boolean isRetrying() {
        return maxAttempts > 1;
    }
}
