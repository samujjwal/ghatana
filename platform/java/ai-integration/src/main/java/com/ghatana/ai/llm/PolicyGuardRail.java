/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.TokenStream;
import com.ghatana.ai.llm.ToolCallResult;
import com.ghatana.ai.llm.ToolDefinition;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Policy guard rail that evaluates policies before LLM calls.
 *
 * <p>Wraps an LLMGateway and intercepts all LLM requests to evaluate
 * policies via OPA before allowing the request to proceed. If policy evaluation
 * denies the request, the LLM call is blocked and a PolicyDeniedException is returned.</p>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * LLMGateway gateway = DefaultLLMGateway.builder()...build();
 * PolicyAsCodeEngine opaClient = new OpaClient("http://opa:8181", executor);
 * LLMGateway guardedGateway = new PolicyGuardRail(gateway, opaClient, "llm.guard");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Policy guard rail for LLM calls using OPA
 * @doc.layer infrastructure
 * @doc.pattern Decorator
 */
public class PolicyGuardRail implements LLMGateway {

    private static final Logger logger = LoggerFactory.getLogger(PolicyGuardRail.class);

    private final LLMGateway delegate;
    private final PolicyAsCodeEngine policyEngine;
    private final String defaultPolicyPath;

    /**
     * Creates a policy guard rail.
     *
     * @param delegate the underlying LLM gateway
     * @param policyEngine the policy evaluation engine (OPA)
     * @param defaultPolicyPath the default OPA policy path to evaluate
     */
    public PolicyGuardRail(LLMGateway delegate, PolicyAsCodeEngine policyEngine, String defaultPolicyPath) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.policyEngine = Objects.requireNonNull(policyEngine, "policyEngine cannot be null");
        this.defaultPolicyPath = Objects.requireNonNull(defaultPolicyPath, "defaultPolicyPath cannot be null");
    }

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        return evaluatePolicy(request)
                .then(result -> {
                    if (result.allowed()) {
                        return delegate.complete(request);
                    } else {
                        return Promise.ofException(new PolicyDeniedException(
                                "LLM request denied by policy: " + String.join(", ", result.reasons()),
                                result));
                    }
                });
    }

    @Override
    public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) {
        return evaluatePolicy(request)
                .then(result -> {
                    if (result.allowed()) {
                        return delegate.completeWithTools(request, tools);
                    } else {
                        return Promise.ofException(new PolicyDeniedException(
                                "LLM request with tools denied by policy: " + String.join(", ", result.reasons()),
                                result));
                    }
                });
    }

    @Override
    public Promise<CompletionResult> continueWithToolResults(
            CompletionRequest request,
            List<ToolCallResult> toolResults
    ) {
        return evaluatePolicy(request)
                .then(result -> {
                    if (result.allowed()) {
                        return delegate.continueWithToolResults(request, toolResults);
                    } else {
                        return Promise.ofException(new PolicyDeniedException(
                                "LLM continuation denied by policy: " + String.join(", ", result.reasons()),
                                result));
                    }
                });
    }

    @Override
    public Promise<EmbeddingResult> embed(String text) {
        // Embeddings typically don't require policy checks (read-only)
        return delegate.embed(text);
    }

    @Override
    public Promise<List<EmbeddingResult>> embedBatch(List<String> texts) {
        return delegate.embedBatch(texts);
    }

    @Override
    public Promise<TokenStream> stream(CompletionRequest request) {
        return evaluatePolicy(request)
                .then(result -> {
                    if (result.allowed()) {
                        return delegate.stream(request);
                    } else {
                        return Promise.ofException(new PolicyDeniedException(
                                "LLM stream request denied by policy: " + String.join(", ", result.reasons()),
                                result));
                    }
                });
    }

    @Override
    public MetricsCollector getMetrics() {
        return delegate.getMetrics();
    }

    @Override
    public String getDefaultProvider() {
        return delegate.getDefaultProvider();
    }

    @Override
    public List<String> getAvailableProviders() {
        return delegate.getAvailableProviders();
    }

    @Override
    public boolean isProviderAvailable(String providerName) {
        return delegate.isProviderAvailable(providerName);
    }

    /**
     * Evaluates policy for the given LLM request.
     *
     * @param request the LLM completion request
     * @return promise of policy evaluation result
     */
    private Promise<PolicyEvalResult> evaluatePolicy(CompletionRequest request) {
        String tenantId = extractTenantId(request);
        String policyPath = extractPolicyPath(request);

        Map<String, Object> input = buildPolicyInput(request);

        logger.debug("Evaluating policy {} for tenant {}", policyPath, tenantId);

        return policyEngine.evaluate(tenantId, policyPath, input)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Policy evaluation failed for tenant {}", tenantId, error);
                    } else if (result.allowed()) {
                        logger.debug("Policy allowed for tenant {}", tenantId);
                    } else {
                        logger.warn("Policy denied for tenant {}: {}", tenantId, result.reasons());
                    }
                });
    }

    /**
     * Extracts tenant ID from request metadata.
     */
    private String extractTenantId(CompletionRequest request) {
        if (request.getMetadata() != null && request.getMetadata().containsKey("tenantId")) {
            return request.getMetadata().get("tenantId").toString();
        }
        return "default";
    }

    /**
     * Extracts policy path from request metadata.
     */
    private String extractPolicyPath(CompletionRequest request) {
        if (request.getMetadata() != null && request.getMetadata().containsKey("policyPath")) {
            return request.getMetadata().get("policyPath").toString();
        }
        return defaultPolicyPath;
    }

    /**
     * Builds policy input from LLM request.
     */
    private Map<String, Object> buildPolicyInput(CompletionRequest request) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", request.getPrompt());
        input.put("model", request.getModel());
        input.put("temperature", request.getTemperature());
        input.put("maxTokens", request.getMaxTokens());
        input.put("metadata", request.getMetadata() != null ? request.getMetadata() : Map.of());
        return input;
    }

    /**
     * Exception thrown when policy denies an LLM request.
     */
    public static class PolicyDeniedException extends RuntimeException {

        private final PolicyEvalResult policyResult;

        public PolicyDeniedException(String message, PolicyEvalResult policyResult) {
            super(message);
            this.policyResult = policyResult;
        }

        public PolicyEvalResult getPolicyResult() {
            return policyResult;
        }
    }
}
