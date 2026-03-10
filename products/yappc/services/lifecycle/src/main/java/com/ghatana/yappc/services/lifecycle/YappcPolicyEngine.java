/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle PolicyEngine Implementation
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.governance.PolicyEngine;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * YAML-backed implementation of {@link PolicyEngine} for YAPPC lifecycle governance.
 *
 * <p>Loads policy definitions at construction time from:
 * <ol>
 *   <li>{@code config/policies/lifecycle-policies.yaml} (filesystem, relative to {@code yappc.config.dir})</li>
 *   <li>{@code /policies/lifecycle-policies.yaml} on the classpath (fallback)</li>
 * </ol>
 *
 * <p>Evaluation logic:
 * <ol>
 *   <li>Locate the policy by {@code policyName} in the loaded map.</li>
 *   <li>For each rule whose {@code appliesTo} scope matches the {@code context}:</li>
 *   <li>Evaluate the rule's condition (METRIC_THRESHOLD, ARTIFACT_PRESENT, CONFIDENCE_SCORE).</li>
 *   <li>If any BLOCK rule's condition is <em>not</em> met → return {@code false} (block).</li>
 *   <li>If no rules block → return {@code true} (allow, default-permit).</li>
 * </ol>
 *
 * <p>A missing or unresolvable policy file defaults to allow-all (permissive), and a warning is logged.
 *
 * @doc.type class
 * @doc.purpose YAML-backed governance policy engine for YAPPC lifecycle rules
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reason
 */
