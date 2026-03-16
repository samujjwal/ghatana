/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.health;

import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.datacloud.config.model.CompiledPluginConfig;
import com.ghatana.platform.observability.health.HealthCheck;
import com.ghatana.platform.observability.health.HealthCheck.HealthCheckResult;
import com.ghatana.platform.observability.health.HealthCheckRegistry;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Factory for creating and registering health checks from plugin
 * configurations. Automatically creates appropriate health checks based on
 * plugin backend type.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes health check creation from plugin configs. Supports custom check
 * implementations per backend type (PostgreSQL, Redis, Kafka, S3, etc.).
 *
 * <p>
 * <b>Usage Example</b><br>
 * <pre>{@code
 * PluginHealthCheckFactory factory = new PluginHealthCheckFactory(
 *     configRegistry,
 *     healthCheckRegistry
 * );
 *
 * // Register custom check implementation for PostgreSQL
 * factory.registerCheckImplementation("postgresql", plugin ->
 *     () -> checkPostgresConnection(plugin)
 * );
 *
 * // Create and register health checks for all plugins
 * factory.createAndRegisterAll("tenant-1")
 *     .whenResult(count -> log.info("Registered {} health checks", count));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for creating health checks from plugin configuration
 * @doc.layer product
 * @doc.pattern Factory, Strategy
 */
public class PluginHealthCheckFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PluginHealthCheckFactory.class);

    private final ConfigRegistry configRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    // Custom check implementations per backend type
    private final Map<String, Function<CompiledPluginConfig, Supplier<Promise<HealthCheckResult>>>> checkImplementations = new ConcurrentHashMap<>();

    // Registered health checks (for cleanup)
    private final Map<String, ConfigDrivenHealthCheck> activeChecks = new ConcurrentHashMap<>();

    /**
     * Creates a new PluginHealthCheckFactory.
     *
     * @param configRegistry the config registry for loading plugins
     * @param healthCheckRegistry the health check registry for registration
     */
    public PluginHealthCheckFactory(
            @NotNull ConfigRegistry configRegistry,
            @NotNull HealthCheckRegistry healthCheckRegistry) {
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry required");
        this.healthCheckRegistry = Objects.requireNonNull(healthCheckRegistry, "healthCheckRegistry required");

        // Register default check implementations
        registerDefaultImplementations();
    }

    /**
     * Registers a custom check implementation for a backend type.
     *
     * @param backendType the backend type (e.g., "postgresql", "redis")
     * @param checkFactory factory that creates the check function from plugin
     * config
     */
    public void registerCheckImplementation(
            @NotNull String backendType,
            @NotNull Function<CompiledPluginConfig, Supplier<Promise<HealthCheckResult>>> checkFactory) {

        checkImplementations.put(backendType.toLowerCase(), checkFactory);
        LOG.info("Registered health check implementation for backend: {}", backendType);
    }

    /**
     * Creates and registers a health check for a specific plugin.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return Promise containing the created health check, or empty if plugin
     * not found
     */
    public Promise<java.util.Optional<ConfigDrivenHealthCheck>> createAndRegister(
            @NotNull String tenantId,
            @NotNull String pluginName) {

        return configRegistry.getPlugin(tenantId, pluginName)
                .then(optPlugin -> {
                    if (optPlugin.isEmpty()) {
                        LOG.warn("Plugin not found: {}/{}", tenantId, pluginName);
                        return Promise.of(java.util.Optional.<ConfigDrivenHealthCheck>empty());
                    }

                    CompiledPluginConfig plugin = optPlugin.get();

                    if (plugin.healthCheck() == null || !plugin.healthCheck().enabled()) {
                        LOG.debug("Health check disabled for plugin: {}", pluginName);
                        return Promise.of(java.util.Optional.<ConfigDrivenHealthCheck>empty());
                    }

                    ConfigDrivenHealthCheck healthCheck = createHealthCheck(plugin);

                    // Register with health check registry
                    healthCheckRegistry.register(healthCheck);

                    // Track for cleanup
                    String key = tenantId + ":" + pluginName;
                    activeChecks.put(key, healthCheck);

                    LOG.info("Registered health check for plugin: {} (interval={}, timeout={})",
                            pluginName,
                            healthCheck.getInterval(),
                            healthCheck.getTimeout());

                    return Promise.of(java.util.Optional.of(healthCheck));
                });
    }

    /**
     * Creates and registers health checks for all plugins of a tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise containing count of registered health checks
     */
    public Promise<Integer> createAndRegisterAll(@NotNull String tenantId) {
        return configRegistry.getPluginNames(tenantId)
                .then(pluginNames -> {
                    LOG.info("Creating health checks for {} plugins (tenant={})",
                            pluginNames.size(), tenantId);

                    java.util.List<Promise<java.util.Optional<ConfigDrivenHealthCheck>>> promises
                            = pluginNames.stream()
                                    .map(name -> createAndRegister(tenantId, name))
                                    .toList();

                    return Promises.toList(promises)
                            .map(results -> {
                                int count = (int) results.stream()
                                        .filter(java.util.Optional::isPresent)
                                        .count();
                                LOG.info("Registered {} health checks for tenant {}", count, tenantId);
                                return count;
                            });
                });
    }

    /**
     * Unregisters health check for a specific plugin.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     */
    public void unregister(@NotNull String tenantId, @NotNull String pluginName) {
        String key = tenantId + ":" + pluginName;
        ConfigDrivenHealthCheck check = activeChecks.remove(key);

        if (check != null) {
            healthCheckRegistry.unregister(check.getName());
            LOG.info("Unregistered health check for plugin: {}", pluginName);
        }
    }

    /**
     * Unregisters all health checks for a tenant.
     *
     * @param tenantId tenant identifier
     */
    public void unregisterAll(@NotNull String tenantId) {
        String prefix = tenantId + ":";

        activeChecks.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList()
                .forEach(key -> {
                    ConfigDrivenHealthCheck check = activeChecks.remove(key);
                    if (check != null) {
                        healthCheckRegistry.unregister(check.getName());
                    }
                });

        LOG.info("Unregistered all health checks for tenant: {}", tenantId);
    }

    /**
     * Gets an active health check by plugin name.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return the health check, or null if not registered
     */
    public ConfigDrivenHealthCheck getHealthCheck(String tenantId, String pluginName) {
        return activeChecks.get(tenantId + ":" + pluginName);
    }

    private ConfigDrivenHealthCheck createHealthCheck(CompiledPluginConfig plugin) {
        String backendType = plugin.backend().type().toLowerCase();

        Function<CompiledPluginConfig, Supplier<Promise<HealthCheckResult>>> factory
                = checkImplementations.get(backendType);

        Supplier<Promise<HealthCheckResult>> checkFunction;
        if (factory != null) {
            checkFunction = factory.apply(plugin);
        } else {
            // Default: simple ping check
            checkFunction = () -> createDefaultCheck(plugin);
        }

        return ConfigDrivenHealthCheck.builder()
                .pluginConfig(plugin)
                .checkFunction(checkFunction)
                .name(plugin.name() + "-health")
                .critical(false) // Plugins are readiness checks, not liveness
                .build();
    }

    private Promise<HealthCheckResult> createDefaultCheck(CompiledPluginConfig plugin) {
        // Default implementation - just reports healthy if plugin is loaded
        // Real implementations should check actual connectivity
        return Promise.of(HealthCheckResult.healthy(
                "Plugin loaded",
                Map.of(
                        "plugin", plugin.name(),
                        "type", plugin.type().name(),
                        "backend", plugin.backend().type()
                ),
                java.time.Duration.ZERO));
    }

    private void registerDefaultImplementations() {
        // -----------------------------------------------------------------------
        // PostgreSQL — TCP socket probe to the JDBC host:port
        // -----------------------------------------------------------------------
        registerCheckImplementation("postgresql", plugin -> () ->
                checkTcpConnectivity("postgresql", plugin.backend().connectionUrl(), 5432,
                        Duration.ofSeconds(3)));

        // -----------------------------------------------------------------------
        // Redis — TCP socket probe to host:port
        // -----------------------------------------------------------------------
        registerCheckImplementation("redis", plugin -> () ->
                checkTcpConnectivity("redis", plugin.backend().connectionUrl(), 6379,
                        Duration.ofSeconds(3)));

        // -----------------------------------------------------------------------
        // Kafka — TCP socket probe to bootstrap broker host:port
        // -----------------------------------------------------------------------
        registerCheckImplementation("kafka", plugin -> () ->
                checkTcpConnectivity("kafka", plugin.backend().connectionUrl(), 9092,
                        Duration.ofSeconds(3)));

        // -----------------------------------------------------------------------
        // S3 / MinIO — HTTP HEAD request to the endpoint
        // -----------------------------------------------------------------------
        registerCheckImplementation("s3", plugin -> () ->
                checkHttpEndpoint("s3", plugin.backend().connectionUrl(), Duration.ofSeconds(5)));

        // -----------------------------------------------------------------------
        // ClickHouse — HTTP GET to the built-in health probe endpoint
        // -----------------------------------------------------------------------
        registerCheckImplementation("clickhouse", plugin -> () -> {
            String rawUrl = plugin.backend().connectionUrl();
            // ClickHouse HTTP health endpoint: <scheme>://<host>:<port>/ping
            String pingUrl = buildClickHousePingUrl(rawUrl);
            return checkHttpEndpoint("clickhouse", pingUrl, Duration.ofSeconds(5));
        });

        LOG.info("Registered {} default health check implementations", checkImplementations.size());
    }

    // =========================================================================
    // Internal probe helpers
    // =========================================================================

    /**
     * Opens a TCP socket to {@code host:port} derived from {@code connectionUrl}
     * and returns {@link HealthCheckResult#healthy} / {@link HealthCheckResult#unhealthy}
     * accordingly.
     */
    private static Promise<HealthCheckResult> checkTcpConnectivity(
            String backendType,
            String connectionUrl,
            int defaultPort,
            Duration timeout) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            long start = System.currentTimeMillis();
            String host = "unknown";
            int port = defaultPort;

            try {
                HostPort hp = parseHostPort(connectionUrl, defaultPort);
                host = hp.host();
                port = hp.port();

                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port),
                            (int) timeout.toMillis());
                }

                long elapsedMs = System.currentTimeMillis() - start;
                return HealthCheckResult.healthy(
                        backendType + " reachable",
                        Map.of("host", host, "port", port, "responseTimeMs", elapsedMs),
                        Duration.ofMillis(elapsedMs));

            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - start;
                return HealthCheckResult.unhealthy(
                        backendType + " unreachable: " + e.getMessage(),
                        Map.of("host", host, "port", port,
                                "error", e.getClass().getSimpleName(),
                                "responseTimeMs", elapsedMs),
                        Duration.ofMillis(elapsedMs));
            }
        });
    }

    /**
     * Issues an HTTP HEAD (or GET) to {@code url} and treats any 2xx/3xx as
     * healthy.
     */
    private static Promise<HealthCheckResult> checkHttpEndpoint(
            String backendType,
            String url,
            Duration timeout) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            long start = System.currentTimeMillis();
            if (url == null || url.isBlank()) {
                return HealthCheckResult.unhealthy(
                        backendType + " endpoint URL not configured",
                        Map.of(),
                        Duration.ZERO);
            }

            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(timeout)
                        .build();

                HttpResponse<Void> response = client.send(
                        request, HttpResponse.BodyHandlers.discarding());

                long elapsedMs = System.currentTimeMillis() - start;
                int status = response.statusCode();
                boolean ok = (status >= 200 && status < 400);

                return ok
                        ? HealthCheckResult.healthy(
                                backendType + " responded HTTP " + status,
                                Map.of("url", url, "statusCode", status, "responseTimeMs", elapsedMs),
                                Duration.ofMillis(elapsedMs))
                        : HealthCheckResult.unhealthy(
                                backendType + " returned HTTP " + status,
                                Map.of("url", url, "statusCode", status, "responseTimeMs", elapsedMs),
                                Duration.ofMillis(elapsedMs));

            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - start;
                return HealthCheckResult.unhealthy(
                        backendType + " unreachable: " + e.getMessage(),
                        Map.of("url", url, "error", e.getClass().getSimpleName(),
                                "responseTimeMs", elapsedMs),
                        Duration.ofMillis(elapsedMs));
            }
        });
    }

    /**
     * Parses a connection URL (JDBC, Redis URL, or plain {@code host:port}) and
     * returns the host/port tuple.
     */
    private static HostPort parseHostPort(String connectionUrl, int defaultPort) {
        if (connectionUrl == null || connectionUrl.isBlank()) {
            return new HostPort("localhost", defaultPort);
        }
        try {
            // Strip JDBC prefix: jdbc:postgresql://host:port/db → //host:port/db
            String normalized = connectionUrl;
            if (normalized.startsWith("jdbc:")) {
                normalized = normalized.substring(normalized.indexOf("//"));
            }
            URI uri = URI.create(normalized);
            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            return new HostPort(host, port);
        } catch (Exception e) {
            // Fallback: try to parse "host:port" or just "host"
            String stripped = connectionUrl.replaceAll("^[a-zA-Z+.]+://", "").split("/")[0];
            String[] parts = stripped.split(":");
            String host = parts[0].isBlank() ? "localhost" : parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1].split("\\?")[0]) : defaultPort;
            return new HostPort(host, port);
        }
    }

    /**
     * Derives the ClickHouse HTTP /ping URL from a JDBC or HTTP connection URL.
     */
    private static String buildClickHousePingUrl(String connectionUrl) {
        if (connectionUrl == null || connectionUrl.isBlank()) {
            return "http://localhost:8123/ping";
        }
        try {
            HostPort hp = parseHostPort(connectionUrl, 8123);
            return "http://" + hp.host() + ":" + hp.port() + "/ping";
        } catch (Exception e) {
            return "http://localhost:8123/ping";
        }
    }

    private record HostPort(String host, int port) {}
