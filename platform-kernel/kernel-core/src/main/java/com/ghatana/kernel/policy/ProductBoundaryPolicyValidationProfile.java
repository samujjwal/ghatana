package com.ghatana.kernel.policy;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Declares product-owned validation requirements for boundary policy packs
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class ProductBoundaryPolicyValidationProfile {

    private final String productName;
    private final String rulePrefix;
    private final String defaultDenyRuleId;
    private final String targetScopePrefix;
    private final boolean requireAuditOnDefaultDeny;
    private final Set<String> requiredMetadataKeys;

    private ProductBoundaryPolicyValidationProfile(Builder builder) {
        this.productName = requireNonBlank(builder.productName, "productName");
        this.rulePrefix = requireNonBlank(builder.rulePrefix, "rulePrefix");
        this.defaultDenyRuleId = requireNonBlank(builder.defaultDenyRuleId, "defaultDenyRuleId");
        this.targetScopePrefix = requireNonBlank(builder.targetScopePrefix, "targetScopePrefix");
        this.requireAuditOnDefaultDeny = builder.requireAuditOnDefaultDeny;
        this.requiredMetadataKeys = Set.copyOf(builder.requiredMetadataKeys);
    }

    public String getProductName() {
        return productName;
    }

    public String getRulePrefix() {
        return rulePrefix;
    }

    public String getDefaultDenyRuleId() {
        return defaultDenyRuleId;
    }

    public String getTargetScopePrefix() {
        return targetScopePrefix;
    }

    public boolean isRequireAuditOnDefaultDeny() {
        return requireAuditOnDefaultDeny;
    }

    public Set<String> getRequiredMetadataKeys() {
        return requiredMetadataKeys;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }

    public static final class Builder {
        private String productName;
        private String rulePrefix;
        private String defaultDenyRuleId;
        private String targetScopePrefix;
        private boolean requireAuditOnDefaultDeny = true;
        private Set<String> requiredMetadataKeys = new LinkedHashSet<>(Set.of("packVersion", "ruleCategory"));

        private Builder() {
        }

        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }

        public Builder rulePrefix(String rulePrefix) {
            this.rulePrefix = rulePrefix;
            return this;
        }

        public Builder defaultDenyRuleId(String defaultDenyRuleId) {
            this.defaultDenyRuleId = defaultDenyRuleId;
            return this;
        }

        public Builder targetScopePrefix(String targetScopePrefix) {
            this.targetScopePrefix = targetScopePrefix;
            return this;
        }

        public Builder requireAuditOnDefaultDeny(boolean requireAuditOnDefaultDeny) {
            this.requireAuditOnDefaultDeny = requireAuditOnDefaultDeny;
            return this;
        }

        public Builder requiredMetadataKeys(Set<String> requiredMetadataKeys) {
            this.requiredMetadataKeys = new LinkedHashSet<>(Objects.requireNonNull(requiredMetadataKeys));
            return this;
        }

        public ProductBoundaryPolicyValidationProfile build() {
            return new ProductBoundaryPolicyValidationProfile(this);
        }
    }
}
