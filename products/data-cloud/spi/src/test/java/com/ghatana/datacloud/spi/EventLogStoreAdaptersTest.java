package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EventLogStoreAdaptersTest {
    @Test
    void tenantContext_roundtrip() { // GH-90000
        com.ghatana.datacloud.spi.TenantContext dc = new com.ghatana.datacloud.spi.TenantContext( // GH-90000
            "t1", Optional.of("w1"), Map.of("k","v"));
        com.ghatana.platform.domain.eventstore.TenantContext plat = EventLogStoreAdapters.toPlatformTenantContext(dc); // GH-90000
        com.ghatana.datacloud.spi.TenantContext dc2 = EventLogStoreAdapters.toDataCloudTenantContext(plat); // GH-90000
        assertThat(dc2.tenantId()).isEqualTo("t1");
        assertThat(dc2.workspaceId()).contains("w1");
        assertThat(dc2.metadata()).containsEntry("k","v"); // GH-90000
    }

    @Test
    void eventEntry_roundtrip() { // GH-90000
        com.ghatana.datacloud.spi.EventLogStore.EventEntry dc = new com.ghatana.datacloud.spi.EventLogStore.EventEntry( // GH-90000
            java.util.UUID.randomUUID(), "type", "1", Instant.now(), // GH-90000
            java.nio.ByteBuffer.wrap("payload".getBytes()), "ct", Map.of(), Optional.of("idk"));
        com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry plat = EventLogStoreAdapters.toPlatformEventEntry(dc); // GH-90000
        com.ghatana.datacloud.spi.EventLogStore.EventEntry dc2 = EventLogStoreAdapters.toDataCloudEventEntry(plat); // GH-90000
        assertThat(dc2.eventId()).isEqualTo(dc.eventId()); // GH-90000
        assertThat(dc2.eventType()).isEqualTo(dc.eventType()); // GH-90000
        assertThat(dc2.eventVersion()).isEqualTo(dc.eventVersion()); // GH-90000
        assertThat(dc2.payload()).hasToString(dc.payload().toString()); // GH-90000
        assertThat(dc2.contentType()).isEqualTo(dc.contentType()); // GH-90000
        assertThat(dc2.idempotencyKey()).isEqualTo(dc.idempotencyKey()); // GH-90000
    }
}
