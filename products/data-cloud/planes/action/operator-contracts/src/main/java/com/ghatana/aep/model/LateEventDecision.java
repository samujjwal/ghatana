package com.ghatana.aep.model;

/**
 * Runtime decision for a late or on-time event.
 *
 * @doc.type record
 * @doc.purpose Describes how an EventOperator should treat an event relative to watermark and lateness policy
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record LateEventDecision(
        boolean late,
        boolean processable,
        EventTimeContext.LateEventBehavior behavior,
        UncertaintyAdjustment uncertaintyAdjustment
) {

    public LateEventDecision {
        if (!late && !processable) {
            throw new IllegalArgumentException("on-time events must be processable");
        }
        if (behavior == null) {
            behavior = EventTimeContext.LateEventBehavior.INCORPORATE;
        }
        if (uncertaintyAdjustment == null) {
            uncertaintyAdjustment = UncertaintyAdjustment.none();
        }
    }
}
