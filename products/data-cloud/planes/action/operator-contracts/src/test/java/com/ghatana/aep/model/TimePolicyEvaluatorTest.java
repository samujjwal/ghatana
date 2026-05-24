package com.ghatana.aep.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TimePolicyEvaluatorTest {

    @Test
    void incorporatesEventsWithinAllowedLateness() {
        EventTimeContext context = context(EventTimeContext.LateEventBehavior.DEGRADE_CONFIDENCE);

        LateEventDecision decision = TimePolicyEvaluator.evaluateLateEvent(
            context,
            Instant.parse("2026-05-23T00:09:00Z"));

        assertThat(decision.late()).isFalse();
        assertThat(decision.processable()).isTrue();
    }

    @Test
    void degradedLateEventProducesConfidencePenalty() {
        EventTimeContext context = context(EventTimeContext.LateEventBehavior.DEGRADE_CONFIDENCE);

        LateEventDecision decision = TimePolicyEvaluator.evaluateLateEvent(
            context,
            Instant.parse("2026-05-23T00:07:59Z"));

        assertThat(decision.late()).isTrue();
        assertThat(decision.processable()).isTrue();
        assertThat(decision.uncertaintyAdjustment().lateEventPenalty()).isGreaterThan(0.0);
    }

    @Test
    void rejectToDlqLateEventIsNotProcessable() {
        EventTimeContext context = context(EventTimeContext.LateEventBehavior.REJECT_TO_DLQ);

        LateEventDecision decision = TimePolicyEvaluator.evaluateLateEvent(
            context,
            Instant.parse("2026-05-23T00:07:59Z"));

        assertThat(decision.late()).isTrue();
        assertThat(decision.processable()).isFalse();
        assertThat(decision.behavior()).isEqualTo(EventTimeContext.LateEventBehavior.REJECT_TO_DLQ);
    }

    @Test
    void detectsExpiredPartialMatchState() {
        EventTimeContext context = new EventTimeContext(
            EventTimeContext.TimeMode.EVENT_TIME,
            Optional.empty(),
            Duration.ZERO,
            EventTimeContext.LateEventBehavior.INCORPORATE,
            Optional.of(Instant.parse("2026-05-23T00:10:00Z")));

        assertThat(TimePolicyEvaluator.partialMatchExpired(
            context,
            Instant.parse("2026-05-23T00:10:00Z"))).isTrue();
    }

    @Test
    void recordedAgentOutputReplayRequiresRecordedOutputs() {
        ReplayContext replayContext = new ReplayContext(
            ReplayContext.ReplayMode.RECORDED_AGENT_OUTPUT,
            Optional.of("replay-1"),
            Optional.empty(),
            Optional.empty(),
            Map.of("execution-1", Map.of("decision", "HIGH_RISK")));

        assertThat(TimePolicyEvaluator.replayUsesRecordedAgentOutput(replayContext)).isTrue();
    }

    private static EventTimeContext context(EventTimeContext.LateEventBehavior behavior) {
        return new EventTimeContext(
            EventTimeContext.TimeMode.EVENT_TIME,
            Optional.of(Instant.parse("2026-05-23T00:10:00Z")),
            Duration.ofMinutes(2),
            behavior,
            Optional.empty());
    }
}
