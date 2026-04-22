/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EventDurabilityConfig} and related enums.
 */
@DisplayName("EventDurabilityConfig [GH-90000]")
class EventDurabilityConfigTest {

    @Nested
    @DisplayName("defaults [GH-90000]")
    class Defaults {

        @Test
        void defaultDurabilityLevel() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            assertThat(cfg.getDefaultDurabilityLevel()) // GH-90000
                    .isEqualTo(EventDurabilityService.DurabilityLevel.MAJORITY_ACK); // GH-90000
        }

        @Test
        void defaultTimeout() { // GH-90000
            assertThat(new EventDurabilityConfig().getDurabilityTimeout()) // GH-90000
                    .isEqualTo(Duration.ofSeconds(30)); // GH-90000
        }

        @Test
        void defaultReplicaCount() { // GH-90000
            assertThat(new EventDurabilityConfig().getRequiredReplicaCount()).isEqualTo(2); // GH-90000
        }

        @Test
        void fsyncEnabledByDefault() { // GH-90000
            assertThat(new EventDurabilityConfig().isFsyncEnabled()).isTrue(); // GH-90000
        }

        @Test
        void defaultCheckpointInterval() { // GH-90000
            assertThat(new EventDurabilityConfig().getCheckpointIntervalMs()).isEqualTo(5000L); // GH-90000
        }

        @Test
        void defaultMaxCheckpointLag() { // GH-90000
            assertThat(new EventDurabilityConfig().getMaxCheckpointLagMs()).isEqualTo(30000L); // GH-90000
        }

        @Test
        void cdcEnabledByDefault() { // GH-90000
            assertThat(new EventDurabilityConfig().isCdcEnabled()).isTrue(); // GH-90000
        }

        @Test
        void defaultCdcBufferSize() { // GH-90000
            assertThat(new EventDurabilityConfig().getCdcBufferSize()).isEqualTo(1000); // GH-90000
        }

        @Test
        void defaultReplayBatchSize() { // GH-90000
            assertThat(new EventDurabilityConfig().getReplayBatchSize()).isEqualTo(100); // GH-90000
        }

        @Test
        void defaultMaxReplayEvents() { // GH-90000
            assertThat(new EventDurabilityConfig().getMaxReplayEvents()).isEqualTo(10000L); // GH-90000
        }
    }

    @Nested
    @DisplayName("setters [GH-90000]")
    class Setters {

        @Test
        void setDurabilityLevel() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setDefaultDurabilityLevel(EventDurabilityService.DurabilityLevel.ALL_ACK); // GH-90000
            assertThat(cfg.getDefaultDurabilityLevel()) // GH-90000
                    .isEqualTo(EventDurabilityService.DurabilityLevel.ALL_ACK); // GH-90000
        }

        @Test
        void setDurabilityTimeout() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setDurabilityTimeout(Duration.ofSeconds(60)); // GH-90000
            assertThat(cfg.getDurabilityTimeout()).isEqualTo(Duration.ofSeconds(60)); // GH-90000
        }

        @Test
        void setReplicaCount() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setRequiredReplicaCount(3); // GH-90000
            assertThat(cfg.getRequiredReplicaCount()).isEqualTo(3); // GH-90000
        }

        @Test
        void setFsyncEnabled() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setFsyncEnabled(false); // GH-90000
            assertThat(cfg.isFsyncEnabled()).isFalse(); // GH-90000
        }

        @Test
        void setCheckpointInterval() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setCheckpointIntervalMs(1000L); // GH-90000
            assertThat(cfg.getCheckpointIntervalMs()).isEqualTo(1000L); // GH-90000
        }

        @Test
        void setMaxCheckpointLag() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setMaxCheckpointLagMs(60000L); // GH-90000
            assertThat(cfg.getMaxCheckpointLagMs()).isEqualTo(60000L); // GH-90000
        }

        @Test
        void setCdcEnabled() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setCdcEnabled(false); // GH-90000
            assertThat(cfg.isCdcEnabled()).isFalse(); // GH-90000
        }

        @Test
        void setCdcBufferSize() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setCdcBufferSize(500); // GH-90000
            assertThat(cfg.getCdcBufferSize()).isEqualTo(500); // GH-90000
        }

        @Test
        void setReplayBatchSize() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setReplayBatchSize(200); // GH-90000
            assertThat(cfg.getReplayBatchSize()).isEqualTo(200); // GH-90000
        }

        @Test
        void setMaxReplayEvents() { // GH-90000
            EventDurabilityConfig cfg = new EventDurabilityConfig(); // GH-90000
            cfg.setMaxReplayEvents(50000L); // GH-90000
            assertThat(cfg.getMaxReplayEvents()).isEqualTo(50000L); // GH-90000
        }
    }

    @Nested
    @DisplayName("DurabilityLevel enum [GH-90000]")
    class DurabilityLevelEnum {

        @Test
        void allValuesPresent() { // GH-90000
            assertThat(EventDurabilityService.DurabilityLevel.values()).containsExactlyInAnyOrder( // GH-90000
                    EventDurabilityService.DurabilityLevel.NONE,
                    EventDurabilityService.DurabilityLevel.LEADER_ACK,
                    EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                    EventDurabilityService.DurabilityLevel.ALL_ACK,
                    EventDurabilityService.DurabilityLevel.FSYNC_ACK
            );
        }

        @Test
        void valueOfByName() { // GH-90000
            assertThat(EventDurabilityService.DurabilityLevel.valueOf("NONE [GH-90000]"))
                    .isSameAs(EventDurabilityService.DurabilityLevel.NONE); // GH-90000
        }
    }
}
