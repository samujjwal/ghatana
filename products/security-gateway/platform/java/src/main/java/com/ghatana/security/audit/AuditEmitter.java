/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 *
 * This source code and the accompanying materials are the confidential
 * and proprietary information of Ghatana Inc. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Ghatana Inc.
 *
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */
package com.ghatana.security.audit;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.core.exception.ErrorCode;
import com.ghatana.platform.core.exception.ErrorCodeMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AuditEmitter provides helper methods for emitting audit events related to
 * pattern learning operations, policy enforcement, and governance actions.
 * 
 * This class standardizes audit event creation and emission across the system,
 * ensuring consistent audit logging for compliance and security monitoring.
 
 *
 * @doc.type class
 * @doc.purpose Audit emitter
 * @doc.layer core
 * @doc.pattern Component
*/
public class AuditEmitter {
    private static final Logger logger = LoggerFactory.getLogger(AuditEmitter.class);
    
    // Standard event types for pattern learning operations
    public static final String PATTERN_RECOMMEND_REQUEST = "PATTERN_RECOMMEND_REQUEST";
    public static final String PATTERN_RECOMMEND_SUCCESS = "PATTERN_RECOMMEND_SUCCESS";
    public static final String PATTERN_RECOMMEND_FAILURE = "PATTERN_RECOMMEND_FAILURE";
    
    public static final String PATTERN_EVALUATE_REQUEST = "PATTERN_EVALUATE_REQUEST";
    public static final String PATTERN_EVALUATE_SUCCESS = "PATTERN_EVALUATE_SUCCESS";
    public static final String PATTERN_EVALUATE_FAILURE = "PATTERN_EVALUATE_FAILURE";
    
    public static final String POLICY_ENFORCEMENT_GRANTED = "POLICY_ENFORCEMENT_GRANTED";
    public static final String POLICY_ENFORCEMENT_DENIED = "POLICY_ENFORCEMENT_DENIED";
    
    public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";
    public static final String AUTHENTICATION_FAILURE = "AUTHENTICATION_FAILURE";
    public static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";
    
    private final AuditEventSink auditEventSink;
    
    /**
     * Creates a new AuditEmitter with the specified audit event sink.
     *
     * @param auditEventSink the sink for emitting audit events
     */
    public AuditEmitter(AuditEventSink auditEventSink) {
        this.auditEventSink = auditEventSink;
    }
    
    /**
     * Emits an audit event for a pattern recommendation request.
     *
     * @param userId the ID of the user making the request
     * @param tenantId the tenant ID
     * @param details additional details about the request
     * @return Promise that completes when the audit event is emitted
     */
    public Promise<Void> emitPatternRecommendRequest(String userId, String tenantId, 
                                                              Map<String, Object> details) {
        AuditEvent event = createAuditEvent(
                PATTERN_RECOMMEND_REQUEST,
                userId,
                "patterns/recommend",
                "REQUEST",
                "SUCCESS",
                enhanceDetails(details, tenantId)
        );
        
        return emitAuditEvent(event);
    }
    
    /**
     * Emits an audit event for a successful pattern recommendation.
     *
     * @param userId the ID of the user who made the request
     * @param tenantId the tenant ID
     * @param recommendationCount the number of recommendations returned
     * @param processingTimeMs the processing time in milliseconds
     * @return Promise that completes when the audit event is emitted
     */
    public Promise<Void> emitPatternRecommendSuccess(String userId, String tenantId, 
                                                              int recommendationCount, long processingTimeMs) {
        Map<String, Object> details = Map.of(
                "tenantId", tenantId,
                "recommendationCount", recommendationCount,
                "processingTimeMs", processingTimeMs
        );
        
        AuditEvent event = createAuditEvent(
                PATTERN_RECOMMEND_SUCCESS,
                userId,
                "patterns/recommend",
                "RECOMMEND",
                "SUCCESS",
                details
        );
        
        return emitAuditEvent(event);
    }
    
    /**
     * Emits an audit event for a failed pattern recommendation.
     *
     * @param userId the ID of the user who made the request
     * @param tenantId the tenant ID
     * @param errorMessage the error message
     * @param errorCode the error code (optional)
     * @return Promise that completes when the audit event is emitted
     */
    public Promise<Void> emitPatternRecommendFailure(String userId, String tenantId, 
                                                              String errorMessage, String errorCode) {
        Map<String, Object> details = Map.of(
                "tenantId", tenantId,
                "errorMessage", errorMessage,
                "errorCode", errorCode != null ? errorCode : ErrorCodeMappers.fromIngress(ErrorCode.UNKNOWN_ERROR.name()).name()
        );
        
        AuditEvent event = createAuditEvent(
                PATTERN_RECOMMEND_FAILURE,
                userId,
                "patterns/recommend",
                "RECOMMEND",
                "FAILURE",
                details
        );
        
        return emitAuditEvent(event);
    }
    
    /**
     * Emits an audit event for a pattern evaluation request.
     *
     * @param userId the ID of the user making the request
     * @param tenantId the tenant ID
     * @param shadowMode whether shadow mode is enabled
     * @param details additional details about the request
     * @return Promise that completes when the audit event is emitted
     */
    public Promise<Void> emitPatternEvaluateRequest(String userId, String tenantId, 
                                                             boolean shadowMode, Map<String, Object> details) {
        Map<String, Object> enhancedDetails = enhanceDetails(details, tenantId);
        enhancedDetails.put("shadowMode", shadowMode);
        
        AuditEvent event = createAuditEvent(
                PATTERN_EVALUATE_REQUEST,
                userId,
                "patterns/evaluate",
                "REQUEST",
                "SUCCESS",
                enhancedDetails
        );
        
        return emitAuditEvent(event);
    }
    
