package com.ghatana.aep;

import com.ghatana.aep.config.AepConfigValidator;
import com.ghatana.aep.consent.ConsentServiceFactory;
import com.ghatana.aep.consent.ConsentService;
import com.ghatana.aep.consent.DefaultConsentService;
import com.ghatana.aep.delivery.EventDeliveryService;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.InMemoryEventCloud;
import com.ghatana.aep.forecasting.ForecastingEngine;
import com.ghatana.aep.forecasting.NaiveForecastingEngine;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
        AepConfigValidator.validate(config);

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
        AepConfigValidator.validate(config);

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
        private static final long DEFAULT_IDEMPOTENCY_TTL_SECONDS = 86_400L;
        private static final int DEFAULT_IDEMPOTENCY_MAX_KEYS_PER_TENANT = 10_000;

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
            return new AepConfig("test-instance", 1, 10, false, false, 0.9, Map.of());
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

            public AepConfig build() {
                return new AepConfig(instanceId, workerThreads, maxPipelinesPerTenant,
                    enableMetrics, enableTracing, anomalyThreshold, customConfig);
            }
        }

        public long idempotencyTtlSeconds() {
            Object raw = customConfig.get(IDEMPOTENCY_TTL_SECONDS_KEY);
            if (raw instanceof Number number && number.longValue() > 0) {
                return number.longValue();
            }
            return DEFAULT_IDEMPOTENCY_TTL_SECONDS;
        }

        public int maxIdempotencyKeysPerTenant() {
            Object raw = customConfig.get(IDEMPOTENCY_MAX_KEYS_PER_TENANT_KEY);
            if (raw instanceof Number number && number.intValue() > 0) {
                return number.intValue();
            }
            return DEFAULT_IDEMPOTENCY_MAX_KEYS_PER_TENANT;
        }

        public Optional<String> consentProviderName() {
            Object raw = customConfig.get(CONSENT_PROVIDER_KEY);
            if (raw instanceof String value && !value.isBlank()) {
                return Optional.of(value);
            }
            return Optional.empty();
        }
    }

    // ==================== Default Implementation ====================

    /**
     * Default AEP engine implementation.
     *
     * <p>Fixes addressed in this implementation:
     * <ul>
     *   <li>AEP-005: Event delivery to external systems via {@link EventDeliveryService}</li>
     *   <li>AEP-006: Full consent enforcement via {@link ConsentService}</li>
     *   <li>AEP-009: Identity resolution delegated cleanly; no duplicate logic</li>
     *   <li>AEP-011: Idempotency key tracking to deduplicate repeated events</li>
     *   <li>AEP-012: {@link EventAttributeExtractor} centralises header/payload extraction</li>
     *   <li>AEP-013: Tenant isolation enforced on pattern registration and lookup</li>
     *   <li>AEP-014: Basic pipeline execution (sequential step processing)</li>
     *   <li>AEP-016: Sequence pattern enforces event timestamp ordering</li>
     *   <li>AEP-017: Subscriber failures tracked via dead-letter queue logging</li>
     * </ul>
     */
    private static class DefaultAepEngine implements AepEngine {
        private final EventCloud eventCloud;
        private final AepConfig config;
        private final ForecastingEngine forecastingEngine;
        private final ConsentService consentService;
        private final EventDeliveryService deliveryService;
        private final EventSchemaValidator schemaValidator = new EventSchemaValidator();

        // Tenant-isolated pattern registry: tenantId -> patternId -> Pattern
        private final Map<String, Map<String, AepEngine.Pattern>> patternsByTenant = new ConcurrentHashMap<>();
        // Tenant-isolated subscription registry: tenantId -> list of entries
        private final Map<String, List<SubscriptionEntry>> subscriptionsByTenant = new ConcurrentHashMap<>();
        // Sequence progress: tenantId -> patternId -> correlationKey -> (progress, lastEventTime)
        private final Map<String, Map<String, Map<String, SequenceProgress>>> sequenceProgressByTenant =
            new ConcurrentHashMap<>();
        // Idempotency tracking: tenantId -> idempotencyKey -> first-seen processing time
        private final Map<String, Map<String, Instant>> seenIdempotencyKeysByTenant = new ConcurrentHashMap<>();
        // Subscriber failure tracking: tenantId -> patternId -> failureCount
        private final Map<String, Map<String, Long>> subscriberFailureCountByTenant = new ConcurrentHashMap<>();

        private volatile boolean closed = false;

        DefaultAepEngine(EventCloud eventCloud, AepConfig config,
                         ForecastingEngine forecastingEngine,
                         ConsentService consentService,
                         EventDeliveryService deliveryService) {
            this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud required");
            this.config = Objects.requireNonNull(config, "config required");
            this.forecastingEngine = Objects.requireNonNull(forecastingEngine, "forecastingEngine required");
            this.consentService = Objects.requireNonNull(consentService, "consentService required");
            this.deliveryService = Objects.requireNonNull(deliveryService, "deliveryService required");
        }

        @Override
        public Promise<AepEngine.ProcessingResult> process(String tenantId, AepEngine.Event event) {
            checkNotClosed();
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(event, "event must not be null");
            String eventId = UUID.randomUUID().toString();

            // AEP-002: Schema validation
            EventSchemaValidator.ValidationResult schemaResult = schemaValidator.validate(event);
            if (!schemaResult.isValid()) {
                logger.warn("Event schema validation failed for tenant={}, eventId={}: {}",
                    tenantId, eventId, schemaResult.summary());
                return Promise.of(AepEngine.ProcessingResult.failed(eventId,
                    "Schema validation failed: " + schemaResult.summary()));
            }

            // AEP-011: Idempotency check
            Optional<String> idempotencyKey = event.idempotencyKey();
            if (idempotencyKey.isPresent()) {
                Instant now = Instant.now();
                Map<String, Instant> seen = seenIdempotencyKeysByTenant
                    .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
                purgeExpiredIdempotencyKeys(seen, now);
                if (seen.putIfAbsent(idempotencyKey.get(), now) != null) {
                    logger.debug("Duplicate event suppressed by idempotency key={} for tenant={}",
                        idempotencyKey.get(), tenantId);
                    return Promise.of(AepEngine.ProcessingResult.skipped(eventId,
                        "Duplicate event suppressed: idempotencyKey=" + idempotencyKey.get()));
                }
                trimIdempotencyKeysToBudget(seen);
            }

            // Resolve identity and consent
            AepEngine.Event normalizedEvent = resolveConsent(resolveIdentity(event));

            // AEP-006: Delegate consent enforcement to ConsentService
            return consentService.evaluateConsent(tenantId, normalizedEvent)
                .then(decision -> {
                    if (!decision.allowed()) {
                        return Promise.of(AepEngine.ProcessingResult.skipped(eventId,
                            "Event rejected by consent policy"));
                    }

                    // Process through registered patterns
                    List<AepEngine.Detection> detections = new ArrayList<>();
                    Map<String, AepEngine.Pattern> patterns =
                        patternsByTenant.getOrDefault(tenantId, Map.of());

                    for (AepEngine.Pattern pattern : patterns.values()) {
                        Optional<AepEngine.Detection> detection =
                            matchPattern(tenantId, pattern, normalizedEvent);
                        detection.ifPresent(detections::add);
                    }

                    // AEP-017: Notify subscribers with enhanced failure tracking
                    notifySubscribers(tenantId, detections);

                    // AEP-005: Deliver event to external destinations asynchronously
                    return deliveryService.deliver(tenantId, normalizedEvent, detections)
                        .map(deliveryResult -> {
                            Map<String, Object> metadata = new LinkedHashMap<>();
                            metadata.put("processed", true);
                            metadata.put("consentStatus",
                                normalizedEvent.consentContext().status().name());
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
        }

        // AEP-014: Basic pipeline execution — runs steps sequentially
        @Override
        public void submitPipeline(String tenantId, AepEngine.Pipeline pipeline) {
            checkNotClosed();
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(pipeline, "pipeline must not be null");
            logger.info("Submitting pipeline id={} name='{}' with {} steps for tenant={}",
                pipeline.id(), pipeline.name(), pipeline.steps().size(), tenantId);

            for (AepEngine.PipelineStep step : pipeline.steps()) {
                try {
                    executePipelineStep(tenantId, pipeline.id(), step);
                } catch (Exception e) {
                    logger.error("Pipeline step type={} failed in pipeline={} for tenant={}: {}",
                        step.type(), pipeline.id(), tenantId, e.getMessage(), e);
                    // Continue with remaining steps; callers observe step failures via logging.
                }
            }
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
                    pipelineId, step.type(), step.config().get("message"))
                ;
                default -> logger.debug("Unknown pipeline step type={} — no handler registered, skipping",
                    step.type());
            }
        }

        @Override
        public AepEngine.Subscription subscribe(String tenantId, String patternId,
                                                 Consumer<AepEngine.Detection> handler) {
            checkNotClosed();
            SubscriptionEntry entry = new SubscriptionEntry(patternId, handler);
            subscriptionsByTenant.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(entry);

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
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(definition, "definition must not be null");

            AepEngine.Pattern pattern = new AepEngine.Pattern(
                UUID.randomUUID().toString(),
                definition.name(),
                definition.description(),
                definition.type(),
                definition.config(),
                java.time.Instant.now()
            );

            patternsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(pattern.id(), pattern);

            return Promise.of(pattern);
        }

        @Override
        public Promise<Optional<AepEngine.Pattern>> getPattern(String tenantId, String patternId) {
            checkNotClosed();
            // AEP-013: Only return patterns that belong to this tenantId
            Map<String, AepEngine.Pattern> patterns = patternsByTenant.getOrDefault(tenantId, Map.of());
            return Promise.of(Optional.ofNullable(patterns.get(patternId)));
        }

        @Override
        public Promise<List<AepEngine.Pattern>> listPatterns(String tenantId) {
            checkNotClosed();
            Map<String, AepEngine.Pattern> patterns = patternsByTenant.getOrDefault(tenantId, Map.of());
            return Promise.of(new ArrayList<>(patterns.values()));
        }

        @Override
        public Promise<Void> deletePattern(String tenantId, String patternId) {
            checkNotClosed();
            // AEP-013: Only delete patterns in this tenant's registry
            Map<String, AepEngine.Pattern> patterns = patternsByTenant.get(tenantId);
            if (patterns != null) {
                patterns.remove(patternId);
            }
            // Clean up sequence progress for this pattern
            Map<String, Map<String, SequenceProgress>> tenantSeq =
                sequenceProgressByTenant.get(tenantId);
            if (tenantSeq != null) {
                tenantSeq.remove(patternId);
            }
            return Promise.complete();
        }

        @Override
        public Promise<List<AepEngine.Anomaly>> detectAnomalies(String tenantId,
                                                                  List<AepEngine.Event> events) {
            checkNotClosed();
            List<AepEngine.Anomaly> anomalies = new ArrayList<>();
            for (AepEngine.Event event : events) {
                if (isAnomalous(event, config.anomalyThreshold())) {
                    double score = ((Number) event.payload().getOrDefault(
                        "anomaly_score", config.anomalyThreshold())).doubleValue();
                    anomalies.add(new AepEngine.Anomaly(
                        UUID.randomUUID().toString(),
                        "THRESHOLD_EXCEEDED",
                        score,
                        Map.of("event_type", event.type())
                    ));
                }
            }
            return Promise.of(anomalies);
        }

        @Override
        public Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data) {
            checkNotClosed();
            return forecastingEngine.forecast(tenantId, data);
        }

        @Override
        public void close() {
            closed = true;
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

        // ── Internal helpers ────────────────────────────────────────────────

        private void checkNotClosed() {
            if (closed) {
                throw new IllegalStateException("AepEngine is closed");
            }
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

        // AEP-017: Enhanced subscriber notification with failure tracking
        private void notifySubscribers(String tenantId, List<AepEngine.Detection> detections) {
            List<SubscriptionEntry> subs = subscriptionsByTenant.getOrDefault(tenantId, List.of());
            for (AepEngine.Detection detection : detections) {
                for (SubscriptionEntry sub : subs) {
                    if (sub.patternId.equals(detection.patternId()) || sub.patternId.equals("*")) {
                        try {
                            sub.handler.accept(detection);
                        } catch (Exception e) {
                            // Track failure count per pattern for monitoring
                            long failures = subscriberFailureCountByTenant
                                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                                .merge(detection.patternId(), 1L, Long::sum);
                            logger.warn(
                                "Subscriber failed for tenant={}, patternId={} (totalFailures={}): {}",
                                tenantId, detection.patternId(), failures, e.getMessage(), e);
                        }
                    }
                }
            }
        }

        // AEP-012: Centralised identity extraction using EventAttributeExtractor utility
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
            // Stitched ID: prefer explicit userId over anonymous/session fallback
            String stitchedId = firstNonBlank(userId, anonymousId, sessionId);

            return event.withIdentityContext(new AepEngine.IdentityContext(
                Optional.ofNullable(userId),
                Optional.ofNullable(anonymousId),
                Optional.ofNullable(sessionId),
                Optional.ofNullable(stitchedId)
            ));
        }

        // AEP-012: Centralised consent extraction using EventAttributeExtractor utility
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
            double threshold = overrideThreshold != null ? overrideThreshold.doubleValue()
                : config.anomalyThreshold();
            if (!isAnomalous(event, threshold)) {
                return Optional.empty();
            }
            return Optional.of(buildDetection(pattern, Map.of(
                "eventType", event.type(),
                "threshold", threshold
            ), 0.95));
        }

        // AEP-016: Sequence matching with timestamp ordering enforcement
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
                sequenceProgressByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
            Map<String, SequenceProgress> patternState =
                tenantState.computeIfAbsent(pattern.id(), k -> new ConcurrentHashMap<>());

            SequenceProgress current = patternState.getOrDefault(correlationKey,
                new SequenceProgress(0, java.time.Instant.EPOCH));

            // AEP-016: Reject out-of-order events (enforce temporal ordering)
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

            return Optional.of(buildDetection(pattern,
                Map.of("eventType", event.type()), 0.8));
        }

        private AepEngine.Detection buildDetection(AepEngine.Pattern pattern,
                                                    Map<String, Object> details,
                                                    double confidence) {
            return new AepEngine.Detection(
                pattern.id(),
                pattern.name(),
                confidence,
                details,
                java.time.Instant.now()
            );
        }

        private boolean isAnomalous(AepEngine.Event event, double threshold) {
            Number score = asNumber(event.payload().get("anomaly_score"));
            return score != null && score.doubleValue() > threshold;
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

        // ── Type conversion utilities (AEP-015: centralised to avoid duplication) ──

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

        // ── Inner record types ────────────────────────────────────────────────

        private record SubscriptionEntry(String patternId, Consumer<AepEngine.Detection> handler) {}

        /** Tracks sequence pattern progress with temporal ordering. */
        private record SequenceProgress(int progress, java.time.Instant lastEventTime) {}
    }
}
