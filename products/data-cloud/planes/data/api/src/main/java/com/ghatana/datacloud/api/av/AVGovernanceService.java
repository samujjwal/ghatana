/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.av;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Governance service for AV content.
 * 
 * P8.5: Add AV governance: retention, redaction, legal hold, consent.
 * Manages retention policies, redaction requests, legal holds, and consent tracking.
 * 
 * @doc.type interface
 * @doc.purpose AV governance management
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AVGovernanceService {

    /**
     * Applies a retention policy to an AV asset.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @param policy the retention policy
     * @return a Promise that resolves to the result
     */
    Promise<PolicyResult> applyRetentionPolicy(String assetId, String tenantId, AVAsset.AVRetention policy);

    /**
     * Requests redaction of sensitive content in an AV asset.
     *
     * @param request the redaction request
     * @return a Promise that resolves to the redaction job
     */
    Promise<RedactionJob> requestRedaction(RedactionRequest request);

    /**
     * Gets the status of a redaction job.
     *
     * @param jobId the job ID
     * @return a Promise that resolves to the redaction job
     */
    Promise<RedactionJob> getRedactionStatus(String jobId);

    /**
     * Places a legal hold on an AV asset.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @param hold the legal hold details
     * @return a Promise that resolves to the result
     */
    Promise<HoldResult> placeLegalHold(String assetId, String tenantId, LegalHold hold);

    /**
     * Releases a legal hold on an AV asset.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @param holdId the hold ID
     * @return a Promise that resolves to the result
     */
    Promise<HoldResult> releaseLegalHold(String assetId, String tenantId, String holdId);

    /**
     * Updates consent information for an AV asset.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @param consent the consent information
     * @return a Promise that resolves to the result
     */
    Promise<ConsentResult> updateConsent(String assetId, String tenantId, AVAsset.AVConsent consent);

    /**
     * Gets legal holds for an asset.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @return a Promise that resolves to the list of holds
     */
    Promise<List<LegalHold>> getLegalHolds(String assetId, String tenantId);

    /**
     * Audits compliance for AV assets.
     *
     * @param tenantId the tenant ID
     * @param filters audit filters
     * @return a Promise that resolves to the audit report
     */
    Promise<AuditReport> auditCompliance(String tenantId, AuditFilters filters);

    /**
     * Redaction request.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @param redactionType type of redaction
     * @param redactionRules redaction rules to apply
     * @param regions regions to redact (for video)
     * @param timestamps timestamps to redact (for audio)
     * @param requestedBy who requested the redaction
     * @param reason reason for redaction
     */
    record RedactionRequest(
            String assetId,
            String tenantId,
            RedactionType redactionType,
            List<RedactionRule> redactionRules,
            List<Region> regions,
            List<TimeRange> timestamps,
            String requestedBy,
            String reason) {

        public RedactionRequest(
                String assetId,
                String tenantId,
                RedactionType redactionType,
                List<RedactionRule> redactionRules,
                String requestedBy,
                String reason) {
            this(assetId, tenantId, redactionType, redactionRules, List.of(), List.of(), requestedBy, reason);
        }
    }

    /**
     * Redaction type.
     */
    enum RedactionType {
        VISUAL_BLUR,
        VISUAL_BLACKOUT,
        AUDIO_MUTE,
        AUDIO_BEEP,
        TEXT_REDACT,
        FACE_BLUR,
        LICENSE_PLATE_BLUR
    }

    /**
     * Redaction rule.
     *
     * @param ruleId rule ID
     * @param ruleType type of content to redact
     * @param confidence minimum confidence for detection
     * @param parameters rule-specific parameters
     */
    record RedactionRule(
            String ruleId,
            RuleType ruleType,
            double confidence,
            Map<String, Object> parameters) {

        public enum RuleType {
            FACE,
            LICENSE_PLATE,
            PERSON_NAME,
            PHONE_NUMBER,
            EMAIL,
            SSN,
            CREDIT_CARD,
            CUSTOM_OBJECT,
            CUSTOM_KEYWORD
        }
    }

    /**
     * Region for video redaction.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param width width
     * @param height height
     * @param timestampMs timestamp
     */
    record Region(int x, int y, int width, int height, long timestampMs) {}

    /**
     * Time range for audio redaction.
     *
     * @param startTimeMs start time
     * @param endTimeMs end time
     */
    record TimeRange(long startTimeMs, long endTimeMs) {}

    /**
     * Redaction job.
     *
     * @param id job ID
     * @param assetId the asset ID
     * @param tenantId tenant ID
     * @param status job status
     * @param progress progress percentage
     * @param redactedUri URI of redacted asset
     * @param error error message (if failed)
     * @param startedAt when the job started
     * @param completedAt when the job completed
     */
    record RedactionJob(
            String id,
            String assetId,
            String tenantId,
            JobStatus status,
            int progress,
            String redactedUri,
            String error,
            Instant startedAt,
            Instant completedAt) {

        public enum JobStatus {
            PENDING,
            RUNNING,
            COMPLETED,
            FAILED,
            CANCELLED
        }
    }

    /**
     * Legal hold.
     *
     * @param id hold ID
     * @param assetId the asset ID
     * @param tenantId tenant ID
     * @param caseReference case or matter reference
     * @param placedBy who placed the hold
     * @param reason reason for the hold
     * @param expiresAt when the hold expires (null for indefinite)
     * @param createdAt when the hold was placed
     * @param releasedAt when the hold was released (null if active)
     */
    record LegalHold(
            String id,
            String assetId,
            String tenantId,
            String caseReference,
            String placedBy,
            String reason,
            Instant expiresAt,
            Instant createdAt,
            Instant releasedAt) {

        public boolean isActive() {
            return releasedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()));
        }
    }

    /**
     * Hold result.
     *
     * @param success whether the operation succeeded
     * @param holdId the hold ID
     * @param error error message (if failed)
     */
    record HoldResult(boolean success, String holdId, String error) {
        public static HoldResult success(String holdId) {
            return new HoldResult(true, holdId, null);
        }

        public static HoldResult failed(String error) {
            return new HoldResult(false, null, error);
        }
    }

    /**
     * Consent result.
     *
     * @param success whether the operation succeeded
     * @param assetId the asset ID
     * @param error error message (if failed)
     */
    record ConsentResult(boolean success, String assetId, String error) {
        public static ConsentResult success(String assetId) {
            return new ConsentResult(true, assetId, null);
        }

        public static ConsentResult failed(String error) {
            return new ConsentResult(false, null, error);
        }
    }

    /**
     * Policy result.
     *
     * @param success whether the operation succeeded
     * @param assetId the asset ID
     * @param policyId the policy ID
     * @param error error message (if failed)
     */
    record PolicyResult(boolean success, String assetId, String policyId, String error) {
        public static PolicyResult success(String assetId, String policyId) {
            return new PolicyResult(true, assetId, policyId, null);
        }

        public static PolicyResult failed(String error) {
            return new PolicyResult(false, null, null, error);
        }
    }

    /**
     * Audit filters.
     *
     * @param consentFilter consent status filter
     * @param retentionFilter retention compliance filter
     * @param legalHoldFilter legal hold filter
     * @param dateRange date range for audit
     */
    record AuditFilters(
            Boolean consentFilter,
            Boolean retentionFilter,
            Boolean legalHoldFilter,
            DateRange dateRange) {

        public record DateRange(Instant start, Instant end) {}
    }

    /**
     * Audit report.
     *
     * @param tenantId tenant ID
     * @param auditedAt when the audit was run
     * @param totalAssets total number of assets audited
     * @param compliantAssets number of compliant assets
     * @param nonCompliantAssets number of non-compliant assets
     * @param violations list of violations found
     * @param summary audit summary
     */
    record AuditReport(
            String tenantId,
            Instant auditedAt,
            int totalAssets,
            int compliantAssets,
            int nonCompliantAssets,
            List<Violation> violations,
            String summary) {

        public double complianceRate() {
            return totalAssets > 0 ? (double) compliantAssets / totalAssets : 0.0;
        }
    }

    /**
     * Compliance violation.
     *
     * @param assetId the asset ID
     * @param violationType type of violation
     * @param description violation description
     * @param severity violation severity
     * @param detectedAt when the violation was detected
     */
    record Violation(
            String assetId,
            ViolationType violationType,
            String description,
            Severity severity,
            Instant detectedAt) {

        public enum ViolationType {
            MISSING_CONSENT,
            EXPIRED_RETENTION,
            EXPIRED_CONSENT,
            PENDING_REDACTION,
            VIOLATES_LEGAL_HOLD
        }

        public enum Severity {
            LOW,
            MEDIUM,
            HIGH,
            CRITICAL
        }
    }
}