    /**
     * Emits an audit event for a successful pattern evaluation.
     *
     * @param userId the ID of the user who made the request
     * @param tenantId the tenant ID
     * @param evaluationCount the number of patterns evaluated
     * @param shadowMode whether shadow mode was enabled
     * @param processingTimeMs the processing time in milliseconds
     * @return Promise that completes when the audit event is emitted
     */
    public Promise<Void> emitPatternEvaluateSuccess(String userId, String tenantId, 
                                                             int evaluationCount, boolean shadowMode, 
                                                             long processingTimeMs) {
        Map<String, Object> details = Map.of(
                "tenantId", tenantId,
                "evaluationCount", evaluationCount,
                "shadowMode", shadowMode,
                "processingTimeMs", processingTimeMs
        );
        
        AuditEvent event = createAuditEvent(
                PATTERN_EVALUATE_SUCCESS,
                userId,
                "patterns/evaluate",
                "EVALUATE",
                "SUCCESS",
                details
        );
        
        return emitAuditEvent(event);
    }
    
    /**
     * Emits an audit event for a failed pattern evaluation.
     *
     * @param userId the ID of the user who made the request
     * @param tenantId the tenant ID
     * @param errorMessage the error message
     * @param shadowMode whether shadow mode was enabled
     * @return Promise that completes when the audit event is emitted
     */
    public Promise<Void> emitPatternEvaluateFailure(String userId, String tenantId, 
                                                             String errorMessage, boolean shadowMode) {
        Map<String, Object> details = Map.of(
                "tenantId", tenantId,
                "errorMessage", errorMessage,
                "shadowMode", shadowMode,
                "errorCode", ErrorCodeMappers.fromIngress(ErrorCode.UNKNOWN_ERROR.name()).name()
        );
        
        AuditEvent event = createAuditEvent(
                PATTERN_EVALUATE_FAILURE,
                userId,
                "patterns/evaluate",
                "EVALUATE",
                "FAILURE",
                details
        );
        
        return emitAuditEvent(event);
    }
    
    /**
     * Emits an audit event for policy enforcement decisions.
     *
     * @param userId the ID of the user subject to policy enforcement
     * @param resource the resource being accessed
     * @param permission the permission being checked
     * @param granted whether access was granted
     * @param reason the reason for the decision
     * @return Promise that completes when the audit event is emitted
     */
    public Promise<Void> emitPolicyEnforcement(String userId, String resource, 
                                                        String permission, boolean granted, String reason) {
        Map<String, Object> details = Map.of(
                "permission", permission,
                "reason", reason
        );
        
        String eventType = granted ? POLICY_ENFORCEMENT_GRANTED : POLICY_ENFORCEMENT_DENIED;
        String status = granted ? "SUCCESS" : "DENIED";
        
        AuditEvent event = createAuditEvent(
                eventType,
                userId,
                resource,
                "POLICY_CHECK",
                status,
                details
        );
        
        return emitAuditEvent(event);
    }
    
    /**
     * Emits an audit event for authentication attempts.
     *
     * @param userId the ID of the user attempting authentication (may be null for failures)
     * @param success whether authentication was successful
     * @param method the authentication method used
     * @param clientInfo information about the client (IP, user agent, etc.)
     * @return Promise that completes when the audit event is emitted
     */
    public Promise<Void> emitAuthentication(String userId, boolean success, 
                                                     String method, Map<String, Object> clientInfo) {
        Map<String, Object> details = Map.of(
                "method", method,
                "clientInfo", clientInfo
        );
        
        String eventType = success ? AUTHENTICATION_SUCCESS : AUTHENTICATION_FAILURE;
        String status = success ? "SUCCESS" : "FAILURE";
        
        AuditEvent event = createAuditEvent(
                eventType,
                userId != null ? userId : "unknown",
                "authentication",
                "AUTHENTICATE",
                status,
                details
        );
        
        return emitAuditEvent(event);
    }
    
    /**
     * Creates a standard audit event with common fields populated.
     */
    private AuditEvent createAuditEvent(String eventType, String principal, String resource, 
                                       String action, String status, Map<String, Object> details) {
        AuditEvent.Builder builder = AuditEvent.builder()
                .eventType(eventType)
                .timestamp(Instant.now())
                .principal(principal)
                .resourceId(resource)
                .success("SUCCESS".equalsIgnoreCase(status));
        
        if (details != null) {
            builder.details(details);
        }
        builder.detail("action", action);
        if (status != null) {
            builder.detail("status", status);
        }
        
        return builder.build();
    }
    
    /**
     * Enhances event details with tenant information and common metadata.
     */
    private Map<String, Object> enhanceDetails(Map<String, Object> details, String tenantId) {
        Map<String, Object> enhanced = new java.util.HashMap<>(details != null ? details : Map.of());
        enhanced.put("tenantId", tenantId);
        enhanced.put("timestamp", Instant.now().toString());
        return enhanced;
    }
    
    /**
     * Emits an audit event asynchronously using ActiveJ Promise.
     *
     * @param event the audit event to emit
     * @return a Promise that completes when the event has been emitted
     */
    public Promise<Void> emitAuditEvent(AuditEvent event) {
        return Promise.ofBlocking(Executors.newSingleThreadExecutor(), () -> {
            try {
                auditEventSink.emit(event);
                logger.debug("Emitted audit event: {} for principal: {}", 
                           event.getEventType(), event.getPrincipal());
            } catch (Exception e) {
                logger.error("Failed to emit audit event: {} for principal: {}", 
                           event.getEventType(), event.getPrincipal(), e);
                throw new RuntimeException("Audit event emission failed", e);
            }
        });
    }
}