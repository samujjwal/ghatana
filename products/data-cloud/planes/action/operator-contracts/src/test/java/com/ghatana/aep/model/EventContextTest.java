package com.ghatana.aep.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventContextTest {

    @Test
    void requiresTenantTimeUncertaintyAndReplay() {
        assertThatThrownBy(() -> new EventContext<>(
            "",
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            time(),
            UncertaintyContext.certain(),
            replay(),
            Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void normalizesOptionalStateAndBindings() {
        EventContext<String> context = new EventContext<>(
            "tenant-a",
            null,
            null,
            null,
            null,
            time(),
            UncertaintyContext.certain(),
            replay(),
            Optional.of("input"));

        assertThat(context.events()).isEmpty();
        assertThat(context.partialMatch()).isEmpty();
        assertThat(context.match()).isEmpty();
        assertThat(context.bindings()).isEmpty();
        assertThat(context.input()).contains("input");
    }

    @Test
    void uncertaintyRejectsOutOfRangeConfidence() {
        assertThatThrownBy(() -> new UncertaintyContext(
            1.1,
            1.0,
            1.0,
            1.0,
            1.0,
            1.0,
            1.0,
            1.0,
            1.0,
            Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventDetectionConfidence");
    }

    private static EventTimeContext time() {
        return new EventTimeContext(
            EventTimeContext.TimeMode.EVENT_TIME,
            Optional.empty(),
            Duration.ZERO,
            EventTimeContext.LateEventBehavior.REJECT_TO_DLQ,
            Optional.empty());
    }

    private static ReplayContext replay() {
        return new ReplayContext(
            ReplayContext.ReplayMode.LIVE,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of());
    }
}
