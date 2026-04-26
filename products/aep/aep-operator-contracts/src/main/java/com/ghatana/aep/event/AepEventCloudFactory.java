/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory for creating {@link EventCloud} instances.
 *
 * <p>Discovers the appropriate implementation via {@link ServiceLoader}:
 * <ol>
 *   <li>Looks for an {@code EventCloud} provider (e.g.,
 *       {@code DataCloudBackedEventCloud} from {@code aep-event-cloud}).</li>
 *   <li>Falls back to {@link InMemoryEventCloud} only in development mode.</li>
 * </ol>
 *
 * <p>T-19: In production ({@code AEP_PROFILE=production} or when neither
 * {@code AEP_PROFILE} nor {@code AEP_DEV_MODE} is set), the factory <em>fails
 * closed</em>: if no durable SPI provider is found it throws
 * {@link IllegalStateException} rather than silently using in-memory storage.
 *
 * <p>Set {@code AEP_DEV_MODE=true} to permit the in-memory fallback in
 * non-production environments.
 *
 * @doc.type class
 * @doc.purpose EventCloud factory with ServiceLoader discovery and production fail-closed policy
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class AepEventCloudFactory {

    /** Environment variable to permit in-memory fallback (dev/test only). */
    private static final String AEP_DEV_MODE = "AEP_DEV_MODE";
    /** Environment variable that declares the runtime profile. */
    private static final String AEP_PROFILE = "AEP_PROFILE";
    /** Environment variable (legacy) for the runtime environment name. */
    private static final String AEP_ENV = "AEP_ENV";

    private AepEventCloudFactory() {}

    /**
     * Creates the default {@link EventCloud} instance.
     *
     * <p>Uses {@link ServiceLoader} to discover a durable {@code EventCloud} provider.
     * When none is found:
     * <ul>
     *   <li>If {@code AEP_DEV_MODE=true} — falls back to {@link InMemoryEventCloud}
     *       with a warning printed to stderr.</li>
     *   <li>Otherwise (production) — throws {@link IllegalStateException} so the
     *       process fails fast rather than silently losing events.</li>
     * </ul>
     *
     * @return a ready-to-use EventCloud
     * @throws IllegalStateException if no durable provider is found in production
     */
    public static EventCloud createDefault() {
        return createDefault(System.getenv());
    }

    /**
     * Creates the default {@link EventCloud} instance using the supplied environment map.
     *
     * @param environment the environment variables to consult for profile/dev-mode flags
     * @return a ready-to-use EventCloud
     * @throws IllegalStateException if no durable provider is found in production
     */
    public static EventCloud createDefault(Map<String, String> environment) {
        return ServiceLoader.load(EventCloud.class)
            .findFirst()
            .orElseGet(() -> {
                boolean isDevMode = "true".equalsIgnoreCase(environment.get(AEP_DEV_MODE));
                if (isDevMode) {
                    System.err.println(
                        "[AepEventCloudFactory] INFO: No EventCloud SPI provider found. " +
                        "Using InMemoryEventCloud (AEP_DEV_MODE=true). " +
                        "Add aep-event-cloud + data-cloud to classpath for production use.");
                    return new InMemoryEventCloud();
                }

                // T-19: fail closed in production — do not silently fall back to in-memory
                boolean isProduction = isProduction(environment);
                if (isProduction) {
                    throw new IllegalStateException(
                        "[AepEventCloudFactory] No durable EventCloud SPI provider found in " +
                        "production. Add aep-event-cloud and a data-cloud implementation to the " +
                        "classpath, or set AEP_DEV_MODE=true to acknowledge the in-memory fallback " +
                        "in non-production environments.");
                }

                // Non-production, non-dev-mode: warn and fall back (staging/integration CI)
                System.err.println(
                    "[AepEventCloudFactory] WARNING: No EventCloud SPI provider found. " +
                    "Using InMemoryEventCloud. Set AEP_DEV_MODE=true to suppress this warning, " +
                    "or add aep-event-cloud + data-cloud to classpath.");
                return new InMemoryEventCloud();
            });
    }

    private static boolean isProduction(Map<String, String> environment) {
        String profile = environment.get(AEP_PROFILE);
        if (profile != null && !profile.isBlank()) {
            return "production".equalsIgnoreCase(profile.trim());
        }
        String env = environment.get(AEP_ENV);
        if (env != null && !env.isBlank()) {
            return "production".equalsIgnoreCase(env.trim());
        }
        // Default to production when no env vars are set (fail safe)
        return true;
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
