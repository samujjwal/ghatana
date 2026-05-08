package com.ghatana.datacloud.launcher.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OllamaCompletionService;
import com.ghatana.ai.llm.OpenAICompletionService;
import com.ghatana.datacloud.application.observability.ClickHouseTraceExporter;
import com.ghatana.datacloud.application.observability.TraceExportService;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.client.LearningSignalStore;
import com.ghatana.datacloud.di.DataCloudBrainModule;
import com.ghatana.datacloud.infrastructure.config.DataCloudDatabaseConfig;
import com.ghatana.datacloud.launcher.JdbcDatabaseHealthProbe;
import com.ghatana.datacloud.launcher.EventStoreHealthProbe;
import com.ghatana.datacloud.launcher.DataCloudLauncherSettings;
import com.ghatana.datacloud.launcher.DataCloudTransportStartupException;
import com.ghatana.datacloud.launcher.audit.EventLogAuditService;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.launcher.settings.JdbcSettingsStore;
import com.ghatana.datacloud.spi.EntityWriteIdempotencyStore;
import com.ghatana.datacloud.spi.EventLogStoreAdapters;
import com.ghatana.datacloud.storage.H2EntityWriteIdempotencyStore;
import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.client.autonomy.DefaultAutonomyController;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.observability.clickhouse.ClickHouseTraceStorage;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.dns.DnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;

/**
 * @doc.type class
 * @doc.purpose Starts and manages standalone Data-Cloud HTTP dependencies and transport lifecycle
 * @doc.layer product
 * @doc.pattern Bootstrap
 */
public final class DataCloudHttpLauncherBootstrap {

    private DataCloudHttpLauncherBootstrap() {}

    public static void start(DataCloudClient client, Logger log) {
        try {
            start(client, log, System.getenv());
        } catch (UnknownHostException e) {
            throw new RuntimeException("Failed to resolve DNS server address", e);
        }
    }

    static void start(DataCloudClient client, Logger log, Map<String, String> env) throws UnknownHostException {
        start(client, log, env, DataCloudBrainModule::createStandalone, DataCloudLearningBridge::new);
    }

