package com.ghatana.phr.security;

import com.ghatana.kernel.security.Policy;
import com.ghatana.kernel.security.SecurityContext;

import java.util.*;

/**
 * Component for HIPAAPrivacyPolicy
 *
 * @doc.type class
 * @doc.purpose Component for HIPAAPrivacyPolicy
 * @doc.layer product
 * @doc.pattern Service
 */
public class HIPAAPrivacyPolicy implements Policy {
    private final String tenantId;
    private final String policyId;
    private final Set<PolicyRule> rules;

    public HIPAAPrivacyPolicy(String tenantId) {
        this.tenantId = tenantId;
        this.policyId = "HIPAA-PRIVACY-" + tenantId;
        this.rules = new HashSet<>();
        initializeRules();
    }

    private void initializeRules() {
        rules.add(new MinimumNecessaryRule());
        rules.add(new AuthorizedAccessRule());
        rules.add(new AuditTrailRule());
    }

    @Override
    public String getPolicyId() {
        return policyId;
    }

    @Override
    public String getName() {
        return "HIPAA Privacy Policy";
    }

    @Override
    public PolicyType getType() {
        return PolicyType.DATA_ACCESS;
    }

    @Override
    public Set<PolicyRule> getRules() {
        return Collections.unmodifiableSet(rules);
    }

    @Override
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenantId", tenantId);
        metadata.put("compliance", "HIPAA");
        metadata.put("version", "1.0");
        return metadata;
    }

    @Override
    public boolean appliesTo(SecurityContext context) {
        return context.getTenantId().equals(tenantId);
    }

    private static class MinimumNecessaryRule implements PolicyRule {
        @Override
        public String getRuleId() {
            return "HIPAA-MIN-NECESSARY";
        }

        @Override
        public String getDescription() {
            return "Enforce minimum necessary access to PHI";
        }

        @Override
        public boolean evaluate(SecurityContext context) {
            return context.isAuthenticated() &&
                   (context.hasRole("HEALTHCARE_PROVIDER") ||
                    context.hasRole("PATIENT") ||
                    context.hasRole("ADMINISTRATOR"));
        }
    }

    private static class AuthorizedAccessRule implements PolicyRule {
        @Override
        public String getRuleId() {
            return "HIPAA-AUTHORIZED-ACCESS";
        }

        @Override
        public String getDescription() {
            return "Verify user is authorized to access PHI";
        }

        @Override
        public boolean evaluate(SecurityContext context) {
            return context.isAuthenticated() &&
                   context.hasPermission("read:patient-records");
        }
    }

    private static class AuditTrailRule implements PolicyRule {
        @Override
        public String getRuleId() {
            return "HIPAA-AUDIT-TRAIL";
        }

        @Override
        public String getDescription() {
            return "All PHI access must be audited";
        }

        @Override
        public boolean evaluate(SecurityContext context) {
            return true;
        }
    }
}
