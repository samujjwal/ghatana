package com.ghatana.aep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.analytics.AepAnomalyDetector;
import com.ghatana.aep.audit.EventProcessingAuditService;
import com.ghatana.aep.async.AepAsyncUtils;
import com.ghatana.aep.cache.AepConsentCache;
import com.ghatana.aep.cache.AepPatternCache;
import com.ghatana.aep.config.AepConfigReloadBridge;
import com.ghatana.aep.config.UnifiedAepConfigValidator;
import com.ghatana.aep.consent.ConsentService;
import com.ghatana.aep.consent.ConsentServiceFactory;
import com.ghatana.aep.consent.DefaultConsentService;
import com.ghatana.aep.delivery.EventDeliveryService;
import com.ghatana.aep.error.AepErrorHandler;
import com.ghatana.aep.error.AepTenantException;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.InMemoryEventCloud;
import com.ghatana.aep.forecasting.ForecastingEngine;
import com.ghatana.aep.forecasting.NaiveForecastingEngine;
import com.ghatana.aep.health.AepHealthIndicator;
import com.ghatana.aep.lifecycle.GracefulShutdownCoordinator;
import com.ghatana.aep.metrics.AepMetricsCollector;
import com.ghatana.aep.ratelimit.AepRateLimiter;
import com.ghatana.aep.tracing.AepTraceContext;
import com.ghatana.aep.version.EventVersionCompatibility;
import com.ghatana.platform.observability.health.HealthCheckRegistry;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * AEP Entry Point - Factory for creating AEP engine instances.
 *
 * <p>This is the primary entry point for AEP. It provides factory methods:
 * <ul>
 *   <li>{@link #create(AepConfig)} - Create with configuration</li>
 *   <li>{@link #embedded()} - Create embedded instance</li>
 *   <li>{@link #forTesting()} - Create for testing</li>
 * </ul>
 *
 * <p>New structure uses platform modules for shared infrastructure:
 * <ul>
 *   <li>platform/java/event-cloud - Event processing infrastructure</li>
 *   <li>platform/java/observability - Metrics and tracing</li>
 *   <li>platform/java/config - Configuration management</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose AEP factory entry point
 * @doc.layer api
 * @doc.pattern Factory
 * @since 1.0.0
 */
public final class Aep {

    private static final Logger logger = LoggerFactory.getLogger(Aep.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Aep() {
        // Utility class - no instantiation
    }

    // ==================== Factory Methods ====================

    /**
     * Create an AEP engine with the given configuration.
     *
     * @param config AEP configuration
     * @return configured AEP engine
     */
    public static AepEngine create(AepConfig config) {
        Objects.requireNonNull(config, "config required");
        UnifiedAepConfigValidator.validateApiConfig(config);

        EventCloud eventCloud = discoverEventCloud(config);
        ConsentService consentService = ConsentServiceFactory.create(config);

        return new DefaultAepEngine(eventCloud, config,
            new NaiveForecastingEngine(),
            consentService,
            EventDeliveryService.noOp());
    }

    /**
     * Create an AEP engine with explicit delivery destinations.
     *
     * @param config          AEP configuration
     * @param deliveryService delivery service for routing processed events
     * @return configured AEP engine
     */
    public static AepEngine create(AepConfig config, EventDeliveryService deliveryService) {
        Objects.requireNonNull(config, "config required");
        Objects.requireNonNull(deliveryService, "deliveryService required");
        UnifiedAepConfigValidator.validateApiConfig(config);

        EventCloud eventCloud = discoverEventCloud(config);
        ConsentService consentService = ConsentServiceFactory.create(config);

        return new DefaultAepEngine(eventCloud, config,
            new NaiveForecastingEngine(),
            consentService,
            deliveryService);
    }

    /**
     * Create an embedded AEP engine with default configuration.
     *
     * @return embedded AEP engine
     */
    public static AepEngine embedded() {
        return create(AepConfig.defaults());
    }

    /**
     * Create an AEP engine for testing with in-memory storage.
     *
     * @return testing AEP engine
     */
    public static AepEngine forTesting() {
        return new DefaultAepEngine(
            new InMemoryEventCloud(),
            AepConfig.forTesting(),
            new NaiveForecastingEngine(),
            new DefaultConsentService(),
            EventDeliveryService.noOp());
    }

    private static EventCloud discoverEventCloud(AepConfig config) {
        // Try ServiceLoader first
        ServiceLoader<EventCloud> loader = ServiceLoader.load(EventCloud.class);
        Optional<EventCloud> discovered = loader.findFirst();

        if (discovered.isPresent()) {
            return discovered.get();
        }

        return new InMemoryEventCloud();
    }

    // ==================== Configuration ====================

    /**
     * AEP configuration record.
     */
    public record AepConfig(
        String instanceId,
        int workerThreads,
        int maxPipelinesPerTenant,
        boolean enableMetrics,
        boolean enableTracing,
        double anomalyThreshold,
        Map<String, Object> customConfig
    ) {
        public static final String IDEMPOTENCY_TTL_SECONDS_KEY = "idempotencyTtlSeconds";
        public static final String IDEMPOTENCY_MAX_KEYS_PER_TENANT_KEY = "idempotencyMaxKeysPerTenant";
        public static final String CONSENT_PROVIDER_KEY = "consentProvider";
        public static final String ASYNC_TIMEOUT_MS_KEY = "asyncTimeoutMs";
        public static final String RATE_LIMIT_ENABLED_KEY = AepRateLimiter.ENABLED_KEY;
        public static final String RATE_LIMIT_MAX_REQUESTS_PER_MINUTE_KEY = AepRateLimiter.MAX_REQUESTS_PER_MINUTE_KEY;
        public static final String RATE_LIMIT_BURST_SIZE_KEY = AepRateLimiter.BURST_SIZE_KEY;
        public static final String RATE_LIMIT_WINDOW_SECONDS_KEY = AepRateLimiter.WINDOW_SECONDS_KEY;
        public static final String CONSENT_CACHE_TTL_SECONDS_KEY = AepConsentCache.TTL_SECONDS_KEY;
        public static final String CONSENT_CACHE_MAX_ENTRIES_KEY = AepConsentCache.MAX_ENTRIES_KEY;
        public static final String PATTERN_CACHE_TTL_SECONDS_KEY = AepPatternCache.TTL_SECONDS_KEY;
        public static final String SHUTDOWN_DRAIN_TIMEOUT_MS_KEY = GracefulShutdownCoordinator.DRAIN_TIMEOUT_MS_KEY;
        public static final String HOT_RELOAD_CONFIG_PATH_KEY = AepConfigReloadBridge.CONFIG_PATH_KEY;
        public static final String HOT_RELOAD_CHECK_INTERVAL_MS_KEY = AepConfigReloadBridge.CHECK_INTERVAL_MS_KEY;
        public static final String CURRENT_EVENT_VERSION_KEY = "currentEventVersion";
        public static final String MIN_SUPPORTED_EVENT_VERSION_KEY = "minSupportedEventVersion";

        private static final long DEFAULT_IDEMPOTENCY_TTL_SECONDS = 86_400L;
        private static final int DEFAULT_IDEMPOTENCY_MAX_KEYS_PER_TENANT = 10_000;
        private static final long DEFAULT_ASYNC_TIMEOUT_MS = 10_000L;
        private static final int DEFAULT_RATE_LIMIT_MAX_REQUESTS_PER_MINUTE = 10_000;
        private static final int DEFAULT_RATE_LIMIT_BURST_SIZE = 1_000;
        private static final long DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 60L;
        private static final long DEFAULT_CONSENT_CACHE_TTL_SECONDS = 300L;
        private static final int DEFAULT_CONSENT_CACHE_MAX_ENTRIES = 10_000;
        private static final long DEFAULT_PATTERN_CACHE_TTL_SECONDS = 30L;
        private static final long DEFAULT_SHUTDOWN_DRAIN_TIMEOUT_MS = 30_000L;
        private static final long DEFAULT_HOT_RELOAD_CHECK_INTERVAL_MS = 30_000L;
        private static final String DEFAULT_EVENT_VERSION = EventVersionCompatibility.DEFAULT_CURRENT_VERSION;
        private static final String DEFAULT_MIN_SUPPORTED_EVENT_VERSION = EventVersionCompatibility.DEFAULT_MIN_VERSION;

        public AepConfig {
            instanceId = instanceId != null ? instanceId : UUID.randomUUID().toString();
            if (workerThreads <= 0) workerThreads = Runtime.getRuntime().availableProcessors();
            if (maxPipelinesPerTenant <= 0) maxPipelinesPerTenant = 100;
            customConfig = customConfig != null ? Map.copyOf(customConfig) : Map.of();
        }

        public static AepConfig defaults() {
            return new AepConfig(null, 0, 100, true, false, 0.9, Map.of());
        }

        public static AepConfig forTesting() {
            return builder()
                .instanceId("test-instance")
                .workerThreads(1)
                .maxPipelinesPerTenant(10)
                .enableMetrics(false)
                .enableTracing(false)
                .anomalyThreshold(0.9)
                .asyncTimeout(Duration.ofSeconds(2))
                .build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String instanceId;
            private int workerThreads = 0;
            private int maxPipelinesPerTenant = 100;
            private boolean enableMetrics = true;
            private boolean enableTracing = false;
            private double anomalyThreshold = 0.9;
            private Map<String, Object> customConfig = new LinkedHashMap<>();

            public Builder instanceId(String instanceId) {
                this.instanceId = instanceId;
                return this;
            }

            public Builder workerThreads(int threads) {
                this.workerThreads = threads;
                return this;
            }

            public Builder maxPipelinesPerTenant(int max) {
                this.maxPipelinesPerTenant = max;
                return this;
            }

            public Builder enableMetrics(boolean enable) {
                this.enableMetrics = enable;
                return this;
            }

            public Builder enableTracing(boolean enable) {
                this.enableTracing = enable;
                return this;
            }

            public Builder anomalyThreshold(double anomalyThreshold) {
                this.anomalyThreshold = anomalyThreshold;
                return this;
            }

            public Builder customConfig(Map<String, Object> config) {
                this.customConfig = config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>();
                return this;
            }

            public Builder idempotencyTtlSeconds(long ttlSeconds) {
                this.customConfig.put(IDEMPOTENCY_TTL_SECONDS_KEY, ttlSeconds);
                return this;
            }

            public Builder maxIdempotencyKeysPerTenant(int maxKeys) {
                this.customConfig.put(IDEMPOTENCY_MAX_KEYS_PER_TENANT_KEY, maxKeys);
                return this;
            }

            public Builder consentProvider(String providerName) {
                if (providerName == null || providerName.isBlank()) {
                    this.customConfig.remove(CONSENT_PROVIDER_KEY);
                } else {
                    this.customConfig.put(CONSENT_PROVIDER_KEY, providerName);
                }
                return this;
            }

            public Builder asyncTimeout(Duration timeout) {
                this.customConfig.put(ASYNC_TIMEOUT_MS_KEY, Objects.requireNonNull(timeout, "timeout required").toMillis());
                return this;
            }

            public Builder rateLimitEnabled(boolean enabled) {
                this.customConfig.put(RATE_LIMIT_ENABLED_KEY, enabled);
                return this;
            }

            public Builder rateLimiting(int maxRequestsPerMinute, int burstSize) {
                this.customConfig.put(RATE_LIMIT_ENABLED_KEY, true);
                this.customConfig.put(RATE_LIMIT_MAX_REQUESTS_PER_MINUTE_KEY, maxRequestsPerMinute);
                this.customConfig.put(RATE_LIMIT_BURST_SIZE_KEY, burstSize);
                return this;
            }

            public Builder rateLimitWindow(Duration duration) {
                this.customConfig.put(RATE_LIMIT_WINDOW_SECONDS_KEY,
                    Objects.requireNonNull(duration, "duration required").getSeconds());
                return this;
            }

            public Builder consentCache(Duration ttl, int maxEntries) {
                this.customConfig.put(CONSENT_CACHE_TTL_SECONDS_KEY, Objects.requireNonNull(ttl, "ttl required").getSeconds());
                this.customConfig.put(CONSENT_CACHE_MAX_ENTRIES_KEY, maxEntries);
                return this;
            }

            public Builder patternCacheTtl(Duration ttl) {
                this.customConfig.put(PATTERN_CACHE_TTL_SECONDS_KEY, Objects.requireNonNull(ttl, "ttl required").getSeconds());
                return this;
            }

            public Builder shutdownDrainTimeout(Duration timeout) {
                this.customConfig.put(SHUTDOWN_DRAIN_TIMEOUT_MS_KEY, Objects.requireNonNull(timeout, "timeout required").toMillis());
                return this;
            }

            public Builder hotReload(Path configPath, Duration checkInterval) {
                this.customConfig.put(HOT_RELOAD_CONFIG_PATH_KEY, Objects.requireNonNull(configPath, "configPath required").toString());
                this.customConfig.put(HOT_RELOAD_CHECK_INTERVAL_MS_KEY,
                    Objects.requireNonNull(checkInterval, "checkInterval required").toMillis());
                return this;
            }

            public Builder currentEventVersion(String version) {
                this.customConfig.put(CURRENT_EVENT_VERSION_KEY, version);
                return this;
            }

            public Builder minSupportedEventVersion(String version) {
                this.customConfig.put(MIN_SUPPORTED_EVENT_VERSION_KEY, version);
                return this;
            }

            public AepConfig build() {
                return new AepConfig(instanceId, workerThreads, maxPipelinesPerTenant,
                    enableMetrics, enableTracing, anomalyThreshold, customConfig);
            }
        }

        public long idempotencyTtlSeconds() {
            return longConfig(IDEMPOTENCY_TTL_SECONDS_KEY, DEFAULT_IDEMPOTENCY_TTL_SECONDS);
        }

        public int maxIdempotencyKeysPerTenant() {
            return intConfig(IDEMPOTENCY_MAX_KEYS_PER_TENANT_KEY, DEFAULT_IDEMPOTENCY_MAX_KEYS_PER_TENANT);
        }

        public Optional<String> consentProviderName() {
            Object raw = customConfig.get(CONSENT_PROVIDER_KEY);
            if (raw instanceof String value && !value.isBlank()) {
                return Optional.of(value);
            }
            return Optional.empty();
        }

        public long asyncTimeoutMs() {
            return longConfig(ASYNC_TIMEOUT_MS_KEY, DEFAULT_ASYNC_TIMEOUT_MS);
        }

        public boolean rateLimitEnabled() {
            Object raw = customConfig.get(RATE_LIMIT_ENABLED_KEY);
            return raw instanceof Boolean value ? value : false;
        }

        public int rateLimitMaxRequestsPerMinute() {
            return intConfig(RATE_LIMIT_MAX_REQUESTS_PER_MINUTE_KEY, DEFAULT_RATE_LIMIT_MAX_REQUESTS_PER_MINUTE);
        }

        public int rateLimitBurstSize() {
            return intConfig(RATE_LIMIT_BURST_SIZE_KEY, DEFAULT_RATE_LIMIT_BURST_SIZE);
        }

        public long rateLimitWindowSeconds() {
            return longConfig(RATE_LIMIT_WINDOW_SECONDS_KEY, DEFAULT_RATE_LIMIT_WINDOW_SECONDS);
        }

        public long consentCacheTtlSeconds() {
            return longConfig(CONSENT_CACHE_TTL_SECONDS_KEY, DEFAULT_CONSENT_CACHE_TTL_SECONDS);
        }

        public int consentCacheMaxEntries() {
            return intConfig(CONSENT_CACHE_MAX_ENTRIES_KEY, DEFAULT_CONSENT_CACHE_MAX_ENTRIES);
        }

        public long patternCacheTtlSeconds() {
            return longConfig(PATTERN_CACHE_TTL_SECONDS_KEY, DEFAULT_PATTERN_CACHE_TTL_SECONDS);
        }

        public long shutdownDrainTimeoutMs() {
            return longConfig(SHUTDOWN_DRAIN_TIMEOUT_MS_KEY, DEFAULT_SHUTDOWN_DRAIN_TIMEOUT_MS);
        }

        public Optional<Path> hotReloadConfigPath() {
            Object raw = customConfig.get(HOT_RELOAD_CONFIG_PATH_KEY);
            if (raw instanceof String value && !value.isBlank()) {
                return Optional.of(Path.of(value));
            }
            return Optional.empty();
        }

        public long hotReloadCheckIntervalMs() {
            return longConfig(HOT_RELOAD_CHECK_INTERVAL_MS_KEY, DEFAULT_HOT_RELOAD_CHECK_INTERVAL_MS);
        }

        public String currentEventVersion() {
            Object raw = customConfig.get(CURRENT_EVENT_VERSION_KEY);
            if (raw instanceof String value && !value.isBlank()) {
                return value;
            }
            return DEFAULT_EVENT_VERSION;
        }

        public String minSupportedEventVersion() {
            Object raw = customConfig.get(MIN_SUPPORTED_EVENT_VERSION_KEY);
            if (raw instanceof String value && !value.isBlank()) {
                return value;
            }
            return DEFAULT_MIN_SUPPORTED_EVENT_VERSION;
        }

        private int intConfig(String key, int defaultValue) {
            Object raw = customConfig.get(key);
            if (raw instanceof Number number && number.intValue() > 0) {
                return number.intValue();
            }
            return defaultValue;
        }

        private long longConfig(String key, long defaultValue) {
            Object raw = customConfig.get(key);
            if (raw instanceof Number number && number.longValue() > 0L) {
                return number.longValue();
            }
            return defaultValue;
        }
    }

    // ==================== Default Implementation ====================

    /**
     * Default AEP engine implementation.
     */
    private static class DefaultAepEngine implements AepEngine {
        private final EventCloud eventCloud;
        private final AepConfig config;
        private final ForecastingEngine forecastingEngine;
        private final ConsentService consentService;
        private final EventDeliveryService deliveryService;
        private final EventProcessingAuditService auditService;
        private final EventSchemaValidator schemaValidator = new EventSchemaValidator();
        private final AepMetricsCollector metrics;
        private final EventVersionCompatibility versionCompatibility;
        private final AepPatternCache patternCache;
        private final GracefulShutdownCoordinator shutdownCoordinator;
        private final AepHealthIndicator healthIndicator;
        private final Optional<AepConfigReloadBridge> reloadBridge;
        private final AtomicReference<AepRateLimiter> rateLimiter;
        private final AtomicReference<AepConsentCache> consentCache;
        private final AtomicReference<Duration> asyncTimeout;
        private final AtomicReference<Double> anomalyThreshold;
        private final AtomicReference<Boolean> tracingEnabled;
        private final AtomicReference<Boolean> rateLimitEnabled;
        private final AtomicReference<Integer> rateLimitMaxRequestsPerMinute;
        private final AtomicReference<Integer> rateLimitBurstSize;
        private final AtomicReference<Long> rateLimitWindowSeconds;
        private final AtomicReference<Long> consentCacheTtlSeconds;

        private final Map<String, Map<String, AepEngine.Pattern>> patternsByTenant = new ConcurrentHashMap<>();
        private final Map<String, List<SubscriptionEntry>> subscriptionsByTenant = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Map<String, SequenceProgress>>> sequenceProgressByTenant =
            new ConcurrentHashMap<>();
        private final Map<String, Map<String, Instant>> seenIdempotencyKeysByTenant = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Long>> subscriberFailureCountByTenant = new ConcurrentHashMap<>();
        private final Map<String, List<AepEngine.PatternDetector>> patternDetectorsByTenant = new ConcurrentHashMap<>();

        private volatile boolean closed = false;

        DefaultAepEngine(EventCloud eventCloud, AepConfig config,
                         ForecastingEngine forecastingEngine,
                         ConsentService consentService,
                         EventDeliveryService deliveryService) {
            this(eventCloud, config, forecastingEngine, consentService, deliveryService, null);
        }

        DefaultAepEngine(EventCloud eventCloud, AepConfig config,
                         ForecastingEngine forecastingEngine,
                         ConsentService consentService,
                         EventDeliveryService deliveryService,
                         EventProcessingAuditService auditService) {
            this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud required");
            this.config = Objects.requireNonNull(config, "config required");
            this.forecastingEngine = Objects.requireNonNull(forecastingEngine, "forecastingEngine required");
            this.consentService = Objects.requireNonNull(consentService, "consentService required");
            this.deliveryService = Objects.requireNonNull(deliveryService, "deliveryService required");
            this.auditService = auditService != null ? auditService : new EventProcessingAuditService();
            this.metrics = config.enableMetrics() ? AepMetricsCollector.create() : AepMetricsCollector.noop();
            this.versionCompatibility = EventVersionCompatibility.builder()
                .currentVersion(config.currentEventVersion())
                .minSupportedVersion(config.minSupportedEventVersion())
                .build();
            this.patternCache = AepPatternCache.builder()
                .ttl(Duration.ofSeconds(config.patternCacheTtlSeconds()))
                .build();
            this.asyncTimeout = new AtomicReference<>(Duration.ofMillis(config.asyncTimeoutMs()));
            this.anomalyThreshold = new AtomicReference<>(config.anomalyThreshold());
            this.tracingEnabled = new AtomicReference<>(config.enableTracing());
            this.rateLimitEnabled = new AtomicReference<>(config.rateLimitEnabled());
            this.rateLimitMaxRequestsPerMinute = new AtomicReference<>(config.rateLimitMaxRequestsPerMinute());
            this.rateLimitBurstSize = new AtomicReference<>(config.rateLimitBurstSize());
            this.rateLimitWindowSeconds = new AtomicReference<>(config.rateLimitWindowSeconds());
            this.consentCacheTtlSeconds = new AtomicReference<>(config.consentCacheTtlSeconds());
            this.rateLimiter = new AtomicReference<>(buildRateLimiter());
            this.consentCache = new AtomicReference<>(buildConsentCache());
            this.shutdownCoordinator = GracefulShutdownCoordinator.builder()
                .componentName("aep-engine-" + config.instanceId())
                .drainTimeout(Duration.ofMillis(config.shutdownDrainTimeoutMs()))
                .build();
            this.shutdownCoordinator.registerShutdownHook();
            this.healthIndicator = AepHealthIndicator.builder()
                .engineClosed(() -> closed)
                .degradeIfErrorRateExceeds(0.10)
                .build();
            registerHealthCheck();
            this.reloadBridge = createReloadBridge();
            this.reloadBridge.ifPresent(bridge -> {
                bridge.addListener(this::applyRuntimeTuning);
                bridge.start();
            });
        }

        @Override
        public Promise<AepEngine.ProcessingResult> process(String tenantId, AepEngine.Event event) {
            checkNotClosed();
            requireTenantId(tenantId);
            Objects.requireNonNull(event, "event must not be null");
            validateEventTenantContext(tenantId, event);

            AepEngine.Event correlatedEvent = AepTraceContext.ensureCorrelationId(event);
            String fallbackEventId = UUID.randomUUID().toString();
            Instant startedAt = Instant.now();
            GracefulShutdownCoordinator.OperationTicket operationTicket =
                shutdownCoordinator.beginOperation("process");

            Promise<AepEngine.ProcessingResult> processingPromise = processingPipeline(
                tenantId, correlatedEvent, fallbackEventId);

            Promise<AepEngine.ProcessingResult> contextualPromise = tracingEnabled.get()
                ? AepTraceContext.withEventContext(tenantId, correlatedEvent, () -> processingPromise)
                : processingPromise;

            return contextualPromise
                .whenResult(result -> {
                    recordOutcomeMetrics(tenantId, result, startedAt);
                    if (isFailureResult(result)) {
                        healthIndicator.recordFailure();
                    } else {
                        healthIndicator.recordSuccess();
                    }
                })
                .whenException(error -> {
                    metrics.incrementEventsFailed(tenantId);
                    metrics.recordEventProcessingTime(tenantId, elapsedMs(startedAt));
                    healthIndicator.recordFailure();
                })
                .whenComplete(($, $$) -> operationTicket.close());
        }

        @Override
        public void submitPipeline(String tenantId, AepEngine.Pipeline pipeline) {
            checkNotClosed();
            requireTenantId(tenantId);
            Objects.requireNonNull(pipeline, "pipeline must not be null");
            
            // Validate DAG structure
            if (!pipeline.isValidDAG()) {
                throw new IllegalArgumentException("Pipeline contains cycles and is not a valid DAG");
            }
            
            logger.info("Submitting pipeline id={} name='{}' with {} steps for tenant={}",
                pipeline.id(), pipeline.name(), pipeline.steps().size(), tenantId);

            // Execute steps in DAG order (topological sort) using Promise chain
            executePipelineDAG(tenantId, pipeline)
                .whenComplete(() -> logger.info("Pipeline {} execution completed", pipeline.id()))
                .whenException(ex -> logger.error("Pipeline {} execution failed: {}", pipeline.id(), ex.getMessage()));
        }

        private Promise<Void> executePipelineDAG(String tenantId, AepEngine.Pipeline pipeline) {
            Map<String, AepEngine.PipelineStep> stepsById = pipeline.steps().stream()
                .collect(Collectors.toMap(AepEngine.PipelineStep::id, s -> s));
            
            Map<String, Boolean> completedSteps = new ConcurrentHashMap<>();
            List<Exception> errors = new CopyOnWriteArrayList<>();

            // Execute steps in topological order, grouping independent steps for parallel execution
            List<String> executionOrder = topologicalSort(pipeline);
            
            // Group steps by dependency level for parallel execution
            Map<String, List<String>> stepsByLevel = new LinkedHashMap<>();
            for (String stepId : executionOrder) {
                AepEngine.PipelineStep step = stepsById.get(stepId);
                if (step == null) continue;
                
                // Determine level based on dependencies
                int level = 0;
                for (String dep : step.dependsOn()) {
                    if (stepsById.containsKey(dep)) {
                        level = Math.max(level, getStepLevel(dep, stepsByLevel) + 1);
                    }
                }
                
                String levelKey = "level_" + level;
                stepsByLevel.computeIfAbsent(levelKey, k -> new ArrayList<>()).add(stepId);
            }
            
            // Execute each level in sequence, with parallel execution within each level
            Promise<Void> executionPromise = Promise.complete();
            
            for (List<String> levelSteps : stepsByLevel.values()) {
                final List<String> currentLevelSteps = levelSteps;
                executionPromise = executionPromise.then(() -> {
                    // Execute all steps in this level in parallel using Promises.all()
                    List<Promise<Void>> stepPromises = currentLevelSteps.stream()
                        .map(stepId -> {
                            AepEngine.PipelineStep step = stepsById.get(stepId);
                            if (step == null) return Promise.complete();
                            
                            return waitForDependenciesAsync(step.dependsOn(), completedSteps)
                                .then(() -> Promise.ofBlocking(() -> {
                                    try {
                                        executePipelineStep(tenantId, pipeline.id(), step);
                                        completedSteps.put(stepId, true);
                                        logger.debug("Completed step {} in pipeline {}", stepId, pipeline.id());
                                    } catch (Exception e) {
                                        logger.error("Failed to execute step {} in pipeline {}: {}", stepId, pipeline.id(), e.getMessage(), e);
                                        errors.add(e);
                                        completedSteps.put(stepId, false);
                                    }
                                }));
                        })
                        .collect(Collectors.toList());
                    
                    return Promises.all(stepPromises).toVoid();
                });
            }
            
            return executionPromise.then(() -> {
                if (!errors.isEmpty()) {
                    // Collect all errors in the exception message
                    String allErrors = errors.stream()
                        .map(e -> e.getMessage())
                        .collect(Collectors.joining("; "));
                    throw new RuntimeException("Pipeline execution failed with " + errors.size() + " errors: " + allErrors, 
                        errors.get(0));
                }
            });
        }

        private int getStepLevel(String stepId, Map<String, List<String>> stepsByLevel) {
            for (Map.Entry<String, List<String>> entry : stepsByLevel.entrySet()) {
                if (entry.getValue().contains(stepId)) {
                    return Integer.parseInt(entry.getKey().substring(6)); // Extract level from "level_X"
                }
            }
            return 0;
        }

        private List<String> topologicalSort(AepEngine.Pipeline pipeline) {
            Map<String, Integer> inDegree = new HashMap<>();
            Map<String, List<String>> adjacency = new HashMap<>();
            
            // Initialize
            for (AepEngine.PipelineStep step : pipeline.steps()) {
                inDegree.put(step.id(), 0);
                adjacency.put(step.id(), new ArrayList<>());
            }
            
            // Build graph
            for (AepEngine.PipelineStep step : pipeline.steps()) {
                for (String dep : step.dependsOn()) {
                    if (inDegree.containsKey(dep)) {
                        adjacency.get(dep).add(step.id());
                        inDegree.merge(step.id(), 1, Integer::sum);
                    }
                }
            }
            
            // Kahn's algorithm
            Queue<String> queue = new LinkedList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.add(entry.getKey());
                }
            }
            
            List<String> result = new ArrayList<>();
            while (!queue.isEmpty()) {
                String stepId = queue.poll();
                result.add(stepId);
                
                for (String neighbor : adjacency.getOrDefault(stepId, List.of())) {
                    inDegree.merge(neighbor, -1, Integer::sum);
                    if (inDegree.get(neighbor) == 0) {
                        queue.add(neighbor);
                    }
                }
            }
            
            return result;
        }

        private void waitForDependencies(List<String> dependencies, Map<String, Boolean> completedSteps) {
            if (dependencies.isEmpty()) return;
            
            // Poll for completion (simple implementation)
            // In production, this should use proper async coordination
            int maxWait = 10000; // 10 seconds
            int waited = 0;
            while (waited < maxWait) {
                boolean allComplete = dependencies.stream()
                    .allMatch(dep -> completedSteps.containsKey(dep) && completedSteps.get(dep));
                
                if (allComplete) return;
                
                try {
                    Thread.sleep(50);
                    waited += 50;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for dependencies", e);
                }
            }
            
            throw new RuntimeException("Timeout waiting for dependencies: " + dependencies);
        }

        private Promise<Void> waitForDependenciesAsync(List<String> dependencies, Map<String, Boolean> completedSteps) {
            if (dependencies.isEmpty()) {
                return Promise.complete();
            }
            
            // Async check for dependency completion
            return Promise.ofBlocking(() -> {
                int maxWait = 10000; // 10 seconds
                int waited = 0;
                while (waited < maxWait) {
                    boolean allComplete = dependencies.stream()
                        .allMatch(dep -> completedSteps.containsKey(dep) && completedSteps.get(dep));
                    
                    if (allComplete) return;
                    
                    try {
                        Thread.sleep(50);
                        waited += 50;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for dependencies", e);
                    }
                }
                
                throw new RuntimeException("Timeout waiting for dependencies: " + dependencies);
            });
        }

        @Override
        public AepEngine.Subscription subscribe(String tenantId, String patternId,
                                                Consumer<AepEngine.Detection> handler) {
            checkNotClosed();
            requireTenantId(tenantId);
            Objects.requireNonNull(handler, "handler must not be null");
            Objects.requireNonNull(patternId, "patternId must not be null");
            validatePatternAccess(tenantId, patternId, false);

            SubscriptionEntry entry = new SubscriptionEntry(patternId, handler);
            subscriptionsByTenant.computeIfAbsent(tenantId, ignored -> new CopyOnWriteArrayList<>()).add(entry);

            return new AepEngine.Subscription() {
                private volatile boolean cancelled = false;

                @Override
                public void cancel() {
                    cancelled = true;
                    subscriptionsByTenant.getOrDefault(tenantId, List.of()).remove(entry);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }
            };
        }

        @Override
        public Promise<AepEngine.Pattern> registerPattern(String tenantId,
                                                          AepEngine.PatternDefinition definition) {
            checkNotClosed();
            requireTenantId(tenantId);
            Objects.requireNonNull(definition, "definition must not be null");
            validatePatternDefinitionTenant(tenantId, definition);

            AepEngine.Pattern pattern = new AepEngine.Pattern(
                UUID.randomUUID().toString(),
                definition.name(),
                definition.description(),
                definition.type(),
                definition.config(),
                Instant.now()
            );

            Map<String, AepEngine.Pattern> patterns = patternsByTenant.computeIfAbsent(
                tenantId, ignored -> new ConcurrentHashMap<>());
            patterns.put(pattern.id(), pattern);
            patternCache.put(tenantId, patterns.values());
            metrics.gaugeActivePatterns(tenantId, patterns.size());

            return Promise.of(pattern);
        }

        @Override
        public Promise<Optional<AepEngine.Pattern>> getPattern(String tenantId, String patternId) {
            checkNotClosed();
            requireTenantId(tenantId);
            validatePatternAccess(tenantId, patternId, false);
            return Promise.of(Optional.ofNullable(
                patternsByTenant.getOrDefault(tenantId, Map.of()).get(patternId)));
        }

        @Override
        public Promise<List<AepEngine.Pattern>> listPatterns(String tenantId) {
            checkNotClosed();
            requireTenantId(tenantId);
            return Promise.of(new ArrayList<>(patternsByTenant.getOrDefault(tenantId, Map.of()).values()));
        }

        @Override
        public void registerPatternDetector(String tenantId, AepEngine.PatternDetector detector) {
            checkNotClosed();
            requireTenantId(tenantId);
            Objects.requireNonNull(detector, "detector must not be null");
            patternDetectorsByTenant.computeIfAbsent(tenantId, ignored -> new CopyOnWriteArrayList()).add(detector);
            logger.info("Registered pattern detector for tenant={}", tenantId);
        }

        @Override
        public void unregisterPatternDetector(String tenantId, AepEngine.PatternDetector detector) {
            checkNotClosed();
            requireTenantId(tenantId);
            Objects.requireNonNull(detector, "detector must not be null");
            List<AepEngine.PatternDetector> detectors = patternDetectorsByTenant.get(tenantId);
            if (detectors != null) {
                detectors.remove(detector);
                logger.info("Unregistered pattern detector for tenant={}", tenantId);
            }
        }

        @Override
        public Promise<Void> deletePattern(String tenantId, String patternId) {
            checkNotClosed();
            requireTenantId(tenantId);
            Objects.requireNonNull(patternId, "patternId must not be null");
            validatePatternAccess(tenantId, patternId, true);

            Map<String, AepEngine.Pattern> patterns = patternsByTenant.get(tenantId);
            if (patterns != null) {
                patterns.remove(patternId);
                patternCache.put(tenantId, new ArrayList<>(patterns.values()));
                metrics.gaugeActivePatterns(tenantId, patterns.size());
            }

            Map<String, Map<String, SequenceProgress>> tenantSeq = sequenceProgressByTenant.get(tenantId);
            if (tenantSeq != null) {
                tenantSeq.remove(patternId);
            }
            return Promise.complete();
        }

        @Override
        public Promise<List<AepEngine.Anomaly>> detectAnomalies(String tenantId,
                                                                List<AepEngine.Event> events) {
            checkNotClosed();
            requireTenantId(tenantId);
            List<AepEngine.Anomaly> anomalies = new ArrayList<>();
            Map<String, List<Double>> historyBySeries = new HashMap<>();
            AepAnomalyDetector detector = AepAnomalyDetector.builder().build();
            for (AepEngine.Event event : events) {
                Double explicitScore = extractNumeric(event.payload(), "anomaly_score");
                if (explicitScore != null && explicitScore >= anomalyThreshold.get()) {
                    anomalies.add(new AepEngine.Anomaly(
                        UUID.randomUUID().toString(),
                        "THRESHOLD_EXCEEDED",
                        explicitScore,
                        Map.of("event_type", event.type())
                    ));
                    appendHistory(historyBySeries, event.type(), explicitScore);
                    continue;
                }

                Double signal = extractNumeric(event.payload(), "value");
                if (signal == null) {
                    signal = explicitScore;
                }
                if (signal == null) {
                    continue;
                }

                List<Double> history = historyBySeries.computeIfAbsent(event.type(), ignored -> new ArrayList<>());
                if (explicitScore == null) {
                    AepAnomalyDetector.AnomalyEvent detected = detector.evaluate(event.type(), history, signal);
                    if (detected != null) {
                        anomalies.add(new AepEngine.Anomaly(
                            UUID.randomUUID().toString(),
                            "MODEL_DETECTED",
                            detected.zScore(),
                            Map.of(
                                "event_type", event.type(),
                                "detector", "z-score",
                                "severity", detected.severity().name(),
                                "mean", detected.meanValue(),
                                "stddev", detected.stdDev(),
                                "observed_value", detected.observedValue()
                            )
                        ));
                    }
                }

                appendHistory(historyBySeries, event.type(), signal);
            }
            return Promise.of(anomalies);
        }

        @Override
        public Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data) {
            checkNotClosed();
            requireTenantId(tenantId);
            return AepAsyncUtils.withTimeout(
                forecastingEngine.forecast(tenantId, data),
                asyncTimeout.get(),
                "forecast"
            );
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            shutdownCoordinator.close();
            reloadBridge.ifPresent(AepConfigReloadBridge::close);
            unregisterHealthCheck();
            patternCache.clear();
            consentCache.get().clear();
            rateLimiter.get().resetAll();
            patternsByTenant.clear();
            subscriptionsByTenant.clear();
            sequenceProgressByTenant.clear();
            seenIdempotencyKeysByTenant.clear();
            subscriberFailureCountByTenant.clear();
        }

        @Override
        public EventCloud eventCloud() {
            return eventCloud;
        }

        private Promise<AepEngine.ProcessingResult> processingPipeline(String tenantId,
                                                                       AepEngine.Event event,
                                                                       String fallbackEventId) {
            return AepErrorHandler.run(tenantId, "event.process", () -> {
                AepRateLimiter.RateLimitDecision rateLimitDecision = rateLimiter.get().tryAcquire(tenantId);
                if (!rateLimitDecision.allowed()) {
                    metrics.incrementRateLimited(tenantId);
                    auditService.logDecision(tenantId, fallbackEventId, "RATE_LIMIT", "DENIED",
                        "Rate limited; retry after " + rateLimitDecision.retryAfterSeconds() + "s",
                        Map.of("retryAfterSeconds", rateLimitDecision.retryAfterSeconds()));
                    return Promise.of(AepEngine.ProcessingResult.skipped(
                        fallbackEventId,
                        "Rate limited; retry after " + rateLimitDecision.retryAfterSeconds() + "s"
                    ));
                }

                Optional<AepEngine.ProcessingResult> duplicateResult = duplicateEventResult(tenantId, event, fallbackEventId);
                if (duplicateResult.isPresent()) {
                    metrics.incrementIdempotencyHits(tenantId);
                    auditService.logDecision(tenantId, fallbackEventId, "IDEMPOTENCY", "SKIPPED",
                        "Duplicate event detected", Map.of());
                    return Promise.of(duplicateResult.get());
                }

                AepEngine.Event compatibleEvent;
                try {
                    compatibleEvent = versionCompatibility.migrate(tenantId, event);
                } catch (RuntimeException exception) {
                    auditService.logDecision(tenantId, fallbackEventId, "VERSION_MIGRATION", "FAILED",
                        "Version migration failed: " + exception.getMessage(),
                        Map.of("fromVersion", event.version(), "error", exception.getMessage()));
                    return Promise.of(AepEngine.ProcessingResult.failed(fallbackEventId, exception.getMessage()));
                }

                if (!Objects.equals(event.version(), compatibleEvent.version())) {
                    metrics.incrementVersionMigrations(tenantId, event.version());
                    auditService.logDecision(tenantId, fallbackEventId, "VERSION_MIGRATION", "ALLOWED",
                        "Event migrated from version " + event.version() + " to " + compatibleEvent.version(),
                        Map.of("fromVersion", event.version(), "toVersion", compatibleEvent.version()));
                }

                EventSchemaValidator.ValidationResult schemaResult = schemaValidator.validate(compatibleEvent);
                if (!schemaResult.isValid()) {
                    metrics.incrementSchemaViolations(tenantId);
                    auditService.logDecision(tenantId, fallbackEventId, "SCHEMA_VALIDATION", "FAILED",
                        "Schema validation failed: " + schemaResult.summary(),
                        Map.of("summary", schemaResult.summary()));
                    return Promise.of(AepEngine.ProcessingResult.failed(
                        fallbackEventId,
                        "Schema validation failed: " + schemaResult.summary()
                    ));
                }

                AepEngine.Event normalizedEvent = resolveConsent(resolveIdentity(compatibleEvent));
                String eventId = appendToEventCloud(tenantId, normalizedEvent, fallbackEventId);

                Instant consentStartedAt = Instant.now();
                return evaluateConsent(tenantId, normalizedEvent)
                    .then(decision -> {
                        metrics.recordConsentEvalTime(tenantId, elapsedMs(consentStartedAt));
                        if (!decision.allowed()) {
                            metrics.incrementConsentDenied(tenantId);
                            auditService.logDecision(tenantId, eventId, "CONSENT", "DENIED",
                                "Event rejected by consent policy: " + decision.reason(),
                                Map.of("reason", decision.reason()));
                            return Promise.of(AepEngine.ProcessingResult.skipped(
                                eventId,
                                "Event rejected by consent policy"
                            ));
                        }

                        metrics.incrementConsentAllowed(tenantId);
                        auditService.logDecision(tenantId, eventId, "CONSENT", "ALLOWED",
                            "Event allowed by consent policy",
                            Map.of("reason", decision.reason()));

                        Instant patternStartedAt = Instant.now();
                        List<AepEngine.Detection> detections = new ArrayList<>();
                        List<AepEngine.Pattern> patterns = patternCache.get(tenantId,
                            () -> new ArrayList<>(patternsByTenant.getOrDefault(tenantId, Map.of()).values()));

                        // First, run built-in pattern matching
                        for (AepEngine.Pattern pattern : patterns) {
                            Optional<AepEngine.Detection> detection = matchPattern(tenantId, pattern, normalizedEvent);
                            detection.ifPresent(found -> {
                                detections.add(found);
                                metrics.incrementPatternsMatched(tenantId, found.patternId());
                            });
                        }

                        // Then, run registered custom pattern detectors (e.g., PatternDetectionAgent)
                        List<AepEngine.PatternDetector> detectors = patternDetectorsByTenant.getOrDefault(tenantId, List.of());
                        if (!detectors.isEmpty()) {
                            List<Promise<List<AepEngine.Detection>>> detectorPromises = detectors.stream()
                                .map(detector -> detector.detect(tenantId, normalizedEvent, patterns)
                                    .then(detectedDetections -> {
                                        metrics.incrementPatternsMatched(tenantId, "custom-detector");
                                        return detectedDetections;
                                    })
                                    .whenException(e -> {
                                        logger.warn("Pattern detector failed for tenant={}: {}", tenantId, e.getMessage());
                                        return Promise.of(List.of());
                                    }))
                                .toList();
                            
                            // Wait for all detectors to complete and collect their detections
                            return Promises.toList(detectorPromises)
                                .map(allDetections -> {
                                    for (List<AepEngine.Detection> detectorDetections : allDetections) {
                                        detections.addAll(detectorDetections);
                                    }
                                    metrics.recordPatternMatchTime(tenantId, elapsedMs(patternStartedAt));
                                    notifySubscribers(tenantId, detections);

                                    Instant deliveryStartedAt = Instant.now();
                                    return AepAsyncUtils.withTimeout(
                                            deliveryService.deliver(tenantId, normalizedEvent, detections),
                                            asyncTimeout.get(),
                                            "delivery")
                                        .map(deliveryResult -> {
                                            metrics.recordDeliveryTime(tenantId, elapsedMs(deliveryStartedAt));
                                            if (deliveryResult.hasFailures()) {
                                                metrics.incrementDeliveryFailed(tenantId);
                                            } else {
                                                metrics.incrementDeliverySuccess(tenantId);
                                            }

                                            Map<String, Object> metadata = new LinkedHashMap<>();
                                            metadata.put("processed", true);
                                            metadata.put("correlationId", normalizedEvent.correlationId());
                                            metadata.put("consentStatus", normalizedEvent.consentContext().status().name());
                                            metadata.put("eventVersion", normalizedEvent.version());
                                            normalizedEvent.identityContext().stitchedId()
                                                .ifPresent(stitchedId -> metadata.put("stitchedId", stitchedId));
                                            if (deliveryResult.hasFailures()) {
                                                metadata.put("deliveryFailures", deliveryResult.failed());
                                                metadata.put("deliveryFailureCategories", deliveryResult.failureDetails().entrySet().stream()
                                                    .collect(java.util.stream.Collectors.toMap(
                                                        Map.Entry::getKey,
                                                        entry -> entry.getValue().category().name())));
                                            }
                                            return new AepEngine.ProcessingResult(eventId, true, detections, metadata);
                                        });
                                })
                                .mapException(e -> new RuntimeException("Pattern detection failed: " + e.getMessage(), e))
                                .then(Promise::of, e -> Promise.of(AepEngine.ProcessingResult.failed(eventId, e.getMessage())));
                        }

                        metrics.recordPatternMatchTime(tenantId, elapsedMs(patternStartedAt));

                        notifySubscribers(tenantId, detections);

                        Instant deliveryStartedAt = Instant.now();
                        return AepAsyncUtils.withTimeout(
                                deliveryService.deliver(tenantId, normalizedEvent, detections),
                                asyncTimeout.get(),
                                "delivery")
                            .map(deliveryResult -> {
                                metrics.recordDeliveryTime(tenantId, elapsedMs(deliveryStartedAt));
                                if (deliveryResult.hasFailures()) {
                                    metrics.incrementDeliveryFailed(tenantId);
                                } else {
                                    metrics.incrementDeliverySuccess(tenantId);
                                }

                                Map<String, Object> metadata = new LinkedHashMap<>();
                                metadata.put("processed", true);
                                metadata.put("correlationId", normalizedEvent.correlationId());
                                metadata.put("consentStatus", normalizedEvent.consentContext().status().name());
                                metadata.put("eventVersion", normalizedEvent.version());
                                normalizedEvent.identityContext().stitchedId()
                                    .ifPresent(stitchedId -> metadata.put("stitchedId", stitchedId));
                                if (deliveryResult.hasFailures()) {
                                    metadata.put("deliveryFailures", deliveryResult.failed());
                                    metadata.put("deliveryFailureCategories", deliveryResult.failureDetails().entrySet().stream()
                                        .collect(java.util.stream.Collectors.toMap(
                                            Map.Entry::getKey,
                                            entry -> entry.getValue().category().name())));
                                }
                                return new AepEngine.ProcessingResult(eventId, true, detections, metadata);
                            });
                    });
            });
        }

        private void executePipelineStep(String tenantId, String pipelineId,
                                         AepEngine.PipelineStep step) {
            logger.debug("Executing pipeline step type={} in pipeline={} for tenant={}",
                step.type(), pipelineId, tenantId);
            switch (step.type().toLowerCase(Locale.ROOT)) {
                case PipelineStepTypes.REGISTER_PATTERN -> {
                    String name = asString(step.config().get("name"));
                    String typeStr = asString(step.config().get("patternType"));
                    if (name == null || typeStr == null) {
                        logger.warn("{} step missing name or patternType config",
                            PipelineStepTypes.REGISTER_PATTERN);
                        return;
                    }
                    AepEngine.PatternType patternType =
                        AepEngine.PatternType.valueOf(typeStr.toUpperCase(Locale.ROOT));
                    registerPattern(tenantId, new AepEngine.PatternDefinition(
                        name, asString(step.config().get("description")), patternType, step.config()
                    ));
                }
                case PipelineStepTypes.LOG -> logger.info("[Pipeline {}][{}] {}",
                    pipelineId, step.type(), step.config().get("message"));
                default -> logger.debug("Unknown pipeline step type={} - no handler registered, skipping",
                    step.type());
            }
        }

        private Optional<AepEngine.ProcessingResult> duplicateEventResult(String tenantId,
                                                                          AepEngine.Event event,
                                                                          String fallbackEventId) {
            Optional<String> idempotencyKey = event.idempotencyKey();
            if (idempotencyKey.isEmpty()) {
                return Optional.empty();
            }

            Instant now = Instant.now();
            Map<String, Instant> seen = seenIdempotencyKeysByTenant
                .computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>());
            purgeExpiredIdempotencyKeys(seen, now);
            if (seen.putIfAbsent(idempotencyKey.get(), now) != null) {
                return Optional.of(AepEngine.ProcessingResult.skipped(
                    fallbackEventId,
                    "Duplicate event suppressed: idempotencyKey=" + idempotencyKey.get()));
            }
            trimIdempotencyKeysToBudget(seen);
            return Optional.empty();
        }

        private Promise<ConsentService.ConsentDecision> evaluateConsent(String tenantId, AepEngine.Event event) {
            Optional<ConsentService.ConsentDecision> cachedDecision = consentCache.get().get(tenantId, event);
            if (cachedDecision.isPresent()) {
                metrics.incrementConsentCacheHit(tenantId);
                return Promise.of(cachedDecision.get());
            }

            metrics.incrementConsentCacheMiss(tenantId);
            return AepAsyncUtils.withTimeout(
                    consentService.evaluateConsent(tenantId, event),
                    asyncTimeout.get(),
                    "consent.eval")
                .map(decision -> {
                    consentCache.get().put(tenantId, event, decision);
                    return decision;
                });
        }

        private String appendToEventCloud(String tenantId, AepEngine.Event event, String fallbackEventId) {
            try {
                byte[] payload = serializeEvent(event);
                return eventCloud.append(tenantId, event.type(), payload);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to append event to event cloud for tenant=" + tenantId,
                    exception);
            }
        }

        private byte[] serializeEvent(AepEngine.Event event) throws JsonProcessingException {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", event.type());
            envelope.put("payload", event.payload());
            envelope.put("headers", event.headers());
            envelope.put("timestamp", event.timestamp().toString());
            envelope.put("version", event.version());
            envelope.put("correlationId", event.correlationId());
            return OBJECT_MAPPER.writeValueAsBytes(envelope);
        }

        private void recordOutcomeMetrics(String tenantId,
                                          AepEngine.ProcessingResult result,
                                          Instant startedAt) {
            if (isFailureResult(result)) {
                metrics.incrementEventsFailed(tenantId);
            } else if (isSkippedResult(result)) {
                metrics.incrementEventsSkipped(tenantId);
            } else {
                metrics.incrementEventsProcessed(tenantId);
            }
            metrics.recordEventProcessingTime(tenantId, elapsedMs(startedAt));
        }

        private boolean isFailureResult(AepEngine.ProcessingResult result) {
            return Boolean.TRUE.equals(result.metadata().get("failed"));
        }

        private boolean isSkippedResult(AepEngine.ProcessingResult result) {
            return Boolean.TRUE.equals(result.metadata().get("skipped"));
        }

        private void registerHealthCheck() {
            try {
                HealthCheckRegistry.getInstance().register(healthIndicator);
            } catch (IllegalStateException ignored) {
                logger.debug("HealthCheckRegistry not initialized; skipping AEP health registration");
            }
        }

        private void unregisterHealthCheck() {
            try {
                HealthCheckRegistry.getInstance().unregister(healthIndicator.getName());
            } catch (IllegalStateException ignored) {
                logger.debug("HealthCheckRegistry not initialized during AEP shutdown");
            }
        }

        private Optional<AepConfigReloadBridge> createReloadBridge() {
            return config.hotReloadConfigPath().map(path -> AepConfigReloadBridge.builder()
                .configPath(path)
                .checkIntervalMs(config.hotReloadCheckIntervalMs())
                .build());
        }

        private void applyRuntimeTuning(AepConfigReloadBridge.RuntimeTuning tuning) {
            tuning.anomalyThreshold().ifPresent(anomalyThreshold::set);
            tuning.tracingEnabled().ifPresent(tracingEnabled::set);
            tuning.asyncTimeout().ifPresent(asyncTimeout::set);
            tuning.rateLimitEnabled().ifPresent(rateLimitEnabled::set);
            tuning.rateLimitMaxRequestsPerMinute().ifPresent(rateLimitMaxRequestsPerMinute::set);
            tuning.rateLimitBurstSize().ifPresent(rateLimitBurstSize::set);
            tuning.consentCacheTtl().ifPresent(duration -> consentCacheTtlSeconds.set(duration.getSeconds()));
            rateLimiter.set(buildRateLimiter());
            consentCache.set(buildConsentCache());
        }

        private AepRateLimiter buildRateLimiter() {
            return AepRateLimiter.builder()
                .enabled(rateLimitEnabled.get())
                .maxRequestsPerMinute(rateLimitMaxRequestsPerMinute.get())
                .burstSize(rateLimitBurstSize.get())
                .windowDuration(Duration.ofSeconds(rateLimitWindowSeconds.get()))
                .build();
        }

        private AepConsentCache buildConsentCache() {
            return AepConsentCache.builder()
                .ttl(Duration.ofSeconds(consentCacheTtlSeconds.get()))
                .maxEntries(config.consentCacheMaxEntries())
                .build();
        }

        private void checkNotClosed() {
            if (closed || shutdownCoordinator.isShutdownRequested()) {
                throw new IllegalStateException("AepEngine is closed");
            }
        }

        private void requireTenantId(String tenantId) {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            if (tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
        }

        private void validateEventTenantContext(String tenantId, AepEngine.Event event) {
            String eventTenantId = firstNonBlank(
                event.headers().get("tenantId"),
                event.headers().get("x-tenant-id"),
                asString(event.payload().get("tenantId"))
            );
            if (eventTenantId != null && !tenantId.equals(eventTenantId)) {
                throw new AepTenantException(tenantId, "event.process",
                    "Cross-tenant event access rejected for tenantId=" + eventTenantId);
            }
        }

        private void validatePatternDefinitionTenant(String tenantId, AepEngine.PatternDefinition definition) {
            String configuredTenantId = asString(definition.config().get("tenantId"));
            if (configuredTenantId != null && !configuredTenantId.equals(tenantId)) {
                throw new AepTenantException(tenantId, "pattern.register",
                    "Pattern definition tenantId does not match owning tenant");
            }
        }

        private void validatePatternAccess(String tenantId, String patternId, boolean requireOwnedPattern) {
            Objects.requireNonNull(patternId, "patternId must not be null");
            if ("*".equals(patternId)) {
                return;
            }
            if (patternId.isBlank()) {
                throw new IllegalArgumentException("patternId must not be blank");
            }

            Map<String, AepEngine.Pattern> tenantPatterns = patternsByTenant.getOrDefault(tenantId, Map.of());
            if (tenantPatterns.containsKey(patternId)) {
                return;
            }

            Optional<String> owningTenant = findOwningTenant(patternId);
            if (owningTenant.isPresent() && !owningTenant.get().equals(tenantId)) {
                throw new AepTenantException(tenantId, "pattern.access",
                    "Pattern belongs to a different tenant: " + owningTenant.get());
            }
            if (requireOwnedPattern) {
                throw new IllegalArgumentException("Pattern not found for tenant=" + tenantId + ": " + patternId);
            }
        }

        private Optional<String> findOwningTenant(String patternId) {
            return patternsByTenant.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(patternId))
                .map(Map.Entry::getKey)
                .findFirst();
        }

        private void purgeExpiredIdempotencyKeys(Map<String, Instant> seenKeys, Instant now) {
            Instant cutoff = now.minusSeconds(config.idempotencyTtlSeconds());
            seenKeys.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }

        private void trimIdempotencyKeysToBudget(Map<String, Instant> seenKeys) {
            int maxKeys = config.maxIdempotencyKeysPerTenant();
            if (seenKeys.size() <= maxKeys) {
                return;
            }

            int keysToRemove = seenKeys.size() - maxKeys;
            seenKeys.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(keysToRemove)
                .toList()
                .forEach(entry -> seenKeys.remove(entry.getKey(), entry.getValue()));
        }

        private Optional<AepEngine.Detection> matchPattern(String tenantId, AepEngine.Pattern pattern,
                                                           AepEngine.Event event) {
            return switch (pattern.type()) {
                case THRESHOLD -> matchThreshold(pattern, event);
                case ANOMALY -> matchAnomaly(pattern, event);
                case SEQUENCE -> matchSequence(tenantId, pattern, event);
                case CORRELATION -> matchCorrelation(pattern, event);
                case CUSTOM -> matchCustom(pattern, event);
            };
        }

        private void notifySubscribers(String tenantId, List<AepEngine.Detection> detections) {
            List<SubscriptionEntry> subs = subscriptionsByTenant.getOrDefault(tenantId, List.of());
            for (AepEngine.Detection detection : detections) {
                for (SubscriptionEntry sub : subs) {
                    if (sub.patternId.equals(detection.patternId()) || sub.patternId.equals("*")) {
                        try {
                            sub.handler.accept(detection);
                        } catch (Exception e) {
                            long failures = subscriberFailureCountByTenant
                                .computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
                                .merge(detection.patternId(), 1L, Long::sum);
                            logger.warn("Subscriber failed for tenant={}, patternId={} (totalFailures={}): {}",
                                tenantId, detection.patternId(), failures, e.getMessage(), e);
                        }
                    }
                }
            }
        }

        private AepEngine.Event resolveIdentity(AepEngine.Event event) {
            String userId = firstNonBlank(
                event.headers().get("x-user-id"),
                asString(event.payload().get("userId")),
                asString(event.payload().get("user_id"))
            );
            String anonymousId = firstNonBlank(
                event.headers().get("x-anonymous-id"),
                asString(event.payload().get("anonymousId")),
                asString(event.payload().get("anonymous_id"))
            );
            String sessionId = firstNonBlank(
                event.headers().get("x-session-id"),
                asString(event.payload().get("sessionId")),
                asString(event.payload().get("session_id"))
            );
            String stitchedId = firstNonBlank(userId, anonymousId, sessionId);

            return event.withIdentityContext(new AepEngine.IdentityContext(
                Optional.ofNullable(userId),
                Optional.ofNullable(anonymousId),
                Optional.ofNullable(sessionId),
                Optional.ofNullable(stitchedId)
            ));
        }

        private AepEngine.Event resolveConsent(AepEngine.Event event) {
            AepEngine.ConsentStatus status = parseConsentStatus(firstNonBlank(
                event.headers().get("x-consent-status"),
                asString(event.payload().get("consentStatus")),
                asNestedString(event.payload(), "consent", "status")
            ));
            AepEngine.RetentionPolicy retentionPolicy = parseRetentionPolicy(firstNonBlank(
                event.headers().get("x-retention-policy"),
                asString(event.payload().get("retentionPolicy")),
                asNestedString(event.payload(), "consent", "retentionPolicy")
            ));
            List<String> allowedPurposes = allowedPurposes(event);

            return event.withConsentContext(new AepEngine.ConsentContext(
                status, retentionPolicy, allowedPurposes));
        }

        private Optional<AepEngine.Detection> matchThreshold(AepEngine.Pattern pattern,
                                                             AepEngine.Event event) {
            String field = asString(pattern.config().get("field"));
            Number threshold = asNumber(pattern.config().get("threshold"));
            Number value = asNumber(field != null ? event.payload().get(field) : null);

            if (field == null || threshold == null || value == null
                || value.doubleValue() <= threshold.doubleValue()) {
                return Optional.empty();
            }

            return Optional.of(buildDetection(pattern, Map.of(
                "field", field,
                "value", value,
                "threshold", threshold
            ), 1.0));
        }

        private Optional<AepEngine.Detection> matchAnomaly(AepEngine.Pattern pattern,
                                                           AepEngine.Event event) {
            Number overrideThreshold = asNumber(pattern.config().get("threshold"));
            double threshold = overrideThreshold != null
                ? overrideThreshold.doubleValue()
                : anomalyThreshold.get();
            if (!isAnomalous(event, threshold)) {
                return Optional.empty();
            }
            return Optional.of(buildDetection(pattern, Map.of(
                "eventType", event.type(),
                "threshold", threshold
            ), 0.95));
        }

        private Optional<AepEngine.Detection> matchSequence(String tenantId,
                                                            AepEngine.Pattern pattern,
                                                            AepEngine.Event event) {
            List<String> expectedTypes = asStringList(pattern.config().get("expectedTypes"));
            if (expectedTypes.isEmpty()) {
                return Optional.empty();
            }

            String correlationKey = resolveCorrelationKey(pattern, event);
            if (correlationKey == null || correlationKey.isBlank()) {
                logger.debug("Sequence event ignored for pattern={} because no correlation key was resolved",
                    pattern.id());
                return Optional.empty();
            }

            Map<String, Map<String, SequenceProgress>> tenantState =
                sequenceProgressByTenant.computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>());
            Map<String, SequenceProgress> patternState =
                tenantState.computeIfAbsent(pattern.id(), ignored -> new ConcurrentHashMap<>());

            SequenceProgress current = patternState.getOrDefault(correlationKey,
                new SequenceProgress(0, Instant.EPOCH));
            if (event.timestamp().isBefore(current.lastEventTime())) {
                logger.debug("Out-of-order event ignored for sequence pattern={}: eventTime={} < lastEventTime={}",
                    pattern.id(), event.timestamp(), current.lastEventTime());
                return Optional.empty();
            }

            int progress = current.progress();
            String nextExpected = expectedTypes.get(progress);
            if (nextExpected.equals(event.type())) {
                progress++;
            } else if (expectedTypes.get(0).equals(event.type())) {
                progress = 1;
            } else {
                patternState.remove(correlationKey);
                return Optional.empty();
            }

            if (progress >= expectedTypes.size()) {
                patternState.remove(correlationKey);
                return Optional.of(buildDetection(pattern, Map.of(
                    "sequence", expectedTypes,
                    "correlationKey", correlationKey
                ), 0.9));
            }

            patternState.put(correlationKey, new SequenceProgress(progress, event.timestamp()));
            return Optional.empty();
        }

        private Optional<AepEngine.Detection> matchCorrelation(AepEngine.Pattern pattern,
                                                               AepEngine.Event event) {
            String eventType = asString(pattern.config().get("eventType"));
            List<String> requiredFields = asStringList(pattern.config().get("requiredFields"));
            if (eventType != null && !eventType.equals(event.type())) {
                return Optional.empty();
            }
            if (requiredFields.stream().anyMatch(field -> !event.payload().containsKey(field))) {
                return Optional.empty();
            }
            String correlationKey = resolveCorrelationKey(pattern, event);
            if (correlationKey == null || correlationKey.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(buildDetection(pattern, Map.of(
                "requiredFields", requiredFields,
                "correlationKey", correlationKey
            ), 0.85));
        }

        private Optional<AepEngine.Detection> matchCustom(AepEngine.Pattern pattern,
                                                          AepEngine.Event event) {
            String expectedType = asString(pattern.config().get("eventType"));
            if (expectedType != null && !expectedType.equals(event.type())) {
                return Optional.empty();
            }

            Object matches = pattern.config().get("payloadMatches");
            if (matches instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (!Objects.equals(event.payload().get(key), entry.getValue())) {
                        return Optional.empty();
                    }
                }
            }

            return Optional.of(buildDetection(pattern, Map.of("eventType", event.type()), 0.8));
        }

        private AepEngine.Detection buildDetection(AepEngine.Pattern pattern,
                                                   Map<String, Object> details,
                                                   double confidence) {
            return new AepEngine.Detection(
                pattern.id(),
                pattern.name(),
                confidence,
                details,
                Instant.now()
            );
        }

        private boolean isAnomalous(AepEngine.Event event, double threshold) {
            Number score = asNumber(event.payload().get("anomaly_score"));
            return score != null && score.doubleValue() > threshold;
        }

        private static Double extractNumeric(Map<String, Object> payload, String key) {
            Number value = asNumber(payload.get(key));
            return value != null ? value.doubleValue() : null;
        }

        private static void appendHistory(Map<String, List<Double>> historyBySeries,
                                          String seriesId,
                                          double value) {
            historyBySeries.computeIfAbsent(seriesId, ignored -> new ArrayList<>()).add(value);
        }

        private List<String> allowedPurposes(AepEngine.Event event) {
            Object payloadPurposes = event.payload().get("allowedPurposes");
            if (payloadPurposes instanceof List<?> list) {
                return list.stream().map(String::valueOf).map(String::trim)
                    .filter(s -> !s.isBlank()).toList();
            }
            String headerPurposes = event.headers().get("x-allowed-purposes");
            if (headerPurposes == null || headerPurposes.isBlank()) {
                return List.of();
            }
            return Arrays.stream(headerPurposes.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        }

        private String resolveCorrelationKey(AepEngine.Pattern pattern, AepEngine.Event event) {
            String correlationField = asString(pattern.config().get("correlationField"));
            if (correlationField != null) {
                String fieldValue = asString(event.payload().get(correlationField));
                if (fieldValue != null && !fieldValue.isBlank()) {
                    return fieldValue;
                }
            }
            return event.identityContext().stitchedId().orElse(null);
        }

        private static String asString(Object value) {
            return value != null ? String.valueOf(value) : null;
        }

        private static String asNestedString(Map<String, Object> payload,
                                             String parentKey, String childKey) {
            Object parent = payload.get(parentKey);
            if (parent instanceof Map<?, ?> map) {
                Object child = map.get(childKey);
                return child != null ? String.valueOf(child) : null;
            }
            return null;
        }

        private static Number asNumber(Object value) {
            if (value instanceof Number number) {
                return number;
            }
            if (value instanceof String text) {
                try {
                    return Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }

        private static List<String> asStringList(Object value) {
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return List.of();
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        private static AepEngine.ConsentStatus parseConsentStatus(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return AepEngine.ConsentStatus.UNKNOWN;
            }
            try {
                return AepEngine.ConsentStatus.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return AepEngine.ConsentStatus.UNKNOWN;
            }
        }

        private static AepEngine.RetentionPolicy parseRetentionPolicy(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return AepEngine.RetentionPolicy.STANDARD;
            }
            try {
                return AepEngine.RetentionPolicy.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return AepEngine.RetentionPolicy.STANDARD;
            }
        }

        private static long elapsedMs(Instant startedAt) {
            return Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
        }

        private record SubscriptionEntry(String patternId, Consumer<AepEngine.Detection> handler) {
        }

        private record SequenceProgress(int progress, Instant lastEventTime) {
        }
    }
}
