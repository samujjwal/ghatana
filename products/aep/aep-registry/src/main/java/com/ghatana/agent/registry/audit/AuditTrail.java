package com.ghatana.agent.registry.audit;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Audit trail interface.
 * 
 * @doc.type interface
 * @doc.purpose Audit logging contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AuditTrail {
    
    /**
     * Records an audit event.
     */
    Promise<Void> record(AuditEvent event);
    
    /**
     * Gets audit events by time range.
     */
    Promise<List<AuditEvent>> getEvents(Instant start, Instant end);
    
    /**
     * Gets audit events by tenant.
     */
    Promise<List<AuditEvent>> getEventsByTenant(String tenantId, Instant start, Instant end);
    
    /**
     * Gets audit events by type.
     */
    Promise<List<AuditEvent>> getEventsByType(String eventType, Instant start, Instant end);
    
    /**
     * Searches audit events.
     */
    Promise<List<AuditEvent>> search(AuditQuery query);
}
