package com.ghatana.core.connectors;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventEnvelope")
class EventEnvelopeTest {

    @Test
    @DisplayName("fromIngestEvent preserves canonical fields")
    void fromIngestEventPreservesCanonicalFields() { // GH-90000
        IngestEvent event = IngestEvent.builder() // GH-90000
                .tenantId(TenantId.of("tenant-a"))
                .eventTypeName("orders.created")
                .eventTypeVersion("2.1.0")
                .headers(Map.of("source", "api")) // GH-90000
                .contentType(ContentType.JSON) // GH-90000
                .schemaUri("urn:schema:orders:2.1.0")
                .payload("payload".getBytes()) // GH-90000
                .partitionKey("orders-1")
                .build(); // GH-90000

        EventEnvelope envelope = EventEnvelope.fromIngestEvent("evt-123", event); // GH-90000

        assertThat(envelope.eventId()).isEqualTo("evt-123");
        assertThat(envelope.tenantId()).isEqualTo(event.tenantId()); // GH-90000
        assertThat(envelope.eventTypeName()).isEqualTo(event.eventTypeName()); // GH-90000
        assertThat(envelope.eventTypeVersion()).isEqualTo(event.eventTypeVersion()); // GH-90000
        assertThat(envelope.contentType()).isEqualTo(event.contentType()); // GH-90000
        assertThat(envelope.schemaUri()).isEqualTo(event.schemaUri()); // GH-90000
        assertThat(envelope.metadata().headers()).containsEntry("source", "api"); // GH-90000
        assertThat(envelope.metadata().partitionKey()).contains("orders-1");
    }

    @Test
    @DisplayName("ingest event converts to canonical envelope")
    void ingestEventConvertsToCanonicalEnvelope() { // GH-90000
        IngestEvent event = IngestEvent.builder() // GH-90000
                .tenantId(TenantId.of("tenant-b"))
                .eventTypeName("positions.updated")
                .contentType(ContentType.JSON) // GH-90000
                .schemaUri("urn:schema:positions:1.0.0")
                .payload("payload".getBytes()) // GH-90000
                .build(); // GH-90000

        EventEnvelope envelope = event.toEventEnvelope("evt-999");

        assertThat(envelope.eventId()).isEqualTo("evt-999");
        assertThat(envelope.metadata()).isEqualTo(EventMetadata.empty()); // GH-90000
    }
}
