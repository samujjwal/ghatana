/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.release.AgentRelease;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Selects the appropriate execution mode based on task classification, mastery, and context.
 *
 * @doc.type interface
 * @doc.purpose Agent mode selector for execution mode decision
 * @doc.layer agent-core
 * @doc.pattern Selector
 */
public interface AgentModeSelector {

    /**
     * Decides which execution mode to use for a task.
     *
     * @param definition agent definition
     * @param release agent release
     * @param context agent context
     * @param env environment fingerprint
     * @param mastery optional mastery item for the skill
     * @param input agent input
     * @return promise of mode decision
     */
    @NotNull
    Promise<ModeDecision> decide(
            @NotNull AgentDefinition definition,
            @NotNull AgentRelease release,
            @NotNull AgentContext context,
            @NotNull EnvironmentFingerprint env,
            @NotNull Optional<MasteryItem> mastery,
            @NotNull Object input
    );
}
