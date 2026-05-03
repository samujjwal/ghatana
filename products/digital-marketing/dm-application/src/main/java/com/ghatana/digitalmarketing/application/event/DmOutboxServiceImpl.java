package com.ghatana.digitalmarketing.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.event.DmDeadLetterEntry;
import com.ghatana.digitalmarketing.domain.event.DmEvent;
import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import com.ghatana.digitalmarketing.domain.event.DmOutboxStatus;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link DmOutboxService}.
 *
 * <p>Serialises events to JSON using Jackson and writes them to the outbox table.
 * A separate dispatcher (scheduler or background task) calls {@link #dispatchPending}
 * and {@link #retryFailed} to deliver events. Failed events exhaust retries and
 * are moved to the DLQ via {@link DmDeadLetterRepository}.</p>
 *
 * <p><strong>Dispatch</strong> is performed by a pluggable {@link DmEventDispatcher}.
 * In production this writes to a Kafka topic or ActiveMQ queue; in tests it can
 * be replaced with a no-op or in-memory double.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS transactional outbox service implementation (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern ApplicationService, Outbox
 */
public final class DmOutboxServiceImpl implements DmOutboxService {

    private final DmOutboxRepository outboxRepository;
    private final DmDeadLetterRepository dlqRepository;
    private final DmEventDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public DmOutboxServiceImpl(
            DmOutboxRepository outboxRepository,
            DmDeadLetterRepository dlqRepository,
            DmEventDispatcher dispatcher,
            ObjectMapper objectMapper) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository must not be null");
        this.dlqRepository    = Objects.requireNonNull(dlqRepository, "dlqRepository must not be null");
        this.dispatcher       = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.objectMapper     = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public <T> Promise<DmOutboxEntry> append(DmOperationContext ctx, DmEvent<T> event) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(event, "event must not be null");

        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(event.getPayload());
        } catch (JsonProcessingException e) {
            return Promise.ofException(
                new IllegalArgumentException("Cannot serialize event payload for eventId " + event.getEventId(), e));
        }

        Instant now = Instant.now();
        DmOutboxEntry entry = DmOutboxEntry.builder()
            .id(UUID.randomUUID().toString())
            .eventId(event.getEventId())
            .eventType(event.getEventType())
            .tenantId(event.getTenantId())
            .workspaceId(event.getWorkspaceId())
            .correlationId(event.getCorrelationId())
            .serializedPayload(serialized)
            .status(DmOutboxStatus.PENDING)
            .attemptCount(0)
            .createdAt(now)
            .scheduledAt(now)
            .build();

        return outboxRepository.save(entry);
    }

    @Override
    public Promise<Integer> dispatchPending(String tenantId, int batchSize) {
        if (tenantId == null || tenantId.isBlank())
            return Promise.ofException(new IllegalArgumentException("tenantId must not be blank"));
        if (batchSize <= 0)
            return Promise.ofException(new IllegalArgumentException("batchSize must be positive"));

        return outboxRepository.findPending(tenantId, batchSize)
            .then(entries -> dispatchAll(entries, new int[]{0}));
    }

    @Override
    public Promise<Integer> retryFailed(String tenantId, int batchSize) {
        if (tenantId == null || tenantId.isBlank())
            return Promise.ofException(new IllegalArgumentException("tenantId must not be blank"));
        if (batchSize <= 0)
            return Promise.ofException(new IllegalArgumentException("batchSize must be positive"));

        return outboxRepository.findRetryable(tenantId, batchSize)
            .then(entries -> dispatchAll(entries, new int[]{0}));
    }

    private Promise<Integer> dispatchAll(List<DmOutboxEntry> entries, int[] successCount) {
        if (entries.isEmpty()) return Promise.of(0);

        List<Promise<Void>> dispatches = new ArrayList<>();
        for (DmOutboxEntry entry : entries) {
            Promise<Void> p = dispatcher.dispatch(entry)
                .then(
                    __ -> outboxRepository.update(entry.markDispatched())
                              .map(_u -> { successCount[0]++; return (Void) null; }),
                    err -> handleDispatchFailure(entry, err));
            dispatches.add(p);
        }
        return Promises.all(dispatches).map(__ -> successCount[0]);
    }

    private Promise<Void> handleDispatchFailure(DmOutboxEntry entry, Throwable err) {
        DmOutboxEntry failed = entry.recordFailure(err != null ? err.getMessage() : "unknown");
        if (failed.getStatus() == DmOutboxStatus.DEAD) {
            DmDeadLetterEntry dlq = DmDeadLetterEntry.fromOutboxEntry(failed);
            return outboxRepository.update(failed)
                .then(_u -> dlqRepository.save(dlq))
                .toVoid();
        }
        return outboxRepository.update(failed).toVoid();
    }

    @Override
    public Promise<List<DmDeadLetterEntry>> listDeadLetters(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return dlqRepository.findUnreplayed(ctx.getTenantId().getValue(), safeLimit);
    }

    @Override
    public Promise<DmDeadLetterEntry> replayDeadLetter(DmOperationContext ctx, String dlqId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (dlqId == null || dlqId.isBlank())
            return Promise.ofException(new IllegalArgumentException("dlqId must not be blank"));

        return dlqRepository.findById(dlqId)
            .then(opt -> {
                if (opt.isEmpty())
                    return Promise.ofException(new NoSuchElementException("DLQ entry not found: " + dlqId));
                DmDeadLetterEntry dlq = opt.get();
                if (dlq.isReplayed())
                    return Promise.ofException(new IllegalStateException("DLQ entry already replayed: " + dlqId));

                Instant now = Instant.now();
                DmOutboxEntry requeued = DmOutboxEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(dlq.getEventId())
                    .eventType(dlq.getEventType())
                    .tenantId(dlq.getTenantId())
                    .workspaceId(dlq.getWorkspaceId())
                    .correlationId(dlq.getCorrelationId())
                    .serializedPayload(dlq.getSerializedPayload())
                    .status(DmOutboxStatus.PENDING)
                    .attemptCount(0)
                    .createdAt(now)
                    .scheduledAt(now)
                    .build();

                return outboxRepository.save(requeued)
                    .then(_saved -> dlqRepository.update(dlq.markReplayed()));
            });
    }
}
