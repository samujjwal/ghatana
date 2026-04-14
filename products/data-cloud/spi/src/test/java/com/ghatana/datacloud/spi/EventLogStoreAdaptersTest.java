package com.ghatana.datacloud.spi;

import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

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
}
