package com.ghatana.eventcore.domain;

import com.ghatana.contracts.event.v1.EventProto;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Minimal domain wrapper around contracts.event.v1.Event for storage operations.
 * Invariants (to be validated by callers where applicable):
 *  - occurredAt <= ingestedAt (if both available)
 */
public final class EventRecord {
    private final EventProto envelope;

    public EventRecord(EventProto envelope) {
        this.envelope = Objects.requireNonNull(envelope, "envelope");
    }

    public EventProto envelope() {
        return envelope;
    }

    /**
     * Rough size in bytes based on serialized JSON payload and headers; can be refined later.
     */
    public long sizeBytes() {
        long sz = 0L;
        if (envelope.getPayloadJson() != null) {
            sz += envelope.getPayloadJson().getBytes(StandardCharsets.UTF_8).length;
        }
        // approximate for headers map
        sz += envelope.getHeadersMap().toString().getBytes(StandardCharsets.UTF_8).length;
        // id and type
        sz += envelope.getId().toString().getBytes(StandardCharsets.UTF_8).length;
        sz += envelope.getType().getBytes(StandardCharsets.UTF_8).length;
        return sz;
    }

    /**
     * Simple checksum computed over type + payload_json + headers string.
     */
    public long checksum() {
        CRC32 crc = new CRC32();
        crc.update(envelope.getType().getBytes(StandardCharsets.UTF_8));
        if (envelope.getPayloadJson() != null) {
            crc.update(envelope.getPayloadJson().getBytes(StandardCharsets.UTF_8));
        }
        crc.update(envelope.getHeadersMap().toString().getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}
