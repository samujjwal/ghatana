package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * P0-06: In-memory implementation of EntityWriteOutboxProcessor for local development.
 * 
 * <p>This implementation stores outbox entries in memory and processes them synchronously.
 * For production, a durable database-backed implementation should be used.
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
    
    public InMemoryEntityWriteOutboxProcessor() {
    }
    
    @Override
    public Promise<Void> process(EntityWriteOutbox outbox) {
        try {
            log.info("[P0-06] Processing outbox entry: id={}, tenantId={}, collection={}, entityId={}",
                outbox.id(), outbox.tenantId(), outbox.collection(), outbox.entityId());
            
            // Mark as processing
            EntityWriteOutbox processing = EntityWriteOutbox.builder()
                .id(outbox.id())
                .tenantId(outbox.tenantId())
                .collection(outbox.collection())
                .entityId(outbox.entityId())
                .operationType(outbox.operationType())
                .entitySnapshot(outbox.entitySnapshot())
                .eventPayload(outbox.eventPayload())
                .correlationId(outbox.correlationId())
                .createdAt(outbox.createdAt())
                .status(EntityWriteOutbox.OutboxStatus.PROCESSING)
                .build();
            outboxStore.put(outbox.id(), processing);
            
            // In-memory implementation: mark as completed immediately
            // Production implementation would append to event log here
            EntityWriteOutbox completed = EntityWriteOutbox.builder()
                .id(outbox.id())
                .tenantId(outbox.tenantId())
                .collection(outbox.collection())
                .entityId(outbox.entityId())
                .operationType(outbox.operationType())
                .entitySnapshot(outbox.entitySnapshot())
                .eventPayload(outbox.eventPayload())
                .correlationId(outbox.correlationId())
                .createdAt(outbox.createdAt())
                .processedAt(Instant.now())
                .status(EntityWriteOutbox.OutboxStatus.COMPLETED)
                .build();
            outboxStore.put(outbox.id(), completed);
            
            return Promise.of((Void) null);
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
     * Adds an outbox entry to the pending queue.
     */
    public void addPending(EntityWriteOutbox outbox) {
        outboxStore.put(outbox.id(), outbox);
        pendingQueue.add(outbox);
    }
}
