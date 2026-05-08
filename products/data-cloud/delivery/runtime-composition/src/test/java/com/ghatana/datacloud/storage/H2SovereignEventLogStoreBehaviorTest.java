package com.ghatana.datacloud.storage;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
