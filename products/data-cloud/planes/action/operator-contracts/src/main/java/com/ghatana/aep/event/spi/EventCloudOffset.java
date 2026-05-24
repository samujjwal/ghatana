package com.ghatana.aep.event.spi;

/**
 * @doc.type record
 * @doc.purpose Identifies an EventCloud stream position by tenant, partition, and offset
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EventCloudOffset(String tenantId, String partition, long offset) {

    public EventCloudOffset {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (partition == null || partition.isBlank()) {
            throw new IllegalArgumentException("partition must not be blank");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
