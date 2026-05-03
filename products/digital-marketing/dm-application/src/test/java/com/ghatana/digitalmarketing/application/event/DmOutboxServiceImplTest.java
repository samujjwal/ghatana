package com.ghatana.digitalmarketing.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.event.DmDeadLetterEntry;
import com.ghatana.digitalmarketing.domain.event.DmEvent;
import com.ghatana.digitalmarketing.domain.event.DmEventRegistry;
import com.ghatana.digitalmarketing.domain.event.DmEventType;
import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import com.ghatana.digitalmarketing.domain.event.DmOutboxStatus;
import com.ghatana.digitalmarketing.domain.event.DmPiiClassification;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DMOS-F2-002: Transactional Outbox, DLQ, and retry.
 */
@DisplayName("F2-002: DmOutboxService tests")
class DmOutboxServiceImplTest extends EventloopTestBase {

    private InMemoryOutboxRepository outboxRepo;
    private InMemoryDeadLetterRepository dlqRepo;
    private CapturingDispatcher dispatcher;
    private DmOutboxService service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        outboxRepo = new InMemoryOutboxRepository();
        dlqRepo = new InMemoryDeadLetterRepository();
        dispatcher = new CapturingDispatcher(false);
        service = new DmOutboxServiceImpl(outboxRepo, dlqRepo, dispatcher, new ObjectMapper());

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-append-1"))
            .build();
    }

    private DmEvent<String> buildEvent(String workspaceId) {
        return DmEvent.<String>builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(DmEventType.CAMPAIGN_CREATED)
            .schemaVersion(DmEventRegistry.SCHEMA_VERSION)
            .tenantId("tenant-1")
            .workspaceId(workspaceId)
            .actor("user-1")
            .actorType(DmEvent.ActorType.USER)
            .correlationId("corr-1")
            .idempotencyKey("idem-1")
            .occurredAt(Instant.now())
            .sourceService("dm-application")
            .piiClassification(DmPiiClassification.NONE)
            .payload("campaign-payload")
            .build();
    }

    // ── append ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("append stores event in outbox with PENDING status")
    void appendStoresAsPending() {
        DmEvent<String> event = buildEvent("ws-1");

        DmOutboxEntry entry = runPromise(() -> service.append(ctx, event));

        assertThat(entry.getId()).isNotBlank();
        assertThat(entry.getStatus()).isEqualTo(DmOutboxStatus.PENDING);
        assertThat(entry.getEventId()).isEqualTo(event.getEventId());
        assertThat(entry.getEventType()).isEqualTo(DmEventType.CAMPAIGN_CREATED);
        assertThat(entry.getTenantId()).isEqualTo("tenant-1");
        assertThat(entry.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("append rejects null context")
    void appendRejectsNullCtx() {
        DmEvent<String> event = buildEvent("ws-1");
        assertThatThrownBy(() -> runPromise(() -> service.append(null, event)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("append serializes payload to JSON string")
    void appendSerializesPayload() {
        DmEvent<String> event = buildEvent("ws-1");

        DmOutboxEntry entry = runPromise(() -> service.append(ctx, event));

        assertThat(entry.getSerializedPayload()).isNotBlank();
    }

    // ── dispatchPending ──────────────────────────────────────────────────────

    @Test
    @DisplayName("dispatchPending delivers PENDING entries and marks them DISPATCHED")
    void dispatchPendingMarksDispatched() {
        runPromise(() -> service.append(ctx, buildEvent("ws-1")));
        runPromise(() -> service.append(ctx, buildEvent("ws-1")));

        int dispatched = runPromise(() -> service.dispatchPending("tenant-1", 10));

        assertThat(dispatched).isEqualTo(2);
        outboxRepo.store.values().forEach(e ->
            assertThat(e.getStatus()).isEqualTo(DmOutboxStatus.DISPATCHED));
    }

    @Test
    @DisplayName("dispatchPending returns 0 when no PENDING entries")
    void dispatchPendingReturnsZeroWhenNoPending() {
        int dispatched = runPromise(() -> service.dispatchPending("tenant-1", 10));
        assertThat(dispatched).isZero();
    }

    @Test
    @DisplayName("dispatchPending rejects blank tenantId")
    void dispatchPendingRejectsBlankTenantId() {
        assertThatThrownBy(() -> runPromise(() -> service.dispatchPending("  ", 10)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("dispatchPending rejects non-positive batchSize")
    void dispatchPendingRejectsZeroBatchSize() {
        assertThatThrownBy(() -> runPromise(() -> service.dispatchPending("tenant-1", 0)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("batchSize");
    }

    // ── failure and DLQ ──────────────────────────────────────────────────────

    @Test
    @DisplayName("repeated dispatch failures escalate to DEAD and create DLQ entry")
    void repeatedFailuresEscalateToDead() {
        dispatcher = new CapturingDispatcher(true);
        service = new DmOutboxServiceImpl(outboxRepo, dlqRepo, dispatcher, new ObjectMapper());

        runPromise(() -> service.append(ctx, buildEvent("ws-1")));

        // First dispatch attempt: PENDING → FAILED (attempt 1)
        runPromise(() -> service.dispatchPending("tenant-1", 10));
        // Retry attempts 2-5: FAILED → ... → DEAD on 5th
        for (int i = 0; i < DmOutboxEntry.MAX_ATTEMPTS - 1; i++) {
            runPromise(() -> service.retryFailed("tenant-1", 10));
        }

        // Should have one DLQ entry
        List<DmDeadLetterEntry> dlq = runPromise(() -> service.listDeadLetters(ctx, 100));
        assertThat(dlq).hasSize(1);
        assertThat(dlq.get(0).isReplayed()).isFalse();
        assertThat(dlq.get(0).getTotalAttempts()).isEqualTo(DmOutboxEntry.MAX_ATTEMPTS);
    }

    // ── retryFailed ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("retryFailed re-attempts FAILED entries within attempt limit")
    void retryFailedRetriesEligibleEntries() {
        // Dispatcher fails once then succeeds
        AtomicBoolean failFirst = new AtomicBoolean(true);
        DmEventDispatcher flippingDispatcher = entry -> {
            if (failFirst.getAndSet(false)) {
                return Promise.ofException(new RuntimeException("transient"));
            }
            return Promise.complete();
        };
        service = new DmOutboxServiceImpl(outboxRepo, dlqRepo, flippingDispatcher, new ObjectMapper());

        runPromise(() -> service.append(ctx, buildEvent("ws-1")));
        runPromise(() -> service.dispatchPending("tenant-1", 10)); // fails → FAILED
        int retried = runPromise(() -> service.retryFailed("tenant-1", 10)); // succeeds
        assertThat(retried).isEqualTo(1);
    }

    // ── replayDeadLetter ─────────────────────────────────────────────────────

    @Test
    @DisplayName("replayDeadLetter marks entry replayed and re-queues in outbox")
    void replayDeadLetterRequeuesEntry() {
        dispatcher = new CapturingDispatcher(true);
        service = new DmOutboxServiceImpl(outboxRepo, dlqRepo, dispatcher, new ObjectMapper());

        runPromise(() -> service.append(ctx, buildEvent("ws-1")));
        runPromise(() -> service.dispatchPending("tenant-1", 10));
        for (int i = 0; i < DmOutboxEntry.MAX_ATTEMPTS - 1; i++) {
            runPromise(() -> service.retryFailed("tenant-1", 10));
        }

        List<DmDeadLetterEntry> dlq = runPromise(() -> service.listDeadLetters(ctx, 10));
        String dlqId = dlq.get(0).getId();

        // Now fix dispatcher
        dispatcher = new CapturingDispatcher(false);
        service = new DmOutboxServiceImpl(outboxRepo, dlqRepo, dispatcher, new ObjectMapper());

        DmDeadLetterEntry replayed = runPromise(() -> service.replayDeadLetter(ctx, dlqId));
        assertThat(replayed.isReplayed()).isTrue();
        assertThat(replayed.getReplayedAt()).isNotNull();
    }

    @Test
    @DisplayName("replayDeadLetter rejects already-replayed DLQ entry")
    void replayAlreadyReplayedFails() {
        dispatcher = new CapturingDispatcher(true);
        service = new DmOutboxServiceImpl(outboxRepo, dlqRepo, dispatcher, new ObjectMapper());

        runPromise(() -> service.append(ctx, buildEvent("ws-1")));
        runPromise(() -> service.dispatchPending("tenant-1", 10));
        for (int i = 0; i < DmOutboxEntry.MAX_ATTEMPTS - 1; i++) {
            runPromise(() -> service.retryFailed("tenant-1", 10));
        }

        List<DmDeadLetterEntry> dlq = runPromise(() -> service.listDeadLetters(ctx, 10));
        String dlqId = dlq.get(0).getId();

        dispatcher = new CapturingDispatcher(false);
        service = new DmOutboxServiceImpl(outboxRepo, dlqRepo, dispatcher, new ObjectMapper());

        runPromise(() -> service.replayDeadLetter(ctx, dlqId));

        final DmOutboxService finalService = service;
        assertThatThrownBy(() -> runPromise(() -> finalService.replayDeadLetter(ctx, dlqId)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("already replayed");
    }

    @Test
    @DisplayName("replayDeadLetter rejects blank dlqId")
    void replayDeadLetterRejectsBlankId() {
        assertThatThrownBy(() -> runPromise(() -> service.replayDeadLetter(ctx, "  ")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dlqId");
    }

    @Test
    @DisplayName("replayDeadLetter rejects null DLQ id")
    void replayDeadLetterRejectsNullId() {
        assertThatThrownBy(() -> runPromise(() -> service.replayDeadLetter(ctx, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dispatchPending rejects null tenant id")
    void dispatchPendingRejectsNullTenantId() {
        assertThatThrownBy(() -> runPromise(() -> service.dispatchPending(null, 10)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("retryFailed rejects blank tenant id")
    void retryFailedRejectsBlankTenantId() {
        assertThatThrownBy(() -> runPromise(() -> service.retryFailed("", 10)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("retryFailed rejects non-positive batch size")
    void retryFailedRejectsZeroBatchSize() {
        assertThatThrownBy(() -> runPromise(() -> service.retryFailed("tenant-1", 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("replayDeadLetter rejects unknown DLQ id")
    void replayDeadLetterRejectsUnknownId() {
        assertThatThrownBy(() -> runPromise(() -> service.replayDeadLetter(ctx, "unknown-id")))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ── In-memory doubles ────────────────────────────────────────────────────

    static final class InMemoryOutboxRepository implements DmOutboxRepository {
        final Map<String, DmOutboxEntry> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmOutboxEntry> save(DmOutboxEntry entry) {
            store.put(entry.getId(), entry);
            return Promise.of(entry);
        }

        @Override
        public Promise<List<DmOutboxEntry>> findPending(String tenantId, int limit) {
            List<DmOutboxEntry> result = store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.getStatus() == DmOutboxStatus.PENDING)
                .sorted((a, b) -> a.getScheduledAt().compareTo(b.getScheduledAt()))
                .limit(limit)
                .toList();
            return Promise.of(result);
        }

        @Override
        public Promise<List<DmOutboxEntry>> findRetryable(String tenantId, int limit) {
            List<DmOutboxEntry> result = store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.isRetryable())
                .limit(limit)
                .toList();
            return Promise.of(result);
        }

        @Override
        public Promise<DmOutboxEntry> update(DmOutboxEntry entry) {
            store.put(entry.getId(), entry);
            return Promise.of(entry);
        }

        @Override
        public Promise<Optional<DmOutboxEntry>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmOutboxStatus status) {
            long count = store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.getStatus() == status)
                .count();
            return Promise.of(count);
        }
    }

    static final class InMemoryDeadLetterRepository implements DmDeadLetterRepository {
        final Map<String, DmDeadLetterEntry> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmDeadLetterEntry> save(DmDeadLetterEntry entry) {
            store.put(entry.getId(), entry);
            return Promise.of(entry);
        }

        @Override
        public Promise<List<DmDeadLetterEntry>> findUnreplayed(String tenantId, int limit) {
            List<DmDeadLetterEntry> result = store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && !e.isReplayed())
                .limit(limit)
                .toList();
            return Promise.of(result);
        }

        @Override
        public Promise<Optional<DmDeadLetterEntry>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<DmDeadLetterEntry> update(DmDeadLetterEntry entry) {
            store.put(entry.getId(), entry);
            return Promise.of(entry);
        }
    }

    static final class CapturingDispatcher implements DmEventDispatcher {
        final List<DmOutboxEntry> dispatched = new ArrayList<>();
        private final boolean failAlways;

        CapturingDispatcher(boolean failAlways) {
            this.failAlways = failAlways;
        }

        @Override
        public Promise<Void> dispatch(DmOutboxEntry entry) {
            if (failAlways) {
                return Promise.ofException(new RuntimeException("dispatch-failure"));
            }
            dispatched.add(entry);
            return Promise.complete();
        }
    }
}
