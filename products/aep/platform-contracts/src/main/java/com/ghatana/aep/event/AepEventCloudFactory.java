/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event;

/**
 * Factory for creating {@link EventCloud} instances.
 *
 * <p>Selects the appropriate implementation based on the
 * {@code EVENT_CLOUD_TRANSPORT} environment variable. Defaults to the
 * in-memory implementation for development and testing.
 *
 * @doc.type class
 * @doc.purpose EventCloud factory with transport selection
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class AepEventCloudFactory {

    private AepEventCloudFactory() {}

    /**
     * Creates the default {@link EventCloud} instance.
     *
     * <p>Currently returns an {@link InMemoryEventCloud}. Production
     * transports (gRPC, Kafka) will be wired here once available.
     *
     * @return a ready-to-use EventCloud
     */
    public static EventCloud createDefault() {
        return new InMemoryEventCloud();
    }
}
