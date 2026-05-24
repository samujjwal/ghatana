package com.ghatana.aep.event.spi;

import com.ghatana.aep.model.CanonicalEvent;

import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Represents a CanonicalEvent with its EventCloud offset and partition metadata
 * @doc.layer product
 * @doc.pattern Contract
 */
public record EventCloudRecord(EventCloudOffset offset, CanonicalEvent event) {

    public EventCloudRecord {
        offset = Objects.requireNonNull(offset, "offset must not be null");
        event = Objects.requireNonNull(event, "event must not be null");
    }
}
