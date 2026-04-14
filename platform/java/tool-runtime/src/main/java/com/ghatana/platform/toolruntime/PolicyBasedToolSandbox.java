/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Policy-gated {@link ToolSandbox} that evaluates every tool invocation against
 * the {@link PolicyAsCodeEngine} before delegating to a wrapped sandbox.
 *
 * <p>Policy name: {@code "tool_execution_policy"}.
 * Input passed to the policy:
 * <pre>{@code
 * {
 *   "tenantId":  "<tenant>",
 *   "agentId":   "<agent>",
 *   "toolName":  "<tool>",
 *   "inputKeys": [<keys of the input map>]
 * }
 * }</pre>
 *
 * <p>If the policy denies, an {@link IllegalArgumentException} is returned
 * as a failed promise so the calling agent can detect and log the denial without
 * crashing the event-loop.
 *
 * @doc.type class
 * @doc.purpose Policy-gated tool sandbox that enforces tool execution policies
 * @doc.layer platform
 * @doc.pattern Decorator
 */
public final class PolicyBasedToolSandbox implements ToolSandbox {

    private static final Logger log = LoggerFactory.getLogger(PolicyBasedToolSandbox.class);
    private static final String POLICY_NAME = "tool_execution_policy";

    private final PolicyAsCodeEngine policyEngine;
    private final ToolSandbox delegate;

    /**
     * @param policyEngine evaluates tool execution policies; never {@code null}
     * @param delegate     the underlying sandbox to delegate allowed calls to; never {@code null}
     */
    public PolicyBasedToolSandbox(PolicyAsCodeEngine policyEngine, ToolSandbox delegate) {
        this.policyEngine = Objects.requireNonNull(policyEngine, "policyEngine");
        this.delegate     = Objects.requireNonNull(delegate,     "delegate");
    }

    @Override
    public Promise<String> execute(String tenantId, String agentId, String toolName,
                                   Map<String, Object> input) {
        Objects.requireNonNull(tenantId,  "tenantId");
        Objects.requireNonNull(agentId,   "agentId");
        Objects.requireNonNull(toolName,  "toolName");
        Objects.requireNonNull(input,     "input");

        Map<String, Object> policyInput = Map.of(
            "tenantId",  tenantId,
            "agentId",   agentId,
            "toolName",  toolName,
            "inputKeys", input.keySet().stream().sorted().toList()
        );

        return policyEngine.evaluate(tenantId, POLICY_NAME, policyInput)
            .then((PolicyEvalResult result) -> {
                if (!result.allowed()) {
                    log.warn("[tool-sandbox] Denied: tenant={} agent={} tool={} reasons={}",
                        tenantId, agentId, toolName, result.reasons());
                    return Promise.ofException(new IllegalArgumentException(
                        "Tool execution denied by policy '%s': %s"
                            .formatted(result.policyName(), result.reasons())));
                }
                log.debug("[tool-sandbox] Allowed: tenant={} agent={} tool={}", tenantId, agentId, toolName);
                return delegate.execute(tenantId, agentId, toolName, input);
            });
    }
}
