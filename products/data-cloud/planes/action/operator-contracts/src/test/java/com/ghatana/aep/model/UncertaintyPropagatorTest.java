package com.ghatana.aep.model;

import com.ghatana.aep.operator.contract.OperatorKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UncertaintyPropagatorTest {

    @Test
    void andCombinesConfidenceAndAppliesEventQualityPenalty() {
        UncertaintyContext propagated = UncertaintyPropagator.propagate(
            OperatorKind.AND,
            List.of(context(0.9, 0.8, 0.9, 1.0), context(0.7, 0.6, 0.8, 1.0)),
            new UncertaintyAdjustment(0.1, 0.0, 0.0));

        assertThat(propagated.patternConfidence()).isLessThan(0.6);
        assertThat(propagated.evidence()).containsEntry("propagationRule", "AND");
    }

    @Test
    void orUsesBestSatisfyingBranch() {
        UncertaintyContext propagated = UncertaintyPropagator.propagate(
            OperatorKind.OR,
            List.of(context(0.4, 0.4, 0.7, 1.0), context(0.9, 0.8, 0.9, 1.0)),
            UncertaintyAdjustment.none());

        assertThat(propagated.patternConfidence()).isEqualTo(0.8);
    }

    @Test
    void sequenceIncludesTemporalConfidence() {
        UncertaintyContext propagated = UncertaintyPropagator.propagate(
            OperatorKind.SEQ,
            List.of(context(0.9, 0.9, 0.5, 1.0), context(0.8, 0.8, 0.7, 1.0)),
            UncertaintyAdjustment.none());

        assertThat(propagated.patternConfidence()).isEqualTo(0.48);
    }

    @Test
    void eventOperatorUsesModelRetrievalCompletenessAndCalibration() {
        UncertaintyContext propagated = UncertaintyPropagator.propagate(
            OperatorKind.AGENT_PREDICATE,
            List.of(context(0.95, 0.95, 0.95, 0.72)),
            UncertaintyAdjustment.none());

        assertThat(propagated.patternConfidence()).isEqualTo(0.72);
        assertThat(propagated.evidence()).containsEntry("propagationRule", "AGENT_PREDICATE");
    }

    @Test
    void adjustmentRejectsOutOfRangePenalty() {
        assertThatThrownBy(() -> new UncertaintyAdjustment(1.2, 0.0, 0.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lateEventPenalty");
    }

    private static UncertaintyContext context(
            double eventDetection,
            double pattern,
            double temporal,
            double agentConfidence) {
        return new UncertaintyContext(
            eventDetection,
            0.9,
            temporal,
            0.95,
            pattern,
            agentConfidence,
            agentConfidence,
            agentConfidence,
            agentConfidence,
            Map.of());
    }
}
