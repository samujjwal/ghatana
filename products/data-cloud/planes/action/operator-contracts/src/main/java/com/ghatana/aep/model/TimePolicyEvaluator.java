package com.ghatana.aep.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Evaluates event time, watermark, and replay-safe lateness decisions.
 *
 * @doc.type class
 * @doc.purpose Applies AEP time semantics for late-event handling and partial-match expiry
 * @doc.layer product
 * @doc.pattern Policy
 */
public final class TimePolicyEvaluator {

    private static final double LATE_EVENT_CONFIDENCE_PENALTY = 0.25;

    private TimePolicyEvaluator() {}

    public static LateEventDecision evaluateLateEvent(EventTimeContext context, Instant eventTime) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(eventTime, "eventTime");
        if (context.watermark().isEmpty()) {
            return new LateEventDecision(false, true, EventTimeContext.LateEventBehavior.INCORPORATE,
                UncertaintyAdjustment.none());
        }

        Instant lateBefore = context.watermark().get().minus(context.allowedLateness());
        boolean late = eventTime.isBefore(lateBefore);
        if (!late) {
            return new LateEventDecision(false, true, EventTimeContext.LateEventBehavior.INCORPORATE,
                UncertaintyAdjustment.none());
        }

        boolean processable = context.lateEventBehavior() != EventTimeContext.LateEventBehavior.REJECT_TO_DLQ;
        UncertaintyAdjustment adjustment = context.lateEventBehavior() == EventTimeContext.LateEventBehavior.DEGRADE_CONFIDENCE
            ? new UncertaintyAdjustment(LATE_EVENT_CONFIDENCE_PENALTY, 0.0, 0.0)
            : UncertaintyAdjustment.none();
        return new LateEventDecision(true, processable, context.lateEventBehavior(), adjustment);
    }

    public static boolean partialMatchExpired(EventTimeContext context, Instant now) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(now, "now");
        return context.partialMatchExpiresAt()
            .map(expiry -> !now.isBefore(expiry))
            .orElse(false);
    }

    public static boolean replayUsesRecordedAgentOutput(ReplayContext replayContext) {
        Objects.requireNonNull(replayContext, "replayContext");
        return replayContext.mode() == ReplayContext.ReplayMode.RECORDED_AGENT_OUTPUT
            && !replayContext.recordedOutputs().isEmpty();
    }
}
