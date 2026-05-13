/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Selector for agent version during evaluation.
 *
 * @doc.type interface
 * @doc.purpose Version selection for evaluation
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface VersionSelector {

    /**
     * Selects the appropriate agent version for evaluation.
     *
     * @param artifactId artifact ID
     * @param versionScope version scope constraint
     * @param environmentConfig environment configuration
     * @return selected version
     */
    @NotNull
    String selectVersion(
            @NotNull String artifactId,
            @NotNull String versionScope,
            @NotNull Map<String, String> environmentConfig);
}
