/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.types.identity;

import java.util.UUID;

/**
 * Unique identifier for a partition.
 *
 * @doc.type record
 * @doc.purpose Typed identifier for data partitions
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PartitionId(String value) implements Identifier {

    public PartitionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Partition ID cannot be null or blank");
        }
    }

    public static PartitionId of(String value) {
        return new PartitionId(value);
    }

    public static PartitionId of(int value) {
        return new PartitionId(String.valueOf(value));
    }

    public static PartitionId random() {
        return new PartitionId(UUID.randomUUID().toString());
    }

    @Override
    public String raw() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
