package com.ghatana.aep.event;

import com.ghatana.datacloud.spi.EventLogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Factory for resolving the default AEP EventCloud implementation.
 */
public final class AepEventCloudFactory {

    private static final Logger log = LoggerFactory.getLogger(AepEventCloudFactory.class);

    private AepEventCloudFactory() {
    }

    /**
     * Creates the default EventCloud implementation.
     *
     * <p>Prefers a Data Cloud {@link EventLogStore}-backed implementation discovered
     * via {@link ServiceLoader}. Falls back to an in-memory implementation ONLY when
     * the {@code AEP_DEV_MODE} environment variable is set to {@code "true"}.
     * In production (no dev-mode flag), the absence of a provider causes a fast-fail
     * to prevent silent event loss.
     *
     * @throws IllegalStateException if no provider is found and dev mode is disabled
     */
    public static EventCloud createDefault() {
        Optional<EventLogStore> discoveredStore = ServiceLoader.load(EventLogStore.class)
            .findFirst();

        if (discoveredStore.isPresent()) {
            log.info("Using EventLogStore-backed AEP EventCloud: {}",
                discoveredStore.get().getClass().getName());
            return new EventLogStoreBackedEventCloud(discoveredStore.get());
        }

        boolean devMode = Boolean.parseBoolean(
                System.getenv().getOrDefault("AEP_DEV_MODE", "false"));

        if (!devMode) {
            throw new IllegalStateException(
                    "[AEP] No EventLogStore SPI provider found on classpath and AEP_DEV_MODE is not set. "
                    + "Set AEP_DEV_MODE=true to allow in-memory fallback for local development. "
                    + "In production, ensure the data-cloud module is on the classpath.");
        }

        log.warn("[AEP] AEP_DEV_MODE=true — using InMemoryEventCloud. "
                + "Events will be LOST on restart. DO NOT use in production.");
        return new InMemoryEventCloud();
    }
}

