/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.memory;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tracking metadata for a record within the tiered memory system.
 *
 * <p>Maintains lifecycle information including access patterns, tier history,
 * and metrics needed for intelligent tier transitions. This record is stored
 * alongside the actual data record in the memory system.
 *
 * <h2>Tracked Information</h2>
 * <ul>
 *   <li><b>Identity</b>: Record ID and current tier location</li>
 *   <li><b>Timing</b>: Insertion, last access, and expiry times</li>
 *   <li><b>Access</b>: Hit counts and access patterns</li>
 *   <li><b>Salience</b>: Current score and history</li>
 *   <li><b>Transitions</b>: Promotion/demotion history</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * TierEntry entry = TierEntry.builder()
 *     .recordId("event-123")
 *     .currentTier(MemoryTier.HOT)
 *     .insertedAt(Instant.now())
 *     .lastSalienceScore(0.85)
 *     .build();
 *
 * TierEntry updated = entry.recordAccess()
 *     .updateSalience(0.92);
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Track record metadata within tiered memory
 * @doc.layer core
 * @doc.pattern Value Object, Metadata
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see MemoryTier
 * @see MemoryTierRouter
 */
@Value
@Builder(toBuilder = true)
public class TierEntry {

    /**
     * Unique identifier for this entry tracking record.
     */
    @Builder.Default
    String entryId = UUID.randomUUID().toString();

    /**
     * The ID of the data record being tracked.
     */
    String recordId;

    /**
     * The type/category of the record (e.g., "event", "entity", "timeseries").
     */
    @Builder.Default
    String recordType = "unknown";

    /**
     * The tenant ID this record belongs to.
     */
    String tenantId;

    /**
     * Current memory tier where the record resides.
     */
    @Builder.Default
    MemoryTier currentTier = MemoryTier.WARM;

    /**
     * Previous tier before the last transition, if any.
     */
    MemoryTier previousTier;

    /**
     * When the record was first inserted into the tiered system.
     */
    @Builder.Default
    Instant insertedAt = Instant.now();

    /**
     * When the record was last accessed (read or updated).
     */
    @Builder.Default
    Instant lastAccessedAt = Instant.now();

    /**
     * When the record entered the current tier.
     */
    @Builder.Default
    Instant tierEnteredAt = Instant.now();

    /**
     * When the record will expire from the current tier.
     *
     * <p>After expiry, the record is eligible for demotion or eviction.
     */
    Instant expiresAt;

    /**
     * Number of times the record has been accessed.
     */
    @Builder.Default
    long accessCount = 0;

    /**
     * Number of accesses within the current evaluation window.
     */
    @Builder.Default
    int windowAccessCount = 0;

    /**
     * When the current access window started.
     */
    @Builder.Default
    Instant windowStartedAt = Instant.now();

    /**
     * Most recent salience score for this record.
     */
    @Builder.Default
    double lastSalienceScore = 0.5;

    /**
     * Salience score when the record entered the current tier.
     */
    @Builder.Default
    double tierEntrySalienceScore = 0.5;

    /**
     * Number of times the record has been promoted.
     */
    @Builder.Default
    int promotionCount = 0;

    /**
     * Number of times the record has been demoted.
     */
    @Builder.Default
    int demotionCount = 0;

    /**
     * Estimated size in bytes of the tracked record.
     */
    @Builder.Default
    long estimatedSizeBytes = 0;

    /**
     * Whether this record is currently pinned (exempt from eviction).
     */
    @Builder.Default
    boolean pinned = false;

    /**
     * Reason for pinning, if pinned.
     */
    String pinnedReason;

    /**
     * Additional metadata for the entry.
     */
    @Builder.Default
    Map<String, String> metadata = Map.of();

    /**
     * Creates a new entry for a fresh record.
     *
     * @param recordId the record ID
     * @param recordType the record type
     * @param tenantId the tenant ID
     * @param initialTier the initial tier placement
     * @param salienceScore the initial salience score
     * @param estimatedSize estimated size in bytes
     * @param ttlSeconds TTL in seconds, or -1 for tier default
     * @return a new TierEntry
     */
    public static TierEntry create(
            String recordId,
            String recordType,
            String tenantId,
            MemoryTier initialTier,
            double salienceScore,
            long estimatedSize,
            long ttlSeconds) {

        Instant now = Instant.now();
        Instant expiry = ttlSeconds > 0
                ? now.plusSeconds(ttlSeconds)
                : now.plus(initialTier.getDefaultTtl());

        return TierEntry.builder()
                .recordId(recordId)
                .recordType(recordType)
                .tenantId(tenantId)
                .currentTier(initialTier)
                .insertedAt(now)
                .lastAccessedAt(now)
                .tierEnteredAt(now)
                .expiresAt(expiry)
                .lastSalienceScore(salienceScore)
                .tierEntrySalienceScore(salienceScore)
                .estimatedSizeBytes(estimatedSize)
                .windowStartedAt(now)
                .build();
    }

    /**
     * Records an access to this entry.
     *
     * @return updated entry with incremented access counts
     */
    public TierEntry recordAccess() {
        return this.toBuilder()
                .lastAccessedAt(Instant.now())
                .accessCount(accessCount + 1)
                .windowAccessCount(windowAccessCount + 1)
                .build();
    }

