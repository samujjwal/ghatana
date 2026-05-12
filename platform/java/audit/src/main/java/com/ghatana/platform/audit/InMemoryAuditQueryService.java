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

    // -------------------------------------------------------------------------
    // AuditService — query convenience methods
    // -------------------------------------------------------------------------

    @Override
    public Promise<List<AuditEvent>> query(AuditQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        // Convert AuditQuery to AuditSearchCriteria
        AuditQueryService.AuditSearchCriteria criteria = AuditQueryService.AuditSearchCriteria.builder()
                .resourceType(query.resourceType())
                .resourceId(query.resourceId())
                .principal(query.principal())
                .eventType(query.eventType())
                .fromDate(query.startDate())
                .toDate(query.endDate())
                .success(query.success())
                .offset(query.offset())
                .limit(query.limit())
                .build();

        // Use tenantId from query, or fall back to filtering by projectId/phase in details
        String tenantId = query.tenantId();
        if (tenantId == null && query.projectId() != null) {
            // If no tenantId but projectId, filter all events by projectId in details
            return filterAllEvents(event ->
                Objects.equals(query.projectId(), event.getDetail("projectId")) ||
                Objects.equals(query.projectId(), event.getResourceId())
            ).map(filtered -> filterByCriteriaSync(filtered, criteria));
        }

        if (tenantId == null) {
            // If no tenantId and no projectId, return empty list
            return Promise.of(List.of());
        }

        return search(tenantId, criteria);
    }

    @Override
    public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");

        return filterAllEvents(event ->
            Objects.equals(projectId, event.getDetail("projectId")) ||
            Objects.equals(projectId, event.getResourceId())
        ).map(events -> events.stream()
                .filter(e -> e.getTimestamp() != null &&
                        !e.getTimestamp().isBefore(startDate) &&
                        !e.getTimestamp().isAfter(endDate))
                .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(phase, "phase cannot be null");
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");

        return filterAllEvents(event ->
            (Objects.equals(projectId, event.getDetail("projectId")) ||
             Objects.equals(projectId, event.getResourceId())) &&
            Objects.equals(phase, event.getDetail("phase"))
        ).map(events -> events.stream()
                .filter(e -> e.getTimestamp() != null &&
                        !e.getTimestamp().isBefore(startDate) &&
                        !e.getTimestamp().isAfter(endDate))
                .collect(Collectors.toList()));
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

    private Promise<List<AuditEvent>> filterAllEvents(java.util.function.Predicate<AuditEvent> predicate) {
        List<AuditEvent> allEvents = eventsByTenant.values().stream()
            .flatMap(List::stream)
            .filter(predicate)
            .collect(Collectors.toList());
        return Promise.of(allEvents);
    }

    private Promise<List<AuditEvent>> filterByCriteria(List<AuditEvent> events, AuditQueryService.AuditSearchCriteria criteria) {
        return Promise.of(filterByCriteriaSync(events, criteria));
    }

    private List<AuditEvent> filterByCriteriaSync(List<AuditEvent> events, AuditQueryService.AuditSearchCriteria criteria) {
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

        return results;
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
