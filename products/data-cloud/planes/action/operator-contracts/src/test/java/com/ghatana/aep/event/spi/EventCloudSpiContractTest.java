package com.ghatana.aep.event.spi;

import com.ghatana.aep.model.CanonicalEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCloudSpiContractTest {

    @Test
    void offsetRejectsNegativePositions() {
        assertThatThrownBy(() -> new EventCloudOffset("tenant-a", "partition-0", -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("offset");
    }

    @Test
    void recordRequiresOffsetAndEvent() {
        CanonicalEvent event = new CanonicalEvent(
            "event-1",
            "tenant-a",
            "deploy.started",
            "1.0.0",
            Instant.parse("2026-05-23T00:00:00Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            List.of(),
            "corr-1",
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            "idem-1");

        EventCloudRecord record = new EventCloudRecord(new EventCloudOffset("tenant-a", "partition-0", 1), event);

        assertThat(record.event()).isEqualTo(event);
        assertThat(record.offset().offset()).isEqualTo(1);
    }
}
