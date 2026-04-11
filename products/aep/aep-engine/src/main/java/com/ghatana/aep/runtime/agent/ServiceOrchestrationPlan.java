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
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Execution plan for Tier-S (Service-Orchestrated) agents.
 *
 * <p>Reads the delegation chain from the agent YAML definition and
 * dispatches to sub-agents recursively via the {@link AgentDispatcher}.
 * Supports sequential, parallel, and conditional execution modes.
 *
 * @doc.type interface
 * @doc.purpose Tier-S (delegation chain) execution strategy
 * @doc.layer framework
 * @doc.pattern Strategy, Composite
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public interface ServiceOrchestrationPlan {

    /**
     * Executes an agent via delegation to sub-agents.
     *
     * @param entry      the catalog agent entry with delegation definition
     * @param input      the input payload
     * @param ctx        execution context
     * @param dispatcher the parent dispatcher for recursive sub-agent invocation
     * @return a Promise of the aggregated result
     */
    @NotNull
    Promise<AgentResult<Object>> execute(
            @NotNull CatalogAgentEntry entry,
            @NotNull Object input,
            @NotNull AgentContext ctx,
            @NotNull AgentDispatcher dispatcher);
}
