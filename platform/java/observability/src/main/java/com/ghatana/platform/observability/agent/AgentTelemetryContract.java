/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.platform.observability.agent;

/**
 * Internal semantic convention constants for agent telemetry.
 *
 * <p>Attribute names follow the {@code ghatana.agent.*} namespace.
 * These constants define the canonical span names and attribute keys used
 * across all agent runtimes in the platform.
 *
 * <p>Sensitive data fields (prompts, payloads, memory fragments) are intentionally
 * <strong>not</strong> defined here. Raw content must never be set as span attributes.
 * Use {@link TelemetryRedactionFilter} when integrating spans from external sources.
 *
 * @see TelemetryRedactionFilter
 * @doc.type class
 * @doc.purpose Internal semantic convention constants for agent telemetry.
 *             Attribute names follow the ghatana.agent.* namespace.
 *             Version: 1.0 (2026-04-06)
 * @doc.layer platform
 * @doc.pattern Contract
 */
public final class AgentTelemetryContract {

    /** Semantic version of this contract. Used to tag spans for schema evolution. */
    public static final String VERSION = "1.0";

    /** Namespace prefix for all agent span names and attribute keys. */
    public static final String SPAN_PREFIX = "ghatana.agent";

    // ── Span names (11 standard lifecycle phases) ────────────────────────────

    /** Root span that wraps the entire agent run. */
    public static final String SPAN_RUN_START          = "ghatana.agent.run.start";

    /** Context (memory + knowledge) retrieval sub-span. */
    public static final String SPAN_CONTEXT_RETRIEVAL  = "ghatana.agent.context.retrieval";

    /** Planner/reasoning invocation sub-span. */
    public static final String SPAN_PLANNER_INVOKE     = "ghatana.agent.planner.invoke";

    /** Tool execution sub-span (one per tool call). */
    public static final String SPAN_TOOL_EXECUTE       = "ghatana.agent.tool.execute";

    /** Sub-agent delegation sub-span. */
    public static final String SPAN_SUB_AGENT_DELEGATE = "ghatana.agent.delegate";

    /** Policy evaluation sub-span. */
    public static final String SPAN_POLICY_EVAL        = "ghatana.agent.policy.eval";

    /** Approval request sub-span (human-in-the-loop gate). */
    public static final String SPAN_APPROVAL_REQUEST   = "ghatana.agent.approval.request";

    /** Memory write sub-span. */
    public static final String SPAN_MEMORY_WRITE       = "ghatana.agent.memory.write";

    /** Evaluation gate sub-span (quality/safety checks). */
    public static final String SPAN_EVAL_GATE          = "ghatana.agent.eval.gate";

    /** External system commit sub-span (durable side effect). */
    public static final String SPAN_EXTERNAL_COMMIT    = "ghatana.agent.external.commit";

    /** Root span completion marker. */
    public static final String SPAN_RUN_COMPLETE       = "ghatana.agent.run.complete";

    // ── Attribute keys ────────────────────────────────────────────────────────

    /** Logical agent identifier (e.g. {@code "fraud-detector-v1"}). */
    public static final String ATTR_AGENT_ID           = "ghatana.agent.id";

    /** Agent release record identifier for audit correlation. */
    public static final String ATTR_AGENT_RELEASE_ID   = "ghatana.agent.release_id";

    /** Policy pack identifier applied to this run. */
    public static final String ATTR_POLICY_PACK_ID     = "ghatana.agent.policy_pack_id";

    /** Tenant identifier scoping this run. */
    public static final String ATTR_TENANT_ID          = "ghatana.agent.tenant_id";

    /** Correlation ID propagated from the caller for cross-service tracing. */
    public static final String ATTR_CORRELATION_ID     = "ghatana.agent.correlation_id";

    /** Action class of the tool executed (e.g. READ, WRITE, EXECUTE). */
    public static final String ATTR_ACTION_CLASS       = "ghatana.agent.action_class";

    /** Tool identifier used in a tool execution span. */
    public static final String ATTR_TOOL_ID            = "ghatana.agent.tool.id";

    /** Version of this telemetry contract schema. Set on root run spans. */
    public static final String ATTR_TELEMETRY_VERSION  = "ghatana.agent.telemetry.version";

    /** Explanation contract version declared in the agent release. */
    public static final String ATTR_EXPLANATION_CONTRACT_VERSION = "ghatana.agent.explanation.version";

    /** Redaction profile ID from the agent release governing this run. */
    public static final String ATTR_REDACTION_PROFILE_ID = "ghatana.agent.redaction_profile_id";

    /** Data access decision (ALLOW/DENY) for context hydration gate. */
    public static final String ATTR_DATA_ACCESS_DECISION = "ghatana.agent.data_access.decision";

    /** Policy evaluation result (ALLOW/DENY). */
    public static final String ATTR_POLICY_DECISION    = "ghatana.agent.policy.decision";

    /** Number of context items retrieved in a context retrieval span. */
    public static final String ATTR_CONTEXT_ITEM_COUNT = "ghatana.agent.context.item_count";

    /** Memory class/namespace for memory write spans. */
    public static final String ATTR_MEMORY_CLASS       = "ghatana.agent.memory.class";

    private AgentTelemetryContract() { /* constants class — not instantiable */ }
}
