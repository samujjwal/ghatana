/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.signal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LearningSignal value type")
class LearningSignalTest {

    @Nested
    @DisplayName("factory")
    class Factory {

        @Test
        @DisplayName("generates a non-blank signalId")
        void generatesId() {
            var s = LearningSignal.of("skill.success", "agent-1", "tenant-1", "corr-1", null);
            assertThat(s.signalId()).isNotBlank();
        }

        @Test
        @DisplayName("sets all fields including emittedAt")
        void setsAllFields() {
            var s = LearningSignal.of("tool.failure", "agent-2", "t2", "c2", Map.of("tool", "search"));
            assertThat(s.signalType()).isEqualTo("tool.failure");
            assertThat(s.sourceAgentId()).isEqualTo("agent-2");
            assertThat(s.tenantId()).isEqualTo("t2");
            assertThat(s.correlationId()).isEqualTo("c2");
            assertThat(s.payload()).containsEntry("tool", "search");
            assertThat(s.emittedAt()).isNotNull();
        }

        @Test
        @DisplayName("null payload is treated as empty map")
        void nullPayloadIsEmpty() {
            var s = LearningSignal.of("sig", "a", "t", "c", null);
            assertThat(s.payload()).isEmpty();
        }

        @Test
        @DisplayName("each call generates a unique signalId")
        void uniqueIds() {
            var s1 = LearningSignal.of("x", "a", "t", "c", null);
            var s2 = LearningSignal.of("x", "a", "t", "c", null);
            assertThat(s1.signalId()).isNotEqualTo(s2.signalId());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("blank signalType throws")
        void blankSignalType() {
            assertThatThrownBy(() -> LearningSignal.of("  ", "a", "t", "c", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("signalType");
        }

        @Test
        @DisplayName("payload is immutable")
        void payloadImmutable() {
            var s = LearningSignal.of("sig", "a", "t", "c", Map.of("k", "v"));
            assertThatThrownBy(() -> s.payload().put("x", "y"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("LearningSignalRouter.noOp")
    class NoOpRouter {

        @Test
        @DisplayName("noOp router completes without error")
        void noOpCompletes() throws Exception {
            var router = LearningSignalRouter.noOp();
            var signal = LearningSignal.of("sig", "a", "t", "c", null);
            var promise = router.route(signal);
            // In a synchronous context the promise is already complete
            assertThat(promise.isComplete()).isTrue();
        }
    }
}
