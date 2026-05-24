package com.ghatana.aep.learning;

import com.ghatana.aep.model.CanonicalEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelatedEventGroupMinerTest {

    @Test
    void discoversCorrelatedEventGroupsInsideWindow() {
        CorrelatedEventGroupMiner miner = new CorrelatedEventGroupMiner();

        List<CorrelatedEventGroup> groups = miner.mine(List.of(
            event("event-1", "deploy.started", "corr-a", "2026-05-23T00:00:00Z"),
            event("event-2", "service.error_rate_elevated", "corr-a", "2026-05-23T00:05:00Z"),
            event("event-3", "pager.alert_created", "corr-a", "2026-05-23T00:07:00Z"),
            event("event-4", "noise.single", "corr-b", "2026-05-23T00:08:00Z")),
            Duration.ofMinutes(30),
            2);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).correlationId()).isEqualTo("corr-a");
        assertThat(groups.get(0).events()).extracting(CanonicalEvent::eventType)
            .containsExactly("deploy.started", "service.error_rate_elevated", "pager.alert_created");
        assertThat(groups.get(0).support()).isEqualTo(0.75);
        assertThat(groups.get(0).metadata()).containsKey("eventTypes");
        assertThat(groups.get(0).searchSpaceReductionRatio()).isGreaterThan(0.0);
    }

    @Test
    void ignoresGroupsOutsideWindowOrBelowMinimumSize() {
        CorrelatedEventGroupMiner miner = new CorrelatedEventGroupMiner();

        List<CorrelatedEventGroup> groups = miner.mine(List.of(
            event("event-1", "deploy.started", "corr-a", "2026-05-23T00:00:00Z"),
            event("event-2", "service.error_rate_elevated", "corr-a", "2026-05-23T02:00:00Z"),
            event("event-3", "noise.single", "corr-b", "2026-05-23T00:08:00Z")),
            Duration.ofMinutes(30),
            2);

        assertThat(groups).isEmpty();
    }

    @Test
    void rejectsInvalidMiningPolicy() {
        CorrelatedEventGroupMiner miner = new CorrelatedEventGroupMiner();

        assertThatThrownBy(() -> miner.mine(List.of(), Duration.ZERO, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxWindow");
        assertThatThrownBy(() -> miner.mine(List.of(), Duration.ofMinutes(1), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("minimumGroupSize");
    }

    private static CanonicalEvent event(String eventId, String eventType, String correlationId, String eventTime) {
        return new CanonicalEvent(
            eventId,
            "tenant-a",
            eventType,
            "1.0.0",
            Instant.parse(eventTime),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of("system", "test"),
            List.of(),
            correlationId,
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            eventId + "-idempotency");
    }
}
