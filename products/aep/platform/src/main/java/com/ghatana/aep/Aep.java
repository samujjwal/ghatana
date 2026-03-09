package com.ghatana.aep;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.AepEventCloudFactory;
import com.ghatana.aep.event.InMemoryEventCloud;
import io.activej.promise.Promise;

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
        
        // Discover EventCloud via ServiceLoader or use configured one
        EventCloud eventCloud = discoverEventCloud(config);
        
        return new DefaultAepEngine(eventCloud, config);
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
        return new DefaultAepEngine(new InMemoryEventCloud(), AepConfig.forTesting());
    }

    private static EventCloud discoverEventCloud(AepConfig config) {
        // Try ServiceLoader first
        ServiceLoader<EventCloud> loader = ServiceLoader.load(EventCloud.class);
        Optional<EventCloud> discovered = loader.findFirst();
        
        if (discovered.isPresent()) {
            return discovered.get();
        }

        return AepEventCloudFactory.createDefault();
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
        Map<String, Object> customConfig
    ) {
        public AepConfig {
            instanceId = instanceId != null ? instanceId : UUID.randomUUID().toString();
            if (workerThreads <= 0) workerThreads = Runtime.getRuntime().availableProcessors();
            if (maxPipelinesPerTenant <= 0) maxPipelinesPerTenant = 100;
            customConfig = customConfig != null ? Map.copyOf(customConfig) : Map.of();
        }

        public static AepConfig defaults() {
            return new AepConfig(null, 0, 100, true, false, Map.of());
        }

        public static AepConfig forTesting() {
            return new AepConfig("test-instance", 1, 10, false, false, Map.of());
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
            private Map<String, Object> customConfig = Map.of();

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

            public Builder customConfig(Map<String, Object> config) {
                this.customConfig = config;
                return this;
            }

            public AepConfig build() {
                return new AepConfig(instanceId, workerThreads, maxPipelinesPerTenant,
                    enableMetrics, enableTracing, customConfig);
            }
        }
    }

    // ==================== Default Implementation ====================

    /**
     * Default AEP engine implementation.
     */
    private static class DefaultAepEngine implements AepEngine {
        private final EventCloud eventCloud;
        private final AepConfig config;
        private final Map<String, Map<String, AepEngine.Pattern>> patternsByTenant = new ConcurrentHashMap<>();
        private final Map<String, List<SubscriptionEntry>> subscriptionsByTenant = new ConcurrentHashMap<>();
        private volatile boolean closed = false;

        DefaultAepEngine(EventCloud eventCloud, AepConfig config) {
            this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud required");
            this.config = Objects.requireNonNull(config, "config required");
        }

        @Override
        public Promise<AepEngine.ProcessingResult> process(String tenantId, AepEngine.Event event) {
            checkNotClosed();
            Objects.requireNonNull(event, "event must not be null");
            String eventId = UUID.randomUUID().toString();
            
            // Process through registered patterns
            List<AepEngine.Detection> detections = new ArrayList<>();
            Map<String, AepEngine.Pattern> patterns = patternsByTenant.getOrDefault(tenantId, Map.of());
            
            for (AepEngine.Pattern pattern : patterns.values()) {
                Optional<AepEngine.Detection> detection = matchPattern(pattern, event);
                detection.ifPresent(detections::add);
            }
            
            // Notify subscribers
            notifySubscribers(tenantId, detections);
            
            return Promise.of(AepEngine.ProcessingResult.success(eventId, detections));
        }

        @Override
        public void submitPipeline(String tenantId, AepEngine.Pipeline pipeline) {
            checkNotClosed();
            // Pipeline execution would be implemented here
        }

        @Override
        public AepEngine.Subscription subscribe(String tenantId, String patternId, Consumer<AepEngine.Detection> handler) {
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
        public Promise<AepEngine.Pattern> registerPattern(String tenantId, AepEngine.PatternDefinition definition) {
            checkNotClosed();
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
            Map<String, AepEngine.Pattern> patterns = patternsByTenant.get(tenantId);
            if (patterns != null) {
                patterns.remove(patternId);
            }
            return Promise.complete();
        }

        @Override
        public Promise<List<AepEngine.Anomaly>> detectAnomalies(String tenantId, List<AepEngine.Event> events) {
            checkNotClosed();
            List<AepEngine.Anomaly> anomalies = new ArrayList<>();
            for (AepEngine.Event event : events) {
                if (isAnomalous(event)) {
                    anomalies.add(new AepEngine.Anomaly(
                        UUID.randomUUID().toString(),
                        "THRESHOLD_EXCEEDED",
                        0.85,
                        Map.of("event_type", event.type())
                    ));
                }
            }
            return Promise.of(anomalies);
        }

        @Override
        public Promise<AepEngine.Forecast> forecast(String tenantId, AepEngine.TimeSeriesData data) {
            checkNotClosed();
            List<AepEngine.DataPoint> predictions = new ArrayList<>();
            if (!data.points().isEmpty()) {
                AepEngine.DataPoint last = data.points().get(data.points().size() - 1);
                for (int i = 1; i <= 5; i++) {
                    predictions.add(new AepEngine.DataPoint(
                        last.timestamp().plusSeconds(i * 3600),
                        last.value() * (1 + 0.01 * i)
                    ));
                }
            }
            return Promise.of(new AepEngine.Forecast(data.metric(), predictions, 0.75, Map.of()));
        }

        @Override
        public void close() {
            closed = true;
            patternsByTenant.clear();
            subscriptionsByTenant.clear();
        }

        @Override
        public EventCloud eventCloud() {
            return eventCloud;
        }

        private void checkNotClosed() {
            if (closed) {
                throw new IllegalStateException("AepEngine is closed");
            }
        }

        private Optional<AepEngine.Detection> matchPattern(AepEngine.Pattern pattern, AepEngine.Event event) {
            // Simple pattern matching - production would use advanced detection
            return Optional.empty();
        }

        private void notifySubscribers(String tenantId, List<AepEngine.Detection> detections) {
            List<SubscriptionEntry> subs = subscriptionsByTenant.getOrDefault(tenantId, List.of());
            for (AepEngine.Detection detection : detections) {
                for (SubscriptionEntry sub : subs) {
                    if (sub.patternId.equals(detection.patternId()) || sub.patternId.equals("*")) {
                        try {
                            sub.handler.accept(detection);
                        } catch (Exception e) {
                            // Log and continue
                        }
                    }
                }
            }
        }

        private boolean isAnomalous(AepEngine.Event event) {
            return event.payload().containsKey("anomaly_score") &&
                ((Number) event.payload().get("anomaly_score")).doubleValue() > 0.9;
        }

        private record SubscriptionEntry(String patternId, Consumer<AepEngine.Detection> handler) {}
    }
}
