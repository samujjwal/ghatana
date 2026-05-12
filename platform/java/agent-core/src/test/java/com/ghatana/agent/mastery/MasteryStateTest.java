/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for MasteryState enum
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("MasteryState Tests")
class MasteryStateTest {

    @Test
    @DisplayName("Active states include OBSERVED, PRACTICED, COMPETENT, MASTERED")
    void activeStates() {
        assertThat(MasteryState.OBSERVED.isActiveForRetrieval()).isTrue();
        assertThat(MasteryState.PRACTICED.isActiveForRetrieval()).isTrue();
        assertThat(MasteryState.COMPETENT.isActiveForRetrieval()).isTrue();
        assertThat(MasteryState.MASTERED.isActiveForRetrieval()).isTrue();
    }

    @Test
    @DisplayName("Non-active states include MAINTENANCE_ONLY, OBSOLETE, RETIRED, QUARANTINED")
    void nonActiveStates() {
        assertThat(MasteryState.MAINTENANCE_ONLY.isActiveForRetrieval()).isFalse();
        assertThat(MasteryState.OBSOLETE.isActiveForRetrieval()).isFalse();
        assertThat(MasteryState.RETIRED.isActiveForRetrieval()).isFalse();
        assertThat(MasteryState.QUARANTINED.isActiveForRetrieval()).isFalse();
    }

    @Test
    @DisplayName("COMPETENT and MASTERED require evaluation for promotion")
    void evaluationRequiredForPromotion() {
        assertThat(MasteryState.COMPETENT.requiresEvaluationForPromotion()).isTrue();
        assertThat(MasteryState.MASTERED.requiresEvaluationForPromotion()).isTrue();
    }

    @Test
    @DisplayName("Lower states do not require evaluation for promotion")
    void lowerStatesDoNotRequireEvaluation() {
        assertThat(MasteryState.UNKNOWN.requiresEvaluationForPromotion()).isFalse();
        assertThat(MasteryState.OBSERVED.requiresEvaluationForPromotion()).isFalse();
        assertThat(MasteryState.PRACTICED.requiresEvaluationForPromotion()).isFalse();
    }

    @Test
    @DisplayName("Only OBSERVED can transition from UNKNOWN")
    void onlyObservedFromUnknown() {
        assertThat(MasteryState.OBSERVED.canTransitionFromUnknown()).isTrue();
        assertThat(MasteryState.PRACTICED.canTransitionFromUnknown()).isFalse();
        assertThat(MasteryState.COMPETENT.canTransitionFromUnknown()).isFalse();
        assertThat(MasteryState.MASTERED.canTransitionFromUnknown()).isFalse();
    }
}
