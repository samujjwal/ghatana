/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.dispatch;

/**
 * The three execution tiers for agent invocations.
 *
 * @doc.type enum
 * @doc.purpose Execution tier classification
 * @doc.layer framework
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public enum ExecutionTier {

    /**
     * Tier-J: Java-Implemented. A registered {@code TypedAgent} bean
     * exists in the runtime registry matching the agent ID.
     * Direct method call, highest performance.
     */
    JAVA_IMPLEMENTED,

    /**
     * Tier-S: Service-Orchestrated. Agent definition has a PIPELINE generator
     * with delegation steps. Execution fans out to sub-agents recursively.
     */
    SERVICE_ORCHESTRATED,

    /**
     * Tier-L: LLM-Executed. Agent definition has an LLM generator step.
     * Execution builds a prompt from the template and calls the LLM provider.
     */
    LLM_EXECUTED,

    /**
     * Unresolvable: The agent ID was not found in any catalog or registry.
     */
    UNRESOLVABLE
}
