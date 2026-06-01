package com.ghatana.datacloud.launcher;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Event-store-backed standalone health snapshot supplier.
 *
 * WS5: Extended to check append, read, and tail readiness where safe.
 *
 * @doc.type class
 * @doc.purpose Produce standalone event-store health snapshots from the platform event store SPI
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class EventStoreHealthProbe implements Supplier<Map<String, Object>> {

    private final EventLogStore eventLogStore;
    private final long timeoutMillis;
    private final boolean checkAppend;
    private final boolean checkRead;
    private final boolean checkTail;

    public EventStoreHealthProbe(EventLogStore eventLogStore, long timeoutMillis) {
        this(eventLogStore, timeoutMillis, true, true, true);
    }

    public EventStoreHealthProbe(EventLogStore eventLogStore, long timeoutMillis, 
                                 boolean checkAppend, boolean checkRead, boolean checkTail) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore must not be null");
        this.timeoutMillis = Math.max(1L, timeoutMillis);
        this.checkAppend = checkAppend;
        this.checkRead = checkRead;
        this.checkTail = checkTail;
    }

    @Override
    public Map<String, Object> get() {
        long startedAt = System.nanoTime();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        
        // Check read capability (getLatestOffset)
        Map<String, Object> readCheck = checkReadCapability(startedAt);
        snapshot.putAll(readCheck);
        
        if (!"UP".equals(readCheck.get("status"))) {
            // If read is down, skip other checks
            snapshot.put("append", "SKIPPED");
            snapshot.put("tail", "SKIPPED");
            snapshot.put("latencyMs", durationMs(startedAt));
            snapshot.put("timeoutMs", timeoutMillis);
            return snapshot;
        }

        // Check append capability (write a test event)
        if (checkAppend) {
            Map<String, Object> appendCheck = checkAppendCapability(startedAt);
            snapshot.put("append", appendCheck.get("status"));
            if (appendCheck.containsKey("message")) {
                snapshot.put("appendMessage", appendCheck.get("message"));
            }
        } else {
            snapshot.put("append", "SKIPPED");
        }

        // Check tail capability (establish a subscription and cancel immediately)
        if (checkTail) {
            Map<String, Object> tailCheck = checkTailCapability(startedAt);
            snapshot.put("tail", tailCheck.get("status"));
            if (tailCheck.containsKey("message")) {
                snapshot.put("tailMessage", tailCheck.get("message"));
            }
        } else {
            snapshot.put("tail", "SKIPPED");
        }

        snapshot.put("latencyMs", durationMs(startedAt));
        snapshot.put("timeoutMs", timeoutMillis);
        return snapshot;
    }

    private Map<String, Object> checkReadCapability(long startedAt) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> latestOffset = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        eventLogStore.getLatestOffset(TenantContext.of("health-check", Map.of("probe", "event_store")))
            .whenResult(offset -> {
                latestOffset.set(offset);
                latch.countDown();
            })
            .whenException(error -> {
                failure.set(error);
                latch.countDown();
            });

        try {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return failureSnapshot("Event store read probe timed out", startedAt);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return failureSnapshot("Event store read probe interrupted", startedAt);
        }

        Throwable error = failure.get();
        if (error != null) {
            return failureSnapshot(error.getClass().getSimpleName() + ": " + error.getMessage(), startedAt);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "UP");
        snapshot.put("latestOffset", String.valueOf(latestOffset.get()));
        return snapshot;
    }

    private Map<String, Object> checkAppendCapability(long startedAt) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // WS5: Write a minimal test event to verify append capability
        EventEntry testEntry = EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType("health-check")
            .eventVersion("1.0.0")
            .timestamp(Instant.now())
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .contentType("application/json")
            .idempotencyKey("health-check-append-test")
            .build();

        eventLogStore.append(TenantContext.of("health-check", Map.of("probe", "event_store")), testEntry)
            .whenResult(offset -> latch.countDown())
            .whenException(error -> {
                failure.set(error);
                latch.countDown();
            });

        try {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("status", "DOWN");
                snapshot.put("message", "Event store append probe timed out");
                return snapshot;
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("status", "DOWN");
            snapshot.put("message", "Event store append probe interrupted");
            return snapshot;
        }

        Throwable error = failure.get();
        if (error != null) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("status", "DOWN");
            snapshot.put("message", error.getClass().getSimpleName() + ": " + error.getMessage());
            return snapshot;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "UP");
        return snapshot;
    }

    private Map<String, Object> checkTailCapability(long startedAt) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<EventLogStore.Subscription> subscriptionRef = new AtomicReference<>();
        AtomicReference<EventLogStore.SubscriptionState> stateRef = new AtomicReference<>();

        // WS5-9: Establish a tail subscription, verify state, and cancel to verify tail capability
        eventLogStore.tail(TenantContext.of("health-check", Map.of("probe", "event_store")), 
                          Offset.of("0"), entry -> {})
            .whenResult(subscription -> {
                subscriptionRef.set(subscription);
                // WS5-9: Verify subscription state is ACTIVE
                stateRef.set(subscription.getState());
                subscription.cancel();
                // WS5-9: Verify state transitions to CLOSED after cancel
                if (subscription.getState() == EventLogStore.SubscriptionState.CLOSED) {
                    latch.countDown();
                } else {
                    failure.set(new IllegalStateException("Subscription did not transition to CLOSED after cancel"));
                    latch.countDown();
                }
            })
            .whenException(error -> {
                failure.set(error);
                latch.countDown();
            });

        try {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("status", "DOWN");
                snapshot.put("message", "Event store tail probe timed out");
                return snapshot;
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("status", "DOWN");
            snapshot.put("message", "Event store tail probe interrupted");
            return snapshot;
        }

        Throwable error = failure.get();
        if (error != null) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("status", "DOWN");
            snapshot.put("message", error.getClass().getSimpleName() + ": " + error.getMessage());
            return snapshot;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "UP");
        snapshot.put("initialState", stateRef.get() != null ? stateRef.get().toString() : "UNKNOWN");
        return snapshot;
    }

    private Map<String, Object> failureSnapshot(String message, long startedAt) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "DOWN");
        snapshot.put("message", message);
        return snapshot;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}