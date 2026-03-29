/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.facade;

import com.ghatana.datacloud.application.audit.AuditingService;
import com.ghatana.datacloud.application.storage.AdminStorageManagementService;
import com.ghatana.datacloud.application.storage.StorageRouterService;
import com.ghatana.datacloud.application.webhook.WebhookService;
import com.ghatana.datacloud.entity.audit.AuditAction;
import com.ghatana.datacloud.entity.audit.AuditLog;
import com.ghatana.datacloud.entity.webhook.Webhook;
import com.ghatana.datacloud.entity.webhook.WebhookEventType;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain facade aggregating infrastructure-related application services.
 *
 * <p>Handlers that previously needed to inject {@code StorageRouterService},
 * {@code AuditingService}, {@code AdminStorageManagementService}, and
 * {@code WebhookService} separately now inject one
 * {@code InfrastructureDomainService} instead.
 *
 * @doc.type class
 * @doc.purpose Domain facade for infrastructure-related application services
 * @doc.layer application
 * @doc.pattern Facade, Service
 */
public final class InfrastructureDomainService {

    private final StorageRouterService storageRouter;
    private final AuditingService auditingService;
    private final AdminStorageManagementService adminStorage;
    private final WebhookService webhookService;

    public InfrastructureDomainService(
            StorageRouterService storageRouter,
            AuditingService auditingService,
            AdminStorageManagementService adminStorage,
            WebhookService webhookService) {
        this.storageRouter   = Objects.requireNonNull(storageRouter, "storageRouter");
        this.auditingService = Objects.requireNonNull(auditingService, "auditingService");
        this.adminStorage    = Objects.requireNonNull(adminStorage, "adminStorage");
        this.webhookService  = Objects.requireNonNull(webhookService, "webhookService");
    }

    // ── Storage routing ───────────────────────────────────────────────────────

    public Promise<StorageRouterService.RoutingTarget> resolveBackend(
            String tenantId, String collectionName, String query) {
        return storageRouter.resolveBackendFor(tenantId, collectionName, query);
    }

    public Promise<List<String>> getAllBackends(String tenantId, String collectionName) {
        return storageRouter.getAllBackendsFor(tenantId, collectionName);
    }

    public void invalidateRoutingCache(String tenantId, String collectionName) {
        storageRouter.invalidateRoutingCache(tenantId, collectionName);
    }

    public Map<String, Long> storageRoutingCacheStats() {
        return storageRouter.getCacheStats();
    }

    // ── Audit logging ─────────────────────────────────────────────────────────

    public Promise<Void> logAction(String tenantId, String userId, AuditAction action,
                                   String resourceType, String resourceId, String details) {
        return auditingService.logAction(tenantId, userId, action, resourceType, resourceId, details);
    }

    public Promise<List<AuditLog>> getUserActivity(String tenantId, String userId) {
        return auditingService.getUserActivity(tenantId, userId);
    }

    public Promise<List<AuditLog>> getResourceAuditTrail(
            String tenantId, String resourceType, String resourceId) {
        return auditingService.getResourceAuditTrail(tenantId, resourceType, resourceId);
    }

    public Promise<List<AuditLog>> getAuditTrailByDateRange(
            String tenantId, Instant startTime, Instant endTime) {
        return auditingService.getAuditTrailByDateRange(tenantId, startTime, endTime);
    }

    public Promise<String> exportAuditLogs(String tenantId, Instant startTime, Instant endTime) {
        return auditingService.exportAuditLogs(tenantId, startTime, endTime);
    }

    // ── Webhooks ──────────────────────────────────────────────────────────────

    public Promise<Webhook> registerWebhook(String tenantId, Webhook webhook, String userId) {
        return webhookService.registerWebhook(tenantId, webhook, userId);
    }

    public Promise<List<Webhook>> listWebhooks(String tenantId) {
        return webhookService.listWebhooks(tenantId);
    }

    public Promise<List<Webhook>> listWebhooksByEventType(
            String tenantId, WebhookEventType eventType) {
        return webhookService.listWebhooksByEventType(tenantId, eventType);
    }

    public Promise<Optional<Webhook>> getWebhook(UUID id, String tenantId) {
        return webhookService.getWebhook(id, tenantId);
    }

    public Promise<Webhook> updateWebhookEnabled(UUID id, String tenantId, boolean enabled) {
        return webhookService.updateEnabled(id, tenantId, enabled);
    }

    public Promise<Void> deleteWebhook(UUID id, String tenantId) {
        return webhookService.deleteWebhook(id, tenantId);
    }

    // ── Escape hatches ────────────────────────────────────────────────────────

    public StorageRouterService router() {
        return storageRouter;
    }

    public AuditingService auditing() {
        return auditingService;
    }

    public AdminStorageManagementService adminStorage() {
        return adminStorage;
    }

    public WebhookService webhooks() {
        return webhookService;
    }
}
