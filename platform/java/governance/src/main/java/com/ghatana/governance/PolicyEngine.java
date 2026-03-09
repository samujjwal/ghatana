package com.ghatana.governance;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Policy engine for governance rules and compliance checking.
 * 
 * <p>This is a minimal implementation until the full governance module is migrated.</p>
 *
 * @doc.type interface
 * @doc.purpose Engine for evaluating governance policies against actions
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface PolicyEngine {
    
    /**
     * Evaluate a policy against provided context.
     * 
     * @param policyName The name of the policy to evaluate
     * @param context The context data for policy evaluation
     * @return Promise containing the evaluation result (true if policy passes)
     */
    @NotNull
    Promise<Boolean> evaluate(@NotNull String policyName, @NotNull Map<String, Object> context);
    
    /**
     * Check if a policy exists.
     */
    @NotNull
    Promise<Boolean> policyExists(@NotNull String policyName);
}
