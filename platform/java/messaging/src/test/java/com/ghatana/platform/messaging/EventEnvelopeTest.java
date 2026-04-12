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
    void fromIngestEventPreservesCanonicalFields() {
        IngestEvent event = IngestEvent.builder()
                .tenantId(TenantId.of("tenant-a"))
                .eventTypeName("orders.created")
                .eventTypeVersion("2.1.0")
                .headers(Map.of("source", "api"))
                .contentType(ContentType.JSON)
                .schemaUri("urn:schema:orders:2.1.0")
                .payload("payload".getBytes())
                .partitionKey("orders-1")
                .build();

        EventEnvelope envelope = EventEnvelope.fromIngestEvent("evt-123", event);

        assertThat(envelope.eventId()).isEqualTo("evt-123");
        assertThat(envelope.tenantId()).isEqualTo(event.tenantId());
        assertThat(envelope.eventTypeName()).isEqualTo(event.eventTypeName());
        assertThat(envelope.eventTypeVersion()).isEqualTo(event.eventTypeVersion());
        assertThat(envelope.contentType()).isEqualTo(event.contentType());
        assertThat(envelope.schemaUri()).isEqualTo(event.schemaUri());
        assertThat(envelope.metadata().headers()).containsEntry("source", "api");
        assertThat(envelope.metadata().partitionKey()).contains("orders-1");
    }

    @Test
    @DisplayName("ingest event converts to canonical envelope")
    void ingestEventConvertsToCanonicalEnvelope() {
        IngestEvent event = IngestEvent.builder()
                .tenantId(TenantId.of("tenant-b"))
                .eventTypeName("positions.updated")
                .contentType(ContentType.JSON)
                .schemaUri("urn:schema:positions:1.0.0")
                .payload("payload".getBytes())
                .build();

        EventEnvelope envelope = event.toEventEnvelope("evt-999");

        assertThat(envelope.eventId()).isEqualTo("evt-999");
        assertThat(envelope.metadata()).isEqualTo(EventMetadata.empty());
    }
}