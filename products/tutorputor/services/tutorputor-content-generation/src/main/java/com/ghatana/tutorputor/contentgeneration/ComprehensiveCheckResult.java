package com.ghatana.tutorputor.contentgeneration;

/**
 * Combined result of a comprehensive content safety check.
 *
 * <p>Aggregates policy check result, PII detection, and content classification
 * into a single response for convenience.
 *
 * @param policyResult the core policy check result
 * @param classification the content classification
 * @param piiDetected whether PII was detected in the content
 *
 * @doc.type record
 * @doc.purpose Comprehensive content check aggregate result
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ComprehensiveCheckResult(
        PolicyCheckResult policyResult,
        ContentClassification classification,
        boolean piiDetected
) {}
