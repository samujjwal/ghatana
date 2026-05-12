/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.environment;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Provider for generating environment fingerprints from agent context and input.
 *
 * <p>Implementations analyze the agent's context and input to extract version
 * information about dependencies, tools, runtimes, and frameworks.
 *
 * @doc.type interface
 * @doc.purpose Provider for environment fingerprint generation
 * @doc.layer agent-core
 * @doc.pattern Provider
 */
public interface EnvironmentFingerprintProvider {

    /**
     * Generates an environment fingerprint from the agent context and input.
     *
     * @param ctx agent context
     * @param input agent input
     * @return promise of environment fingerprint
     */
    @NotNull
    Promise<EnvironmentFingerprint> fingerprint(
            @NotNull AgentContext ctx,
            @NotNull Object input
    );
}