    static void start(
            DataCloudClient client,
            Logger log,
            Map<String, String> env,
            Function<LearningSignalStore, DataCloudBrain> brainFactory,
            Function<DataCloudBrain, DataCloudLearningBridge> learningBridgeFactory) throws UnknownHostException {
        int port = DataCloudLauncherSettings.resolveHttpPort(env);
        DataCloud.DataCloudConfig.DataCloudProfile profile = DataCloudLauncherSettings.resolveProfile(new String[0], env);
        boolean embeddedProfile = DataCloudLauncherSettings.isEmbeddedProfile(profile);
        boolean sovereignProfile = profile == DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN;

        DataCloudBrain brain = null;
        DataCloudLearningBridge learningBridge = null;

        if (DataCloudLauncherSettings.isBrainEnabled(env)) {
            BrainServices brainServices = startBrainServices(
                    log,
                    () -> brainFactory.apply(null),
                    learningBridgeFactory,
                    Runtime.getRuntime()::addShutdownHook);
            brain = brainServices.brain();
            learningBridge = brainServices.learningBridge();
        }

        AnalyticsServices analyticsServices = DataCloudLauncherSettings.isAnalyticsEnabled(env)
                ? startAnalyticsServices(log, AnalyticsQueryEngine::new, ReportService::new)
                : AnalyticsServices.disabled();
        AnalyticsQueryEngine analyticsEngine = analyticsServices.analyticsEngine();

        AIModelManager aiModelManager = null;
        FeatureStoreService featureStoreService = null;
        ReportService reportService = analyticsServices.reportService();

        boolean databaseEnabled = DataCloudLauncherSettings.isDatabaseEnabled(env);
        boolean aiEnabled = DataCloudLauncherSettings.isAiEnabled(env);
        DataSource databaseDataSource = null;
        if (!embeddedProfile && (databaseEnabled || aiEnabled)) {
            databaseDataSource = startRequiredDatabaseDataSource(log, DataCloudHttpLauncherBootstrap::buildDatabaseDataSource);
        }

        if (aiEnabled && databaseDataSource != null) {
            DataSource aiDataSource = databaseDataSource;
            AiServices aiServices = startRequiredAiServices(log, () -> buildAiServices(aiDataSource));
            aiModelManager = aiServices.aiModelManager();
            featureStoreService = aiServices.featureStoreService();
        }

        // B1: Wire LLM completion service using platform:java:ai-integration.
        // Resolved from AI_PROVIDER / OPENAI_API_KEY / OLLAMA_HOST env vars.
        // Nullable — server gracefully degrades to stub mode when absent.
        CompletionService completionService = sovereignProfile ? null : buildCompletionService(env, log);
        if (sovereignProfile) {
            log.info("Sovereign profile active — external LLM completion backends are disabled");
        }

        // B4: Wire ClickHouseTraceStorage as TraceExporter so spans are flushed
        // to the observability backend instead of being silently discarded.
        // Resolved from CLICKHOUSE_HOST / CLICKHOUSE_PORT / CLICKHOUSE_DATABASE env vars.
        // Nullable — server still generates but discards spans when absent.
        TraceExportService traceExportService = buildTraceExportService(env, log);
        double traceSamplingRate = resolveTraceSamplingRate(env);
        io.micrometer.prometheusmetrics.PrometheusMeterRegistry httpPrometheusRegistry =
            new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
                io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        MetricsCollector httpMetricsCollector = MetricsCollectorFactory.create(httpPrometheusRegistry);
        recordAiProviderStateMetric(httpMetricsCollector, aiEnabled, completionService, env, log);

        try {
            // B13: optional Trino coordinator URL for federated queries
            String trinoUrl = env.get("TRINO_URL");
            if (trinoUrl != null && !trinoUrl.isBlank()) {
                log.info("Federated Trino queries enabled: {}", trinoUrl);
            }

            ApiKeyResolver apiKeyResolver = buildApiKeyResolver(env, log);
            JwtTokenProvider jwtProvider = buildJwtProvider(env, log);
            String jwtTenantClaim = env.getOrDefault("DATACLOUD_JWT_TENANT_CLAIM", "tenant_id");
            AutonomyController autonomyController = sovereignProfile ? new DefaultAutonomyController() : null;

                EventLogStore eventLogStore = EventLogStoreAdapters.toPlatformStore(client.eventLogStore());
                DataCloudHttpServer httpServer = new DataCloudHttpServer(client, port, brain, learningBridge, analyticsEngine)
                    .withDeploymentMode(profile.name().toLowerCase())
                    .withReportService(reportService)
                    .withAiModelManager(aiModelManager)
                    .withFeatureStoreService(featureStoreService)
                    .withCompletionService(completionService)
                    .withTraceExportService(traceExportService)
                    .withTraceSamplingRate(traceSamplingRate)
                    .withTrinoUrl(trinoUrl)
                    .withMetricsCollector(httpMetricsCollector)
                    .withStrictTenantResolution(!embeddedProfile)
                    .withStorageCompactionConfig(
                        DataCloudLauncherSettings.resolveStorageCompactionIntervalSeconds(env),
                        DataCloudLauncherSettings.resolveStorageCompactionThreshold(env))
                    .withRateLimitConfig(
                        DataCloudLauncherSettings.resolveRateLimitRequests(env),
                        DataCloudLauncherSettings.resolveRateLimitWindowSeconds(env));

                if (autonomyController != null) {
                    httpServer.withAutonomyController(autonomyController);
                }

                if (!embeddedProfile && databaseDataSource != null) {
                    httpServer.withSettingsStore(new JdbcSettingsStore(
                        databaseDataSource,
                        new ObjectMapper().findAndRegisterModules()));
                    httpServer.withIdempotencyStore(
                        new H2EntityWriteIdempotencyStore(databaseDataSource, Duration.ofHours(24)));
                }

                if (eventLogStore != null) {
                    httpServer
                        .withAuditService(new EventLogAuditService(eventLogStore, new ObjectMapper().findAndRegisterModules()))
                        .withEventLogStore(eventLogStore)
                        .withHealthSubsystem("event_store", new EventStoreHealthProbe(eventLogStore, 500));
                }

                // P3.9.1: Entity lineage tracking via LineagePlugin
                LineagePlugin lineagePlugin = new LineagePlugin();
                lineagePlugin.start(); // starts synchronously via Promise.complete()
                httpServer.withLineagePlugin(lineagePlugin);

                if (apiKeyResolver != null) {
                    httpServer.withApiKeyResolver(apiKeyResolver);
                }
                if (jwtProvider != null) {
                    httpServer.withJwtProvider(jwtProvider)
                            .withJwtTenantClaim(jwtTenantClaim);
                }

                // Security check: enforce authentication by default in production
                boolean insecureMode = Boolean.parseBoolean(env.getOrDefault("DATACLOUD_INSECURE_MODE", "false"));
                boolean hasAuthConfigured = apiKeyResolver != null || jwtProvider != null;
                String configuredBindHost = validateAuthenticationAndResolveBindHost(
                    insecureMode,
                    hasAuthConfigured,
                    embeddedProfile,
                    env.getOrDefault("DATACLOUD_BIND_HOST", ""),
                    log);

                if (!configuredBindHost.isBlank()) {
                    httpServer.withListenHost(configuredBindHost);
                }

            startTransport(
                    httpServer,
                    port,
                    databaseEnabled,
                    aiEnabled ? new AiServices(aiModelManager, featureStoreService) : null,
                    databaseDataSource,
                    log,
                    Runtime.getRuntime()::addShutdownHook);
        } catch (Exception e) {
            log.error("Failed to start HTTP server on port {}", port, e);
            closeDataSource(databaseDataSource);
            throw new DataCloudTransportStartupException(
                    "Failed to start HTTP server on port " + port,
                    e);
        }
    }

