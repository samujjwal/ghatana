/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.audit.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Generic audit service wrapper.
 *
 * <p>Wraps the platform audit service and provides kernel-specific
 * functionality including simplified audit methods and tenant management.
 * This service contains NO finance-specific logic.</p>
 *
 * @doc.type class
 * @doc.purpose Generic audit service wrapper - simplified audit methods, tenant management
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class AuditServiceWrapper {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceWrapper.class);

    private final KernelContext context;
    private final AuditService platformAuditService;
    private final Map<String, Object> auditCache;
    private final Executor executor;
    private volatile boolean started = false;

    /**
     * Creates a new audit service wrapper.
     *
     * @param context the kernel context
     * @param platformAuditService the platform audit service
     */
    public AuditServiceWrapper(KernelContext context, AuditService platformAuditService) {
        this.context = context;
        this.platformAuditService = platformAuditService;
        this.auditCache = new ConcurrentHashMap<>();
        this.executor = context.getExecutor("audit");
    }

    /**
     * Starts the audit service wrapper.
     */
    public void start() {
        log.info("Starting audit service wrapper");
        started = true;
        log.info("Audit service wrapper started");
    }

    /**
     * Stops the audit service wrapper.
     */
    public void stop() {
        log.info("Stopping audit service wrapper");
        auditCache.clear();
        started = false;
        log.info("Audit service wrapper stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started;
    }

    /**
     * Records a simple audit event.
     *
     * @param action the action being audited
     * @param resource the resource being acted upon
     * @param userId the user performing the action
     * @return Promise completing when event is recorded
     */
    public Promise<Void> record(String action, String resource, String userId) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Audit service not started"));
        }

        log.debug("Recording audit event: {} {} by {}", action, resource, userId);
        AuditEvent event = createAuditEvent(action, resource, userId, null, null);
        return platformAuditService.record(event)
            .whenResult(ignored -> {
                log.debug("Audit event recorded: {}", event.getId());

            });
    }

    /**
     * Records an audit event with additional metadata.
     *
     * @param action the action being audited
     * @param resource the resource being acted upon
     * @param userId the user performing the action
     * @param tenantId the tenant identifier
     * @param metadata additional audit metadata
     * @return Promise completing when event is recorded
     */
    public Promise<Void> record(String action, String resource, String userId, 
                                String tenantId, Map<String, Object> metadata) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Audit service not started"));
        }

        log.debug("Recording audit event: {} {} by {} for tenant: {}", action, resource, userId, tenantId);
        AuditEvent event = createAuditEvent(action, resource, userId, tenantId, metadata);
        return platformAuditService.record(event)
            .whenResult(ignored -> {
                log.debug("Audit event recorded: {}", event.getId());

            });
    }

    /**
     * Records a security audit event.
     *
     * @param securityAction the security action
     * @param resource the resource being secured
     * @param userId the user performing the action
     * @param outcome the outcome of the security action
     * @return Promise completing when event is recorded
     */
    public Promise<Void> recordSecurity(String securityAction, String resource, 
                                        String userId, String outcome) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Audit service not started"));
        }

        log.debug("Recording security audit event: {} {} by {} - outcome: {}", 
            securityAction, resource, userId, outcome);
        Map<String, Object> secMetadata = Map.of(
            "category", "security",
            "outcome", outcome,
            "severity", "high"
        );
        AuditEvent event = createAuditEvent(securityAction, resource, userId, null, secMetadata);
        return platformAuditService.record(event)
            .whenResult(ignored -> {
                log.debug("Security audit event recorded: {}", event.getId());

            });
    }

    /**
     * Records a configuration change audit event.
     *
     * @param configAction the configuration action
     * @param configKey the configuration key
     * @param userId the user making the change
     * @param oldValue the previous value
     * @param newValue the new value
     * @return Promise completing when event is recorded
     */
    public Promise<Void> recordConfiguration(String configAction, String configKey, 
                                            String userId, String oldValue, String newValue) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Audit service not started"));
        }

        log.debug("Recording configuration audit event: {} {} by {} from {} to {}", 
            configAction, configKey, userId, oldValue, newValue);
        Map<String, Object> configMetadata = Map.of(
            "category", "configuration",
            "configKey", configKey,
            "oldValue", oldValue,
            "newValue", newValue
        );
        AuditEvent event = createAuditEvent(configAction, "configuration:" + configKey, userId, null, configMetadata);
        return platformAuditService.record(event)
            .whenResult(ignored -> {
                log.debug("Configuration audit event recorded: {}", event.getId());

            });
    }

    /**
     * Gets the underlying platform audit service.
     *
     * @return the platform audit service
     */
    public AuditService getPlatformAuditService() {
        return platformAuditService;
    }

    // ==================== Private Methods ====================

    private AuditEvent createAuditEvent(String action, String resource, String userId, 
                                       String tenantId, Map<String, Object> metadata) {
        return AuditEvent.builder()
            .tenantId(tenantId != null ? tenantId : "default")
            .eventType(action)
            .principal(userId)
            .resourceType(resource)
            .success(true)
            .timestamp(Instant.now())
            .details(metadata != null ? metadata : Map.of())
            .build();
    }
}
