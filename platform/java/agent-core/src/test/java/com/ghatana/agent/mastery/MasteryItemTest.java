/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests for {@link MasteryItem}.
 *
 * @doc.type class
 * @doc.purpose Tests for MasteryItem immutability and null validation
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("MasteryItem Tests")
class MasteryItemTest {

    private static MasteryItem buildItem(List<MasteryTransition> stateHistory) {
        Instant now = Instant.now();
        return new MasteryItem(
                "mastery-1",
                "tenant-1",
                "skill-1",
                "domain-1",
                "agent-1",
                "release-1.0.0",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                new MasteryScore(0.8, 0.7, 0.6, 0.9, 0.5, 0.7, 0.8),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of("evidence-1"),
                List.<String>of(),
                List.<String>of(),
                stateHistory,
                now,
                now.plusSeconds(86400),
                Map.<String,String>of(),
                0.8
        );
    }

    @Test
    @DisplayName("Null stateHistory is rejected with NullPointerException")
    void nullStateHistoryIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> buildItem(null))
                .withMessageContaining("stateHistory");
    }

    @Test
    @DisplayName("Mutable input list does not mutate record after construction")
    void mutableInputListDoesNotMutateRecord() {
        List<MasteryTransition> mutableHistory = new ArrayList<>();
        MasteryItem item = buildItem(mutableHistory);

        assertThat(item.stateHistory()).isEmpty();

        // Mutate the original list — the record must remain unchanged
        mutableHistory.add(new MasteryTransition(
                "txn-1",
                "tenant-1",
                "mastery-1",
                "agent-1",
                "release-1.0.0",
                null,
                MasteryState.UNKNOWN,
                MasteryState.COMPETENT,
                "test transition",
                "test-engine",
                Instant.now(),
                Map.of(),
                Map.of()
        ));

        assertThat(item.stateHistory()).isEmpty();
    }

    @Test
    @DisplayName("Record exposes stateHistory as unmodifiable list")
    void stateHistoryIsUnmodifiable() {
        MasteryItem item = buildItem(List.of());

        assertThat(item.stateHistory()).isUnmodifiable();
    }
}