    /**
     * Builds an {@link ApiKeyResolver} from the {@code DATACLOUD_API_KEYS} environment variable.
     *
     * <p>The variable accepts a comma-separated list of entries in the format
     * {@code <raw-key>:<tenant-id>} (e.g. {@code secret123:acme-corp}).  Each entry binds the
     * raw API key to a specific tenant so that the resolved {@link Principal} carries the
     * tenant identifier and the security filter can enforce {@code X-Tenant-ID} alignment.
     *
     * <p>In embedded/local profiles only, a plain key without a tenant suffix is accepted for
     * developer convenience and resolves to the {@code "service"} tenant; a deprecation warning
     * is emitted.  This form is rejected at startup for all non-embedded profiles to prevent
     * silent cross-tenant access.
     *
     * <p>Returns {@code null} when the variable is absent or blank, leaving the security
     * filter inactive (acceptable in {@code local} profile; a warning is logged otherwise).
     *
     * @param env environment variables map
     * @param log logger for diagnostics
     * @return a ready {@link ApiKeyResolver}, or {@code null} when auth is unconfigured
     */
    static ApiKeyResolver buildApiKeyResolver(Map<String, String> env, Logger log) {
        String keysEnv = env.get("DATACLOUD_API_KEYS");
        boolean embeddedProfile = DataCloudLauncherSettings.isEmbeddedProfile(
            DataCloudLauncherSettings.resolveProfile(new String[0], env));

        if (keysEnv == null || keysEnv.isBlank()) {
            if (!embeddedProfile) {
                throw new IllegalStateException(
                    "DATACLOUD_API_KEYS environment variable must be set for non-embedded deployment profiles. " +
                    "API key authentication cannot be disabled in production environments. " +
                    "Set DATACLOUD_API_KEYS to a comma-separated list of '<key>:<tenant-id>' entries.");
            }
            log.warn("[DC-E1] DATACLOUD_API_KEYS not set in embedded/local profile — " +
                     "API key authentication is DISABLED. Set DATACLOUD_API_KEYS to enable.");
            return null;
        }

        // DC-P1-005: parse key:tenant bindings. Each entry format: "<raw-key>:<tenant-id>"
        Map<String, Principal> keyToPrincipal = new LinkedHashMap<>();
        for (String entry : keysEnv.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) continue;

            String rawKey;
            String boundTenant;
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx > 0) {
                rawKey = trimmed.substring(0, colonIdx);
                boundTenant = trimmed.substring(colonIdx + 1).trim();
                if (rawKey.isBlank() || boundTenant.isBlank()) {
                    throw new IllegalStateException(
                        "DATACLOUD_API_KEYS entry '" + trimmed + "' has a blank key or tenant. " +
                        "Expected format: '<raw-key>:<tenant-id>'.");
                }
            } else {
                // Plain key without tenant — allowed only in embedded/local profiles.
                if (!embeddedProfile) {
                    throw new IllegalStateException(
                        "DATACLOUD_API_KEYS entry '" + trimmed + "' is missing a tenant binding. " +
                        "Non-embedded profiles require the '<raw-key>:<tenant-id>' format so that " +
                        "each API key is bound to exactly one tenant and cannot access arbitrary tenants.");
                }
                rawKey = trimmed;
                boundTenant = "service";
                log.warn("[DC-P1-005] API key without tenant binding in local profile — " +
                         "key fingerprint={} will be bound to 'service' tenant. " +
                         "Use '<key>:<tenant-id>' format in non-local environments.",
                         apiKeyFingerprint(rawKey));
            }

