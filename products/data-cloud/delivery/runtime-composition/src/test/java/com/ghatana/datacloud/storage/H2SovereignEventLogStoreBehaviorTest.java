package com.ghatana.datacloud.storage;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("H2 sovereign event log behavior")
class H2SovereignEventLogStoreBehaviorTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("offsets are tenant-scoped and start from 1 per tenant")
    void offsetsAreTenantScoped() throws Exception {
        try (H2SovereignEventLogStore store = new H2SovereignEventLogStore(tempDir.resolve("tenant-offsets"))) {
            TenantContext tenantA = TenantContext.of("tenant-a");
            TenantContext tenantB = TenantContext.of("tenant-b");

            Offset a1 = runPromise(() -> store.append(tenantA, entry("order.created", "idem-a1")));
            Offset b1 = runPromise(() -> store.append(tenantB, entry("order.created", "idem-b1")));
            Offset a2 = runPromise(() -> store.append(tenantA, entry("order.created", "idem-a2")));

            assertThat(a1.value()).isEqualTo("1");
            assertThat(b1.value()).isEqualTo("1");
            assertThat(a2.value()).isEqualTo("2");

            Offset latestA = runPromise(() -> store.getLatestOffset(tenantA));
            Offset latestB = runPromise(() -> store.getLatestOffset(tenantB));
            assertThat(latestA.value()).isEqualTo("2");
            assertThat(latestB.value()).isEqualTo("1");
        }
    }

    @Test
    @DisplayName("duplicate idempotency key returns existing offset without duplicate row")
    void duplicateIdempotencyReturnsExistingOffset() throws Exception {
        try (H2SovereignEventLogStore store = new H2SovereignEventLogStore(tempDir.resolve("idempotency"))) {
            TenantContext tenant = TenantContext.of("tenant-idem");

            Offset first = runPromise(() -> store.append(tenant, entry("event.type", "stable-key")));
            Offset second = runPromise(() -> store.append(tenant, entry("event.type", "stable-key")));

            assertThat(second).isEqualTo(first);

            List<EventLogStore.EventEntry> events = runPromise(() -> store.read(tenant, Offset.zero(), 100));
            assertThat(events).hasSize(1);
        }
    }

    @Test
    @DisplayName("tail from latest receives only events appended after subscription")
    void tailFromLatestReceivesOnlyNewEvents() throws Exception {
        try (H2SovereignEventLogStore store = new H2SovereignEventLogStore(tempDir.resolve("tail-latest"))) {
            TenantContext tenant = TenantContext.of("tenant-tail");
            runPromise(() -> store.append(tenant, entry("before.1", "idem-before-1")));
            runPromise(() -> store.append(tenant, entry("before.2", "idem-before-2")));

            List<EventLogStore.EventEntry> received = java.util.Collections.synchronizedList(new ArrayList<>());
            EventLogStore.Subscription subscription = runPromise(() -> store.tail(tenant, Offset.of(-1L), received::add));
            try {
                runPromise(() -> store.append(tenant, entry("after.1", "idem-after-1")));

                long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (received.isEmpty() && System.nanoTime() < deadlineNanos) {
                    Thread.sleep(25L);
                }

                assertThat(received)
                    .extracting(EventLogStore.EventEntry::eventType)
                    .containsExactly("after.1");
            } finally {
                subscription.cancel();
            }
        }
    }

    @Test
    @DisplayName("tail runtime snapshot exposes configured polling controls and active subscribers")
    void tailRuntimeSnapshotExposesConfiguredPollingControls() throws Exception {
        H2SovereignEventLogStore.TailPollingConfig pollingConfig =
            new H2SovereignEventLogStore.TailPollingConfig(75L, 3, 25, 5_000L);

        try (H2SovereignEventLogStore store =
                 new H2SovereignEventLogStore(tempDir.resolve("tail-config"), java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), pollingConfig)) {
            TenantContext tenant = TenantContext.of("tenant-tail-config");
            EventLogStore.Subscription sub = runPromise(() -> store.tail(tenant, Offset.of(-1L), e -> { }));
            try {
                Map<String, Object> snapshot = store.tailRuntimeSnapshot();
                assertThat(snapshot.get("pollIntervalMs")).isEqualTo(75L);
                assertThat(snapshot.get("maxSubscribers")).isEqualTo(3);
                assertThat(snapshot.get("maxBatchSize")).isEqualTo(25);
                assertThat(snapshot.get("maxBackoffMs")).isEqualTo(5_000L);
                assertThat(snapshot.get("activeSubscribers")).isEqualTo(1);
            } finally {
                sub.cancel();
            }

            Map<String, Object> afterCancel = store.tailRuntimeSnapshot();
            assertThat(afterCancel.get("activeSubscribers")).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("tail rejects new subscriptions when maxSubscribers is reached")
    void tailRejectsWhenMaxSubscribersReached() throws Exception {
        H2SovereignEventLogStore.TailPollingConfig pollingConfig =
            new H2SovereignEventLogStore.TailPollingConfig(50L, 1, 10, 1_000L);

        try (H2SovereignEventLogStore store =
                 new H2SovereignEventLogStore(tempDir.resolve("tail-subscriber-limit"), java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), pollingConfig)) {
            TenantContext tenant = TenantContext.of("tenant-limit");
            EventLogStore.Subscription first = runPromise(() -> store.tail(tenant, Offset.of(-1L), e -> { }));
            try {
                assertThatThrownBy(() -> runPromise(() -> store.tail(tenant, Offset.of(-1L), e -> { })))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Tail subscriber limit reached");
            } finally {
                first.cancel();
            }
        }
    }

    private static EventLogStore.EventEntry entry(String type, String idempotencyKey) {
        return EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(type)
            .eventVersion("1.0.0")
            .timestamp(Instant.now())
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .contentType("application/json")
            .headers(Map.of())
            .idempotencyKey(idempotencyKey)
            .build();
    }
}
