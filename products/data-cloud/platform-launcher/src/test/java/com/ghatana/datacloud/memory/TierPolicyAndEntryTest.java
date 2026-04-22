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
    @DisplayName("TierPolicy builder with defaults [GH-90000]")
    void tierPolicy_builderDefaults() { // GH-90000
        TierPolicy policy = TierPolicy.builder() // GH-90000
                .tier(MemoryTier.WARM) // GH-90000
                .ttl(Duration.ofHours(1)) // GH-90000
                .build(); // GH-90000

        assertThat(policy.getTier()).isEqualTo(MemoryTier.WARM); // GH-90000
        assertThat(policy.getMaxRecords()).isEqualTo(-1); // unlimited // GH-90000
        assertThat(policy.getMaxBytes()).isEqualTo(-1);   // unlimited // GH-90000
        assertThat(policy.getTtlGracePeriod()).isEqualTo(Duration.ZERO); // GH-90000
        assertThat(policy.getEvictionStrategy()).isEqualTo(TierPolicy.EvictionStrategy.SALIENCE_WEIGHTED_LRU); // GH-90000
        assertThat(policy.getEvictionThreshold()).isCloseTo(0.85, within(0.001)); // GH-90000
        assertThat(policy.getEvictionTarget()).isCloseTo(0.70, within(0.001)); // GH-90000
        assertThat(policy.isAutoTransitionEnabled()).isTrue(); // GH-90000
        assertThat(policy.getMinimumTierResidency()).isEqualTo(Duration.ofMinutes(1)); // GH-90000
        assertThat(policy.getMetadata()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("TierPolicy.defaultFor() creates sensible defaults for each tier [GH-90000]")
    void tierPolicy_defaultFor_allTiers() { // GH-90000
        for (MemoryTier tier : MemoryTier.values()) { // GH-90000
            TierPolicy policy = TierPolicy.defaultFor(tier); // GH-90000
            assertThat(policy.getTier()).isEqualTo(tier); // GH-90000
            assertThat(policy.getEvictionStrategy()).isNotNull(); // GH-90000
            // TTL may be null for ARCHIVE (unlimited retention) // GH-90000
            assertThat(policy.getEffectiveTtl()).isNotNull(); // GH-90000
        }
    }

    @Test
    @DisplayName("TierPolicy.defaultFor(HOT) has small capacity limits [GH-90000]")
    void tierPolicy_defaultForHot() { // GH-90000
        TierPolicy hot = TierPolicy.defaultFor(MemoryTier.HOT); // GH-90000
        assertThat(hot.getTier()).isEqualTo(MemoryTier.HOT); // GH-90000
        assertThat(hot.getMaxRecords()).isEqualTo(10_000); // GH-90000
    }

    @Test
    @DisplayName("TierPolicy.defaultFor(ARCHIVE) has auto-transition disabled [GH-90000]")
    void tierPolicy_defaultForArchive() { // GH-90000
        TierPolicy archive = TierPolicy.defaultFor(MemoryTier.ARCHIVE); // GH-90000
        assertThat(archive.isAutoTransitionEnabled()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("TierPolicy shouldPromote/shouldDemote delegate correctly [GH-90000]")
    void tierPolicy_shouldPromoteDemote() { // GH-90000
        TierPolicy warmPolicy = TierPolicy.builder() // GH-90000
                .tier(MemoryTier.WARM) // GH-90000
                .ttl(Duration.ofHours(1)) // GH-90000
                .promotionSalienceThreshold(0.8) // GH-90000
                .demotionSalienceThreshold(0.4) // GH-90000
                .build(); // GH-90000

        assertThat(warmPolicy.shouldPromote(0.9)).isTrue(); // GH-90000
        assertThat(warmPolicy.shouldPromote(0.6)).isFalse(); // GH-90000
        assertThat(warmPolicy.shouldDemote(0.3)).isTrue(); // GH-90000
        assertThat(warmPolicy.shouldDemote(0.5)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("TierPolicy.validate() returns empty for valid policy [GH-90000]")
    void tierPolicy_validate_validPolicy() { // GH-90000
        TierPolicy policy = TierPolicy.defaultFor(MemoryTier.WARM); // GH-90000
        assertThat(policy.validate()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("TierPolicy.toBuilder() creates modifiable copy [GH-90000]")
    void tierPolicy_toBuilder() { // GH-90000
        TierPolicy original = TierPolicy.defaultFor(MemoryTier.HOT); // GH-90000
        TierPolicy modified = original.toBuilder() // GH-90000
                .maxRecords(50_000) // GH-90000
                .build(); // GH-90000

        assertThat(modified.getMaxRecords()).isEqualTo(50_000); // GH-90000
        assertThat(original.getMaxRecords()).isEqualTo(10_000); // original unchanged // GH-90000
    }

    @Test
    @DisplayName("All EvictionStrategy enum values exist [GH-90000]")
    void evictionStrategy_values() { // GH-90000
        assertThat(TierPolicy.EvictionStrategy.values()).containsExactly( // GH-90000
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
    @DisplayName("TierEntry.create() sets all fields correctly [GH-90000]")
    void tierEntry_create() { // GH-90000
        TierEntry entry = TierEntry.create( // GH-90000
                "rec-1", "event", "tenant-1",
                MemoryTier.WARM, 0.65, 1024, 3600);

        assertThat(entry.getRecordId()).isEqualTo("rec-1 [GH-90000]");
        assertThat(entry.getRecordType()).isEqualTo("event [GH-90000]");
        assertThat(entry.getTenantId()).isEqualTo("tenant-1 [GH-90000]");
        assertThat(entry.getCurrentTier()).isEqualTo(MemoryTier.WARM); // GH-90000
        assertThat(entry.getLastSalienceScore()).isCloseTo(0.65, within(0.001)); // GH-90000
        assertThat(entry.getEstimatedSizeBytes()).isEqualTo(1024); // GH-90000
        assertThat(entry.getAccessCount()).isEqualTo(0); // GH-90000
        assertThat(entry.isPinned()).isFalse(); // GH-90000
        assertThat(entry.getEntryId()).isNotNull().isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("TierEntry.recordAccess() increments counts and updates timestamp [GH-90000]")
    void tierEntry_recordAccess() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .accessCount(5) // GH-90000
                .windowAccessCount(2) // GH-90000
                .build(); // GH-90000

        TierEntry accessed = entry.recordAccess(); // GH-90000

        assertThat(accessed.getAccessCount()).isEqualTo(6); // GH-90000
        assertThat(accessed.getWindowAccessCount()).isEqualTo(3); // GH-90000
        assertThat(accessed.getLastAccessedAt()).isAfterOrEqualTo(entry.getLastAccessedAt()); // GH-90000
    }

    @Test
    @DisplayName("TierEntry.updateSalience() changes score [GH-90000]")
    void tierEntry_updateSalience() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .lastSalienceScore(0.5) // GH-90000
                .build(); // GH-90000

        TierEntry updated = entry.updateSalience(0.9); // GH-90000
        assertThat(updated.getLastSalienceScore()).isCloseTo(0.9, within(0.001)); // GH-90000
    }

    @Test
    @DisplayName("TierEntry.moveTo() updates tier and tracks history [GH-90000]")
    void tierEntry_moveTo() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .currentTier(MemoryTier.HOT) // GH-90000
                .promotionCount(0) // GH-90000
                .demotionCount(0) // GH-90000
                .build(); // GH-90000

        TierEntry demoted = entry.moveTo(MemoryTier.WARM, 3600); // GH-90000
        assertThat(demoted.getCurrentTier()).isEqualTo(MemoryTier.WARM); // GH-90000
        assertThat(demoted.getPreviousTier()).isEqualTo(MemoryTier.HOT); // GH-90000
    }

    @Test
    @DisplayName("TierEntry.pin() and unpin() [GH-90000]")
    void tierEntry_pinUnpin() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .build(); // GH-90000

        TierEntry pinned = entry.pin("important [GH-90000]");
        assertThat(pinned.isPinned()).isTrue(); // GH-90000
        assertThat(pinned.getPinnedReason()).isEqualTo("important [GH-90000]");

        TierEntry unpinned = pinned.unpin(); // GH-90000
        assertThat(unpinned.isPinned()).isFalse(); // GH-90000
        assertThat(unpinned.getPinnedReason()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("TierEntry.isExpired() returns true after expiration [GH-90000]")
    void tierEntry_isExpired() { // GH-90000
        TierEntry expired = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .expiresAt(Instant.now().minusSeconds(10)) // GH-90000
                .build(); // GH-90000

        assertThat(expired.isExpired()).isTrue(); // GH-90000

        TierEntry notExpired = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .expiresAt(Instant.now().plusSeconds(3600)) // GH-90000
                .build(); // GH-90000

        assertThat(notExpired.isExpired()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("TierEntry with no expiresAt is never expired [GH-90000]")
    void tierEntry_noExpiry_neverExpired() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .build(); // GH-90000

        assertThat(entry.isExpired()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("TierEntry.getAge() returns positive duration [GH-90000]")
    void tierEntry_getAge() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .insertedAt(Instant.now().minusSeconds(60)) // GH-90000
                .build(); // GH-90000

        assertThat(entry.getAge()).isGreaterThanOrEqualTo(Duration.ofSeconds(59)); // GH-90000
    }

    @Test
    @DisplayName("TierEntry.resetWindow() resets window counters [GH-90000]")
    void tierEntry_resetWindow() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .windowAccessCount(15) // GH-90000
                .build(); // GH-90000

        TierEntry reset = entry.resetWindow(); // GH-90000
        assertThat(reset.getWindowAccessCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("TierEntry.calculateEvictionPriority() returns bounded value [GH-90000]")
    void tierEntry_calculateEvictionPriority() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .lastSalienceScore(0.5) // GH-90000
                .accessCount(10) // GH-90000
                .insertedAt(Instant.now().minusSeconds(300)) // GH-90000
                .build(); // GH-90000

        double priority = entry.calculateEvictionPriority(); // GH-90000
        assertThat(priority).isBetween(0.0, 1.0); // GH-90000
    }

    @Test
    @DisplayName("TierEntry builder with metadata [GH-90000]")
    void tierEntry_withMetadata() { // GH-90000
        TierEntry entry = TierEntry.builder() // GH-90000
                .recordId("r1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .metadata(Map.of("source", "test", "priority", "high")) // GH-90000
                .build(); // GH-90000

        assertThat(entry.getMetadata()).containsEntry("source", "test"); // GH-90000
        assertThat(entry.getMetadata()).hasSize(2); // GH-90000
    }

    // ═══ AssertJ helper ═══

    private static org.assertj.core.data.Offset<Double> within(double d) { // GH-90000
        return org.assertj.core.data.Offset.offset(d); // GH-90000
    }
}
