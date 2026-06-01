/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WS4-12: Implementation of governance policy service.
 *
 * <p>Provides canonical governance policy evaluation for Data Cloud.
 * This service owns the governance policy primitives and evaluation logic
 * for the governance/core module.
 *
 * @doc.type class
 * @doc.purpose Implementation of governance policy evaluation
 * @doc.layer product
 * @doc.pattern Service
 */
public class GovernancePolicyService {

    private static final Logger log = LoggerFactory.getLogger(GovernancePolicyService.class);

    private final Map<String, GovernancePolicy> policies = new ConcurrentHashMap<>();

    public GovernancePolicyService() {
        log.info("[WS4-12] GovernancePolicyService initialized");
        registerDefaultPolicies();
    }

    /**
     * Registers a governance policy.
     *
     * @param policy the policy to register
     */
    public void registerPolicy(GovernancePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Policy cannot be null");
        }
        policies.put(policy.policyId(), policy);
        log.info("[WS4-12] Registered policy: {} (category: {})", policy.policyId(), policy.category());
    }

    /**
     * Evaluates a policy by ID against the provided context.
     *
     * @param policyId the policy ID
     * @param context the evaluation context
     * @return policy evaluation result
     */
    public GovernancePolicy.PolicyEvaluationResult evaluate(String policyId, GovernancePolicy.EvaluationContext context) {
        GovernancePolicy policy = policies.get(policyId);
        if (policy == null) {
            log.warn("[WS4-12] Policy not found: {}", policyId);
            return GovernancePolicy.PolicyEvaluationResult.deny(
                "Policy not found: " + policyId,
                Set.of("policy.not_found")
            );
        }

        if (!policy.isEnabled()) {
            log.debug("[WS4-12] Policy disabled: {}", policyId);
            return GovernancePolicy.PolicyEvaluationResult.allow();
        }

        return policy.evaluate(context);
    }

    /**
     * Evaluates all policies in a category against the provided context.
     *
     * @param category the policy category
     * @param context the evaluation context
     * @return combined evaluation result
     */
    public GovernancePolicy.PolicyEvaluationResult evaluateCategory(
            GovernancePolicy.PolicyCategory category,
            GovernancePolicy.EvaluationContext context) {
        
        Set<String> allViolatedRules = new HashSet<>();
        Map<String, Object> combinedMetadata = new HashMap<>();
        boolean allAllowed = true;
        String denialReason = null;

        for (GovernancePolicy policy : policies.values()) {
            if (policy.category() == category && policy.isEnabled()) {
                GovernancePolicy.PolicyEvaluationResult result = policy.evaluate(context);
                if (result.isDenied()) {
                    allAllowed = false;
                    allViolatedRules.addAll(result.violatedRules());
                    if (denialReason == null) {
                        denialReason = result.reason();
                    }
                }
                combinedMetadata.putAll(result.metadata());
            }
        }

        if (allAllowed) {
            return GovernancePolicy.PolicyEvaluationResult.allow();
        }

        return new GovernancePolicy.PolicyEvaluationResult(
            false,
            denialReason != null ? denialReason : "Policy category evaluation failed",
            allViolatedRules,
            combinedMetadata,
            Instant.now()
        );
    }

    /**
     * Gets a policy by ID.
     *
     * @param policyId the policy ID
     * @return the policy, or null if not found
     */
    public GovernancePolicy getPolicy(String policyId) {
        return policies.get(policyId);
    }

    /**
     * Checks if a policy exists.
     *
     * @param policyId the policy ID
     * @return true if the policy exists
     */
    public boolean hasPolicy(String policyId) {
        return policies.containsKey(policyId);
    }

    /**
     * Gets all policies in a category.
     *
     * @param category the policy category
     * @return set of policy IDs in the category
     */
    public Set<String> getPoliciesByCategory(GovernancePolicy.PolicyCategory category) {
        Set<String> policyIds = new HashSet<>();
        for (GovernancePolicy policy : policies.values()) {
            if (policy.category() == category) {
                policyIds.add(policy.policyId());
            }
        }
        return policyIds;
    }

    /**
     * Registers default governance policies.
     */
    private void registerDefaultPolicies() {
        // Data classification policy
        registerPolicy(new DataClassificationPolicy("data.classification.default", "1.0.0"));
        
        // Data retention policy
        registerPolicy(new DataRetentionPolicy("data.retention.default", "1.0.0"));
        
        // Data encryption policy
        registerPolicy(new DataEncryptionPolicy("data.encryption.default", "1.0.0"));
        
        // Audit logging policy
        registerPolicy(new AuditLoggingPolicy("audit.logging.default", "1.0.0"));
        
        // Tenant isolation policy
        registerPolicy(new TenantIsolationPolicy("tenant.isolation.default", "1.0.0"));
    }

    // ==================== Default Policy Implementations ====================

    private static class DataClassificationPolicy implements GovernancePolicy {
        private final String policyId;
        private final String version;

        DataClassificationPolicy(String policyId, String version) {
            this.policyId = policyId;
            this.version = version;
        }

        @Override
        public String policyId() {
            return policyId;
        }

        @Override
        public PolicyCategory category() {
            return PolicyCategory.DATA_CLASSIFICATION;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String description() {
            "Default data classification policy for sensitivity levels";
        }

        @Override
        public PolicyEvaluationResult evaluate(EvaluationContext context) {
            // Check for sensitive data patterns
            if (context.resourceData() != null) {
                String dataStr = context.resourceData().toString().toLowerCase();
                if (dataStr.contains("password") || dataStr.contains("secret") || dataStr.contains("token")) {
                    return PolicyEvaluationResult.deny(
                        "Sensitive data detected without classification",
                        Set.of("data.sensitive.unclassified")
                    );
                }
            }
            return PolicyEvaluationResult.allow();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public SeverityLevel severity() {
            return SeverityLevel.HIGH;
        }
    }

    private static class DataRetentionPolicy implements GovernancePolicy {
        private final String policyId;
        private final String version;

        DataRetentionPolicy(String policyId, String version) {
            this.policyId = policyId;
            this.version = version;
        }

        @Override
        public String policyId() {
            return policyId;
        }

        @Override
        public PolicyCategory category() {
            return PolicyCategory.DATA_RETENTION;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String description() {
            "Default data retention policy for data lifecycle";
        }

        @Override
        public PolicyEvaluationResult evaluate(EvaluationContext context) {
            // Check retention requirements based on data classification
            if (context.resourceData() != null) {
                Object classification = context.resourceData().get("classification");
                if ("CRITICAL".equals(classification)) {
                    // Critical data requires longer retention
                    return new PolicyEvaluationResult(
                        true,
                        null,
                        Set.of(),
                        Map.of("retentionDays", 365 * 7, "classification", classification),
                        Instant.now()
                    );
                }
            }
            return PolicyEvaluationResult.allow();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public SeverityLevel severity() {
            return SeverityLevel.MEDIUM;
        }
    }

    private static class DataEncryptionPolicy implements GovernancePolicy {
        private final String policyId;
        private final String version;

        DataEncryptionPolicy(String policyId, String version) {
            this.policyId = policyId;
            this.version = version;
        }

        @Override
        public String policyId() {
            return policyId;
        }

        @Override
        public PolicyCategory category() {
            return PolicyCategory.DATA_ENCRYPTION;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String description() {
            "Default data encryption policy for sensitive data";
        }

        @Override
        public PolicyEvaluationResult evaluate(EvaluationContext context) {
            // Check encryption requirements
            if (context.resourceData() != null) {
                Object classification = context.resourceData().get("classification");
                if ("CONFIDENTIAL".equals(classification) || "RESTRICTED".equals(classification)) {
                    Object encrypted = context.resourceData().get("encrypted");
                    if (!Boolean.TRUE.equals(encrypted)) {
                        return PolicyEvaluationResult.deny(
                            "Encryption required for classified data",
                            Set.of("data.encryption.required")
                        );
                    }
                }
            }
            return PolicyEvaluationResult.allow();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public SeverityLevel severity() {
            return SeverityLevel.CRITICAL;
        }
    }

    private static class AuditLoggingPolicy implements GovernancePolicy {
        private final String policyId;
        private final String version;

        AuditLoggingPolicy(String policyId, String version) {
            this.policyId = policyId;
            this.version = version;
        }

        @Override
        public String policyId() {
            return policyId;
        }

        @Override
        public PolicyCategory category() {
            return PolicyCategory.AUDIT_LOGGING;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String description() {
            "Default audit logging policy for compliance";
        }

        @Override
        public PolicyEvaluationResult evaluate(EvaluationContext context) {
            // All mutating operations require audit logging
            if ("DELETE".equals(context.operation()) || "UPDATE".equals(context.operation())) {
                return new PolicyEvaluationResult(
                    true,
                    null,
                    Set.of(),
                    Map.of("auditRequired", true, "auditLevel", "FULL"),
                    Instant.now()
                );
            }
            return PolicyEvaluationResult.allow();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public SeverityLevel severity() {
            return SeverityLevel.HIGH;
        }
    }

    private static class TenantIsolationPolicy implements GovernancePolicy {
        private final String policyId;
        private final String version;

        TenantIsolationPolicy(String policyId, String version) {
            this.policyId = policyId;
            this.version = version;
        }

        @Override
        public String policyId() {
            return policyId;
        }

        @Override
        public PolicyCategory category() {
            return PolicyCategory.TENANT_ISOLATION;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String description() {
            "Default tenant isolation policy for multi-tenancy";
        }

        @Override
        public PolicyEvaluationResult evaluate(EvaluationContext context) {
            // Ensure tenant context is present
            if (context.tenantId() == null || context.tenantId().isBlank()) {
                return PolicyEvaluationResult.deny(
                    "Tenant context required",
                    Set.of("tenant.context.missing")
                );
            }
            
            // Check for cross-tenant access attempts
            if (context.resourceData() != null) {
                Object resourceTenant = context.resourceData().get("tenantId");
                if (resourceTenant != null && !resourceTenant.toString().equals(context.tenantId())) {
                    return PolicyEvaluationResult.deny(
                        "Cross-tenant access denied",
                        Set.of("tenant.isolation.violation")
                    );
                }
            }
            
            return PolicyEvaluationResult.allow();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public SeverityLevel severity() {
            return SeverityLevel.CRITICAL;
        }
    }
}
