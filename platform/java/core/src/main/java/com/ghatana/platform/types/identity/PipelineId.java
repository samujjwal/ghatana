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
 * Unique identifier for a pipeline.
 *
 * @doc.type record
 * @doc.purpose Typed identifier for processing pipelines
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PipelineId(String value) {
    
    public PipelineId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Pipeline ID cannot be null or blank");
        }
    }
    
    public static PipelineId of(String value) {
        return new PipelineId(value);
    }
    
    public static PipelineId generate() {
        return new PipelineId(UUID.randomUUID().toString());
    }
    
    @Override
    public String toString() {
        return value;
    }
}
