/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.lifecycle;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Optional observer for governed lifecycle phase traces.
 */
@FunctionalInterface
public interface AgentLifecycleHook {
    @NotNull
    Promise<Void> onPhase(@NotNull AgentPhaseTrace phaseTrace, @NotNull AgentContext context);
}
