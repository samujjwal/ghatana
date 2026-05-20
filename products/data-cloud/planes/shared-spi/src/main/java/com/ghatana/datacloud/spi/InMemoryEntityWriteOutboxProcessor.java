package com.ghatana.datacloud.spi;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * P0-06: In-memory implementation of EntityWriteOutboxProcessor for local development.
 * 
 * <p>This implementation stores outbox entries in memory and processes them synchronously.
 * For production, a durable database-backed implementation should be used.
 *
 * <p>DC-P1-09: This processor now emits audit events from outbox entries for atomic
 * entity/event/audit writes. The AuditService is injected to ensure audit events are
 * emitted as part of the outbox processing lifecycle.
 *
 * @doc.type class
 * @doc.purpose In-memory outbox processor for local development
 * @doc.layer product
 * @doc.pattern Outbox, InMemory
 */
public class InMemoryEntityWriteOutboxProcessor implements EntityWriteOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEntityWriteOutboxProcessor.class);

    private final Map<String, EntityWriteOutbox> outboxStore = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<EntityWriteOutbox> pendingQueue = new ConcurrentLinkedQueue<>();
    private final AuditService auditService;

    /**
     * Creates an in-memory outbox processor without audit service.
     * Audit events will not be emitted (for testing only).
     */
    public InMemoryEntityWriteOutboxProcessor() {
        this.auditService = null;
    }

    /**
     * DC-P1-09: Creates an in-memory outbox processor with audit service.
     * Audit events will be emitted from outbox entries for atomic entity/event/audit writes.
     *
     * @param auditService the audit service for emitting audit events
     */
    public InMemoryEntityWriteOutboxProcessor(AuditService auditService) {
        this.auditService = auditService;
    }
    
    @Override
    public Promise<Void> process(EntityWriteOutbox outbox) {
        try {
            log.info("[P0-06] Processing outbox entry: id={}, tenantId={}, collection={}, entityId={}, hasAudit={}",
                outbox.id(), outbox.tenantId(), outbox.collection(), outbox.entityId(), outbox.auditPayload() != null);

            // Mark as processing
            EntityWriteOutbox processing = EntityWriteOutbox.builder()
                .id(outbox.id())
                .tenantId(outbox.tenantId())
                .collection(outbox.collection())
                .entityId(outbox.entityId())
                .operationType(outbox.operationType())
                .entitySnapshot(outbox.entitySnapshot())
                .eventPayload(outbox.eventPayload())
                .auditPayload(outbox.auditPayload())
                .correlationId(outbox.correlationId())
                .createdAt(outbox.createdAt())
                .status(EntityWriteOutbox.OutboxStatus.PROCESSING)
                .build();
            outboxStore.put(outbox.id(), processing);

            // DC-P1-09: Emit audit event if auditPayload is present and auditService is available
            Promise<Void> auditPromise = Promise.of((Void) null);
            if (outbox.auditPayload() != null && auditService != null) {
                log.info("[DC-P1-09] Emitting audit event from outbox: outboxId={}, tenantId={}",
                    outbox.id(), outbox.tenantId());
                try {
                    AuditEvent.Builder auditEventBuilder = AuditEvent.builder()
                        .tenantId(outbox.tenantId())
                        .eventType(String.valueOf(outbox.auditPayload().getOrDefault("eventType", "ENTITY_WRITE")))
                        .principal(String.valueOf(outbox.auditPayload().getOrDefault("principal", "system")))
                        .resourceType("ENTITY")
                        .resourceId(outbox.collection() + "/" + outbox.entityId())
                        .success(Boolean.valueOf(String.valueOf(outbox.auditPayload().getOrDefault("success", true))));

                    // Add all details from auditPayload
                    for (Map.Entry<String, Object> entry : outbox.auditPayload().entrySet()) {
                        if (!"eventType".equals(entry.getKey()) &&
                            !"principal".equals(entry.getKey()) &&
                            !"success".equals(entry.getKey())) {
                            auditEventBuilder.detail(entry.getKey(), entry.getValue());
                        }
                    }

                    AuditEvent auditEvent = auditEventBuilder.build();
                    auditPromise = auditService.record(auditEvent)
                        .whenException(e -> {
                            log.error("[DC-P1-09] Audit emission failed for outbox entry {}: {}", outbox.id(), e.getMessage());
                            throw e;
                        });
                } catch (Exception e) {
                    log.error("[DC-P1-09] Failed to build audit event from outbox entry {}: {}", outbox.id(), e.getMessage());
                    // Continue processing even if audit fails - outbox will be marked as completed
                }
            }

            // Mark as completed after audit emission
            return auditPromise.then($ -> {
                EntityWriteOutbox completed = EntityWriteOutbox.builder()
                    .id(outbox.id())
                    .tenantId(outbox.tenantId())
                    .collection(outbox.collection())
                    .entityId(outbox.entityId())
                    .operationType(outbox.operationType())
                    .entitySnapshot(outbox.entitySnapshot())
                    .eventPayload(outbox.eventPayload())
                    .auditPayload(outbox.auditPayload())
                    .correlationId(outbox.correlationId())
                    .createdAt(outbox.createdAt())
                    .processedAt(Instant.now())
                    .status(EntityWriteOutbox.OutboxStatus.COMPLETED)
                    .build();
                outboxStore.put(outbox.id(), completed);
                return Promise.of((Void) null);
            });
        } catch (Exception e) {
            log.error("[P0-06] Error processing outbox entry: {}", outbox.id(), e);
            return markFailed(outbox.id(), e.getMessage());
        }
    }
    
    @Override
    public Promise<Integer> pollAndProcess(String tenantId, int limit) {
        int processed = 0;
        int count = 0;
        
        for (EntityWriteOutbox entry : pendingQueue) {
            if (count >= limit) {
                break;
            }
            
            if (tenantId == null || tenantId.equals(entry.tenantId())) {
                if (entry.status() == EntityWriteOutbox.OutboxStatus.PENDING) {
                    process(entry).whenComplete((ignored, error) -> {
                        if (error == null) {
                            pendingQueue.remove(entry);
                        }
                    });
                    processed++;
                    count++;
                }
            }
        }
        
        return Promise.of(processed);
    }
    
    @Override
    public Promise<List<EntityWriteOutbox>> getPendingEntries(String tenantId, int limit) {
        List<EntityWriteOutbox> pending = new ArrayList<>();
        
        for (EntityWriteOutbox entry : outboxStore.values()) {
            if (pending.size() >= limit) {
                break;
            }
            
            if (entry.status() == EntityWriteOutbox.OutboxStatus.PENDING) {
                if (tenantId == null || tenantId.equals(entry.tenantId())) {
                    pending.add(entry);
                }
            }
        }
        
        return Promise.of(pending);
    }
    
    @Override
    public Promise<Void> markCompleted(String outboxId) {
        EntityWriteOutbox entry = outboxStore.get(outboxId);
        if (entry != null) {
            EntityWriteOutbox completed = EntityWriteOutbox.builder()
                .id(entry.id())
                .tenantId(entry.tenantId())
                .collection(entry.collection())
                .entityId(entry.entityId())
                .operationType(entry.operationType())
                .entitySnapshot(entry.entitySnapshot())
                .eventPayload(entry.eventPayload())
                .auditPayload(entry.auditPayload()) // DC-P1-09
                .correlationId(entry.correlationId())
                .createdAt(entry.createdAt())
                .processedAt(Instant.now())
                .status(EntityWriteOutbox.OutboxStatus.COMPLETED)
                .build();
            outboxStore.put(outboxId, completed);
        }
        return Promise.of((Void) null);
    }
    
    @Override
    public Promise<Void> markFailed(String outboxId, String errorMessage) {
        EntityWriteOutbox entry = outboxStore.get(outboxId);
        if (entry != null) {
            EntityWriteOutbox failed = EntityWriteOutbox.builder()
                .id(entry.id())
                .tenantId(entry.tenantId())
                .collection(entry.collection())
                .entityId(entry.entityId())
                .operationType(entry.operationType())
                .entitySnapshot(entry.entitySnapshot())
                .eventPayload(entry.eventPayload())
                .auditPayload(entry.auditPayload()) // DC-P1-09
                .correlationId(entry.correlationId())
                .createdAt(entry.createdAt())
                .processedAt(Instant.now())
                .status(EntityWriteOutbox.OutboxStatus.FAILED)
                .errorMessage(errorMessage)
                .build();
            outboxStore.put(outboxId, failed);
        }
        return Promise.of((Void) null);
    }
    
    /**
     * DC-P1-09: Adds an outbox entry to the pending queue for async processing.
     *
     * @param outbox the outbox entry to add
     */
    @Override
    public void addPending(EntityWriteOutbox outbox) {
        log.info("[DC-P1-09] Adding outbox entry to pending queue: outboxId={}, tenantId={}, collection={}",
            outbox.id(), outbox.tenantId(), outbox.collection());
        outboxStore.put(outbox.id(), outbox);
        pendingQueue.add(outbox);
    }
}