public class YappcPolicyEngine implements PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(YappcPolicyEngine.class);

    private static final String CONFIG_DIR_PROP = "yappc.config.dir";
    private static final String POLICY_FILE = "policies/lifecycle-policies.yaml";
    private static final String CLASSPATH_POLICY_FILE = "/" + POLICY_FILE;

    /** Blocking executor for YAML I/O and condition evaluation. */
    private final Executor executor;

    /** Immutable map: policyId → Policy, loaded at startup. */
    private final Map<String, PolicyConfig.Policy> policies;

    /**
     * Constructs a {@code YappcPolicyEngine} and eagerly loads all policy definitions.
     * Uses a virtual-thread-per-task executor for non-blocking promise wrapping.
     */
    public YappcPolicyEngine() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.policies = loadPolicies();
    }

    // =========================================================================
    // PolicyEngine contract
    // =========================================================================

    @Override
    public Promise<Boolean> evaluate(String policyName, Map<String, Object> context) {
        return Promise.ofBlocking(executor, () -> evaluateSync(policyName, context));
    }

    @Override
    public Promise<Boolean> policyExists(String policyName) {
        return Promise.of(policies.containsKey(policyName));
    }

    // =========================================================================
    // Synchronous evaluation (called on blocking executor)
    // =========================================================================

    private boolean evaluateSync(String policyName, Map<String, Object> context) {
        PolicyConfig.Policy policy = policies.get(policyName);
        if (policy == null) {
            log.debug("No policy found for '{}'; applying default-permit", policyName);
            return true;
        }

        for (PolicyConfig.Rule rule : policy.getRules()) {
            if (!ruleApplies(rule, context)) {
                continue;
            }
            boolean conditionMet = conditionMet(rule.getCondition(), context);
            if (!conditionMet && "BLOCK".equalsIgnoreCase(rule.getAction())) {
                log.warn("Policy '{}' blocked by rule '{}' [{}]: condition not satisfied. Context={}",
                        policyName, rule.getId(), rule.getDescription(), context);
                return false;
            }
        }

        log.debug("Policy '{}' evaluation passed for context={}", policyName, context);
        return true;
    }

    /**
     * Returns true if the rule's {@code appliesTo} scope matches the given context.
     * A null scope field is treated as a wildcard (matches any value).
     */
    private boolean ruleApplies(PolicyConfig.Rule rule, Map<String, Object> context) {
        PolicyConfig.AppliesTo scope = rule.getAppliesTo();
        if (scope == null) {
            return true;
        }
        if (scope.getFromPhase() != null) {
            String contextFrom = getString(context, "from_phase", "");
            if (!scope.getFromPhase().equalsIgnoreCase(contextFrom)) {
                return false;
            }
        }
        if (scope.getToPhase() != null) {
            String contextTo = getString(context, "to_phase", "");
            if (!scope.getToPhase().equalsIgnoreCase(contextTo)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the condition is satisfied by the context values.
     * An unsupported condition type defaults to satisfied (non-blocking).
     */
    @SuppressWarnings("unchecked")
    private boolean conditionMet(PolicyConfig.Condition condition, Map<String, Object> context) {
        if (condition == null) {
            return true;
        }
        return switch (condition.getType().toUpperCase()) {
            case "METRIC_THRESHOLD" -> {
                double actualValue = getDouble(context, condition.getMetric(), 0.0);
                double threshold   = condition.getValue();
                yield switch (condition.getOperator() != null ? condition.getOperator().toUpperCase() : "GTE") {
                    case "GTE" -> actualValue >= threshold;
                    case "GT"  -> actualValue  > threshold;
                    case "LTE" -> actualValue <= threshold;
                    case "LT"  -> actualValue  < threshold;
                    case "EQ"  -> Double.compare(actualValue, threshold) == 0;
                    default    -> {
                        log.warn("Unknown metric operator '{}'; treating condition as satisfied", condition.getOperator());
                        yield true;
                    }
                };
            }
            case "ARTIFACT_PRESENT" -> {
                List<String> artifacts = (List<String>) context.getOrDefault("artifact_ids", List.of());
                yield artifacts.contains(condition.getArtifactId());
            }
            case "CONFIDENCE_SCORE" -> {
                double confidence = getDouble(context, "confidence", 0.0);
                yield confidence >= condition.getValue();
            }
            default -> {
                log.warn("Unknown policy condition type '{}'; treating as satisfied", condition.getType());
                yield true;
            }
        };
    }

    // =========================================================================
    // Policy file loading
    // =========================================================================

    private Map<String, PolicyConfig.Policy> loadPolicies() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        // 1. Try filesystem (configurable via system property)
        String configDir = System.getProperty(CONFIG_DIR_PROP, "config");
        File policyFile = new File(configDir, POLICY_FILE);
        if (policyFile.exists()) {
            try {
                return parseConfig(mapper, mapper.readValue(policyFile, PolicyConfig.class));
            } catch (IOException e) {
                log.error("Failed to load policies from filesystem '{}'; trying classpath", policyFile, e);
            }
        }

        // 2. Fallback to classpath
        try (var in = getClass().getResourceAsStream(CLASSPATH_POLICY_FILE)) {
            if (in != null) {
                return parseConfig(mapper, mapper.readValue(in, PolicyConfig.class));
            }
        } catch (IOException e) {
            log.error("Failed to load policies from classpath '{}'; defaulting to allow-all", CLASSPATH_POLICY_FILE, e);
        }

        log.warn("No lifecycle-policies.yaml found anywhere; YappcPolicyEngine will allow all requests");
        return Collections.emptyMap();
    }

    private Map<String, PolicyConfig.Policy> parseConfig(ObjectMapper mapper, PolicyConfig config) {
        Map<String, PolicyConfig.Policy> map = new LinkedHashMap<>();
        for (PolicyConfig.Policy policy : config.getPolicies()) {
            map.put(policy.getId(), policy);
        }
        log.info("YappcPolicyEngine loaded {} policies: {}", map.size(), map.keySet());
        return Collections.unmodifiableMap(map);
    }

    // =========================================================================
    // Context extraction helpers
    // =========================================================================

    private String getString(Map<String, Object> ctx, String key, String defaultValue) {
        Object v = ctx.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private double getDouble(Map<String, Object> ctx, String key, double defaultValue) {
        Object v = ctx.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { /* fall through */ }
        }
        return defaultValue;
    }
}
