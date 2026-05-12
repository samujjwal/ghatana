/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 *
 * Moved from platform/java/audit to products/data-cloud/platform
 * to fix layering violation (platform must not depend on products).
 * This service is inherently product-specific since it uses
 * data-cloud StoragePlugin and Event types.
 */
package com.ghatana.datacloud.audit;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQuery;
import com.ghatana.datacloud.event.model.Event;
import com.ghatana.datacloud.event.spi.StoragePlugin;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of AuditService backed by Data-Cloud StoragePlugin.
 *
 * <p>Persists audit events as immutable EventCloud events.
 * This class lives in the data-cloud product because it depends on
 * data-cloud-specific types (Event, StoragePlugin).
 *
 * @doc.type class
 * @doc.purpose Data cloud audit service implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public class DataCloudAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(DataCloudAuditService.class);
    private static final String AUDIT_STREAM = "audit-log";
    private static final String AUDIT_EVENT_TYPE = "platform.audit.event";
    private static final String AUDIT_EVENT_VERSION = "1.0.0";

    private final StoragePlugin storagePlugin;

    @Inject
    public DataCloudAuditService(StoragePlugin storagePlugin) {
        this.storagePlugin = storagePlugin;
    }

    @Override
    public Promise<Void> record(AuditEvent event) {
        log.debug("Recording audit event: type={}, principal={}, resource={}",
                event.getEventType(), event.getPrincipal(), event.getResourceId());

        Event dataCloudEvent = convertToDataCloudEvent(event);

        return storagePlugin.append(dataCloudEvent)
                .toVoid()
                .whenException(e -> log.error("Failed to persist audit event", e));
    }

    @Override
    public Promise<List<AuditEvent>> query(AuditQuery query) {
        log.debug("Querying audit events with custom criteria");
        
        // For now, return empty list as query functionality needs storage plugin query support
        // This can be implemented once storage plugin provides query capabilities
        return Promise.of(new ArrayList<>());
    }

    @Override
    public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
        log.debug("Querying audit events for project: {}, range: {} to {}", projectId, startDate, endDate);
        
        // For now, return empty list as query functionality needs storage plugin query support
        // This can be implemented once storage plugin provides query capabilities
        return Promise.of(new ArrayList<>());
    }

    @Override
    public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
        log.debug("Querying audit events for project: {}, phase: {}, range: {} to {}", projectId, phase, startDate, endDate);
        
        // For now, return empty list as query functionality needs storage plugin query support
        // This can be implemented once storage plugin provides query capabilities
        return Promise.of(new ArrayList<>());
    }

    private Event convertToDataCloudEvent(AuditEvent auditEvent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", auditEvent.getEventType());
        payload.put("principal", auditEvent.getPrincipal());
        payload.put("resourceType", auditEvent.getResourceType());
        payload.put("resourceId", auditEvent.getResourceId());
        payload.put("success", auditEvent.getSuccess());
        payload.put("details", auditEvent.getDetails());

        // Use tenant ID from event
        String tenantId = auditEvent.getTenantId();

        return Event.builder()
                .tenantId(tenantId)
                .streamName(AUDIT_STREAM)
                .eventTypeName(AUDIT_EVENT_TYPE)
                .eventTypeVersion(AUDIT_EVENT_VERSION)
                .occurrenceTime(auditEvent.getTimestamp())
                .detectionTime(Instant.now())
                .payload(payload)
                .partitionId(Math.abs(tenantId.hashCode()) % 16) // Simple partitioning by tenant
                .idempotencyKey(UUID.randomUUID().toString()) // Audit events are unique occurrences
                .build();
    }
}
