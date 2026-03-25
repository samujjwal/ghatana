/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event;

import java.util.ServiceLoader;

/**
 * Factory for creating {@link EventCloud} instances.
 *
 * <p>Discovers the appropriate implementation via {@link ServiceLoader}:
 * <ol>
 *   <li>Looks for an {@code EventCloud} provider (e.g.,
 *       {@code DataCloudBackedEventCloud} from {@code aep-event-cloud}).</li>
 *   <li>Falls back to {@link InMemoryEventCloud} for development/testing.</li>
 * </ol>
 *
 * <p>In production, ensure {@code aep-event-cloud} and a {@code data-cloud}
 * implementation are on the classpath so that the Data-Cloud backed provider
 * is discovered automatically.
 *
 * @doc.type class
 * @doc.purpose EventCloud factory with ServiceLoader discovery
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class AepEventCloudFactory {

    /** Environment variable to force in-memory mode. */
    private static final String AEP_DEV_MODE = "AEP_DEV_MODE";

    private AepEventCloudFactory() {}

    /**
     * Creates the default {@link EventCloud} instance.
     *
     * <p>Uses {@link ServiceLoader} to discover a production {@code EventCloud}
     * provider. If none is found and {@code AEP_DEV_MODE=true}, falls back to
     * {@link InMemoryEventCloud}. If not in dev mode and no provider is found,
     * still falls back to in-memory with a warning log.
     *
     * @return a ready-to-use EventCloud
     */
    public static EventCloud createDefault() {
        return ServiceLoader.load(EventCloud.class)
            .findFirst()
            .orElseGet(() -> {
                if (!"true".equalsIgnoreCase(System.getenv(AEP_DEV_MODE))) {
                    System.err.println(
                        "[AepEventCloudFactory] WARNING: No EventCloud SPI provider found. " +
                        "Using InMemoryEventCloud. Add aep-event-cloud + data-cloud to classpath " +
                        "for production use, or set AEP_DEV_MODE=true to suppress this warning.");
                }
                return new InMemoryEventCloud();
            });
    }

    /**
     * Creates an {@link EventCloud} with the specified implementation.
     *
     * @param eventCloud explicit EventCloud implementation
     * @return the provided EventCloud
     */
    public static EventCloud create(EventCloud eventCloud) {
        return eventCloud;
    }
}
