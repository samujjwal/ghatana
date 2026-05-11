/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.observability;

import com.ghatana.yappc.governance.route.PrivacyClassification;

import java.util.Map;

/**
 * Service for enforcing privacy classification in runtime behavior.
 *
 * <p>This service ensures that telemetry and logs respect privacy classification:
 * <ul>
 *   <li>PUBLIC: No sensitive payloads, can be exposed publicly</li>
 *   <li>INTERNAL: Workspace/project metadata, internal use only</li>
 *   <li>CONFIDENTIAL: Generated artifacts, source imports, requirements, evidence</li>
 *   <li>RESTRICTED: Rollback, promotion, policy decisions, sensitive previews</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Privacy classification enforcement for telemetry/logs
 * @doc.layer product
 * @doc.pattern Service
 */
public interface PrivacyEnforcementService {

    /**
     * Sanitizes telemetry data based on privacy classification.
     *
     * @param data The raw telemetry data
     * @param classification The privacy classification
     * @return Sanitized telemetry data
     */
    Map<String, Object> sanitizeTelemetry(Map<String, Object> data, PrivacyClassification classification);

    /**
     * Sanitizes log message based on privacy classification.
     *
     * @param message The raw log message
     * @param classification The privacy classification
     * @return Sanitized log message
     */
    String sanitizeLogMessage(String message, PrivacyClassification classification);

    /**
     * Checks if data can be included in telemetry based on classification.
     *
     * @param classification The privacy classification
     * @return true if data can be included, false otherwise
     */
    boolean canIncludeInTelemetry(PrivacyClassification classification);

    /**
     * Checks if data can be included in logs based on classification.
     *
     * @param classification The privacy classification
     * @return true if data can be included, false otherwise
     */
    boolean canIncludeInLogs(PrivacyClassification classification);

    /**
     * Redacts sensitive fields from data based on classification.
     *
     * @param data The data to redact
     * @param classification The privacy classification
     * @return Redacted data
     */
    Map<String, Object> redactSensitiveFields(Map<String, Object> data, PrivacyClassification classification);
}
