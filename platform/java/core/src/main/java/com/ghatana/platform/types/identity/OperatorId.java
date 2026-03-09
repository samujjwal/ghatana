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
 * Unique identifier for an operator.
 *
 * @doc.type record
 * @doc.purpose Typed identifier for pipeline operators
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record OperatorId(String value) implements Identifier {

    public OperatorId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Operator ID cannot be null or blank");
        }
    }

    public static OperatorId of(String value) {
        return new OperatorId(value);
    }

    public static OperatorId of(String namespace, String type, String name, String version) {
        return new OperatorId(namespace + ":" + type + ":" + name + ":" + version);
    }

    public static OperatorId random() {
        return new OperatorId(UUID.randomUUID().toString());
    }

    public String getNamespace() {
        String[] parts = value.split(":");
        return parts.length > 0 ? parts[0] : "";
    }

    public String getType() {
        String[] parts = value.split(":");
        return parts.length > 1 ? parts[1] : "";
    }

    public String getName() {
        String[] parts = value.split(":");
        return parts.length > 2 ? parts[2] : "";
    }

    public String getVersion() {
        String[] parts = value.split(":");
        return parts.length > 3 ? parts[3] : "";
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
