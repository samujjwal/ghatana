package com.ghatana.appplatform.audit.integration;

import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.domain.AuditEntry.Actor;
import com.ghatana.appplatform.audit.domain.AuditEntry.Outcome;
import com.ghatana.appplatform.audit.domain.AuditEntry.Resource;
import com.ghatana.appplatform.audit.port.AuditTrailStore;
import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.port.AggregateEventStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decorator that wires K-05 event store appends into the K-07 audit trail.
 *
 * <p>This closes the Sprint 2 exit gate: <em>"K-05 to K-07 audit hook enabled"</em>.
 *
 * <p>Every successful {@link #appendEvent} generates one {@link AuditEntry}
 * with action {@code EVENT_STORED} and the aggregate/event details recorded as
 * the audit entry details. If audit logging fails it is logged as a warning but
 * does NOT roll back the underlying event append — the event bus must not be
 * blocked by the audit trail.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AuditedAggregateEventStore store = new AuditedAggregateEventStore(
 *     postgresEventStore,         // delegate
 *     postgresAuditTrailStore,    // audit sink
 *     "system",                  // system actor userId
 *     "EventBus"                 // system actor role
 * );
 * }</pre>
 *
 * <h2>Dependency flow</h2>
 * {@code audit-trail} already depends on {@code event-store} (to publish
 * {@code AuditLogCreatedEvent} back to the bus). Placing this decorator in
 * {@code audit-trail} avoids a circular dependency.
 *
 * @doc.type class
 * @doc.purpose K-05→K-07 audit hook — decorates AggregateEventStore to emit an
 *              AuditEntry for every persisted event (Sprint 2 exit gate)
 * @doc.layer product
 * @doc.pattern Decorator
 */
public final class AuditedAggregateEventStore implements AggregateEventStore {

    private static final Logger log = LoggerFactory.getLogger(AuditedAggregateEventStore.class);

    /** Audit action recorded for every event stored via the event bus. */
    public static final String ACTION_EVENT_STORED = "EVENT_STORED";

    private final AggregateEventStore delegate;
    private final AuditTrailStore auditTrailStore;
    private final Actor systemActor;

    /**
     * @param delegate        underlying event store (e.g., {@code PostgresAggregateEventStore})
     * @param auditTrailStore audit sink (e.g., {@code PostgresAuditTrailStore})
     * @param systemUserId    identity used when the event appender has no user context
     * @param systemRole      role used for the audit actor record
     */
    public AuditedAggregateEventStore(
            AggregateEventStore delegate,
            AuditTrailStore auditTrailStore,
            String systemUserId,
            String systemRole) {
        this.delegate       = delegate;
        this.auditTrailStore = auditTrailStore;
        this.systemActor    = Actor.of(systemUserId, systemRole);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Appends the event via the delegate, then fire-and-forget emits an
     * audit entry to K-07. Audit failure does not fail the append.
     */
    @Override
    public Promise<AggregateEventRecord> appendEvent(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            Map<String, Object> data,
            Map<String, Object> metadata) {

        return delegate.appendEvent(aggregateId, aggregateType, eventType, data, metadata)
            .whenResult(record -> emitAuditEntry(record, metadata));
    }

    /** Passthrough — reads are not audited to avoid audit log bloat. */
    @Override
    public Promise<List<AggregateEventRecord>> getEventsByAggregate(
            UUID aggregateId,
            long fromSequence,
            Long toSequence) {
        return delegate.getEventsByAggregate(aggregateId, fromSequence, toSequence);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void emitAuditEntry(AggregateEventRecord record, Map<String, Object> metadata) {
        String tenantId = extractTenantId(metadata);
        String traceId  = extractTraceId(metadata);

        // Resolve actor from metadata (callers can embed actor_id / actor_role in metadata)
        Actor actor = resolveActor(metadata);

        AuditEntry entry = AuditEntry.builder()
            .action(ACTION_EVENT_STORED)
            .actor(actor)
            .resource(Resource.of(record.aggregateType(), record.aggregateId().toString()))
            .outcome(Outcome.SUCCESS)
            .tenantId(tenantId)
            .traceId(traceId)
            .details(Map.of(
                "eventId",       record.eventId().toString(),
                "eventType",     record.eventType(),
                "sequenceNumber", record.sequenceNumber(),
                "createdAtBs",   record.createdAtBs() != null ? record.createdAtBs() : ""
            ))
            .build();

        // Fire-and-forget: audit failure must not block the event pipeline
        auditTrailStore.log(entry)
            .whenException(ex ->
                log.warn("K-07 audit log failed for event {} — continuing without audit: {}",
                    record.eventId(), ex.getMessage()));
    }

    private Actor resolveActor(Map<String, Object> metadata) {
        Object actorId   = metadata.get("actor_id");
        Object actorRole = metadata.get("actor_role");
        if (actorId != null) {
            return Actor.of(actorId.toString(),
                actorRole != null ? actorRole.toString() : systemActor.role());
        }
        return systemActor;
    }

    private static String extractTenantId(Map<String, Object> metadata) {
        Object tenantId = metadata.get("tenant_id");
        return tenantId != null ? tenantId.toString() : "system";
    }

    private static String extractTraceId(Map<String, Object> metadata) {
        Object traceId = metadata.get("trace_id");
        return traceId != null ? traceId.toString() : null;
    }
}