            String keyId = "key-" + apiKeyFingerprint(rawKey);
            keyToPrincipal.put(rawKey, new Principal(keyId, List.of("api-client"), boundTenant));
        }

        if (keyToPrincipal.isEmpty()) {
            if (!embeddedProfile) {
                throw new IllegalStateException(
                    "DATACLOUD_API_KEYS must contain at least one non-blank '<key>:<tenant-id>' entry " +
                    "for non-embedded deployment profiles.");
            }
            log.warn("[DC-E1] DATACLOUD_API_KEYS is set but contains no valid entries — API key authentication is DISABLED");
            return null;
        }

        Map<String, Principal> immutableKeyMap = Collections.unmodifiableMap(keyToPrincipal);
        log.info("[DC-E1] API key authentication enabled ({} key(s) registered)", immutableKeyMap.size());
        return apiKey -> Optional.ofNullable(immutableKeyMap.get(apiKey));
    }

    private static String apiKeyFingerprint(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                encoded.append(String.format("%02x", value));
            }
            return encoded.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Builds a JWT provider from standalone launcher environment variables.
     *
     * <p>Required variables when enabled:
     * <ul>
     *   <li>{@code DATACLOUD_JWT_JWKS_URL} — optional JWKS endpoint used for verification-only JWT auth</li>
     *   <li>{@code DATACLOUD_JWT_SECRET} — shared secret used to sign and validate tokens</li>
     *   <li>{@code DATACLOUD_JWT_VALIDITY_MS} — optional token validity, default 3600000</li>
     * </ul>
     *
     * @return JWT provider or {@code null} when JWT auth is not configured
     */
    static JwtTokenProvider buildJwtProvider(Map<String, String> env, Logger log) {
        String jwksUrl = env.get("DATACLOUD_JWT_JWKS_URL");
        if (jwksUrl != null && !jwksUrl.isBlank()) {
            log.info("[DC-E1] JWT authentication enabled via JWKS endpoint {} (tenant claim: {})",
                    jwksUrl,
                    env.getOrDefault("DATACLOUD_JWT_TENANT_CLAIM", "tenant_id"));
            return JwtTokenProviders.fromJwksUrl(jwksUrl);
        }

        String secret = env.get("DATACLOUD_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            return null;
        }

        long validityMs = Long.parseLong(env.getOrDefault("DATACLOUD_JWT_VALIDITY_MS", "3600000"));
        log.info("[DC-E1] JWT authentication enabled (tenant claim: {})",
                env.getOrDefault("DATACLOUD_JWT_TENANT_CLAIM", "tenant_id"));
        return JwtTokenProviders.fromSharedSecret(secret, validityMs);
    }

    static boolean isLoopbackHost(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    static String validateAuthenticationAndResolveBindHost(boolean insecureMode,
                                                           boolean hasAuthConfigured,
                                                           boolean embeddedProfile,
                                                           String configuredBindHost,
                                                           Logger log) {
        String bindHost = configuredBindHost == null ? "" : configuredBindHost.trim();

        if (!insecureMode && !hasAuthConfigured && !embeddedProfile) {
            throw new IllegalStateException(
                "SECURITY ERROR: No authentication configured for Data-Cloud HTTP server. " +
                    "Authentication is required by default in production environments. " +
                    "Configure either DATACLOUD_API_KEYS for API key authentication " +
                    "or DATACLOUD_JWT_SECRET / DATACLOUD_JWT_JWKS_URL for JWT authentication. " +
                    "To disable this check (NOT recommended for production), set DATACLOUD_INSECURE_MODE=true.");
        }

        if (!insecureMode && !hasAuthConfigured && embeddedProfile) {
            log.warn("[DC-E2] No authentication configured in embedded/local profile. " +
                "Set DATACLOUD_API_KEYS or DATACLOUD_JWT_SECRET to enable authentication. " +
                "To allow insecure mode, set DATACLOUD_INSECURE_MODE=true.");
        }

        if (embeddedProfile && insecureMode && !hasAuthConfigured) {
            if (bindHost.isBlank()) {
                bindHost = "127.0.0.1";
                log.warn("[DC-E2] DATACLOUD_BIND_HOST not set while running insecure embedded mode; forcing loopback bind host {}", bindHost);
            }
            if (!isLoopbackHost(bindHost)) {
                throw new IllegalStateException(
                    "SECURITY ERROR: Insecure embedded mode requires loopback binding. " +
                        "Set DATACLOUD_BIND_HOST to 127.0.0.1 or localhost, or configure authentication.");
            }
        }

        return bindHost;
    }

    private static DataSource buildDatabaseDataSource() {
        return DataCloudDatabaseConfig.fromEnvironment("DATACLOUD_DB")
                .createDataSource();
    }

    /**
     * Builds a {@link CompletionService} from environment variables (B1).
     *
     * <p>Resolution priority:
     * <ol>
     *   <li>If {@code AI_PROVIDER=ollama} — returns an {@link OllamaCompletionService}
     *       using {@code OLLAMA_HOST} (default: {@code http://localhost:11434}) and
     *       {@code OLLAMA_MODEL} (default: {@code llama3}).</li>
     *   <li>If {@code OPENAI_API_KEY} is present — returns an {@link OpenAICompletionService}
     *       with model from {@code OPENAI_MODEL} (default: {@code gpt-4o}).</li>
     *   <li>Otherwise returns {@code null} — server falls back to stub/no-op mode.</li>
     * </ol>
     */
    static CompletionService buildCompletionService(Map<String, String> env, Logger log) throws UnknownHostException {
        MetricsCollector metrics = new NoopMetricsCollector();
        String provider = env.getOrDefault("AI_PROVIDER", "").trim().toLowerCase();

        if ("ollama".equals(provider)) {
            String host = env.getOrDefault("OLLAMA_HOST", "http://localhost:11434");
            String model = env.getOrDefault("OLLAMA_MODEL", "llama3");
            LLMConfiguration config = LLMConfiguration.builder()
                    .baseUrl(host)
                    .modelName(model)
                    .build();
            Eventloop eventloop = Eventloop.create();
            // DC-P1-006: DNS resolver is configurable; sovereign profile must not use public resolvers
            DnsClient dnsClient = DnsClient.create(eventloop, InetAddress.getByName(resolveDnsHost(env, log)));
            HttpClient httpClient = HttpClient.create(eventloop, dnsClient);
            log.info("LLM backend: Ollama at {} model={}", host, model);
            return new OllamaCompletionService(config, httpClient, metrics);
        }

        String openAiKey = env.get("OPENAI_API_KEY");
        if (openAiKey != null && !openAiKey.isBlank()) {
            String model = env.getOrDefault("OPENAI_MODEL", "gpt-4o");
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey(openAiKey)
                    .modelName(model)
                    .build();
            Eventloop eventloop = Eventloop.create();
            // DC-P1-006: DNS resolver is configurable; sovereign profile must not use public resolvers
            DnsClient dnsClient = DnsClient.create(eventloop, InetAddress.getByName(resolveDnsHost(env, log)));
            HttpClient httpClient = HttpClient.create(eventloop, dnsClient);
            log.info("LLM backend: OpenAI model={}", model);
            return new OpenAICompletionService(config, httpClient, metrics);
        }

        log.warn("No LLM backend configured (AI_PROVIDER / OPENAI_API_KEY not set). AI assist routes will return stubs.");
        return null;
    }

    /**
     * DC-P1-006: Resolves the DNS server host from env.
     * {@code DATACLOUD_DNS_RESOLVER} — explicit DNS host (e.g. "192.168.1.1").
     * Defaults to the loopback/system resolver (127.0.0.53 for systemd-resolved, else 127.0.0.1).
     * In sovereign profile, public resolvers (8.8.8.8, 8.8.4.4, 1.1.1.1) are forbidden unless
     * {@code DATACLOUD_ALLOW_PUBLIC_DNS=true} is set.
     */
    private static String resolveDnsHost(Map<String, String> env, Logger log) {
        String configured = env.get("DATACLOUD_DNS_RESOLVER");
        boolean sovereignProfile = DataCloudLauncherSettings.resolveProfile(new String[0], env)
                == com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN;
        boolean allowPublicDns = "true".equalsIgnoreCase(env.getOrDefault("DATACLOUD_ALLOW_PUBLIC_DNS", "false"));

        if (configured != null && !configured.isBlank()) {
            if (sovereignProfile && !allowPublicDns && isPublicDnsAddress(configured)) {
                throw new IllegalStateException(
                    "DC-P1-006: Sovereign profile forbids public DNS resolver '" + configured +
                    "'. Set DATACLOUD_DNS_RESOLVER to a private resolver or set DATACLOUD_ALLOW_PUBLIC_DNS=true to override.");
            }
            log.info("DNS resolver configured: {}", configured);
            return configured;
        }

        // Default: use localhost system resolver
        String systemDefault = "127.0.0.1";
        if (sovereignProfile) {
            log.info("DC-P1-006: Sovereign profile — using system DNS resolver ({})", systemDefault);
        }
        return systemDefault;
    }

    private static boolean isPublicDnsAddress(String host) {
        return host.equals("8.8.8.8") || host.equals("8.8.4.4")
            || host.equals("1.1.1.1") || host.equals("1.0.0.1")
            || host.equals("9.9.9.9") || host.equals("208.67.222.222");
    }

    private static void recordAiProviderStateMetric(MetricsCollector metrics,
                                                    boolean aiEnabled,
                                                    CompletionService completionService,
                                                    Map<String, String> env,
                                                    Logger log) {
        try {
            String provider = env.getOrDefault("AI_PROVIDER", "").trim().toLowerCase();
            if (provider.isBlank()) {
                provider = env.get("OPENAI_API_KEY") != null ? "openai" : "none";
            }
            String result;
            if (!aiEnabled) {
                result = "disabled";
            } else if (completionService == null) {
                result = "unavailable";
            } else {
                result = "configured";
            }
            metrics.incrementCounter("data_cloud_ai_provider_configured", "result", result, "provider", provider);
            log.info("AI provider state metric emitted result={} provider={}", result, provider);
        } catch (Exception e) {
            log.debug("Failed to emit AI provider state metric: {}", e.getMessage());
        }
    }

    /**
     * Builds a {@link TraceExportService} backed by {@link ClickHouseTraceStorage} (B4).
     *
     * <p>Activated when {@code CLICKHOUSE_HOST} is present in the environment.
     * Optional vars: {@code CLICKHOUSE_PORT} (default: 8123),
     * {@code CLICKHOUSE_DATABASE} (default: observability).
     *
     * @return a ready {@link TraceExportService}, or {@code null} when ClickHouse is not configured
     */
    static TraceExportService buildTraceExportService(Map<String, String> env, Logger log) {
        String host = env.get("CLICKHOUSE_HOST");
        if (host == null || host.isBlank()) {
            log.warn("CLICKHOUSE_HOST not set — trace spans will not be exported (B4 degraded).");
            return null;
        }
        int port = Integer.parseInt(env.getOrDefault("CLICKHOUSE_PORT", "8123"));
        String database = env.getOrDefault("CLICKHOUSE_DATABASE", "observability");

        MetricsCollector metrics = new NoopMetricsCollector();
        ClickHouseTraceStorage traceStorage = ClickHouseTraceStorage.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(database)
                .build();
        log.info("Trace export: ClickHouse at {}:{}/{}", host, port, database);
        return new TraceExportService(new ClickHouseTraceExporter(traceStorage), metrics);
    }

    static double resolveTraceSamplingRate(Map<String, String> env) {
        String configured = env.get("DATACLOUD_TRACE_SAMPLING_RATIO");
        if (configured != null && !configured.isBlank()) {
            double parsed = Double.parseDouble(configured.trim());
            if (parsed < 0.0 || parsed > 1.0) {
                throw new IllegalArgumentException("DATACLOUD_TRACE_SAMPLING_RATIO must be between 0.0 and 1.0");
            }
            return parsed;
        }

        String profile = env.getOrDefault("DATACLOUD_PROFILE", "");
        if ("local".equalsIgnoreCase(profile) || "staging".equalsIgnoreCase(profile)) {
            return 1.0;
        }
        return 0.01;
    }

    static DataSource startRequiredDatabaseDataSource(
            Logger log,
            Supplier<DataSource> dataSourceSupplier) {
        try {
            DataSource dataSource = dataSourceSupplier.get();
            log.info("Standalone database DataSource initialised");
            return dataSource;
        } catch (Exception e) {
            log.error("Failed to create standalone database DataSource for enabled DB-backed features", e);
            throw new DataCloudTransportStartupException(
                    "Failed to create standalone database DataSource for enabled DB-backed features",
                    e);
        }
    }

    static AiServices startRequiredAiServices(
            Logger log,
            Supplier<AiServices> aiServicesSupplier) {
        try {
            AiServices aiServices = aiServicesSupplier.get();
            log.info("AI services initialised (model registry + feature store)");
            return aiServices;
        } catch (Exception e) {
            log.error("Failed to start AI services while DATACLOUD_AI_ENABLED=true", e);
            throw new DataCloudTransportStartupException(
                    "Failed to start AI services while DATACLOUD_AI_ENABLED=true",
                    e);
        }
    }

    private static AiServices buildAiServices(DataSource databaseDataSource) {
        io.micrometer.prometheusmetrics.PrometheusMeterRegistry promRegistry =
                new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
                        io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        MetricsCollector metrics = MetricsCollectorFactory.create(promRegistry);
        AiMetricsEmitter aiMetrics = new AiMetricsEmitter(metrics);
        ModelRegistryService modelRegistry = new ModelRegistryService(databaseDataSource, metrics);
        return new AiServices(
                new AIModelManager(modelRegistry, aiMetrics),
                new FeatureStoreService(databaseDataSource, metrics));
    }

    static void startTransport(
            DataCloudHttpServer httpServer,
            int port,
            boolean databaseEnabled,
            AiServices aiServices,
            DataSource databaseDataSource,
            Logger log,
            Consumer<Thread> shutdownHookRegistrar) {
        try {
            if (databaseEnabled && databaseDataSource != null) {
                httpServer.withHealthSubsystem("database", new JdbcDatabaseHealthProbe(databaseDataSource, 5));
            }
            if (aiServices != null) {
                httpServer.withHealthSubsystem("ai_inference", buildAiInferenceHealthProbe(aiServices));
            }
            httpServer.start();
            log.info("HTTP server started on port {}", port);

            shutdownHookRegistrar.accept(new Thread(() -> {
                log.info("Stopping HTTP server...");
                httpServer.stop();
                closeDataSource(databaseDataSource);
            }));
        } catch (Exception e) {
            log.error("Failed to start HTTP server on port {}", port, e);
            closeDataSource(databaseDataSource);
            throw new DataCloudTransportStartupException(
                    "Failed to start HTTP server on port " + port,
                    e);
        }
    }

    static Supplier<Map<String, Object>> buildAiInferenceHealthProbe(AiServices aiServices) {
        return () -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            boolean modelRegistryReady = aiServices != null && aiServices.aiModelManager() != null;
            boolean featureStoreReady = aiServices != null && aiServices.featureStoreService() != null;
            boolean healthy = modelRegistryReady && featureStoreReady;

            snapshot.put("status", healthy ? "UP" : "DOWN");
            snapshot.put("model_registry", modelRegistryReady ? "UP" : "DOWN");
            snapshot.put("feature_store", featureStoreReady ? "UP" : "DOWN");
            snapshot.put("mode", "startup-initialized");
            snapshot.put("checked_at", Instant.now().toString());

            if (!healthy) {
                snapshot.put("message", "AI services incomplete");
            }

            return snapshot;
        };
    }

    static BrainServices startBrainServices(
            Logger log,
            Supplier<DataCloudBrain> brainSupplier,
            Function<DataCloudBrain, DataCloudLearningBridge> learningBridgeFactory,
            Consumer<Thread> shutdownHookRegistrar) {
        try {
            DataCloudBrain brain = brainSupplier.get();
            log.info("Brain initialised (standalone mode)");

            DataCloudLearningBridge learningBridge = learningBridgeFactory.apply(brain);
            learningBridge.start();
            log.info("Learning bridge started (interval=5min)");

            shutdownHookRegistrar.accept(new Thread(() -> {
                log.info("Closing learning bridge...");
                learningBridge.close();
            }));

            return new BrainServices(brain, learningBridge);
        } catch (Exception e) {
            log.warn("Failed to start brain/learning bridge, continuing without: {}", e.getMessage(), e);
            return BrainServices.disabled();
        }
    }

    static AnalyticsServices startAnalyticsServices(
            Logger log,
            Supplier<AnalyticsQueryEngine> analyticsEngineSupplier,
            Function<AnalyticsQueryEngine, ReportService> reportServiceFactory) {
        try {
            AnalyticsQueryEngine analyticsEngine = analyticsEngineSupplier.get();
            log.info("AnalyticsQueryEngine initialised (standalone mode)");

            try {
                ReportService reportService = reportServiceFactory.apply(analyticsEngine);
                log.info("ReportService initialised (analytics-only mode; use EntityExportService for full export)");
                return new AnalyticsServices(analyticsEngine, reportService);
            } catch (Exception e) {
                log.warn("Failed to start report service, continuing without: {}", e.getMessage(), e);
                return new AnalyticsServices(analyticsEngine, null);
            }
        } catch (Exception e) {
            log.warn("Failed to start analytics engine, continuing without: {}", e.getMessage(), e);
            return AnalyticsServices.disabled();
        }
    }

    private static void closeDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource && !hikariDataSource.isClosed()) {
            hikariDataSource.close();
        }
    }

    record BrainServices(DataCloudBrain brain, DataCloudLearningBridge learningBridge) {
        static BrainServices disabled() {
            return new BrainServices(null, null);
        }
    }

    record AnalyticsServices(AnalyticsQueryEngine analyticsEngine, ReportService reportService) {
        static AnalyticsServices disabled() {
            return new AnalyticsServices(null, null);
        }
    }

    record AiServices(AIModelManager aiModelManager, FeatureStoreService featureStoreService) {}
}
