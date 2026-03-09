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
 * Unique identifier for an agent.
 *
 * @doc.type record
 * @doc.purpose Typed identifier for agent instances
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentId(String value) implements Identifier {

    public AgentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Agent ID cannot be null or blank");
        }
    }

    public static AgentId of(String value) {
        return new AgentId(value);
    }

    public static AgentId random() {
        return new AgentId(UUID.randomUUID().toString());
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
