/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.audit;

import com.ghatana.datacloud.entity.audit.AuditAction;
import com.ghatana.datacloud.entity.audit.AuditLog;

import java.time.Instant;
import java.util.Map;

/**
 * Service for auditing governance operations.
 *
 * <p>This service ensures that all mutating governance operations
 * are logged for compliance and forensic analysis.
 *
 * @doc.type class
 * @doc.purpose Audits all mutating governance operations
 * @doc.layer product
 * @doc.pattern Service
 */
public final class GovernanceAuditService {

    private final AuditLogger auditLogger;

    public GovernanceAuditService(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * Logs a policy creation event.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param policyId the policy ID
     * @param policyDetails the policy details
     */
    public void logPolicyCreated(String tenantId, String userId, String policyId, Map<String, Object> policyDetails) {
        AuditLog log = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.CREATE_ENTITY)
            .resourceType("policy")
            .resourceId(policyId)
            .details("Policy created: " + policyId)
            .timestamp(Instant.now())
            .build();

        auditLogger.log(log);
    }

    /**
     * Logs a policy update event.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param policyId the policy ID
     * @param changes the policy changes
     */
    public void logPolicyUpdated(String tenantId, String userId, String policyId, Map<String, Map.Entry<String, String>> changes) {
        AuditLog log = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.UPDATE_ENTITY)
            .resourceType("policy")
            .resourceId(policyId)
            .changes(changes)
            .details("Policy updated: " + policyId)
            .timestamp(Instant.now())
            .build();

        auditLogger.log(log);
    }

    /**
     * Logs a policy deletion event.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param policyId the policy ID
     */
    public void logPolicyDeleted(String tenantId, String userId, String policyId) {
        AuditLog log = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.DELETE_ENTITY)
            .resourceType("policy")
            .resourceId(policyId)
            .details("Policy deleted: " + policyId)
            .timestamp(Instant.now())
            .build();

        auditLogger.log(log);
    }

    /**
     * Logs a rule creation event.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param ruleId the rule ID
     * @param ruleDetails the rule details
     */
    public void logRuleCreated(String tenantId, String userId, String ruleId, Map<String, Object> ruleDetails) {
        AuditLog log = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.CREATE_ENTITY)
            .resourceType("rule")
            .resourceId(ruleId)
            .details("Rule created: " + ruleId)
            .timestamp(Instant.now())
            .build();

        auditLogger.log(log);
    }

    /**
     * Logs a rule update event.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param ruleId the rule ID
     * @param changes the rule changes
     */
    public void logRuleUpdated(String tenantId, String userId, String ruleId, Map<String, Map.Entry<String, String>> changes) {
        AuditLog log = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.UPDATE_ENTITY)
            .resourceType("rule")
            .resourceId(ruleId)
            .changes(changes)
            .details("Rule updated: " + ruleId)
            .timestamp(Instant.now())
            .build();

        auditLogger.log(log);
    }

    /**
     * Logs a rule deletion event.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param ruleId the rule ID
     */
    public void logRuleDeleted(String tenantId, String userId, String ruleId) {
        AuditLog log = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.DELETE_ENTITY)
            .resourceType("rule")
            .resourceId(ruleId)
            .details("Rule deleted: " + ruleId)
            .timestamp(Instant.now())
            .build();

        auditLogger.log(log);
    }

    /**
     * Interface for logging audit events.
     */
    public interface AuditLogger {
        /**
         * Logs an audit event.
         *
         * @param log the audit log entry
         */
        void log(AuditLog log);
    }
}
