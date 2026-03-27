/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Classifier-driven {@link DataMinimizationEngine}.
 *
 * <p>Uses {@link SensitiveDataClassifier} to categorize each field by name, then
 * applies purpose-specific rules:
 * <ul>
 *   <li>PHI fields are <em>always</em> removed unless the purpose is {@code "healthcare"}.</li>
 *   <li>PII fields are <em>masked</em> unless the purpose is in the PII-permitted set for the tenant.</li>
 *   <li>Non-sensitive fields pass through unchanged.</li>
 * </ul>
 * Tenant-specific PII-permitted purposes default to {@code Set.of("analytics", "personalisation")}.
 *
 * @doc.type class
 * @doc.purpose Default field-level data minimization using SensitiveDataClassifier
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DefaultDataMinimizationEngine implements DataMinimizationEngine {

    private static final String MASK = "***";
    private static final Set<String> DEFAULT_PII_PURPOSES = Set.of("analytics", "personalisation");
    private static final String HEALTHCARE_PURPOSE = "healthcare";

    private final SensitiveDataClassifier classifier;

    /**
     * Create an engine backed by the provided classifier.
     *
     * @param classifier the field-name classifier to use
     */
    public DefaultDataMinimizationEngine(SensitiveDataClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public Promise<Map<String, Object>> minimize(
            String tenantId, String purpose, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String field = entry.getKey();
            SensitiveDataClassifier.SensitivityCategory category = classifier.classifyField(field);

            switch (category) {
                case PHI -> {
                    if (HEALTHCARE_PURPOSE.equals(purpose)) {
                        result.put(field, entry.getValue());
                    }
                    // otherwise drop entirely
                }
                case PII -> {
                    if (DEFAULT_PII_PURPOSES.contains(purpose)) {
                        result.put(field, MASK);
                    }
                    // otherwise drop
                }
                default -> result.put(field, entry.getValue());
            }
        }

        return Promise.of(result);
    }
}
