/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.dispatch.tier;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Execution plan for Tier-L (LLM-Executed) agents.
 *
 * <p>Builds a prompt from the agent YAML's generator template, injects
 * input and context variables, calls the configured LLM provider, and
 * parses the structured response into an {@link AgentResult}.
 *
 * @doc.type interface
 * @doc.purpose Tier-L (LLM) execution strategy
 * @doc.layer framework
 * @doc.pattern Strategy
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public interface LlmExecutionPlan {

    /**
     * Executes an agent via LLM prompt invocation.
     *
     * @param entry the catalog agent entry with generator/prompt definition
     * @param input the input payload (injected into prompt as {@code {{input}}})
     * @param ctx   execution context (tenant, project, trace)
     * @return a Promise of the LLM-generated result
     */
    @NotNull
    Promise<AgentResult<Object>> execute(
            @NotNull CatalogAgentEntry entry,
            @NotNull Object input,
            @NotNull AgentContext ctx);
}
