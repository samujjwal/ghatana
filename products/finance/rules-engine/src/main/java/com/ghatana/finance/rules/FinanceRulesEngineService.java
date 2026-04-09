/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.json.PlatformObjectMapper;
import com.ghatana.platform.core.exception.ServiceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Finance Rules Engine Service.
 *
 * <p>Finance-specific rules engine with regulatory compliance and financial validation.
 * Provides policy evaluation for financial operations including trading compliance,
 * risk management, regulatory reporting, and AML monitoring.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Financial policy evaluation with OPA integration</li>
 *   <li>Regulatory compliance checking (SEC, FINRA, FCA, etc.)</li>
 *   <li>Financial risk assessment and limit enforcement</li>
 *   <li>AML and fraud detection rule evaluation</li>
 *   <li>Financial audit trail and compliance reporting</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance rules engine - regulatory compliance, risk management, AML detection
 * @doc.layer finance
 * @doc.pattern Service, Engine
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceRulesEngineService {

    private static final Logger log = LoggerFactory.getLogger(FinanceRulesEngineService.class);
    private static final ObjectMapper MAPPER = PlatformObjectMapper.instance();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Result of a finance rule evaluation.
     *
     * @param compliant whether the financial operation complies with rules
     * @param violations list of rule violations found
     * @param riskLevel assessed risk level (LOW, MEDIUM, HIGH, CRITICAL)
     * @param result full JSON response from rules engine
     * @param policyPath the policy path that was evaluated
     * @param complianceContext regulatory compliance context
     */
    public record FinanceRuleResult(
        boolean compliant,
        Map<String, FinanceViolation> violations,
        String riskLevel,
        Map<String, Object> result,
        String policyPath,
        FinanceComplianceContext complianceContext
    ) {}

    /**
     * Finance-specific violation details.
     *
     * @param ruleId the rule that was violated
     * @param severity violation severity
     * @param description human-readable description
     * @param regulatoryReference applicable regulation reference
     * @param requiredAction required remediation action
     */
    public record FinanceViolation(
        String ruleId,
        String severity,
        String description,
        String regulatoryReference,
        String requiredAction
    ) {}

    /**
     * Regulatory compliance context for rule evaluation.
     *
     * @param jurisdiction regulatory jurisdiction
     * @param regulatoryBody regulatory authority
     * @param complianceLevel compliance level (BASIC, STANDARD, ENHANCED)
     * @param evaluationTime when the evaluation occurred
     */
    public record FinanceComplianceContext(
        String jurisdiction,
        String regulatoryBody,
        String complianceLevel,
        Instant evaluationTime
    ) {}

    /**
     * Financial transaction context for rule evaluation.
     *
     * @param transactionId unique transaction identifier
     * @param transactionType type of financial transaction
     * @param amount transaction amount
     * @param currency transaction currency
     * @param counterparty transaction counterparty
     * @param tenantId tenant/firm identifier
     * @param userId user performing the transaction
     */
    public record FinanceTransactionContext(
        String transactionId,
        String transactionType,
        String amount,
        String currency,
        String counterparty,
        String tenantId,
        String userId
    ) {}

    private final String opaBaseUrl;
    private final HttpClient httpClient;
    private final Executor executor;

    /**
     * Creates a finance rules engine service that talks to OPA at the given base URL.
     *
     * @param opaBaseUrl base URL of the OPA server, e.g. {@code http://localhost:8181}
     * @param executor blocking executor for HTTP calls
     */
    public FinanceRulesEngineService(String opaBaseUrl, Executor executor) {
        this.opaBaseUrl = opaBaseUrl.endsWith("/") ? opaBaseUrl.substring(0, opaBaseUrl.length() - 1) : opaBaseUrl;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * Evaluates financial trading compliance rules.
     *
     * @param transaction financial transaction context
     * @param compliance regulatory compliance context
     * @return promise resolving to finance rule evaluation result
     */
    public Promise<FinanceRuleResult> evaluateTradingCompliance(
            FinanceTransactionContext transaction,
            FinanceComplianceContext compliance) {

        Map<String, Object> input = new HashMap<>();
        input.put("transaction", Map.of(
            "id", transaction.transactionId(),
            "type", transaction.transactionType(),
            "amount", transaction.amount(),
            "currency", transaction.currency(),
            "counterparty", transaction.counterparty(),
            "tenantId", transaction.tenantId(),
            "userId", transaction.userId()
        ));
        input.put("compliance", Map.of(
            "jurisdiction", compliance.jurisdiction(),
            "regulatoryBody", compliance.regulatoryBody(),
            "complianceLevel", compliance.complianceLevel()
        ));
        input.put("evaluationTime", compliance.evaluationTime().toString());

        return evaluateFinanceRule("finance/trading/compliance", input, compliance);
    }

    /**
     * Evaluates AML (Anti-Money Laundering) rules.
     *
     * @param transaction financial transaction context
     * @param compliance regulatory compliance context
     * @return promise resolving to AML rule evaluation result
     */
    public Promise<FinanceRuleResult> evaluateAMLRules(
            FinanceTransactionContext transaction,
            FinanceComplianceContext compliance) {

        Map<String, Object> input = new HashMap<>();
        input.put("transaction", Map.of(
            "id", transaction.transactionId(),
            "type", transaction.transactionType(),
            "amount", transaction.amount(),
            "currency", transaction.currency(),
            "counterparty", transaction.counterparty(),
            "tenantId", transaction.tenantId()
        ));
        input.put("compliance", Map.of(
            "jurisdiction", compliance.jurisdiction(),
            "regulatoryBody", compliance.regulatoryBody(),
            "complianceLevel", compliance.complianceLevel()
        ));

        return evaluateFinanceRule("finance/aml/screening", input, compliance);
    }

    /**
     * Evaluates financial risk management rules.
     *
     * @param transaction financial transaction context
     * @param compliance regulatory compliance context
     * @param riskLimits current risk limits and exposures
     * @return promise resolving to risk rule evaluation result
     */
    public Promise<FinanceRuleResult> evaluateRiskRules(
            FinanceTransactionContext transaction,
            FinanceComplianceContext compliance,
            Map<String, Object> riskLimits) {

        Map<String, Object> input = new HashMap<>();
        input.put("transaction", Map.of(
            "id", transaction.transactionId(),
            "type", transaction.transactionType(),
            "amount", transaction.amount(),
            "currency", transaction.currency(),
            "tenantId", transaction.tenantId()
        ));
        input.put("riskLimits", riskLimits);
        input.put("compliance", Map.of(
            "jurisdiction", compliance.jurisdiction(),
            "regulatoryBody", compliance.regulatoryBody()
        ));

        return evaluateFinanceRule("finance/risk/limits", input, compliance);
    }

    /**
     * Evaluates regulatory reporting requirements.
     *
     * @param transaction financial transaction context
     * @param compliance regulatory compliance context
     * @return promise resolving to reporting rule evaluation result
     */
    public Promise<FinanceRuleResult> evaluateReportingRules(
            FinanceTransactionContext transaction,
            FinanceComplianceContext compliance) {

        Map<String, Object> input = new HashMap<>();
        input.put("transaction", Map.of(
            "id", transaction.transactionId(),
            "type", transaction.transactionType(),
            "amount", transaction.amount(),
            "currency", transaction.currency(),
            "tenantId", transaction.tenantId()
        ));
        input.put("compliance", Map.of(
            "jurisdiction", compliance.jurisdiction(),
            "regulatoryBody", compliance.regulatoryBody(),
            "complianceLevel", compliance.complianceLevel()
        ));

        return evaluateFinanceRule("finance/reporting/requirements", input, compliance);
    }

    /**
     * Evaluates a generic finance rule against the given input document.
     *
     * @param policyPath finance policy path segments separated by slashes
     * @param input input document passed to rules engine
     * @param compliance regulatory compliance context
     * @return promise resolving to finance rule evaluation result
     */
    public Promise<FinanceRuleResult> evaluateFinanceRule(
            String policyPath,
            Map<String, Object> input,
            FinanceComplianceContext compliance) {

        return Promise.ofBlocking(executor, () -> doEvaluateFinanceRule(policyPath, input, compliance));
    }

    @SuppressWarnings("unchecked")
    private FinanceRuleResult doEvaluateFinanceRule(
            String policyPath,
            Map<String, Object> input,
            FinanceComplianceContext compliance) throws Exception {

        String normalizedPath = policyPath.startsWith("/") ? policyPath.substring(1) : policyPath;
        String url = opaBaseUrl + "/v1/data/" + normalizedPath;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", input);
        String requestJson = MAPPER.writeValueAsString(requestBody);

        log.debug("Finance rules evaluate: POST {} with input keys={}", url, input.keySet());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Regulatory-Body", compliance.regulatoryBody())
                .header("X-Jurisdiction", compliance.jurisdiction())
                .timeout(DEFAULT_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new FinanceRulesEvaluationException(
                "Finance rules engine returned HTTP " + response.statusCode() + " for policy=" + policyPath +
                "; body=" + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode resultNode = root.path("result");

        boolean compliant = false;
        String riskLevel = "LOW";
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, FinanceViolation> violations = new HashMap<>();

        if (!resultNode.isMissingNode()) {
            resultMap = MAPPER.convertValue(resultNode, Map.class);

            // Extract compliance result
            Object compliantVal = resultMap.get("compliant");
            if (compliantVal instanceof Boolean b) {
                compliant = b;
            }

            // Extract risk level
            Object riskVal = resultMap.get("riskLevel");
            if (riskVal instanceof String s) {
                riskLevel = s;
            }

            // Extract violations
            Object violationsVal = resultMap.get("violations");
            if (violationsVal instanceof Map) {
                Map<String, Object> violationMaps = (Map<String, Object>) violationsVal;
                for (Map.Entry<String, Object> entry : violationMaps.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> violationMap = (Map<String, Object>) entry.getValue();
                        violations.put(entry.getKey(), new FinanceViolation(
                            entry.getKey(),
                            (String) violationMap.getOrDefault("severity", "MEDIUM"),
                            (String) violationMap.getOrDefault("description", "Rule violation"),
                            (String) violationMap.getOrDefault("regulatoryReference", ""),
                            (String) violationMap.getOrDefault("requiredAction", "Review required")
                        ));
                    }
                }
            }
        }

        log.debug("Finance rules result: policy={} compliant={} riskLevel={} violations={}",
            policyPath, compliant, riskLevel, violations.size());

        return new FinanceRuleResult(compliant, violations, riskLevel, resultMap, policyPath, compliance);
    }

    /**
     * Batch evaluate multiple finance rules for efficiency.
     *
     * @param evaluations list of rule evaluations to perform
     * @return promise resolving to list of finance rule results
     */
    public Promise<Map<String, FinanceRuleResult>> batchEvaluate(
            Map<String, RuleEvaluationRequest> evaluations) {

        return Promise.ofBlocking(executor, () -> {
            Map<String, FinanceRuleResult> results = new HashMap<>();

            for (Map.Entry<String, RuleEvaluationRequest> entry : evaluations.entrySet()) {
                try {
                    FinanceRuleResult result = doEvaluateFinanceRule(
                        entry.getValue().policyPath(),
                        entry.getValue().input(),
                        entry.getValue().compliance()
                    );
                    results.put(entry.getKey(), result);
                } catch (Exception e) {
                    log.error("Failed to evaluate finance rule: {}", entry.getKey(), e);
                    // Continue with other evaluations
                }
            }

            return results;
        });
    }

    /**
     * Request for batch rule evaluation.
     */
    public record RuleEvaluationRequest(
        String policyPath,
        Map<String, Object> input,
        FinanceComplianceContext compliance
    ) {}

    /**
     * Thrown when finance rules engine returns an error HTTP status or the request fails.
     */
    public static final class FinanceRulesEvaluationException extends ServiceException {
        public FinanceRulesEvaluationException(String message) {
            super(message);
        }

        public FinanceRulesEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
