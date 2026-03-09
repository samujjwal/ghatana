/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.policy;

import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.datacloud.config.model.CompiledCollectionConfig;
import com.ghatana.datacloud.config.model.CompiledPolicyConfig;
import com.ghatana.datacloud.config.model.CompiledPolicyConfig.CompiledFieldSelector;
import com.ghatana.datacloud.config.model.CompiledPolicyConfig.CompiledPolicyRule;
import com.ghatana.datacloud.config.model.CompiledPolicyConfig.CompiledPolicySelector;
import com.ghatana.datacloud.config.model.CompiledPolicyConfig.OperationTrigger;
import com.ghatana.datacloud.config.model.CompiledPolicyConfig.PolicyType;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Engine for selecting and applying policies to collections based on selectors.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides policy selection and matching logic that:
 * <ul>
 * <li>Matches policies to collections using label-based selectors</li>
 * <li>Matches policies to fields using annotation-based selectors</li>
 * <li>Returns applicable rules in priority order</li>
 * <li>Filters rules by operation type</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PolicySelectorEngine engine = new PolicySelectorEngine(configRegistry, metrics);
 *
 * // Get all policies matching a collection
 * List<CompiledPolicyConfig> policies = runPromise(() ->
 *     engine.selectPoliciesForCollection(tenantId, collectionName));
 *
 * // Get encryption rules for a specific field
 * List<ApplicableRule> rules = runPromise(() ->
 *     engine.selectRulesForField(tenantId, collectionName, "email", PolicyType.ENCRYPTION));
 * }</pre>
 *
 * <p>
 * <b>Selection Algorithm</b><br>
 * 1. Load all policies for tenant from registry 2. Filter by selector match
 * (labels, collection names, expressions) 3. Sort by priority (lower number =
 * higher priority) 4. Return enabled rules that match the operation
 *
 * @doc.type class
 * @doc.purpose Policy selection engine using YAML-based selectors
 * @doc.layer product
 * @doc.pattern Service
 */
public class PolicySelectorEngine {

    private static final Logger logger = LoggerFactory.getLogger(PolicySelectorEngine.class);

    private final ConfigRegistry configRegistry;
    private final MetricsCollector metrics;

    /**
     * Creates a new PolicySelectorEngine.
     *
     * @param configRegistry the configuration registry
     * @param metrics the metrics collector
     */
    public PolicySelectorEngine(ConfigRegistry configRegistry, MetricsCollector metrics) {
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Select all policies that apply to a collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise of matching policies sorted by priority
     */
    public Promise<List<CompiledPolicyConfig>> selectPoliciesForCollection(
            String tenantId,
            String collectionName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collectionName, "collectionName");

        long startTime = System.nanoTime();

        return configRegistry.getCollectionAsync(tenantId, collectionName)
                .then(collection -> selectPoliciesForCollection(tenantId, collection))
                .whenComplete(() -> {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    metrics.recordTimer(
                            "policy.selection.duration",
                            durationMs,
                            "tenant", tenantId,
                            "collection", collectionName);
                });
    }

