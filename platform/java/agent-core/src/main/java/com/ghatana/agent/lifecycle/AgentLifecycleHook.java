/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.lifecycle;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * @doc.type interface
 * @doc.purpose Optional observer for governed lifecycle phase traces
 * @doc.layer agent-core
 * @doc.pattern FunctionalInterface
 */
/**
 * Optional observer for governed lifecycle phase traces.
 */
@FunctionalInterface
public interface AgentLifecycleHook {
    @NotNull
    Promise<Void> onPhase(@NotNull AgentPhaseTrace phaseTrace, @NotNull AgentContext context);
}
