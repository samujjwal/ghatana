/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.datacloud.governance.audit.GovernanceAuditService;
import com.ghatana.datacloud.governance.policy.ClassificationPolicy;
import com.ghatana.datacloud.governance.policy.RedactionPolicy;
import com.ghatana.datacloud.governance.policy.RetentionPolicy;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestration service for governance operations.
 *
 * <p><b>Purpose</b><br>
 * Provides first-class backend flows for retention, redaction, audit, and classification.
 * Orchestrates governance policies across the Data Cloud platform.
 *
 * <p><b>Capabilities</b><br>
 * <ul>
 *   <li><b>Retention</b>: Automated data retention and purging based on policies</li>
 *   <li><b>Redaction</b>: Sensitive data redaction based on policies</li>
 *   <li><b>Audit</b>: Comprehensive audit logging for all governance operations</li>
 *   <li><b>Classification</b>: Data sensitivity classification and labeling</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Orchestration service for retention, redaction, audit, and classification flows
 * @doc.layer product
 * @doc.pattern Service
 */
public class GovernanceOrchestrationService {

    private final GovernanceAuditService auditService;
    private final RetentionExecutor retentionExecutor;
    private final RedactionExecutor redactionExecutor;
    private final ClassificationExecutor classificationExecutor;

    public GovernanceOrchestrationService(
            GovernanceAuditService auditService,
            RetentionExecutor retentionExecutor,
            RedactionExecutor redactionExecutor,
            ClassificationExecutor classificationExecutor) {
        this.auditService = auditService;
        this.retentionExecutor = retentionExecutor;
        this.redactionExecutor = redactionExecutor;
        this.classificationExecutor = classificationExecutor;
    }

    // ==================== Retention Operations ====================

    /**
     * Executes retention policy for a tenant.
     *
     * @param tenantId tenant identifier
     * @param policyId retention policy ID
     * @param userId user initiating the operation
     * @return Promise of retention result
     */
    public Promise<RetentionResult> executeRetention(String tenantId, String policyId, String userId) {
        return retentionExecutor.execute(tenantId, policyId)
            .then(result -> {
                auditService.logPolicyCreated(tenantId, userId, policyId, 
                    Map.of("retainedCount", result.retainedCount(), "deletedCount", result.deletedCount()));
                return Promise.of(result);
            });
    }

    /**
     * Schedules retention policy execution.
     *
     * @param tenantId tenant identifier
     * @param policyId retention policy ID
     * @param scheduledTime when to execute
     * @param userId user scheduling the operation
     * @return Promise of scheduled job ID
     */
    public Promise<String> scheduleRetention(String tenantId, String policyId, Instant scheduledTime, String userId) {
        String jobId = UUID.randomUUID().toString();
        return retentionExecutor.schedule(tenantId, policyId, scheduledTime, jobId)
            .then(ignored -> {
                auditService.logPolicyCreated(tenantId, userId, policyId, 
                    Map.of("scheduledTime", scheduledTime.toString(), "jobId", jobId));
                return Promise.of(jobId);
            });
    }

    // ==================== Redaction Operations ====================

    /**
     * Applies redaction policy to entity data.
     *
     * @param tenantId tenant identifier
     * @param policyId redaction policy ID
     * @param entityId entity ID
     * @param userRole user role for exemption check
     * @param userId user initiating the operation
     * @return Promise of redacted data
     */
    public Promise<Map<String, Object>> applyRedaction(
            String tenantId, String policyId, String entityId, String userRole, String userId) {
        return redactionExecutor.apply(tenantId, policyId, entityId, userRole)
            .then(redactedData -> {
                Map<String, Map.Entry<String, String>> auditData = new java.util.HashMap<>();
                auditData.put("entityId", Map.entry("value", entityId));
                auditData.put("redactedFields", Map.entry("value", redactedData.keySet().toString()));
                auditService.logPolicyUpdated(tenantId, userId, policyId, auditData);
                return Promise.of(redactedData);
            });
    }

    /**
     * Bulk redaction for multiple entities.
     *
     * @param tenantId tenant identifier
     * @param policyId redaction policy ID
     * @param entityIds list of entity IDs
     * @param userRole user role for exemption check
     * @param userId user initiating the operation
     * @return Promise of redaction result
     */
    public Promise<RedactionResult> bulkRedaction(
            String tenantId, String policyId, List<String> entityIds, String userRole, String userId) {
        return redactionExecutor.bulkApply(tenantId, policyId, entityIds, userRole)
            .then(result -> {
                Map<String, Map.Entry<String, String>> auditData = new java.util.HashMap<>();
                auditData.put("entityCount", Map.entry("value", String.valueOf(entityIds.size())));
                auditData.put("redactedCount", Map.entry("value", String.valueOf(result.redactedCount())));
                auditService.logPolicyUpdated(tenantId, userId, policyId, auditData);
                return Promise.of(result);
            });
    }

    // ==================== Classification Operations ====================

    /**
     * Classifies entity data based on classification policy.
     *
     * @param tenantId tenant identifier
     * @param policyId classification policy ID
     * @param entityId entity ID
     * @param userId user initiating the operation
     * @return Promise of classification result
     */
    public Promise<ClassificationResult> classifyEntity(
            String tenantId, String policyId, String entityId, String userId) {
        return classificationExecutor.classify(tenantId, policyId, entityId)
            .then(result -> {
                auditService.logRuleCreated(tenantId, userId, entityId, 
                    Map.of("sensitivityLevel", result.sensitivityLevel(), "fields", result.classifiedFields()));
                return Promise.of(result);
            });
    }

    /**
     * Bulk classification for multiple entities.
     *
     * @param tenantId tenant identifier
     * @param policyId classification policy ID
     * @param entityIds list of entity IDs
     * @param userId user initiating the operation
     * @return Promise of classification result
     */
    public Promise<ClassificationResult> bulkClassify(
            String tenantId, String policyId, List<String> entityIds, String userId) {
        return classificationExecutor.bulkClassify(tenantId, policyId, entityIds)
            .then(result -> {
                auditService.logRuleCreated(tenantId, userId, "bulk-classify", 
                    Map.of("entityCount", entityIds.size(), "classifiedCount", result.classifiedCount()));
                return Promise.of(result);
            });
    }

    // ==================== Result Records ====================

    public record RetentionResult(
        int retainedCount,
        int deletedCount,
        int archivedCount,
        long durationMs
    ) {}

    public record RedactionResult(
        int redactedCount,
        int skippedCount,
        List<String> redactedFields
    ) {}

    public record ClassificationResult(
        String sensitivityLevel,
        int classifiedCount,
        List<String> classifiedFields,
        boolean requiresManualReview
    ) {}

    // ==================== Executor Interfaces ====================

    public interface RetentionExecutor {
        Promise<RetentionResult> execute(String tenantId, String policyId);
        Promise<Void> schedule(String tenantId, String policyId, Instant scheduledTime, String jobId);
    }

    public interface RedactionExecutor {
        Promise<Map<String, Object>> apply(String tenantId, String policyId, String entityId, String userRole);
        Promise<RedactionResult> bulkApply(String tenantId, String policyId, List<String> entityIds, String userRole);
    }

    public interface ClassificationExecutor {
        Promise<ClassificationResult> classify(String tenantId, String policyId, String entityId);
        Promise<ClassificationResult> bulkClassify(String tenantId, String policyId, List<String> entityIds);
    }
}