    /**
     * Select all policies that apply to a compiled collection config.
     *
     * @param tenantId the tenant identifier
     * @param collection the compiled collection config
     * @return Promise of matching policies sorted by priority
     */
    public Promise<List<CompiledPolicyConfig>> selectPoliciesForCollection(
            String tenantId,
            CompiledCollectionConfig collection) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collection, "collection");

        // Use ConfigRegistry's getPoliciesForCollection which already does filtering
        return configRegistry.getPoliciesForCollection(tenantId, collection.name(), collection.labels())
                .map(policies -> {
                    List<CompiledPolicyConfig> matching = policies.stream()
                            .filter(CompiledPolicyConfig::enabled)
                            .collect(Collectors.toList());

                    logger.debug("Selected {} policies for collection {}/{}",
                            matching.size(), tenantId, collection.name());

                    return matching;
                });
    }

    /**
     * Select rules of a specific type for a collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param ruleType the type of rules to select
     * @return Promise of applicable rules
     */
    public Promise<List<ApplicableRule>> selectRulesForCollection(
            String tenantId,
            String collectionName,
            PolicyType ruleType) {
        return selectPoliciesForCollection(tenantId, collectionName)
                .map(policies -> extractRules(policies, ruleType, null));
    }

    /**
     * Select rules for a specific operation on a collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param operation the operation trigger
     * @return Promise of applicable rules for the operation
     */
    public Promise<List<ApplicableRule>> selectRulesForOperation(
            String tenantId,
            String collectionName,
            OperationTrigger operation) {
        return selectPoliciesForCollection(tenantId, collectionName)
                .map(policies -> extractRulesForOperation(policies, operation));
    }

    /**
     * Select rules that apply to a specific field.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param fieldName the field name
     * @param ruleType the type of rules to select (or null for all)
     * @return Promise of applicable rules for the field
     */
    public Promise<List<ApplicableRule>> selectRulesForField(
            String tenantId,
            String collectionName,
            String fieldName,
            PolicyType ruleType) {
        return selectPoliciesForCollection(tenantId, collectionName)
                .then(policies -> {
                    return configRegistry.getCollectionAsync(tenantId, collectionName)
                            .map(collection -> {
                                // Get field info
                                var fieldConfig = collection.fields().stream()
                                        .filter(f -> f.name().equals(fieldName))
                                        .findFirst()
                                        .orElse(null);

                                if (fieldConfig == null) {
                                    return List.<ApplicableRule>of();
                                }

                                String fieldType = fieldConfig.type().name();
                                // Field annotations would come from raw config; use empty for now
                                Map<String, String> fieldAnnotations = Map.of();

                                return extractRulesForField(
                                        policies,
                                        fieldName,
                                        fieldType,
                                        fieldAnnotations,
                                        ruleType);
                            });
                });
    }

    /**
     * Get all encryption policies applicable to a collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise of encryption rules
     */
    public Promise<List<ApplicableRule>> getEncryptionRules(
            String tenantId,
            String collectionName) {
        return selectRulesForCollection(tenantId, collectionName, PolicyType.ENCRYPTION);
    }

    /**
     * Get all masking policies applicable to a collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise of masking rules
     */
    public Promise<List<ApplicableRule>> getMaskingRules(
            String tenantId,
            String collectionName) {
        return selectRulesForCollection(tenantId, collectionName, PolicyType.MASKING);
    }

    /**
     * Get all audit policies applicable to a collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise of audit rules
     */
    public Promise<List<ApplicableRule>> getAuditRules(
            String tenantId,
            String collectionName) {
        return selectRulesForCollection(tenantId, collectionName, PolicyType.AUDIT);
    }

    /**
     * Get all rate limit policies applicable to a collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise of rate limit rules
     */
    public Promise<List<ApplicableRule>> getRateLimitRules(
            String tenantId,
            String collectionName) {
        return selectRulesForCollection(tenantId, collectionName, PolicyType.RATE_LIMIT);
    }

    /**
     * Get all validation policies applicable to a collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise of validation rules
     */
    public Promise<List<ApplicableRule>> getValidationRules(
            String tenantId,
            String collectionName) {
        return selectRulesForCollection(tenantId, collectionName, PolicyType.VALIDATION);
    }

    // =====================================================================
    // Matching Logic
    // =====================================================================
    private boolean matchesCollection(
            CompiledPolicySelector selector,
            String collectionName,
            Map<String, String> collectionLabels) {
        return selector.matches(collectionName, collectionLabels);
    }

    private List<ApplicableRule> extractRules(
            List<CompiledPolicyConfig> policies,
            PolicyType ruleType,
            OperationTrigger operation) {
        List<ApplicableRule> rules = new ArrayList<>();

        for (CompiledPolicyConfig policy : policies) {
            for (CompiledPolicyRule rule : policy.rules()) {
                if (!rule.enabled()) {
                    continue;
                }
                if (ruleType != null && rule.type() != ruleType) {
                    continue;
                }
                if (operation != null && !rule.shouldTrigger(operation)) {
                    continue;
                }

                rules.add(new ApplicableRule(
                        policy.name(),
                        policy.priority(),
                        rule,
                        policy.selector().matchFields()));
            }
        }

        // Sort by policy priority, then by rule name for deterministic order
        rules.sort(Comparator
                .comparingInt(ApplicableRule::policyPriority)
                .thenComparing(r -> r.rule().name()));

        return rules;
    }

    private List<ApplicableRule> extractRulesForOperation(
            List<CompiledPolicyConfig> policies,
            OperationTrigger operation) {
        return extractRules(policies, null, operation);
    }

    private List<ApplicableRule> extractRulesForField(
            List<CompiledPolicyConfig> policies,
            String fieldName,
            String fieldType,
            Map<String, String> fieldAnnotations,
            PolicyType ruleType) {

        List<ApplicableRule> rules = new ArrayList<>();

        for (CompiledPolicyConfig policy : policies) {
            CompiledFieldSelector fieldSelector = policy.selector().matchFields();

            // Check if field selector matches this field
            if (!fieldSelector.isEmpty()
                    && !fieldSelector.matchesField(fieldName, fieldType, fieldAnnotations)) {
                continue;
            }

            for (CompiledPolicyRule rule : policy.rules()) {
                if (!rule.enabled()) {
                    continue;
                }
                if (ruleType != null && rule.type() != ruleType) {
                    continue;
                }

                rules.add(new ApplicableRule(
                        policy.name(),
                        policy.priority(),
                        rule,
                        fieldSelector));
            }
        }

        rules.sort(Comparator
                .comparingInt(ApplicableRule::policyPriority)
                .thenComparing(r -> r.rule().name()));

        return rules;
    }

    // =====================================================================
    // Result Types
    // =====================================================================
    /**
     * An applicable rule with its policy context.
     *
     * @param policyName the originating policy name
     * @param policyPriority the policy priority
     * @param rule the compiled rule
     * @param fieldSelector the field selector from the policy
     */
    public record ApplicableRule(
            String policyName,
            int policyPriority,
            CompiledPolicyRule rule,
            CompiledFieldSelector fieldSelector) {
        /**
         * Get the rule type.
         *
         * @return the policy type
         */
    public PolicyType type() {
        return rule.type();
    }

    /**
     * Get the rule name.
     *
     * @return the rule name
     */
    public String ruleName() {
        return rule.name();
    }

    /**
     * Check if this rule should trigger for an operation.
     *
     * @param operation the operation
     * @return true if rule should trigger
     */
    public boolean shouldTrigger(OperationTrigger operation) {
        return rule.shouldTrigger(operation);
    }
}
}
