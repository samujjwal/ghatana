/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.signal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LearningSignal value type [GH-90000]")
class LearningSignalTest {

    @Nested
    @DisplayName("factory [GH-90000]")
    class Factory {

        @Test
        @DisplayName("generates a non-blank signalId [GH-90000]")
        void generatesId() { // GH-90000
            var s = LearningSignal.of("skill.success", "agent-1", "tenant-1", "corr-1", null); // GH-90000
            assertThat(s.signalId()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("sets all fields including emittedAt [GH-90000]")
        void setsAllFields() { // GH-90000
            var s = LearningSignal.of("tool.failure", "agent-2", "t2", "c2", Map.of("tool", "search")); // GH-90000
            assertThat(s.signalType()).isEqualTo("tool.failure [GH-90000]");
            assertThat(s.sourceAgentId()).isEqualTo("agent-2 [GH-90000]");
            assertThat(s.tenantId()).isEqualTo("t2 [GH-90000]");
            assertThat(s.correlationId()).isEqualTo("c2 [GH-90000]");
            assertThat(s.payload()).containsEntry("tool", "search"); // GH-90000
            assertThat(s.emittedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("null payload is treated as empty map [GH-90000]")
        void nullPayloadIsEmpty() { // GH-90000
            var s = LearningSignal.of("sig", "a", "t", "c", null); // GH-90000
            assertThat(s.payload()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("each call generates a unique signalId [GH-90000]")
        void uniqueIds() { // GH-90000
            var s1 = LearningSignal.of("x", "a", "t", "c", null); // GH-90000
            var s2 = LearningSignal.of("x", "a", "t", "c", null); // GH-90000
            assertThat(s1.signalId()).isNotEqualTo(s2.signalId()); // GH-90000
        }
    }

    @Nested
    @DisplayName("validation [GH-90000]")
    class Validation {

        @Test
        @DisplayName("blank signalType throws [GH-90000]")
        void blankSignalType() { // GH-90000
            assertThatThrownBy(() -> LearningSignal.of("  ", "a", "t", "c", null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("signalType [GH-90000]");
        }

        @Test
        @DisplayName("payload is immutable [GH-90000]")
        void payloadImmutable() { // GH-90000
            var s = LearningSignal.of("sig", "a", "t", "c", Map.of("k", "v")); // GH-90000
            assertThatThrownBy(() -> s.payload().put("x", "y")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("LearningSignalRouter.noOp [GH-90000]")
    class NoOpRouter {

        @Test
        @DisplayName("noOp router completes without error [GH-90000]")
        void noOpCompletes() throws Exception { // GH-90000
            var router = LearningSignalRouter.noOp(); // GH-90000
            var signal = LearningSignal.of("sig", "a", "t", "c", null); // GH-90000
            var promise = router.route(signal); // GH-90000
            // In a synchronous context the promise is already complete
            assertThat(promise.isComplete()).isTrue(); // GH-90000
        }
    }
}
