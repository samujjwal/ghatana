/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.learning;

import com.ghatana.datacloud.entity.audit.AuditAction;
import com.ghatana.datacloud.entity.audit.AuditLog;
import com.ghatana.datacloud.entity.audit.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Bridge that integrates AEP learning with Data Cloud audit trail.
 *
 * <p>This service ensures that all learning decisions (pattern discovery,
 * review approval/rejection, policy promotion) are logged to the audit trail
 * for compliance and forensic analysis.
 *
 * <p>DC-P1-003: Completes the agent/AEP review/learning/audit loop by providing
 * a unified audit interface for learning operations.
 *
 * @doc.type class
 * @doc.purpose Bridges AEP learning with Data Cloud audit trail
 * @doc.layer product
 * @doc.pattern Bridge
 */
public class AgentLearningAuditBridge {

    private static final Logger log = LoggerFactory.getLogger(AgentLearningAuditBridge.class);

    private final AuditRepository auditRepository;

    public AgentLearningAuditBridge(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Logs a pattern discovery event to the audit trail.
     *
     * @param tenantId the tenant ID
     * @param userId the user or system ID triggering the discovery
     * @param patternId the discovered pattern ID
     * @param confidence the confidence score
     * @param metadata additional metadata about the discovery
     */
    public void logPatternDiscovery(String tenantId, String userId, String patternId,
                                    double confidence, Map<String, Object> metadata) {
        String details = String.format("Pattern discovered with confidence %.2f, source=%s, episodeCount=%s",
            confidence, metadata.getOrDefault("source", "unknown"), metadata.getOrDefault("episodeCount", 0));
        
        AuditLog logEntry = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.CREATE_ENTITY)
            .resourceType("learning-pattern")
            .resourceId(patternId)
            .details(details)
            .timestamp(Instant.now())
            .build();

        try {
            auditRepository.save(logEntry);
            log.info("[learning-audit] Pattern discovery logged: tenant={}, pattern={}, confidence={}",
                tenantId, patternId, confidence);
        } catch (Exception e) {
            log.error("[learning-audit] Failed to log pattern discovery: pattern={}", patternId, e);
        }
    }

    /**
     * Logs a pattern review approval to the audit trail.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID approving the pattern
     * @param reviewId the review item ID
     * @param patternId the pattern ID being approved
     * @param metadata additional metadata about the approval
     */
    public void logPatternApproval(String tenantId, String userId, String reviewId,
                                   String patternId, Map<String, Object> metadata) {
        String details = String.format("Pattern approved via review %s by user %s at %s",
            reviewId, userId, Instant.now().toString());
        
        AuditLog logEntry = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.UPDATE_ENTITY)
            .resourceType("learning-pattern")
            .resourceId(patternId)
            .details(details)
            .timestamp(Instant.now())
            .build();

        try {
            auditRepository.save(logEntry);
            log.info("[learning-audit] Pattern approval logged: tenant={}, review={}, pattern={}",
                tenantId, reviewId, patternId);
        } catch (Exception e) {
            log.error("[learning-audit] Failed to log pattern approval: review={}", reviewId, e);
        }
    }

    /**
     * Logs a pattern review rejection to the audit trail.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID rejecting the pattern
     * @param reviewId the review item ID
     * @param patternId the pattern ID being rejected
     * @param reason the rejection reason
     * @param metadata additional metadata about the rejection
     */
    public void logPatternRejection(String tenantId, String userId, String reviewId,
                                    String patternId, String reason, Map<String, Object> metadata) {
        String details = String.format("Pattern rejected via review %s by user %s at %s: %s",
            reviewId, userId, Instant.now().toString(), reason);
        
        AuditLog logEntry = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.UPDATE_ENTITY)
            .resourceType("learning-pattern")
            .resourceId(patternId)
            .details(details)
            .timestamp(Instant.now())
            .build();

        try {
            auditRepository.save(logEntry);
            log.info("[learning-audit] Pattern rejection logged: tenant={}, review={}, pattern={}",
                tenantId, reviewId, patternId);
        } catch (Exception e) {
            log.error("[learning-audit] Failed to log pattern rejection: review={}", reviewId, e);
        }
    }

    /**
     * Logs a policy promotion event to the audit trail.
     *
     * @param tenantId the tenant ID
     * @param userId the user or system ID promoting the policy
     * @param policyId the policy ID being promoted
     * @param previousVersion the previous policy version (if any)
     * @param newVersion the new policy version
     * @param metadata additional metadata about the promotion
     */
    public void logPolicyPromotion(String tenantId, String userId, String policyId,
                                    String previousVersion, String newVersion, Map<String, Object> metadata) {
        String details = String.format("Policy promoted from %s to %s by user %s at %s",
            previousVersion, newVersion, userId, Instant.now().toString());
        
        AuditLog logEntry = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.UPDATE_ENTITY)
            .resourceType("learning-policy")
            .resourceId(policyId)
            .details(details)
            .changes(Map.of(
                "previousVersion", Map.entry(previousVersion, newVersion)
            ))
            .timestamp(Instant.now())
            .build();

        try {
            auditRepository.save(logEntry);
            log.info("[learning-audit] Policy promotion logged: tenant={}, policy={}, version={}",
                tenantId, policyId, newVersion);
        } catch (Exception e) {
            log.error("[learning-audit] Failed to log policy promotion: policy={}", policyId, e);
        }
    }

    /**
     * Logs a learning cycle completion to the audit trail.
     *
     * @param tenantId the tenant ID
     * @param userId the system ID running the learning cycle
     * @param patternsDiscovered number of patterns discovered
     * @param patternsUpdated number of patterns updated
     * @param durationMs duration of the learning cycle in milliseconds
     * @param metadata additional metadata about the cycle
     */
    public void logLearningCycle(String tenantId, String userId, int patternsDiscovered,
                                int patternsUpdated, long durationMs, Map<String, Object> metadata) {
        String details = String.format("Learning cycle completed by %s at %s: %d discovered, %d updated in %dms, manual=%s",
            userId, Instant.now().toString(), patternsDiscovered, patternsUpdated, durationMs, metadata.getOrDefault("manual", false));
        
        AuditLog logEntry = AuditLog.builder()
            .tenantId(tenantId)
            .userId(userId)
            .action(AuditAction.CREATE_ENTITY)
            .resourceType("learning-cycle")
            .resourceId("cycle-" + Instant.now().toEpochMilli())
            .details(details)
            .timestamp(Instant.now())
            .build();

        try {
            auditRepository.save(logEntry);
            log.info("[learning-audit] Learning cycle logged: tenant={}, discovered={}, updated={}",
                tenantId, patternsDiscovered, patternsUpdated);
        } catch (Exception e) {
            log.error("[learning-audit] Failed to log learning cycle: tenant={}", tenantId, e);
        }
    }
}
