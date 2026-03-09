/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.audit;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In-memory implementation of AuditService and AuditQueryService.
 * Provides thread-safe in-memory storage for audit events during development and testing.
 *
 * @doc.type class
 * @doc.purpose In-memory audit query service for testing
 * @doc.layer platform
 * @doc.pattern Service
 */
public class InMemoryAuditQueryService implements AuditService, AuditQueryService {

    // Tenant-isolated event storage: tenantId -> list of events
    private final Map<String, List<AuditEvent>> eventsByTenant = new ConcurrentHashMap<>();
    
    // Event ID -> AuditEvent for quick lookup by ID
    private final Map<String, AuditEvent> eventsById = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> record(AuditEvent event) {
        String tenantId = event.getTenantId();
        
        eventsByTenant.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>())
            .add(event);
        
        // Store by ID
        String eventId = event.getId();
        if (eventId != null) {
            eventsById.put(buildKey(tenantId, eventId), event);
        }
        
        return Promise.complete();
    }

    @Override
    public Promise<List<AuditEvent>> findByTenantId(String tenantId) {
        List<AuditEvent> events = eventsByTenant.getOrDefault(tenantId, List.of());
        return Promise.of(new ArrayList<>(events));
    }

    @Override
    public Promise<List<AuditEvent>> findByTenantId(String tenantId, int offset, int limit) {
        List<AuditEvent> events = eventsByTenant.getOrDefault(tenantId, List.of());
        List<AuditEvent> paged = events.stream()
            .skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
        return Promise.of(paged);
    }

    @Override
    public Promise<List<AuditEvent>> findByResource(String tenantId, String resourceType, String resourceId) {
        return filterEvents(tenantId, event -> 
            Objects.equals(resourceType, event.getResourceType()) &&
            Objects.equals(resourceId, event.getResourceId())
        );
    }

    @Override
    public Promise<List<AuditEvent>> findByPrincipal(String tenantId, String principal) {
        return filterEvents(tenantId, event -> 
            Objects.equals(principal, event.getPrincipal())
        );
    }

    @Override
    public Promise<List<AuditEvent>> findByEventType(String tenantId, String eventType) {
        return filterEvents(tenantId, event -> 
            Objects.equals(eventType, event.getEventType())
        );
    }

    @Override
    public Promise<List<AuditEvent>> findByTimeRange(String tenantId, Instant from, Instant to) {
        return filterEvents(tenantId, event -> {
            Instant timestamp = event.getTimestamp();
            return timestamp != null &&
                   !timestamp.isBefore(from) &&
                   !timestamp.isAfter(to);
        });
    }

    @Override
    public Promise<Optional<AuditEvent>> findById(String tenantId, String eventId) {
        String key = buildKey(tenantId, eventId);
        AuditEvent event = eventsById.get(key);
        return Promise.of(Optional.ofNullable(event));
    }

    @Override
    public Promise<Long> countByTenantId(String tenantId) {
        List<AuditEvent> events = eventsByTenant.getOrDefault(tenantId, List.of());
        return Promise.of((long) events.size());
    }

    @Override
    public Promise<List<AuditEvent>> search(String tenantId, AuditSearchCriteria criteria) {
        List<AuditEvent> events = eventsByTenant.getOrDefault(tenantId, List.of());
        
        Stream<AuditEvent> stream = events.stream();
        
        // Apply filters
        if (criteria.resourceType() != null) {
            stream = stream.filter(e -> Objects.equals(criteria.resourceType(), e.getResourceType()));
        }
        if (criteria.resourceId() != null) {
            stream = stream.filter(e -> Objects.equals(criteria.resourceId(), e.getResourceId()));
        }
        if (criteria.principal() != null) {
            stream = stream.filter(e -> Objects.equals(criteria.principal(), e.getPrincipal()));
        }
        if (criteria.eventType() != null) {
            stream = stream.filter(e -> Objects.equals(criteria.eventType(), e.getEventType()));
        }
        if (criteria.fromDate() != null) {
            stream = stream.filter(e -> e.getTimestamp() != null && !e.getTimestamp().isBefore(criteria.fromDate()));
        }
        if (criteria.toDate() != null) {
            stream = stream.filter(e -> e.getTimestamp() != null && !e.getTimestamp().isAfter(criteria.toDate()));
        }
        if (criteria.success() != null) {
            stream = stream.filter(e -> Objects.equals(criteria.success(), e.getSuccess()));
        }
        
        // Apply pagination
        List<AuditEvent> results = stream
            .skip(criteria.offset())
            .limit(criteria.limit())
            .collect(Collectors.toList());
        
        return Promise.of(results);
    }

    private Promise<List<AuditEvent>> filterEvents(String tenantId, java.util.function.Predicate<AuditEvent> predicate) {
        List<AuditEvent> events = eventsByTenant.getOrDefault(tenantId, List.of());
        List<AuditEvent> filtered = events.stream()
            .filter(predicate)
            .collect(Collectors.toList());
        return Promise.of(filtered);
    }

    private String buildKey(String tenantId, String eventId) {
        return tenantId + ":" + eventId;
    }

    public void clear() {
        eventsByTenant.clear();
        eventsById.clear();
    }

    public void clearTenant(String tenantId) {
        eventsByTenant.remove(tenantId);
        eventsById.entrySet().removeIf(e -> e.getKey().startsWith(tenantId + ":"));
    }

    public int getTotalEventCount() {
        return eventsByTenant.values().stream()
            .mapToInt(List::size)
            .sum();
    }
}
