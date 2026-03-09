package com.ghatana.datacloud.infrastructure.policy;

import com.ghatana.datacloud.entity.policy.PolicyDecision;
import com.ghatana.datacloud.entity.policy.PolicyEngine;
import com.ghatana.datacloud.entity.policy.PolicyValidationResult;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * OPA (Open Policy Agent) implementation of PolicyEngine.
 *
 * <p><b>Purpose</b><br>
 * Provides integration with Open Policy Agent for policy evaluation.
 * Evaluates Rego policies against input data via OPA REST API or embedded engine.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OpaPolicyEngine engine = new OpaPolicyEngine(
 *     opaClient,
 *     metrics,
 *     "http://opa:8181/v1/data"
 * );
 *
 * Map<String, Object> input = Map.of(
 *     "tenantId", "tenant-123",
 *     "operation", "schema_change"
 * );
 *
 * PolicyDecision decision = runPromise(() -> 
 *     engine.evaluate("schema_change", input)
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Adapter in infrastructure layer
 * - Implements PolicyEngine port (domain)
 * - Wraps OPA client library (e.g., okhttp, activej-http)
 * - Emits metrics for policy evaluations
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - can be shared across threads.
 *
 * <p><b>Configuration</b><br>
 * - OPA endpoint URL
 * - Connection timeout (default 5s)
 * - Retry policy (default 3 retries with backoff)
 *
 * @see PolicyEngine
 * @see PolicyDecision
 * @doc.type class
 * @doc.purpose OPA adapter for policy evaluation
 * @doc.layer product
 * @doc.pattern Adapter (Infrastructure)
 */
public class OpaPolicyEngine implements PolicyEngine {

    private static final Logger logger = LoggerFactory.getLogger(OpaPolicyEngine.class);

    private final OpaClient opaClient;
    private final MetricsCollector metrics;
    private final String opaEndpoint;

    /**
     * Creates an OPA policy engine.
     *
     * @param opaClient the OPA HTTP client (required)
     * @param metrics the metrics collector (required)
     * @param opaEndpoint the OPA API endpoint (required, e.g., "http://opa:8181/v1/data")
     * @throws NullPointerException if any parameter is null
     */
    public OpaPolicyEngine(
            OpaClient opaClient,
            MetricsCollector metrics,
            String opaEndpoint) {
        this.opaClient = Objects.requireNonNull(opaClient, "OpaClient must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.opaEndpoint = Objects.requireNonNull(opaEndpoint, "OPA endpoint must not be null");
    }

    @Override
    public Promise<PolicyDecision> evaluate(String policyName, Map<String, Object> input) {
        Objects.requireNonNull(policyName, "Policy name must not be null");
        Objects.requireNonNull(input, "Input must not be null");

        long startTime = System.currentTimeMillis();

        // Build OPA request
        Map<String, Object> opaRequest = Map.of("input", input);

        // Call OPA API
        return opaClient.evaluate(opaEndpoint, policyName, opaRequest)
            .map(opaResponse -> {
                // Parse OPA response
                boolean allowed = (boolean) opaResponse.getOrDefault("allow", false);
                String reason = (String) opaResponse.getOrDefault("reason", "No reason provided");
                
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) opaResponse.getOrDefault("metadata", Map.of());

                return allowed 
                    ? PolicyDecision.allow(reason, metadata)
                    : PolicyDecision.deny(reason, metadata);
            })
            .whenComplete((decision, ex) -> {
                long duration = System.currentTimeMillis() - startTime;
                
                if (ex == null) {
                    metrics.getMeterRegistry()
                        .timer("policy.evaluation.duration",
                            "policy", policyName,
                            "allowed", String.valueOf(decision.isAllowed()))
                        .record(Duration.ofMillis(duration));
                    
                    logger.debug("Policy evaluated: policy={}, allowed={}, duration={}ms",
                        policyName, decision.isAllowed(), duration);
                } else {
                    metrics.incrementCounter("policy.evaluation.error",
                        "policy", policyName,
                        "error", ex.getClass().getSimpleName());
                    
                    logger.error("Policy evaluation failed: policy={}, duration={}ms",
                        policyName, duration, ex);
                }
            });
    }

    @Override
    public Promise<Void> reloadPolicies() {
        logger.info("Reloading OPA policies from endpoint: {}", opaEndpoint);
        
        return opaClient.reloadPolicies(opaEndpoint)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("policy.reload.success");
                    logger.info("OPA policies reloaded successfully");
                } else {
                    metrics.incrementCounter("policy.reload.error",
                        "error", ex.getClass().getSimpleName());
                    logger.error("Failed to reload OPA policies", ex);
                }
            });
    }

    @Override
    public Promise<PolicyValidationResult> validatePolicy(String policyName, String policyDefinition) {
        Objects.requireNonNull(policyName, "Policy name must not be null");
        Objects.requireNonNull(policyDefinition, "Policy definition must not be null");

        logger.debug("Validating policy: {}", policyName);

        return opaClient.validatePolicy(opaEndpoint, policyName, policyDefinition)
            .map(validationResponse -> {
                boolean valid = (boolean) validationResponse.getOrDefault("valid", false);
                
                @SuppressWarnings("unchecked")
                List<String> errors = (List<String>) validationResponse.getOrDefault("errors", List.of());

                return valid 
                    ? PolicyValidationResult.success()
                    : PolicyValidationResult.invalid(errors);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("policy.validation.completed",
                        "policy", policyName,
                        "valid", String.valueOf(result.isValid()));
                    
                    if (!result.isValid()) {
                        logger.warn("Policy validation failed: policy={}, errors={}",
                            policyName, result.getErrors());
                    }
                } else {
                    metrics.incrementCounter("policy.validation.error",
                        "policy", policyName,
                        "error", ex.getClass().getSimpleName());
                    logger.error("Policy validation error: policy={}", policyName, ex);
                }
            });
    }
}
