package com.ghatana.datacloud.workspace;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.attention.SalienceScore;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * An item in the global workspace spotlight.
 *
 * @doc.type class
 * @doc.purpose Spotlight item in global workspace
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class SpotlightItem implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final String id;
    private final String tenantId;
    private final DataRecord record;
    private final SalienceScore salienceScore;
    private final String summary;
    private final boolean emergency;
    private final PatternMatch patternMatch;
    private final Map<String, Object> context;
    private final Instant spotlightedAt;
    private final Duration ttl;
    private final int priority;
    private final String source;
    private final int accessCount;

    private SpotlightItem(SpotlightItemBuilder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.record = builder.record;
        this.salienceScore = builder.salienceScore;
        this.summary = builder.summary;
        this.emergency = builder.emergency;
        this.patternMatch = builder.patternMatch;
        this.context = Collections.unmodifiableMap(builder.context);
        this.spotlightedAt = builder.spotlightedAt;
        this.ttl = builder.ttl;
        this.priority = builder.priority;
        this.source = builder.source;
        this.accessCount = builder.accessCount;
    }

    public static SpotlightItemBuilder builder() {
        return new SpotlightItemBuilder();
    }

    public SpotlightItemBuilder toBuilder() {
        return builder()
            .id(id)
            .tenantId(tenantId)
            .record(record)
            .salienceScore(salienceScore)
            .summary(summary)
            .emergency(emergency)
            .patternMatch(patternMatch)
            .context(context)
            .spotlightedAt(spotlightedAt)
            .ttl(ttl)
            .priority(priority)
            .source(source)
            .accessCount(accessCount);
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public DataRecord getRecord() { return record; }
    public SalienceScore getSalienceScore() { return salienceScore; }
    public String getSummary() { return summary; }
    public boolean isEmergency() { return emergency; }
    public PatternMatch getPatternMatch() { return patternMatch; }
    public Map<String, Object> getContext() { return context; }
    public Instant getSpotlightedAt() { return spotlightedAt; }
    public Duration getTtl() { return ttl; }
    public int getPriority() { return priority; }
    public String getSource() { return source; }
    public int getAccessCount() { return accessCount; }

    public boolean isExpired() {
        return Instant.now().isAfter(getExpiresAt());
    }

    public Instant getExpiresAt() {
        return spotlightedAt.plus(ttl);
    }

    public Duration getRemainingTtl() {
        Duration remaining = Duration.between(Instant.now(), getExpiresAt());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public SpotlightItem withAccess() {
        return toBuilder().accessCount(accessCount + 1).build();
    }

    public SpotlightItem withExtendedTtl(Duration extension) {
        return toBuilder().ttl(ttl.plus(extension)).build();
    }

    public boolean hasHigherPriorityThan(SpotlightItem other) {
        if (other == null) {
            return true;
        }
        if (this.priority != other.priority) {
            return this.priority < other.priority;
        }
        if (this.emergency != other.emergency) {
            return this.emergency;
        }
        return this.salienceScore.getScore() > other.salienceScore.getScore();
    }

    public static final class SpotlightItemBuilder {
        private String id = UUID.randomUUID().toString();
        private String tenantId;
        private DataRecord record;
        private SalienceScore salienceScore;
        private String summary;
        private boolean emergency;
        private PatternMatch patternMatch;
        private Map<String, Object> context = Collections.emptyMap();
        private Instant spotlightedAt = Instant.now();
        private Duration ttl = DEFAULT_TTL;
        private int priority = 5;
        private String source = "attention-manager";
        private int accessCount = 0;

        private SpotlightItemBuilder() {
        }

        public SpotlightItemBuilder id(String id) { this.id = id; return this; }
        public SpotlightItemBuilder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public SpotlightItemBuilder record(DataRecord record) { this.record = record; return this; }
        public SpotlightItemBuilder salienceScore(SalienceScore salienceScore) { this.salienceScore = salienceScore; return this; }
        public SpotlightItemBuilder summary(String summary) { this.summary = summary; return this; }
        public SpotlightItemBuilder emergency(boolean emergency) { this.emergency = emergency; return this; }
        public SpotlightItemBuilder patternMatch(PatternMatch patternMatch) { this.patternMatch = patternMatch; return this; }
        public SpotlightItemBuilder context(Map<String, Object> context) { this.context = context != null ? context : Collections.emptyMap(); return this; }
        public SpotlightItemBuilder spotlightedAt(Instant spotlightedAt) { this.spotlightedAt = spotlightedAt != null ? spotlightedAt : Instant.now(); return this; }
        public SpotlightItemBuilder ttl(Duration ttl) { this.ttl = ttl != null ? ttl : DEFAULT_TTL; return this; }
        public SpotlightItemBuilder priority(int priority) { this.priority = priority; return this; }
        public SpotlightItemBuilder source(String source) { this.source = source != null ? source : "attention-manager"; return this; }
        public SpotlightItemBuilder accessCount(int accessCount) { this.accessCount = accessCount; return this; }

        public SpotlightItem build() {
            return new SpotlightItem(this);
        }
    }

    /**
     * Represents a pattern match that triggered spotlight elevation.
     */
    public static final class PatternMatch implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String patternId;
        private final String patternType;
        private final double confidence;
        private final java.util.List<String> matchedEventIds;
        private final boolean hasReflexAction;
        private final String reflexActionId;

        private PatternMatch(PatternMatchBuilder builder) {
            this.patternId = builder.patternId;
            this.patternType = builder.patternType;
            this.confidence = builder.confidence;
            this.matchedEventIds = java.util.List.copyOf(builder.matchedEventIds);
            this.hasReflexAction = builder.hasReflexAction;
            this.reflexActionId = builder.reflexActionId;
        }

        public static PatternMatchBuilder builder() { return new PatternMatchBuilder(); }

        public String getPatternId() { return patternId; }
        public String getPatternType() { return patternType; }
        public double getConfidence() { return confidence; }
        public java.util.List<String> getMatchedEventIds() { return matchedEventIds; }
        public boolean isHasReflexAction() { return hasReflexAction; }
        public String getReflexActionId() { return reflexActionId; }

        public static final class PatternMatchBuilder {
            private String patternId;
            private String patternType;
            private double confidence;
            private java.util.List<String> matchedEventIds = java.util.List.of();
            private boolean hasReflexAction;
            private String reflexActionId;

            private PatternMatchBuilder() {
            }

            public PatternMatchBuilder patternId(String patternId) { this.patternId = patternId; return this; }
            public PatternMatchBuilder patternType(String patternType) { this.patternType = patternType; return this; }
            public PatternMatchBuilder confidence(double confidence) { this.confidence = confidence; return this; }
            public PatternMatchBuilder matchedEventIds(java.util.List<String> matchedEventIds) { this.matchedEventIds = matchedEventIds != null ? matchedEventIds : java.util.List.of(); return this; }
            public PatternMatchBuilder hasReflexAction(boolean hasReflexAction) { this.hasReflexAction = hasReflexAction; return this; }
            public PatternMatchBuilder reflexActionId(String reflexActionId) { this.reflexActionId = reflexActionId; return this; }

            public PatternMatch build() { return new PatternMatch(this); }
        }
    }
}
