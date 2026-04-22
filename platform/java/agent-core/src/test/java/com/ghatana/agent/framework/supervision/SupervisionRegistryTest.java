/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.supervision;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SupervisionContract}, {@link SupervisionStrategy}, and
 * {@link InMemorySupervisionRegistry}.
 *
 * @doc.type class
 * @doc.purpose Tests for P8-T6: SupervisionContract + registry
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("SupervisionRegistry (P8-T6) [GH-90000]")
class SupervisionRegistryTest {

    private InMemorySupervisionRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new InMemorySupervisionRegistry(); // GH-90000
    }

    private static SupervisionContract contract( // GH-90000
            String supervisorId, List<String> subs, SupervisionStrategy strategy) {
        return SupervisionContract.of(supervisorId, subs, "tenant-1", strategy); // GH-90000
    }

    // ─── SupervisionContract ─────────────────────────────────────────────────

    @Nested
    @DisplayName("SupervisionContract [GH-90000]")
    class ContractTests {

        @Test
        @DisplayName("blank supervisorAgentId is rejected [GH-90000]")
        void blankSupervisorRejected() { // GH-90000
            assertThatThrownBy(() -> new SupervisionContract( // GH-90000
                    "", List.of("sub-1 [GH-90000]"), "t1", SupervisionStrategy.RESTART_ONE, 3))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("supervisorAgentId [GH-90000]");
        }

        @Test
        @DisplayName("maxRestarts < -1 is rejected [GH-90000]")
        void invalidMaxRestarts() { // GH-90000
            assertThatThrownBy(() -> new SupervisionContract( // GH-90000
                    "sup", List.of("sub-1 [GH-90000]"), "t1", SupervisionStrategy.RESTART_ONE, -2))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("maxRestarts [GH-90000]");
        }

        @Test
        @DisplayName("isUnlimitedRestarts() is true for maxRestarts=-1 [GH-90000]")
        void unlimitedRestartsFlag() { // GH-90000
            SupervisionContract c = new SupervisionContract( // GH-90000
                    "sup", List.of("sub-1 [GH-90000]"), "t1", SupervisionStrategy.ESCALATE, -1);
            assertThat(c.isUnlimitedRestarts()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("subordinateAgentIds is immutable [GH-90000]")
        void subordinatesImmutable() { // GH-90000
            SupervisionContract c = contract("sup", List.of("sub-1 [GH-90000]"), SupervisionStrategy.STOP_ONE);
            assertThatThrownBy(() -> c.subordinateAgentIds().add("sub-2 [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ─── InMemorySupervisionRegistry ─────────────────────────────────────────

    @Nested
    @DisplayName("InMemorySupervisionRegistry [GH-90000]")
    class RegistryTests {

        @Test
        @DisplayName("register then findBySupervisor returns contract [GH-90000]")
        void registerThenFind() { // GH-90000
            SupervisionContract c = contract("sup-A", List.of("sub-1", "sub-2"), // GH-90000
                    SupervisionStrategy.RESTART_ONE);
            registry.register(c); // GH-90000
            Optional<SupervisionContract> found = registry.findBySupervisor("sup-A [GH-90000]");
            assertThat(found).isPresent().contains(c); // GH-90000
        }

        @Test
        @DisplayName("findBySupervisor returns empty for unknown supervisor [GH-90000]")
        void unknownSupervisorReturnsEmpty() { // GH-90000
            assertThat(registry.findBySupervisor("no-such [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("findBySubordinate finds contract containing the subordinate [GH-90000]")
        void findBySubordinate() { // GH-90000
            registry.register(contract("sup-B", List.of("sub-X", "sub-Y"), // GH-90000
                    SupervisionStrategy.STOP_ALL));
            Optional<SupervisionContract> found = registry.findBySubordinate("sub-X [GH-90000]");
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().supervisorAgentId()).isEqualTo("sup-B [GH-90000]");
        }

        @Test
        @DisplayName("findBySubordinate returns empty when agent not subordinate of anyone [GH-90000]")
        void notASubordinateReturnsEmpty() { // GH-90000
            assertThat(registry.findBySubordinate("orphan [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("later register replaces earlier contract for same supervisor [GH-90000]")
        void registrationReplaces() { // GH-90000
            registry.register(contract("sup-C", List.of("sub-1 [GH-90000]"), SupervisionStrategy.RESTART_ONE));
            SupervisionContract replacement = contract("sup-C", List.of("sub-1", "sub-2"), // GH-90000
                    SupervisionStrategy.ESCALATE);
            registry.register(replacement); // GH-90000
            Optional<SupervisionContract> found = registry.findBySupervisor("sup-C [GH-90000]");
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().strategy()).isEqualTo(SupervisionStrategy.ESCALATE); // GH-90000
            assertThat(found.get().subordinateAgentIds()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("deregister removes contract [GH-90000]")
        void deregisterRemovesContract() { // GH-90000
            registry.register(contract("sup-D", List.of("sub-1 [GH-90000]"), SupervisionStrategy.STOP_ONE));
            registry.deregister("sup-D [GH-90000]");
            assertThat(registry.findBySupervisor("sup-D [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("all 6 strategies are distinct and enumerable [GH-90000]")
        void allStrategiesEnumerable() { // GH-90000
            assertThat(SupervisionStrategy.values()).hasSize(6); // GH-90000
        }
    }
}
