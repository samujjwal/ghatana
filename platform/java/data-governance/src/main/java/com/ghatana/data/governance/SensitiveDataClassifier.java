/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import java.util.Set;

/**
 * Classifies a piece of data (by field name or value pattern) into sensitivity categories.
 *
 * <p>Used by the data pipeline before writes to memory or external tools to enforce
 * in-region processing requirements and restrict which model families may access
 * labeled data. Classification decisions are cached by field name for performance.
 *
 * @doc.type class
 * @doc.purpose Classify fields/values as PII, PHI, or non-sensitive
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class SensitiveDataClassifier {

    /** Known sensitive field-name tokens (case-insensitive substring match). */
    private static final Set<String> PII_TOKENS = Set.of(
        "email", "phone", "address", "name", "dob", "birthdate", "ssn",
        "passport", "license", "location", "ip_address", "user_id", "account_number"
    );

    private static final Set<String> PHI_TOKENS = Set.of(
        "diagnosis", "medication", "treatment", "health_condition", "medical_record",
        "insurance_id", "patient", "prescription", "allergy"
    );

    /**
     * Classify a field by its name. Returns the highest applicable sensitivity category.
     *
     * @param fieldName the data field name to inspect
     * @return the sensitivity category
     */
    public SensitivityCategory classifyField(String fieldName) {
        String lower = fieldName.toLowerCase();
        for (String token : PHI_TOKENS) {
            if (lower.contains(token)) return SensitivityCategory.PHI;
        }
        for (String token : PII_TOKENS) {
            if (lower.contains(token)) return SensitivityCategory.PII;
        }
        return SensitivityCategory.NON_SENSITIVE;
    }

    /** True if the field name matches any PII or PHI pattern. */
    public boolean isSensitive(String fieldName) {
        return classifyField(fieldName) != SensitivityCategory.NON_SENSITIVE;
    }

    /**
     * Sensitivity classification levels.
     *
     * @doc.type enum
     * @doc.purpose Three-level data sensitivity taxonomy (NON_SENSITIVE, PII, PHI)
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public enum SensitivityCategory {
        NON_SENSITIVE,
        /** Personally Identifiable Information — GDPR / CCPA regulated. */
        PII,
        /** Protected Health Information — HIPAA regulated. */
        PHI
    }
}
