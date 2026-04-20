package com.ghatana.datacloud.client;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical learning signal for AI/ML training.
 *
 * @doc.type class
 * @doc.purpose Canonical learning signal model
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class LearningSignal {

    private final UUID signalId;
    private final Instant timestamp;
    private final String tenantId;
    private final SignalType signalType;
    private final SignalSource source;
    private final String sourceId;
    private final String sourceType;
    private final String category;
    private final double strength;
    private final double confidence;
    private final Set<String> tags;
    private final Map<String, Object> features;
    private final Map<String, Object> metrics;
    private final Map<String, Object> context;
    private final Map<String, Object> metadata;
    private final String correlationId;
    private final int version;

    private LearningSignal(Builder builder) {
        this.signalId = builder.signalId;
        this.timestamp = builder.timestamp;
        this.tenantId = builder.tenantId;
        this.signalType = builder.signalType;
        this.source = builder.source;
        this.sourceId = builder.sourceId;
        this.sourceType = builder.sourceType;
        this.category = builder.category;
        this.strength = builder.strength;
        this.confidence = builder.confidence;
        this.tags = Collections.unmodifiableSet(builder.tags);
        this.features = Collections.unmodifiableMap(builder.features);
        this.metrics = Collections.unmodifiableMap(builder.metrics);
        this.context = Collections.unmodifiableMap(builder.context);
        this.metadata = Collections.unmodifiableMap(builder.metadata);
        this.correlationId = builder.correlationId;
        this.version = builder.version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getSignalId() { return signalId; }
    public Instant getTimestamp() { return timestamp; }
    public String getTenantId() { return tenantId; }
    public SignalType getSignalType() { return signalType; }
    public SignalSource getSource() { return source; }
    public String getSourceId() { return sourceId; }
    public String getSourceType() { return sourceType; }
    public String getCategory() { return category; }
    public double getStrength() { return strength; }
    public double getConfidence() { return confidence; }
    public Set<String> getTags() { return tags; }
    public Map<String, Object> getFeatures() { return features; }
    public Map<String, Object> getMetrics() { return metrics; }
    public Map<String, Object> getContext() { return context; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getCorrelationId() { return correlationId; }
    public int getVersion() { return version; }

    public enum SignalType {
        QUERY,
        PERFORMANCE,
        FEEDBACK,
        GOVERNANCE,
        DATA_QUALITY,
        OPERATIONAL,
        PREDICTION_OUTCOME,
        ANOMALY,
        CUSTOM,
        REINFORCEMENT,
        CORRECTION,
        ERROR,
        OBSERVATION,
        ADAPTATION,
        EXPLORATION,
        INSTRUCTION
    }

    public static final class SignalSource {
        private final String plugin;
        private final String collection;
        private final String operation;
        private final String actor;
        private final Map<String, String> metadata;

        private SignalSource(SignalSourceBuilder builder) {
            this.plugin = builder.plugin;
            this.collection = builder.collection;
            this.operation = builder.operation;
            this.actor = builder.actor;
            this.metadata = Collections.unmodifiableMap(builder.metadata);
        }

        public static SignalSourceBuilder builder() {
            return new SignalSourceBuilder();
        }

        public String getPlugin() { return plugin; }
        public String getCollection() { return collection; }
        public String getOperation() { return operation; }
        public String getActor() { return actor; }
        public Map<String, String> getMetadata() { return metadata; }

        public static final class SignalSourceBuilder {
            private String plugin;
            private String collection;
            private String operation;
            private String actor;
            private Map<String, String> metadata = Collections.emptyMap();

            private SignalSourceBuilder() {
            }

            public SignalSourceBuilder plugin(String plugin) { this.plugin = plugin; return this; }
            public SignalSourceBuilder collection(String collection) { this.collection = collection; return this; }
            public SignalSourceBuilder operation(String operation) { this.operation = operation; return this; }
            public SignalSourceBuilder actor(String actor) { this.actor = actor; return this; }
            public SignalSourceBuilder metadata(Map<String, String> metadata) { this.metadata = metadata != null ? metadata : Collections.emptyMap(); return this; }

            public SignalSource build() { return new SignalSource(this); }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("signalId", signalId.toString());
        result.put("timestamp", timestamp.toString());
        result.put("tenantId", tenantId != null ? tenantId : "");
        result.put("signalType", signalType != null ? signalType.name() : SignalType.CUSTOM.name());
        result.put("source", source != null ? sourceToMap() : Map.of());
        result.put("features", features != null ? features : Map.of());
        result.put("metrics", metrics != null ? metrics : Map.of());
        result.put("context", context != null ? context : Map.of());
        result.put("correlationId", correlationId != null ? correlationId : "");
        result.put("version", version);
        return result;
    }

    private Map<String, Object> sourceToMap() {
        if (source == null) {
            return Map.of();
        }
        return Map.of(
            "plugin", source.plugin != null ? source.plugin : "",
            "collection", source.collection != null ? source.collection : "",
            "operation", source.operation != null ? source.operation : "",
            "actor", source.actor != null ? source.actor : "",
            "metadata", source.metadata != null ? source.metadata : Map.of()
        );
    }

    public static final class Builder {
        private UUID signalId = UUID.randomUUID();
        private Instant timestamp = Instant.now();
        private String tenantId;
        private SignalType signalType;
        private SignalSource source;
        private String sourceId;
        private String sourceType;
        private String category;
        private double strength = 1.0;
        private double confidence = 1.0;
        private Set<String> tags = Collections.emptySet();
        private Map<String, Object> features = Collections.emptyMap();
        private Map<String, Object> metrics = Collections.emptyMap();
        private Map<String, Object> context = Collections.emptyMap();
        private Map<String, Object> metadata = Collections.emptyMap();
        private String correlationId;
        private int version = 1;

        private Builder() {
        }

        public Builder signalId(UUID signalId) { this.signalId = signalId != null ? signalId : UUID.randomUUID(); return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp != null ? timestamp : Instant.now(); return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder signalType(SignalType signalType) { this.signalType = signalType; return this; }
        public Builder source(SignalSource source) { this.source = source; return this; }
        public Builder sourceId(String sourceId) { this.sourceId = sourceId; return this; }
        public Builder sourceType(String sourceType) { this.sourceType = sourceType; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder strength(double strength) { this.strength = strength; return this; }
        public Builder confidence(double confidence) { this.confidence = confidence; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags != null ? tags : Collections.emptySet(); return this; }
        public Builder features(Map<String, Object> features) { this.features = features != null ? features : Collections.emptyMap(); return this; }
        public Builder metrics(Map<String, Object> metrics) { this.metrics = metrics != null ? metrics : Collections.emptyMap(); return this; }
        public Builder context(Map<String, Object> context) { this.context = context != null ? context : Collections.emptyMap(); return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata != null ? metadata : Collections.emptyMap(); return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder version(int version) { this.version = version; return this; }

        public LearningSignal build() {
            return new LearningSignal(this);
        }
    }
}
