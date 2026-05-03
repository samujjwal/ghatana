package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StreamingCapability}.
 */
@DisplayName("StreamingCapability")
class StreamingCapabilityTest {

    @Test
    @DisplayName("SubscriptionOptions builder creates options")
    void subscriptionOptionsBuilder_createsOptions() {
        StreamingCapability.SubscriptionOptions options = StreamingCapability.SubscriptionOptions.builder()
                .fromOffset(100L)
                .partitions(List.of(0, 1, 2))
                .streamName("events")
                .batchSize(200)
                .pollInterval(Duration.ofMillis(50))
                .build();

        assertThat(options.fromOffset()).isEqualTo(100L);
        assertThat(options.partitions()).isEqualTo(List.of(0, 1, 2));
        assertThat(options.streamName()).isEqualTo("events");
        assertThat(options.batchSize()).isEqualTo(200);
        assertThat(options.pollInterval()).isEqualTo(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("SubscriptionOptions builder sets defaults")
    void subscriptionOptionsBuilder_setsDefaults() {
        StreamingCapability.SubscriptionOptions options = StreamingCapability.SubscriptionOptions.builder()
                .build();

        assertThat(options.batchSize()).isEqualTo(100);
        assertThat(options.pollInterval()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("TailOptions builder creates options")
    void tailOptionsBuilder_createsOptions() {
        StreamingCapability.TailOptions options = StreamingCapability.TailOptions.builder()
                .fromBeginning()
                .follow(true)
                .batchSize(500)
                .build();

        assertThat(options.fromBeginning()).isTrue();
        assertThat(options.fromEnd()).isFalse();
        assertThat(options.follow()).isTrue();
        assertThat(options.batchSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("TailOptions builder sets defaults")
    void tailOptionsBuilder_setsDefaults() {
        StreamingCapability.TailOptions options = StreamingCapability.TailOptions.builder()
                .build();

        assertThat(options.fromBeginning()).isFalse();
        assertThat(options.fromEnd()).isTrue();
        assertThat(options.follow()).isTrue();
        assertThat(options.batchSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("TailOptions fromOffset sets offset")
    void tailOptions_fromOffset_setsOffset() {
        StreamingCapability.TailOptions options = StreamingCapability.TailOptions.builder()
                .fromOffset(500L)
                .build();

        assertThat(options.fromOffset()).isEqualTo(500L);
        assertThat(options.fromBeginning()).isFalse();
        assertThat(options.fromEnd()).isFalse();
    }

    @Test
    @DisplayName("TailOptions fromTimestamp sets timestamp")
    void tailOptions_fromTimestamp_setsTimestamp() {
        Instant timestamp = Instant.now();
        StreamingCapability.TailOptions options = StreamingCapability.TailOptions.builder()
                .fromTimestamp(timestamp)
                .build();

        assertThat(options.fromTimestamp()).isEqualTo(timestamp);
        assertThat(options.fromBeginning()).isFalse();
        assertThat(options.fromEnd()).isFalse();
    }
}
