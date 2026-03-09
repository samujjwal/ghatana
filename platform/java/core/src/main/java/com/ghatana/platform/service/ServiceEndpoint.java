/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.service;

/**
 * Represents a service endpoint for service discovery.
 *
 * @doc.type record
 * @doc.purpose Service endpoint address for inter-service communication
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ServiceEndpoint(
    String name,
    String endpoint,
    ServiceType type
) {
    public enum ServiceType {
        HTTP,
        GRPC,
        DATABASE,
        CACHE,
        QUEUE
    }

    public ServiceEndpoint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be null or blank");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Service endpoint cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Service type cannot be null");
        }
    }
}
