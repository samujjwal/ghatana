/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import java.util.Set;

/**
 * Classification policy for data sensitivity levels.
 *
 * <p>Defines how data should be classified based on sensitivity and
 * what governance rules apply to each classification level.
 *
 * @doc.type record
 * @doc.purpose Defines data classification requirements for sensitivity
 * @doc.layer product
 * @doc.pattern Policy
 */
public record ClassificationPolicy(
    String policyId,
    Set<String> sensitiveFields,
    SensitivityLevel defaultLevel,
    boolean autoClassify,
    boolean requireManualReview) {

    public enum SensitivityLevel {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        RESTRICTED,
        CRITICAL
    }

    public ClassificationPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (sensitiveFields == null) {
            sensitiveFields = Set.of();
        }
        if (defaultLevel == null) {
            defaultLevel = SensitivityLevel.INTERNAL;
        }
    }

    /**
     * Determines the sensitivity level for a field.
     *
     * @param field the field name
     * @return the sensitivity level
     */
    public SensitivityLevel classifyField(String field) {
        if (sensitiveFields.contains(field)) {
            // In a real implementation, this would look up the field's specific level
            // For now, default to CONFIDENTIAL for sensitive fields
            return SensitivityLevel.CONFIDENTIAL;
        }
        return defaultLevel;
    }

    /**
     * Checks if a field requires manual review.
     *
     * @param field the field name
     * @return true if manual review is required
     */
    public boolean requiresManualReview(String field) {
        if (!requireManualReview) {
            return false;
        }
        SensitivityLevel level = classifyField(field);
        return level == SensitivityLevel.RESTRICTED || level == SensitivityLevel.CRITICAL;
    }

    /**
     * Gets the retention period based on sensitivity level.
     *
     * @param level the sensitivity level
     * @return retention period in days
     */
    public int getRetentionDays(SensitivityLevel level) {
        return switch (level) {
            case PUBLIC -> 365 * 10; // 10 years
            case INTERNAL -> 365 * 7; // 7 years
            case CONFIDENTIAL -> 365 * 5; // 5 years
            case RESTRICTED -> 365 * 3; // 3 years
            case CRITICAL -> 365 * 2; // 2 years
        };
    }

    /**
     * Checks if a field should be redacted based on sensitivity.
     *
     * @param field the field name
     * @param userRole the user's role
     * @return true if redaction is required
     */
    public boolean shouldRedact(String field, String userRole) {
        SensitivityLevel level = classifyField(field);
        // In a real implementation, this would check role-based access
        return level == SensitivityLevel.RESTRICTED || level == SensitivityLevel.CRITICAL;
    }
}
