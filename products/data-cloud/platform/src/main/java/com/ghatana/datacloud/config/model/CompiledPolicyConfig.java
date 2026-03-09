/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Compiled policy configuration with validated and type-safe values. Policies
 * define rules for encryption, masking, auditing, rate limiting, and
 * validation.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled policy configuration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CompiledPolicyConfig(
        String name,
        String displayName,
        String description,
        String namespace,
        Map<String, String> labels,
        CompiledPolicySelector selector,
        List<CompiledPolicyRule> rules,
        int priority,
        boolean enabled) {

    /**
     * Policy type enumeration.
     */
    public enum PolicyType {
        /**
         * Field-level encryption
         */
        ENCRYPTION,
        /**
         * Data masking for read operations
         */
        MASKING,
        /**
         * Audit logging for operations
         */
        AUDIT,
        /**
         * Rate limiting for API calls
         */
        RATE_LIMIT,
        /**
         * Data validation rules
         */
        VALIDATION,
        /**
         * Data retention policy
         */
        RETENTION,
        /**
         * Access control policy
         */
        ACCESS_CONTROL,
        /**
         * Custom policy type
         */
        CUSTOM
    }

    /**
     * Operation triggers for policy rules.
     */
    public enum OperationTrigger {
        CREATE,
        READ,
        UPDATE,
        DELETE,
        ALL
    }

    /**
     * Compiled policy selector for matching collections/fields.
     */
    public record CompiledPolicySelector(
            boolean matchAll,
            Map<String, String> matchLabels,
            CompiledFieldSelector matchFields,
            Set<String> matchCollections,
            Optional<String> matchExpression) {
        /**
         * Check if this selector matches a collection.
         *
         * @param collectionName the collection name
         * @param collectionLabels the collection's labels
         * @return true if selector matches
         */
    public boolean matches(String collectionName, Map<String, String> collectionLabels) {
        if (matchAll) {
            return true;
        }

        // Check collection name match
        if (!matchCollections.isEmpty() && matchCollections.contains(collectionName)) {
            return true;
        }

        // Check label match
        if (!matchLabels.isEmpty()) {
            for (Map.Entry<String, String> entry : matchLabels.entrySet()) {
                String labelValue = collectionLabels.get(entry.getKey());
                if (labelValue == null || !labelValue.equals(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Create a selector that matches all.
     *
     * @return match-all selector
     */
    public static CompiledPolicySelector forAll() {
        return new CompiledPolicySelector(
                true,
                Map.of(),
                CompiledFieldSelector.empty(),
                Set.of(),
                Optional.empty());
    }
}

/**
 * Compiled field-level selector.
 */
public record CompiledFieldSelector(
        Map<String, String> annotations,
        Set<String> fieldNames,
        Set<String> fieldTypes) {

    /**
     * Check if this selector matches a field.
     *
     * @param fieldName the field name
     * @param fieldType the field type
     * @param fieldAnnotations the field's annotations
     * @return true if selector matches
     */
    public boolean matchesField(
            String fieldName,
            String fieldType,
            Map<String, String> fieldAnnotations) {

        // Check field name
        if (!fieldNames.isEmpty() && fieldNames.contains(fieldName)) {
            return true;
        }

        // Check field type
        if (!fieldTypes.isEmpty() && fieldTypes.contains(fieldType)) {
            return true;
        }

        // Check annotations
        if (!annotations.isEmpty() && fieldAnnotations != null) {
            for (Map.Entry<String, String> entry : annotations.entrySet()) {
                String annotationValue = fieldAnnotations.get(entry.getKey());
                if (annotationValue != null && annotationValue.equals(entry.getValue())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if this selector is empty (matches nothing specific).
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return annotations.isEmpty() && fieldNames.isEmpty() && fieldTypes.isEmpty();
    }

    /**
     * Create an empty field selector.
     *
     * @return empty selector
     */
    public static CompiledFieldSelector empty() {
        return new CompiledFieldSelector(Map.of(), Set.of(), Set.of());
    }
}

/**
 * Compiled policy rule.
 */
public record CompiledPolicyRule(
        String name,
        PolicyType type,
        Set<OperationTrigger> triggers,
        Optional<String> condition,
        CompiledRuleConfig config,
        boolean enabled) {

    /**
     * Check if this rule should trigger for the given operation.
     *
     * @param operation the operation being performed
     * @return true if rule should trigger
     */
    public boolean shouldTrigger(OperationTrigger operation) {
        if (!enabled) {
            return false;
        }
        return triggers.contains(OperationTrigger.ALL) || triggers.contains(operation);
    }
}

/**
 * Compiled rule configuration (type-specific settings).
 */
public record CompiledRuleConfig(
        // Encryption config
        Optional<EncryptionConfig> encryption,
        // Masking config
        Optional<MaskingConfig> masking,
        // Audit config
        Optional<AuditConfig> audit,
        // Rate limit config
        Optional<RateLimitConfig> rateLimit,
        // Validation config
        Optional<ValidationConfig> validation,
        // Raw config for custom types
        Map<String, Object> rawConfig) {

    /**
     * Create empty rule config.
     *
     * @return empty config
     */
    public static CompiledRuleConfig empty() {
        return new CompiledRuleConfig(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of());
    }
}

/**
 * Encryption configuration.
 */
public record EncryptionConfig(
        String algorithm,
        String keyProvider,
        String keyPath,
        boolean rotateKeys) {

}

/**
 * Masking configuration.
 */
public record MaskingConfig(
        MaskingStrategy strategy,
        int visibleChars,
        char maskChar,
        Optional<String> condition) {

    /**
     * Masking strategies.
     */
    public enum MaskingStrategy {
        /**
         * Replace all characters
         */
        FULL,
        /**
         * Keep some characters visible
         */
        PARTIAL,
        /**
         * Replace with hash
         */
        HASH,
        /**
         * Tokenize the value
         */
        TOKENIZE
    }
}

/**
 * Audit logging configuration.
 */
public record AuditConfig(
        String destination,
        Set<String> includeFields,
        Set<String> excludeFields,
        boolean includeChanges,
        Optional<String> condition) {

}

/**
 * Rate limiting configuration.
 */
public record RateLimitConfig(
        String keyExpression,
        List<RateLimitWindow> limits,
        RateLimitAction action) {

    /**
     * Rate limit window definition.
     */
    public record RateLimitWindow(
            Duration window,
            int maxRequests) {
    }

    /**
     * Action to take when rate limit exceeded.
     */
    public enum RateLimitAction {
        /**
         * Reject the request
         */
        REJECT,
        /**
         * Queue for later processing
         */
        QUEUE,
        /**
         * Degrade service quality
         */
        DEGRADE
    }
}

/**
 * Validation configuration.
 */
public record ValidationConfig(
        boolean strictMode,
        boolean coerceTypes,
        List<FieldValidationRule> rules) {

    /**
     * Individual field validation rule.
     */
    public record FieldValidationRule(
            String field,
            String rule,
            String message) {
    }
}

/**
 * Check if this policy is applicable to a collection.
 *
 * @param collectionName the collection name
 * @param collectionLabels the collection's labels
 * @return true if policy applies
 */
public boolean appliesTo(String collectionName, Map<String, String> collectionLabels) {
        return enabled && selector.matches(collectionName, collectionLabels);
    }

    /**
     * Get rules that trigger for a specific operation.
     *
     * @param operation the operation
     * @return list of applicable rules
     */
    public List<CompiledPolicyRule> getRulesForOperation(OperationTrigger operation) {
        return rules.stream()
                .filter(rule -> rule.shouldTrigger(operation))
                .toList();
    }
}
