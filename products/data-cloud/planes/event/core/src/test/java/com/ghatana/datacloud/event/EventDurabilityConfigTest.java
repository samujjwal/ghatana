/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("EventDurabilityConfig")
class EventDurabilityConfigTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        void defaultDurabilityLevel() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            assertThat(cfg.getDefaultDurabilityLevel()) 
                    .isEqualTo(EventDurabilityService.DurabilityLevel.MAJORITY_ACK); 
        }

        @Test
        void defaultTimeout() { 
            assertThat(new EventDurabilityConfig().getDurabilityTimeout()) 
                    .isEqualTo(Duration.ofSeconds(30)); 
        }

        @Test
        void defaultReplicaCount() { 
            assertThat(new EventDurabilityConfig().getRequiredReplicaCount()).isEqualTo(2); 
        }

        @Test
        void fsyncEnabledByDefault() { 
            assertThat(new EventDurabilityConfig().isFsyncEnabled()).isTrue(); 
        }

        @Test
        void defaultCheckpointInterval() { 
            assertThat(new EventDurabilityConfig().getCheckpointIntervalMs()).isEqualTo(5000L); 
        }

        @Test
        void defaultMaxCheckpointLag() { 
            assertThat(new EventDurabilityConfig().getMaxCheckpointLagMs()).isEqualTo(30000L); 
        }

        @Test
        void cdcEnabledByDefault() { 
            assertThat(new EventDurabilityConfig().isCdcEnabled()).isTrue(); 
        }

        @Test
        void defaultCdcBufferSize() { 
            assertThat(new EventDurabilityConfig().getCdcBufferSize()).isEqualTo(1000); 
        }

        @Test
        void defaultReplayBatchSize() { 
            assertThat(new EventDurabilityConfig().getReplayBatchSize()).isEqualTo(100); 
        }

        @Test
        void defaultMaxReplayEvents() { 
            assertThat(new EventDurabilityConfig().getMaxReplayEvents()).isEqualTo(10000L); 
        }
    }

    @Nested
    @DisplayName("setters")
    class Setters {

        @Test
        void setDurabilityLevel() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setDefaultDurabilityLevel(EventDurabilityService.DurabilityLevel.ALL_ACK); 
            assertThat(cfg.getDefaultDurabilityLevel()) 
                    .isEqualTo(EventDurabilityService.DurabilityLevel.ALL_ACK); 
        }

        @Test
        void setDurabilityTimeout() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setDurabilityTimeout(Duration.ofSeconds(60)); 
            assertThat(cfg.getDurabilityTimeout()).isEqualTo(Duration.ofSeconds(60)); 
        }

        @Test
        void setReplicaCount() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setRequiredReplicaCount(3); 
            assertThat(cfg.getRequiredReplicaCount()).isEqualTo(3); 
        }

        @Test
        void setFsyncEnabled() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setFsyncEnabled(false); 
            assertThat(cfg.isFsyncEnabled()).isFalse(); 
        }

        @Test
        void setCheckpointInterval() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setCheckpointIntervalMs(1000L); 
            assertThat(cfg.getCheckpointIntervalMs()).isEqualTo(1000L); 
        }

        @Test
        void setMaxCheckpointLag() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setMaxCheckpointLagMs(60000L); 
            assertThat(cfg.getMaxCheckpointLagMs()).isEqualTo(60000L); 
        }

        @Test
        void setCdcEnabled() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setCdcEnabled(false); 
            assertThat(cfg.isCdcEnabled()).isFalse(); 
        }

        @Test
        void setCdcBufferSize() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setCdcBufferSize(500); 
            assertThat(cfg.getCdcBufferSize()).isEqualTo(500); 
        }

        @Test
        void setReplayBatchSize() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setReplayBatchSize(200); 
            assertThat(cfg.getReplayBatchSize()).isEqualTo(200); 
        }

        @Test
        void setMaxReplayEvents() { 
            EventDurabilityConfig cfg = new EventDurabilityConfig(); 
            cfg.setMaxReplayEvents(50000L); 
            assertThat(cfg.getMaxReplayEvents()).isEqualTo(50000L); 
        }
    }

    @Nested
    @DisplayName("DurabilityLevel enum")
    class DurabilityLevelEnum {

        @Test
        void allValuesPresent() { 
            assertThat(EventDurabilityService.DurabilityLevel.values()).containsExactlyInAnyOrder( 
                    EventDurabilityService.DurabilityLevel.NONE,
                    EventDurabilityService.DurabilityLevel.LEADER_ACK,
                    EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                    EventDurabilityService.DurabilityLevel.ALL_ACK,
                    EventDurabilityService.DurabilityLevel.FSYNC_ACK
            );
        }

        @Test
        void valueOfByName() { 
            assertThat(EventDurabilityService.DurabilityLevel.valueOf("NONE"))
                    .isSameAs(EventDurabilityService.DurabilityLevel.NONE); 
        }
    }
}
