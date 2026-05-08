package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * API compatibility tests for EventLogStoreAdapters.
 *
 * <p>These tests enforce DC-DRY-002: the Data-Cloud SPI EventLogStore and the
 * platform EventLogStore are compatible via the adapter bridge, so there are
 * no incompatible dual abstractions.
 */
class EventLogStoreAdaptersTest {
    @Test
    void tenantContext_roundtrip() { 
        com.ghatana.datacloud.spi.TenantContext dc = new com.ghatana.datacloud.spi.TenantContext( 
            "t1", Optional.of("w1"), Map.of("k","v"));
        com.ghatana.platform.domain.eventstore.TenantContext plat = EventLogStoreAdapters.toPlatformTenantContext(dc); 
        com.ghatana.datacloud.spi.TenantContext dc2 = EventLogStoreAdapters.toDataCloudTenantContext(plat); 
        assertThat(dc2.tenantId()).isEqualTo("t1");
        assertThat(dc2.workspaceId()).contains("w1");
        assertThat(dc2.metadata()).containsEntry("k","v"); 
    }

    @Test
    void eventEntry_roundtrip() { 
        com.ghatana.datacloud.spi.EventLogStore.EventEntry dc = new com.ghatana.datacloud.spi.EventLogStore.EventEntry( 
            java.util.UUID.randomUUID(), "type", "1", Instant.now(), 
            java.nio.ByteBuffer.wrap("payload".getBytes()), "ct", Map.of(), Optional.of("idk"));
        com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry plat = EventLogStoreAdapters.toPlatformEventEntry(dc); 
        com.ghatana.datacloud.spi.EventLogStore.EventEntry dc2 = EventLogStoreAdapters.toDataCloudEventEntry(plat); 
        assertThat(dc2.eventId()).isEqualTo(dc.eventId()); 
        assertThat(dc2.eventType()).isEqualTo(dc.eventType()); 
        assertThat(dc2.eventVersion()).isEqualTo(dc.eventVersion()); 
        assertThat(dc2.payload()).hasToString(dc.payload().toString()); 
        assertThat(dc2.contentType()).isEqualTo(dc.contentType()); 
        assertThat(dc2.idempotencyKey()).isEqualTo(dc.idempotencyKey()); 
    }

    @Test
    @DisplayName("DC-DRY-002: toPlatformStore returns platform interface implementation")
    void toPlatformStore_returnsPlatformInterface() {
        EventLogStore noopSpi = createNoopSpiStore();
        com.ghatana.platform.domain.eventstore.EventLogStore platformStore =
            EventLogStoreAdapters.toPlatformStore(noopSpi);
        assertThat(platformStore).isInstanceOf(com.ghatana.platform.domain.eventstore.EventLogStore.class);
    }

    @Test
    @DisplayName("DC-DRY-002: fromPlatformStore returns SPI interface implementation")
    void fromPlatformStore_returnsSpiInterface() {
        com.ghatana.platform.domain.eventstore.EventLogStore noopPlatform = createNoopPlatformStore();
        EventLogStore spiStore = EventLogStoreAdapters.fromPlatformStore(noopPlatform);
        assertThat(spiStore).isInstanceOf(EventLogStore.class);
    }

    @Test
    @DisplayName("DC-DRY-002: DataCloudClient uses SPI EventLogStore (not raw String import)")
    void dataCloudClient_usesCanonicalSpiEventLogStore() throws Exception {
        java.lang.reflect.Method method = com.ghatana.datacloud.DataCloudClient.class
            .getDeclaredMethod("eventLogStore");
        assertThat(method.getReturnType()).isEqualTo(EventLogStore.class);
    }

    private static EventLogStore createNoopSpiStore() {
        return new EventLogStore() {
            public io.activej.promise.Promise<com.ghatana.platform.types.identity.Offset> append(TenantContext t, EventEntry e) { return io.activej.promise.Promise.of(null); }
            public io.activej.promise.Promise<java.util.List<com.ghatana.platform.types.identity.Offset>> appendBatch(TenantContext t, java.util.List<EventEntry> es) { return io.activej.promise.Promise.of(List.of()); }
            public io.activej.promise.Promise<java.util.List<EventEntry>> read(TenantContext t, com.ghatana.platform.types.identity.Offset f, int l) { return io.activej.promise.Promise.of(List.of()); }
            public io.activej.promise.Promise<java.util.List<EventEntry>> readByTimeRange(TenantContext t, Instant s, Instant e, int l) { return io.activej.promise.Promise.of(List.of()); }
            public io.activej.promise.Promise<java.util.List<EventEntry>> readByType(TenantContext t, String type, com.ghatana.platform.types.identity.Offset f, int l) { return io.activej.promise.Promise.of(List.of()); }
            public io.activej.promise.Promise<com.ghatana.platform.types.identity.Offset> getLatestOffset(TenantContext t) { return io.activej.promise.Promise.of(null); }
            public io.activej.promise.Promise<com.ghatana.platform.types.identity.Offset> getEarliestOffset(TenantContext t) { return io.activej.promise.Promise.of(null); }
            public io.activej.promise.Promise<Subscription> tail(TenantContext t, com.ghatana.platform.types.identity.Offset f, java.util.function.Consumer<EventEntry> h) { return io.activej.promise.Promise.of(null); }
        };
    }

    private static com.ghatana.platform.domain.eventstore.EventLogStore createNoopPlatformStore() {
        return new com.ghatana.platform.domain.eventstore.EventLogStore() {
            public io.activej.promise.Promise<com.ghatana.platform.types.identity.Offset> append(com.ghatana.platform.domain.eventstore.TenantContext t, com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry e) { return io.activej.promise.Promise.of(null); }
            public io.activej.promise.Promise<java.util.List<com.ghatana.platform.types.identity.Offset>> appendBatch(com.ghatana.platform.domain.eventstore.TenantContext t, java.util.List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> es) { return io.activej.promise.Promise.of(List.of()); }
            public io.activej.promise.Promise<java.util.List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> read(com.ghatana.platform.domain.eventstore.TenantContext t, com.ghatana.platform.types.identity.Offset f, int l) { return io.activej.promise.Promise.of(List.of()); }
            public io.activej.promise.Promise<java.util.List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> readByTimeRange(com.ghatana.platform.domain.eventstore.TenantContext t, Instant s, Instant e, int l) { return io.activej.promise.Promise.of(List.of()); }
            public io.activej.promise.Promise<java.util.List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> readByType(com.ghatana.platform.domain.eventstore.TenantContext t, String type, com.ghatana.platform.types.identity.Offset f, int l) { return io.activej.promise.Promise.of(List.of()); }
            public io.activej.promise.Promise<com.ghatana.platform.types.identity.Offset> getLatestOffset(com.ghatana.platform.domain.eventstore.TenantContext t) { return io.activej.promise.Promise.of(null); }
            public io.activej.promise.Promise<com.ghatana.platform.types.identity.Offset> getEarliestOffset(com.ghatana.platform.domain.eventstore.TenantContext t) { return io.activej.promise.Promise.of(null); }
            public io.activej.promise.Promise<com.ghatana.platform.domain.eventstore.EventLogStore.Subscription> tail(com.ghatana.platform.domain.eventstore.TenantContext t, com.ghatana.platform.types.identity.Offset f, java.util.function.Consumer<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> h) { return io.activej.promise.Promise.of(null); }
        };
    }
}
