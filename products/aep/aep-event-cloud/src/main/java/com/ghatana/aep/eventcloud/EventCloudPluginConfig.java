/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration for the Event-Cloud plugin.
 *
 * <p>Controls how AEP connects to Data-Cloud: embedded (in-process) or
 * standalone (remote service). When {@code embedded} is {@code true}, AEP
 * starts an in-process Data-Cloud instance using the provided server config.
 * When {@code false}, AEP connects to an external Data-Cloud service via URL.
 *
 * @param embedded     whether to run Data-Cloud in-process
 * @param serviceUrl   Data-Cloud service URL (used when {@code embedded} is false)
 * @param clusterUrls  comma-separated cluster URLs for distributed mode (optional)
 * @param properties   additional configuration properties passed to Data-Cloud
 *
 * @doc.type record
 * @doc.purpose Event-Cloud plugin configuration
 * @doc.layer product
 * @doc.pattern Configuration, Value Object
 */
public record EventCloudPluginConfig(
    boolean embedded,
    Optional<String> serviceUrl,
    Optional<String> clusterUrls,
    Map<String, Object> properties
) {

    public EventCloudPluginConfig {
        serviceUrl = serviceUrl != null ? serviceUrl : Optional.empty();
        clusterUrls = clusterUrls != null ? clusterUrls : Optional.empty();
        properties = properties != null ? Map.copyOf(properties) : Map.of();

        if (!embedded && serviceUrl.isEmpty()) {
            throw new IllegalArgumentException(
                "serviceUrl is required when embedded mode is disabled");
        }
    }

    /**
     * Creates an embedded (in-process) configuration with defaults.
     */
    public static EventCloudPluginConfig embeddedMode() {
        return new EventCloudPluginConfig(true, Optional.empty(), Optional.empty(), Map.of());
    }

    /**
     * Creates an embedded configuration with additional properties.
     */
    public static EventCloudPluginConfig embeddedMode(Map<String, Object> properties) {
        return new EventCloudPluginConfig(true, Optional.empty(), Optional.empty(), properties);
    }

    /**
     * Creates a standalone (remote service) configuration.
     *
     * @param serviceUrl the Data-Cloud service URL
     */
    public static EventCloudPluginConfig standalone(String serviceUrl) {
        Objects.requireNonNull(serviceUrl, "serviceUrl required");
        return new EventCloudPluginConfig(false, Optional.of(serviceUrl), Optional.empty(), Map.of());
    }

    /**
     * Creates a distributed (cluster) configuration.
     *
     * @param clusterUrls comma-separated cluster node URLs
     */
    public static EventCloudPluginConfig distributed(String clusterUrls) {
        Objects.requireNonNull(clusterUrls, "clusterUrls required");
        // Use first URL as the primary service URL
        String primary = clusterUrls.split(",")[0].trim();
        return new EventCloudPluginConfig(
            false, Optional.of(primary), Optional.of(clusterUrls), Map.of());
    }

    /**
     * Resolves configuration from environment variables.
     *
     * <p>Reads:
     * <ul>
     *   <li>{@code DC_DEPLOYMENT_MODE}: "embedded", "standalone", or "distributed"</li>
     *   <li>{@code DC_SERVER_URL}: Data-Cloud service URL</li>
     *   <li>{@code DC_CLUSTER_URLS}: Comma-separated cluster URLs</li>
     * </ul>
     */
    public static EventCloudPluginConfig fromEnvironment() {
        String mode = System.getenv("DC_DEPLOYMENT_MODE");
        if (mode == null || mode.isBlank() || "embedded".equalsIgnoreCase(mode)) {
            return embeddedMode();
        }
        if ("distributed".equalsIgnoreCase(mode)) {
            String urls = System.getenv("DC_CLUSTER_URLS");
            if (urls != null && !urls.isBlank()) {
                return distributed(urls);
            }
        }
        String url = System.getenv("DC_SERVER_URL");
        if (url == null || url.isBlank()) {
            // Fall back to embedded when no URL is configured
            return embeddedMode();
        }
        return standalone(url);
    }
}
