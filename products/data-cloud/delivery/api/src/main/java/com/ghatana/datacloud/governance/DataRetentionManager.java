/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manager for data retention policies.
 *
 * @doc.type interface
 * @doc.purpose Data retention management
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface DataRetentionManager {

    /**
     * Apply retention policy to data.
     *
     * @param tenantId tenant identifier
     * @param policy retention policy
     * @return promise of retention result
     */
    Promise<RetentionResult> applyRetention(String tenantId, RetentionPolicy policy);

    /**
     * Get retention policy for tenant.
     *
     * @param tenantId tenant identifier
     * @param dataType data type
     * @return promise of policy
     */
    Promise<RetentionPolicy> getPolicy(String tenantId, DataType dataType);

    /**
     * Set retention policy.
     *
     * @param tenantId tenant identifier
     * @param policy retention policy
     * @return promise of saved policy
     */
    Promise<RetentionPolicy> setPolicy(String tenantId, RetentionPolicy policy);

    /**
     * Get data lifecycle status.
     *
     * @param tenantId tenant identifier
     * @return promise of lifecycle status
     */
    Promise<LifecycleStatus> getLifecycleStatus(String tenantId);

    /**
     * Archive old data.
     *
     * @param tenantId tenant identifier
     * @param olderThan archive data older than
     * @return promise of archived count
     */
    Promise<Integer> archiveOldData(String tenantId, Instant olderThan);

    /**
     * Delete expired data.
     *
     * @param tenantId tenant identifier
     * @param olderThan delete data older than
     * @return promise of deleted count
     */
    Promise<Integer> deleteExpiredData(String tenantId, Instant olderThan);

    /**
     * Get retention compliance report.
     *
     * @param tenantId tenant identifier
     * @return promise of compliance report
     */
    Promise<ComplianceReport> getComplianceReport(String tenantId);

    /**
     * Data types.
     */
    enum DataType {
        ENTITY_DATA, EVENT_DATA, AUDIT_LOG, REPORT_DATA,
        TEMPORARY_DATA, BACKUP_DATA, ANALYTICS_DATA
    }

    /**
     * Retention action.
     */
    enum RetentionAction {
        DELETE, ARCHIVE, ANONYMIZE, NOTIFY
    }

    /**
     * Retention policy.
     */
    record RetentionPolicy(
        String id,
        String tenantId,
        DataType dataType,
        Duration retentionPeriod,
        RetentionAction action,
        boolean allowExtension,
        List<String> legalHoldReasons,
        Map<String, Object> metadata
    ) {
        /**
         * Check if data has expired.
         */
        public boolean isExpired(Instant createdAt) {
            return Instant.now().isAfter(createdAt.plus(retentionPeriod));
        }
    }

    /**
     * Retention result.
     */
    record RetentionResult(
        int processed,
        int archived,
        int deleted,
        int anonymized,
        int notified,
        int errors
    ) {
        public boolean isSuccessful() {
            return errors == 0;
        }
    }

    /**
     * Lifecycle status.
     */
    record LifecycleStatus(
        String tenantId,
        long totalRecords,
        long activeRecords,
        long archivedRecords,
        long pendingDeletion,
        long underLegalHold,
        Map<DataType, Long> recordsByType
    ) {}

    /**
     * Compliance report.
     */
    record ComplianceReport(
        String tenantId,
        Instant generatedAt,
        boolean compliant,
        List<PolicyCompliance> policyCompliance,
        List<Violation> violations,
        List<Recommendation> recommendations
    ) {}

    /**
     * Policy compliance.
     */
    record PolicyCompliance(
        String policyId,
        DataType dataType,
        long totalRecords,
        long compliantRecords,
        double complianceRate
    ) {
        public boolean isCompliant() {
            return complianceRate >= 0.99; // 99% threshold
        }
    }

    /**
     * Compliance violation.
     */
    record Violation(
        String violationId,
        String policyId,
        DataType dataType,
        String description,
        long affectedRecords,
        Instant detectedAt,
        ViolationSeverity severity
    ) {}

    /**
     * Violation severity.
     */
    enum ViolationSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * Compliance recommendation.
     */
    record Recommendation(
        String recommendationId,
        String title,
        String description,
        DataType dataType,
        int priority
    ) {}
}
