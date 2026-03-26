package com.ghatana.datacloud.workspace;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.attention.SalienceScore;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * An item in the global workspace spotlight.
 *
 * <p><b>Purpose</b><br>
 * Represents a high-salience item that has been elevated to the global workspace
 * for cross-system attention. SpotlightItems contain:
 * <ul>
 *   <li>The original record that triggered elevation</li>
 *   <li>The salience score that caused elevation</li>
 *   <li>Context and metadata for processing</li>
 *   <li>TTL for automatic eviction</li>
 * </ul>
 *
 * <p><b>Lifecycle</b><br>
 * <pre>
 * 1. Record processed by AttentionManager
 * 2. Salience exceeds threshold → SpotlightItem created
 * 3. Item added to GlobalWorkspace spotlight
 * 4. Subscribers notified (normal or broadcast)
 * 5. Item expires after TTL or explicit removal
 * </pre>
 *
 * <p><b>Serialization</b><br>
 * SpotlightItems are Serializable for Redis pub-sub distribution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SpotlightItem item = SpotlightItem.builder()
 *     .record(eventRecord)
 *     .salienceScore(score)
 *     .summary("Fraud pattern detected for customer X")
 *     .emergency(true)
 *     .ttl(Duration.ofMinutes(5))
 *     .build();
 *
 * if (item.isExpired()) {
 *     // Handle expired item
 * }
 * }</pre>
 *
 * @see GlobalWorkspace
 * @see com.ghatana.datacloud.attention.AttentionManager
 * @doc.type class
 * @doc.purpose Spotlight item in global workspace
 * @doc.layer core
 * @doc.pattern Value Object
 */
@Value
@Builder(toBuilder = true)
public class SpotlightItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Default TTL for spotlight items.
     */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /**
     * Unique identifier for this spotlight item.
     */
    @Builder.Default
    String id = UUID.randomUUID().toString();

    /**
     * Tenant ID for multi-tenancy.
     */
    String tenantId;

    /**
     * The record that was elevated.
     */
    DataRecord record;

    /**
     * Salience score that caused elevation.
     */
    SalienceScore salienceScore;

    /**
     * Human-readable summary of why this item was elevated.
     */
    String summary;

    /**
     * Whether this is an emergency requiring broadcast.
     */
    @Builder.Default
    boolean emergency = false;

    /**
     * Pattern match that triggered elevation (if any).
     */
    PatternMatch patternMatch;

    /**
     * Additional context data.
     */
    @Builder.Default
    Map<String, Object> context = Collections.emptyMap();

    /**
     * Time when item was spotlighted.
     */
    @Builder.Default
    Instant spotlightedAt = Instant.now();

    /**
     * Time-to-live for this item.
     */
    @Builder.Default
    Duration ttl = DEFAULT_TTL;

    /**
     * Priority level (1 = highest).
     */
    @Builder.Default
    int priority = 5;

    /**
     * Source that created this spotlight item.
     */
    @Builder.Default
    String source = "attention-manager";

    /**
     * Number of times this item has been accessed.
     */
    @Builder.Default
    int accessCount = 0;

    /**
     * Check if this item has expired.
     *
     * @return true if current time is past expiration
     */
    public boolean isExpired() {
        return Instant.now().isAfter(getExpiresAt());
    }

    /**
     * Get the expiration time.
     *
     * @return Instant when this item expires
     */
    public Instant getExpiresAt() {
        return spotlightedAt.plus(ttl);
    }

    /**
     * Get remaining time before expiration.
     *
     * @return Duration until expiration, or Duration.ZERO if expired
     */
    public Duration getRemainingTtl() {
        Duration remaining = Duration.between(Instant.now(), getExpiresAt());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Create a copy with incremented access count.
     *
     * @return New SpotlightItem with accessCount + 1
     */
    public SpotlightItem withAccess() {
        return toBuilder()
                .accessCount(accessCount + 1)
                .build();
    }

    /**
     * Create a copy with extended TTL.
     *
     * @param extension Duration to add to TTL
     * @return New SpotlightItem with extended TTL
     */
    public SpotlightItem withExtendedTtl(Duration extension) {
        return toBuilder()
                .ttl(ttl.plus(extension))
                .build();
    }

    /**
     * Check if this item has higher priority than another.
     *
     * @param other The other item to compare
     * @return true if this item has higher priority
     */
    public boolean hasHigherPriorityThan(SpotlightItem other) {
        if (other == null) {
            return true;
        }
        // Lower priority number = higher priority
        if (this.priority != other.priority) {
            return this.priority < other.priority;
        }
        // Emergency items take precedence
        if (this.emergency != other.emergency) {
            return this.emergency;
        }
        // Higher salience score wins
        return this.salienceScore.getScore() > other.salienceScore.getScore();
    }

  /**
     * Represents a pattern match that triggered spotlight elevation.
     */
    @Value
    @Builder
    public static class PatternMatch implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Pattern identifier.
         */
        String patternId;

        /**
         * Pattern type (SEQ, AND, OR, etc.).
         */
        String patternType;

        /**
         * Match confidence [0.0, 1.0].
         */
        double confidence;

        /**
         * Events that matched the pattern.
         */
        @Builder.Default
        java.util.List<String> matchedEventIds = java.util.List.of();

        /**
         * Whether this pattern has a predefined reflex action.
         */
        @Builder.Default
        boolean hasReflexAction = false;

        /**
         * Reflex action ID if available.
         */
        String reflexActionId;
    }
}
