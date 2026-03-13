package com.ghatana.aep.event;

import com.ghatana.aep.config.EnvConfig;
import com.ghatana.aep.event.spi.EventCloudConnector;
import com.ghatana.aep.event.spi.GrpcEventCloudConnector;
import com.ghatana.aep.event.spi.HttpEventCloudConnector;
import com.ghatana.datacloud.spi.EventLogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Factory for resolving the active AEP EventCloud implementation.
 *
 * <p>Transport is selected via the {@code EVENT_CLOUD_TRANSPORT} environment variable:
 * <ul>
 *   <li>{@code eventlog} (default) — delegates to Data-Cloud {@link EventLogStore} SPI</li>
 *   <li>{@code grpc} — gRPC transport; endpoint from {@code AEP_GRPC_ENDPOINT}</li>
 *   <li>{@code http} — REST/HTTP transport; base URL from {@code AEP_DC_BASE_URL}</li>
 * </ul>
 *
 * <p>An in-memory fallback is only permitted when {@code AEP_DEV_MODE=true} to prevent
 * silent event loss in production.
 *
 * @doc.type class
 * @doc.purpose Factory for the active AEP EventCloud implementation
 * @doc.layer platform
 * @doc.pattern Factory
 */
public final class AepEventCloudFactory {

    private static final Logger log = LoggerFactory.getLogger(AepEventCloudFactory.class);

    private AepEventCloudFactory() {
    }

    /**
     * Creates the default EventCloud implementation from system environment variables.
     *
     * @return active {@link EventCloud} instance
     * @throws IllegalStateException if no provider is found and {@code AEP_DEV_MODE} is not set
     */
    public static EventCloud createDefault() {
        return createDefault(EnvConfig.fromSystem(), Executors.newCachedThreadPool());
    }

    /**
     * Creates an EventCloud instance using the supplied configuration.
     *
     * @param env              environment config
     * @param blockingExecutor executor for blocking IO in gRPC/HTTP connectors
     * @return active {@link EventCloud} instance
     */
    public static EventCloud createDefault(EnvConfig env, Executor blockingExecutor) {
        String transport = env.eventCloudTransport();
        log.info("[AEP] EVENT_CLOUD_TRANSPORT={}", transport);

        return switch (transport) {
            case "grpc" -> {
                String endpoint = env.aepGrpcEndpoint();
                log.info("[AEP] Using gRPC EventCloudConnector endpoint={}", endpoint);
                EventCloudConnector connector = new GrpcEventCloudConnector(endpoint, blockingExecutor);
                yield new ConnectorBackedEventCloud(connector);
            }
            case "http" -> {
                String baseUrl = env.aepDcBaseUrl();
                log.info("[AEP] Using HTTP EventCloudConnector baseUrl={}", baseUrl);
                EventCloudConnector connector = new HttpEventCloudConnector(baseUrl, blockingExecutor);
                yield new ConnectorBackedEventCloud(connector);
            }
            default -> createEventLogStoreBacked();
        };
    }

    private static EventCloud createEventLogStoreBacked() {
        Optional<EventLogStore> discoveredStore = ServiceLoader.load(EventLogStore.class)
                .findFirst();

        if (discoveredStore.isPresent()) {
            log.info("[AEP] Using EventLogStore-backed AEP EventCloud: {}",
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

