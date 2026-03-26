package com.ghatana.datacloud.memory;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for {@link TierPolicy} and {@link TierEntry}.
 *
 * <p>Covers both Lombok-built value objects: builder defaults, factory methods,
 * validation, transition logic, and entry lifecycle operations.
 */
class TierPolicyAndEntryTest {

    // ═══════════════════════════════════════════
    // TierPolicy tests
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("TierPolicy builder with defaults")
    void tierPolicy_builderDefaults() {
        TierPolicy policy = TierPolicy.builder()
                .tier(MemoryTier.WARM)
                .ttl(Duration.ofHours(1))
                .build();

        assertThat(policy.getTier()).isEqualTo(MemoryTier.WARM);
        assertThat(policy.getMaxRecords()).isEqualTo(-1); // unlimited
        assertThat(policy.getMaxBytes()).isEqualTo(-1);   // unlimited
        assertThat(policy.getTtlGracePeriod()).isEqualTo(Duration.ZERO);
        assertThat(policy.getEvictionStrategy()).isEqualTo(TierPolicy.EvictionStrategy.SALIENCE_WEIGHTED_LRU);
        assertThat(policy.getEvictionThreshold()).isCloseTo(0.85, within(0.001));
        assertThat(policy.getEvictionTarget()).isCloseTo(0.70, within(0.001));
        assertThat(policy.isAutoTransitionEnabled()).isTrue();
        assertThat(policy.getMinimumTierResidency()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("TierPolicy.defaultFor() creates sensible defaults for each tier")
    void tierPolicy_defaultFor_allTiers() {
        for (MemoryTier tier : MemoryTier.values()) {
            TierPolicy policy = TierPolicy.defaultFor(tier);
            assertThat(policy.getTier()).isEqualTo(tier);
            assertThat(policy.getEvictionStrategy()).isNotNull();
            // TTL may be null for ARCHIVE (unlimited retention)
            assertThat(policy.getEffectiveTtl()).isNotNull();
        }
    }

    @Test
    @DisplayName("TierPolicy.defaultFor(HOT) has small capacity limits")
    void tierPolicy_defaultForHot() {
        TierPolicy hot = TierPolicy.defaultFor(MemoryTier.HOT);
        assertThat(hot.getTier()).isEqualTo(MemoryTier.HOT);
        assertThat(hot.getMaxRecords()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("TierPolicy.defaultFor(ARCHIVE) has auto-transition disabled")
    void tierPolicy_defaultForArchive() {
        TierPolicy archive = TierPolicy.defaultFor(MemoryTier.ARCHIVE);
        assertThat(archive.isAutoTransitionEnabled()).isFalse();
    }

    @Test
    @DisplayName("TierPolicy shouldPromote/shouldDemote delegate correctly")
    void tierPolicy_shouldPromoteDemote() {
        TierPolicy warmPolicy = TierPolicy.builder()
                .tier(MemoryTier.WARM)
                .ttl(Duration.ofHours(1))
                .promotionSalienceThreshold(0.8)
                .demotionSalienceThreshold(0.4)
                .build();

        assertThat(warmPolicy.shouldPromote(0.9)).isTrue();
        assertThat(warmPolicy.shouldPromote(0.6)).isFalse();
        assertThat(warmPolicy.shouldDemote(0.3)).isTrue();
        assertThat(warmPolicy.shouldDemote(0.5)).isFalse();
    }

    @Test
    @DisplayName("TierPolicy.validate() returns empty for valid policy")
    void tierPolicy_validate_validPolicy() {
        TierPolicy policy = TierPolicy.defaultFor(MemoryTier.WARM);
        assertThat(policy.validate()).isEmpty();
    }

    @Test
    @DisplayName("TierPolicy.toBuilder() creates modifiable copy")
    void tierPolicy_toBuilder() {
        TierPolicy original = TierPolicy.defaultFor(MemoryTier.HOT);
        TierPolicy modified = original.toBuilder()
                .maxRecords(50_000)
                .build();

        assertThat(modified.getMaxRecords()).isEqualTo(50_000);
        assertThat(original.getMaxRecords()).isEqualTo(10_000); // original unchanged
    }

    @Test
    @DisplayName("All EvictionStrategy enum values exist")
    void evictionStrategy_values() {
        assertThat(TierPolicy.EvictionStrategy.values()).containsExactly(
                TierPolicy.EvictionStrategy.LRU,
                TierPolicy.EvictionStrategy.LFU,
                TierPolicy.EvictionStrategy.FIFO,
                TierPolicy.EvictionStrategy.SALIENCE_WEIGHTED_LRU,
                TierPolicy.EvictionStrategy.TTL_FIRST,
                TierPolicy.EvictionStrategy.RANDOM
        );
    }

    // ═══════════════════════════════════════════
    // TierEntry tests
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("TierEntry.create() sets all fields correctly")
    void tierEntry_create() {
        TierEntry entry = TierEntry.create(
                "rec-1", "event", "tenant-1",
                MemoryTier.WARM, 0.65, 1024, 3600);

        assertThat(entry.getRecordId()).isEqualTo("rec-1");
        assertThat(entry.getRecordType()).isEqualTo("event");
        assertThat(entry.getTenantId()).isEqualTo("tenant-1");
        assertThat(entry.getCurrentTier()).isEqualTo(MemoryTier.WARM);
        assertThat(entry.getLastSalienceScore()).isCloseTo(0.65, within(0.001));
        assertThat(entry.getEstimatedSizeBytes()).isEqualTo(1024);
        assertThat(entry.getAccessCount()).isEqualTo(0);
        assertThat(entry.isPinned()).isFalse();
        assertThat(entry.getEntryId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("TierEntry.recordAccess() increments counts and updates timestamp")
    void tierEntry_recordAccess() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .accessCount(5)
                .windowAccessCount(2)
                .build();

        TierEntry accessed = entry.recordAccess();

        assertThat(accessed.getAccessCount()).isEqualTo(6);
        assertThat(accessed.getWindowAccessCount()).isEqualTo(3);
        assertThat(accessed.getLastAccessedAt()).isAfterOrEqualTo(entry.getLastAccessedAt());
    }

    @Test
    @DisplayName("TierEntry.updateSalience() changes score")
    void tierEntry_updateSalience() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .lastSalienceScore(0.5)
                .build();

        TierEntry updated = entry.updateSalience(0.9);
        assertThat(updated.getLastSalienceScore()).isCloseTo(0.9, within(0.001));
    }

    @Test
    @DisplayName("TierEntry.moveTo() updates tier and tracks history")
    void tierEntry_moveTo() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .currentTier(MemoryTier.HOT)
                .promotionCount(0)
                .demotionCount(0)
                .build();

        TierEntry demoted = entry.moveTo(MemoryTier.WARM, 3600);
        assertThat(demoted.getCurrentTier()).isEqualTo(MemoryTier.WARM);
        assertThat(demoted.getPreviousTier()).isEqualTo(MemoryTier.HOT);
    }

    @Test
    @DisplayName("TierEntry.pin() and unpin()")
    void tierEntry_pinUnpin() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .build();

        TierEntry pinned = entry.pin("important");
        assertThat(pinned.isPinned()).isTrue();
        assertThat(pinned.getPinnedReason()).isEqualTo("important");

        TierEntry unpinned = pinned.unpin();
        assertThat(unpinned.isPinned()).isFalse();
        assertThat(unpinned.getPinnedReason()).isNull();
    }

    @Test
    @DisplayName("TierEntry.isExpired() returns true after expiration")
    void tierEntry_isExpired() {
        TierEntry expired = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .expiresAt(Instant.now().minusSeconds(10))
                .build();

        assertThat(expired.isExpired()).isTrue();

        TierEntry notExpired = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertThat(notExpired.isExpired()).isFalse();
    }

    @Test
    @DisplayName("TierEntry with no expiresAt is never expired")
    void tierEntry_noExpiry_neverExpired() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .build();

        assertThat(entry.isExpired()).isFalse();
    }

    @Test
    @DisplayName("TierEntry.getAge() returns positive duration")
    void tierEntry_getAge() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .insertedAt(Instant.now().minusSeconds(60))
                .build();

        assertThat(entry.getAge()).isGreaterThanOrEqualTo(Duration.ofSeconds(59));
    }

    @Test
    @DisplayName("TierEntry.resetWindow() resets window counters")
    void tierEntry_resetWindow() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .windowAccessCount(15)
                .build();

        TierEntry reset = entry.resetWindow();
        assertThat(reset.getWindowAccessCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("TierEntry.calculateEvictionPriority() returns bounded value")
    void tierEntry_calculateEvictionPriority() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .lastSalienceScore(0.5)
                .accessCount(10)
                .insertedAt(Instant.now().minusSeconds(300))
                .build();

        double priority = entry.calculateEvictionPriority();
        assertThat(priority).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("TierEntry builder with metadata")
    void tierEntry_withMetadata() {
        TierEntry entry = TierEntry.builder()
                .recordId("r1")
                .tenantId("t1")
                .metadata(Map.of("source", "test", "priority", "high"))
                .build();

        assertThat(entry.getMetadata()).containsEntry("source", "test");
        assertThat(entry.getMetadata()).hasSize(2);
    }

    // ═══ AssertJ helper ═══

    private static org.assertj.core.data.Offset<Double> within(double d) {
        return org.assertj.core.data.Offset.offset(d);
    }
}