    /**
     * Updates the salience score.
     *
     * @param newScore the new salience score
     * @return updated entry
     */
    public TierEntry updateSalience(double newScore) {
        return this.toBuilder()
                .lastSalienceScore(newScore)
                .build();
    }

    /**
     * Moves this entry to a new tier.
     *
     * @param newTier the target tier
     * @param newTtlSeconds TTL in the new tier, or -1 for tier default
     * @return updated entry in the new tier
     */
    public TierEntry moveTo(MemoryTier newTier, long newTtlSeconds) {
        Instant now = Instant.now();
        boolean isPromotion = newTier.isHigherPriorityThan(currentTier);
        Instant expiry = newTtlSeconds > 0
                ? now.plusSeconds(newTtlSeconds)
                : now.plus(newTier.getDefaultTtl());

        return this.toBuilder()
                .previousTier(currentTier)
                .currentTier(newTier)
                .tierEnteredAt(now)
                .tierEntrySalienceScore(lastSalienceScore)
                .expiresAt(expiry)
                .promotionCount(isPromotion ? promotionCount + 1 : promotionCount)
                .demotionCount(!isPromotion ? demotionCount + 1 : demotionCount)
                // Reset window on tier change
                .windowAccessCount(0)
                .windowStartedAt(now)
                .build();
    }

    /**
     * Pins this entry, exempting it from eviction.
     *
     * @param reason the reason for pinning
     * @return updated entry with pinned flag
     */
    public TierEntry pin(String reason) {
        return this.toBuilder()
                .pinned(true)
                .pinnedReason(reason)
                .build();
    }

    /**
     * Unpins this entry, making it eligible for eviction.
     *
     * @return updated entry with pinned flag cleared
     */
    public TierEntry unpin() {
        return this.toBuilder()
                .pinned(false)
                .pinnedReason(null)
                .build();
    }

    /**
     * Resets the access window for pattern evaluation.
     *
     * @return updated entry with reset window
     */
    public TierEntry resetWindow() {
        return this.toBuilder()
                .windowAccessCount(0)
                .windowStartedAt(Instant.now())
                .build();
    }

    /**
     * Checks if this entry has expired.
     *
     * @return true if the expiry time has passed
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this entry is near expiry (within 10% of TTL).
     *
     * @return true if close to expiry
     */
    public boolean isNearExpiry() {
        if (expiresAt == null) return false;
        Instant now = Instant.now();
        if (now.isAfter(expiresAt)) return true;

        long totalTtlMillis = java.time.Duration.between(tierEnteredAt, expiresAt).toMillis();
        long remainingMillis = java.time.Duration.between(now, expiresAt).toMillis();
        return remainingMillis < totalTtlMillis * 0.1;
    }

    /**
     * Calculates the age of this entry since insertion.
     *
     * @return age duration
     */
    public java.time.Duration getAge() {
        return java.time.Duration.between(insertedAt, Instant.now());
    }

    /**
     * Calculates time since last access.
     *
     * @return duration since last access
     */
    public java.time.Duration getTimeSinceLastAccess() {
        return java.time.Duration.between(lastAccessedAt, Instant.now());
    }

    /**
     * Calculates time in current tier.
     *
     * @return duration in current tier
     */
    public java.time.Duration getTimeInCurrentTier() {
        return java.time.Duration.between(tierEnteredAt, Instant.now());
    }

    /**
     * Computes an access frequency score (accesses per minute).
     *
     * @return access frequency, or 0 if no meaningful duration
     */
    public double getAccessFrequency() {
        long ageMinutes = getAge().toMinutes();
        return ageMinutes > 0 ? (double) accessCount / ageMinutes : accessCount;
    }

    /**
     * Computes window access frequency (window accesses per minute).
     *
     * @return window access frequency
     */
    public double getWindowAccessFrequency() {
        long windowMinutes = java.time.Duration.between(windowStartedAt, Instant.now()).toMinutes();
        return windowMinutes > 0 ? (double) windowAccessCount / windowMinutes : windowAccessCount;
    }

    /**
     * Calculates an eviction priority score for capacity management.
     *
     * <p>Lower scores indicate higher eviction priority.
     * Considers recency, salience, and access patterns.
     *
     * @return eviction priority score
     */
    public double calculateEvictionPriority() {
        if (pinned) {
            return Double.MAX_VALUE; // Never evict pinned entries
        }

        // Components (each 0-1 range, higher = keep longer)
        double salienceComponent = lastSalienceScore;

        // Recency: exponential decay based on time since last access
        double minutesSinceAccess = getTimeSinceLastAccess().toMinutes();
        double recencyComponent = Math.exp(-minutesSinceAccess / 60.0); // 1-hour half-life

        // Frequency: normalized by log scale
        double frequencyComponent = Math.min(1.0, Math.log1p(accessCount) / 10.0);

        // Weighted combination
        return (salienceComponent * 0.4)
                + (recencyComponent * 0.35)
                + (frequencyComponent * 0.25);
    }
}
