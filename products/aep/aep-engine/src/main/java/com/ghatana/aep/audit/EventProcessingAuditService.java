/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for logging audit events related to event processing decisions.
 * Stores audit events in memory for persistence by downstream consumers.
 *
 * @doc.type class
 * @doc.purpose Logs audit trail entries for event processing decisions
 * @doc.layer product
 * @doc.pattern Service
 */
public class EventProcessingAuditService {

    private static final Logger log = LoggerFactory.getLogger(EventProcessingAuditService.class);

    private final Map<String, AuditEntry> auditLog = new ConcurrentHashMap<>();
    private final int maxEntries;

    /**
     * Creates an audit service with default max entries (10,000).
     */
    public EventProcessingAuditService() {
        this(10_000);
    }

    /**
     * Creates an audit service with specified max entries.
     *
     * @param maxEntries maximum number of audit entries to retain
     */
    public EventProcessingAuditService(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Logs an event processing decision.
     *
     * @param tenantId tenant identifier
     * @param eventId event identifier
     * @param decisionType type of decision (e.g., RATE_LIMIT, CONSENT, SCHEMA_VALIDATION)
     * @param outcome decision outcome (ALLOWED, DENIED, SKIPPED)
     * @param reason human-readable reason for the decision
     * @param details additional decision context
     */
    public void logDecision(String tenantId, String eventId, String decisionType,
                            String outcome, String reason, Map<String, Object> details) {
        String auditId = tenantId + ":" + eventId + ":" + decisionType + ":" + System.currentTimeMillis();
        
        AuditEntry entry = new AuditEntry(
            auditId,
            tenantId,
            eventId,
            decisionType,
            outcome,
            reason,
            details,
            Instant.now()
        );

        auditLog.put(auditId, entry);

        // Prune old entries if over limit
        if (auditLog.size() > maxEntries) {
            auditLog.keySet().stream()
                .findFirst()
                .ifPresent(auditLog::remove);
        }

        log.debug("[audit] Event processing decision: tenant={}, event={}, decision={}, outcome={}, reason={}",
            tenantId, eventId, decisionType, outcome, reason);
    }

    /**
     * Gets audit entries for a tenant.
     *
     * @param tenantId tenant identifier
     * @return list of audit entries for the tenant
     */
    public java.util.List<AuditEntry> getAuditEntries(String tenantId) {
        return auditLog.values().stream()
            .filter(entry -> entry.tenantId().equals(tenantId))
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .toList();
    }

    /**
     * Gets audit entries for a specific event.
     *
     * @param tenantId tenant identifier
     * @param eventId event identifier
     * @return list of audit entries for the event
     */
    public java.util.List<AuditEntry> getAuditEntriesForEvent(String tenantId, String eventId) {
        return auditLog.values().stream()
            .filter(entry -> entry.tenantId().equals(tenantId) && entry.eventId().equals(eventId))
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .toList();
    }

    /**
     * Clears all audit entries.
     */
    public void clear() {
        auditLog.clear();
    }

    /**
     * Audit entry record.
     *
     * @param id unique audit entry identifier
     * @param tenantId tenant identifier
     * @param eventId event identifier
     * @param decisionType type of decision
     * @param outcome decision outcome
     * @param reason human-readable reason
     * @param details additional context
     * @param timestamp when the decision was made
     */
    public record AuditEntry(
        String id,
        String tenantId,
        String eventId,
        String decisionType,
        String outcome,
        String reason,
        Map<String, Object> details,
        Instant timestamp
    ) {
        public AuditEntry {
            details = details != null ? Map.copyOf(details) : Map.of();
        }
    }
}
