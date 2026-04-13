package com.ghatana.datacloud.infrastructure.policy;

import com.ghatana.datacloud.entity.policy.PolicyDecision;
import com.ghatana.datacloud.entity.policy.PolicyEngine;
import com.ghatana.datacloud.entity.policy.PolicyValidationResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * OPA (Open Policy Agent) implementation of PolicyEngine.
 *
 * <p><b>Purpose</b><br>
 * Provides integration with Open Policy Agent for policy evaluation.
 * Evaluates Rego policies against input data via OPA REST API.
 * Delegates core evaluate logic to the platform {@link PolicyAsCodeEngine}.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OpaPolicyEngine engine = new OpaPolicyEngine(
 *     platformOpaClient,   // platform:java:policy-as-code OpaClient
 *     metrics,
 *     "http://opa:8181",   // OPA base URL (no path suffix)
 *     executor             // blocking executor for HTTP
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
 * - Delegates evaluate() to platform:java:policy-as-code PolicyAsCodeEngine
 * - Emits metrics for policy evaluations
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - can be shared across threads.
 *
 * @see PolicyEngine
 * @see PolicyDecision
 * @see PolicyAsCodeEngine
 * @doc.type class
 * @doc.purpose OPA adapter for policy evaluation
 * @doc.layer product
 * @doc.pattern Adapter (Infrastructure)
 */
public class OpaPolicyEngine implements PolicyEngine {

    private static final Logger logger = LoggerFactory.getLogger(OpaPolicyEngine.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final PolicyAsCodeEngine policyEngine;
    private final MetricsCollector metrics;
    private final String opaBaseUrl;
    private final Executor executor;
    private final HttpClient httpClient;

    /**
     * Creates an OPA policy engine.
     *
     * @param policyEngine platform PolicyAsCodeEngine implementation (required)
     * @param metrics      the metrics collector (required)
     * @param opaBaseUrl   OPA server base URL without path (e.g., {@code http://opa:8181}) (required)
     * @param executor     blocking executor for HTTP calls — must NOT be the event-loop
     * @throws NullPointerException if policyEngine, metrics, or opaBaseUrl is null
     */
    public OpaPolicyEngine(
            PolicyAsCodeEngine policyEngine,
            MetricsCollector metrics,
            String opaBaseUrl,
            Executor executor) {
        this.policyEngine = Objects.requireNonNull(policyEngine, "PolicyAsCodeEngine must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.opaBaseUrl = Objects.requireNonNull(opaBaseUrl, "OPA base URL must not be null").stripTrailing();
        this.executor = Objects.requireNonNull(executor, "Executor must not be null");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
    }

    @Override
    public Promise<PolicyDecision> evaluate(String policyName, Map<String, Object> input) {
        Objects.requireNonNull(policyName, "Policy name must not be null");
        Objects.requireNonNull(input, "Input must not be null");

        long startTime = System.currentTimeMillis();
        String tenantId = (String) input.getOrDefault("tenantId", "default");

        // Delegate to platform PolicyAsCodeEngine — fail-closed on exception
        return policyEngine.evaluate(tenantId, policyName, input)
            .then(
                evalResult -> {
                    String reason = evalResult.reasons().isEmpty()
                        ? "OPA evaluation completed"
                        : String.join("; ", evalResult.reasons());
                    Map<String, Object> metadata = Map.of("riskScore", evalResult.riskScore());
                    return Promise.of(evalResult.allowed()
                        ? PolicyDecision.allow(reason, metadata)
                        : PolicyDecision.deny(reason, metadata));
                },
                ex -> {
                    // DC3-H3: Fail-closed — OPA unavailability returns DENY, never propagates exception.
                    logger.warn("OPA evaluation failed for policy={}, applying fail-closed deny ({})",
                        policyName, ex.getMessage());
                    metrics.incrementCounter("policy.evaluation.unavailable",
                        "policy", policyName,
                        "error", ex.getClass().getSimpleName());
                    return Promise.of(PolicyDecision.deny(
                        "OPA_UNAVAILABLE: " + ex.getClass().getSimpleName(), Map.of()));
                }
            )
            .whenComplete((decision, ex) -> {
                long duration = System.currentTimeMillis() - startTime;

                if (ex == null && decision != null) {
                    metrics.getMeterRegistry()
                        .timer("policy.evaluation.duration",
                            "policy", policyName,
                            "allowed", String.valueOf(decision.isAllowed()))
                        .record(Duration.ofMillis(duration));

                    logger.debug("Policy evaluated: policy={}, allowed={}, duration={}ms",
                        policyName, decision.isAllowed(), duration);
                } else if (ex != null) {
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
        logger.info("Reloading OPA policies via: {}", opaBaseUrl);

        return Promise.ofBlocking(executor, () -> {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(opaBaseUrl + "/v1/policies/reload"))
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OPA reload failed with HTTP " + response.statusCode());
            }
            return (Void) null;
        })
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

        return Promise.ofBlocking(executor, () -> {
            String body = """
                {"policy":"%s","definition":"%s"}
                """.formatted(
                    policyName.replace("\"", "\\\""),
                    policyDefinition.replace("\\", "\\\\").replace("\"", "\\\"")
                );
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(opaBaseUrl + "/v1/validate"))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            boolean valid = responseBody.contains("\"valid\":true");
            if (valid) {
                return PolicyValidationResult.success();
            }
            // Extract first error message from response if present
            List<String> errors = new ArrayList<>();
            if (responseBody.contains("\"errors\"")) {
                errors.add("OPA validation failed: check policy syntax");
            }
            return PolicyValidationResult.invalid(errors);
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
