/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.bridge;

import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * {@link BridgeAuthorizationService} backed by the platform OPA policy engine.
 *
 * <p>Evaluates the {@code dmos/authz} Rego policy for every bridge authorization
 * check. The policy input carries tenant identity, principal identity, the target
 * resource, and the requested action. If the policy engine is unavailable the
 * {@link com.ghatana.platform.pac.CircuitBreakingPolicyAsCodeEngine} decorator
 * (wrapping this delegate) will fail closed — denying the request — so callers
 * should always wrap this adapter with the circuit breaker for production use.</p>
 *
 * <p>Policy name used: {@code "dmos/authz"} (maps to OPA path {@code /v1/data/dmos/authz}).</p>
 *
 * @doc.type class
 * @doc.purpose OPA-backed authorization service for DMOS kernel bridge calls
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class OpaAuthorizationService implements BridgeAuthorizationService {

    private static final Logger LOG = LoggerFactory.getLogger(OpaAuthorizationService.class);
    private static final String POLICY_NAME = "dmos/authz";

    private final PolicyAsCodeEngine policyEngine;

    /**
     * Creates a new OpaAuthorizationService.
     *
     * @param policyEngine the policy engine to delegate to (should be wrapped in
     *                     {@link com.ghatana.platform.pac.CircuitBreakingPolicyAsCodeEngine})
     */
    public OpaAuthorizationService(PolicyAsCodeEngine policyEngine) {
        this.policyEngine = Objects.requireNonNull(policyEngine, "policyEngine must not be null");
    }

    @Override
    public Promise<Boolean> isAuthorized(BridgeContext context, String resource, String action) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(resource, "resource must not be null");
        Objects.requireNonNull(action, "action must not be null");

        Map<String, Object> input = Map.of(
            "tenantId", context.getTenantId(),
            "principalId", context.getPrincipalId(),
            "correlationId", context.getCorrelationId(),
            "resource", resource,
            "action", action
        );

        return policyEngine.evaluate(context.getTenantId(), POLICY_NAME, input)
            .map(result -> {
                if (!result.allowed()) {
                    LOG.info("[DMOS][OPA] Authorization denied: tenant={} principal={} resource={} action={} reasons={}",
                        context.getTenantId(), context.getPrincipalId(),
                        resource, action, result.reasons());
                } else {
                    LOG.debug("[DMOS][OPA] Authorization granted: tenant={} principal={} resource={} action={}",
                        context.getTenantId(), context.getPrincipalId(), resource, action);
                }
                return result.allowed();
            })
            .mapException(ex -> {
                // Circuit breaker or transport failure — fail closed
                LOG.error("[DMOS][OPA] Policy evaluation failed, denying request: resource={} action={} error={}",
                    resource, action, ex.getMessage(), ex);
                return ex;
            });
    }
}
