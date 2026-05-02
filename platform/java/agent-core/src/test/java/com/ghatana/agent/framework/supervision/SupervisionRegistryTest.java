/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("SupervisionRegistry (P8-T6)")
class SupervisionRegistryTest {

    private InMemorySupervisionRegistry registry;

    @BeforeEach
    void setUp() { 
        registry = new InMemorySupervisionRegistry(); 
    }

    private static SupervisionContract contract( 
            String supervisorId, List<String> subs, SupervisionStrategy strategy) {
        return SupervisionContract.of(supervisorId, subs, "tenant-1", strategy); 
    }

    // ─── SupervisionContract ─────────────────────────────────────────────────

    @Nested
    @DisplayName("SupervisionContract")
    class ContractTests {

        @Test
        @DisplayName("blank supervisorAgentId is rejected")
        void blankSupervisorRejected() { 
            assertThatThrownBy(() -> new SupervisionContract( 
                    "", List.of("sub-1"), "t1", SupervisionStrategy.RESTART_ONE, 3))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("supervisorAgentId");
        }

        @Test
        @DisplayName("maxRestarts < -1 is rejected")
        void invalidMaxRestarts() { 
            assertThatThrownBy(() -> new SupervisionContract( 
                    "sup", List.of("sub-1"), "t1", SupervisionStrategy.RESTART_ONE, -2))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("maxRestarts");
        }

        @Test
        @DisplayName("isUnlimitedRestarts() is true for maxRestarts=-1")
        void unlimitedRestartsFlag() { 
            SupervisionContract c = new SupervisionContract( 
                    "sup", List.of("sub-1"), "t1", SupervisionStrategy.ESCALATE, -1);
            assertThat(c.isUnlimitedRestarts()).isTrue(); 
        }

        @Test
        @DisplayName("subordinateAgentIds is immutable")
        void subordinatesImmutable() { 
            SupervisionContract c = contract("sup", List.of("sub-1"), SupervisionStrategy.STOP_ONE);
            assertThatThrownBy(() -> c.subordinateAgentIds().add("sub-2"))
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }

    // ─── InMemorySupervisionRegistry ─────────────────────────────────────────

    @Nested
    @DisplayName("InMemorySupervisionRegistry")
    class RegistryTests {

        @Test
        @DisplayName("register then findBySupervisor returns contract")
        void registerThenFind() { 
            SupervisionContract c = contract("sup-A", List.of("sub-1", "sub-2"), 
                    SupervisionStrategy.RESTART_ONE);
            registry.register(c); 
            Optional<SupervisionContract> found = registry.findBySupervisor("sup-A");
            assertThat(found).isPresent().contains(c); 
        }

        @Test
        @DisplayName("findBySupervisor returns empty for unknown supervisor")
        void unknownSupervisorReturnsEmpty() { 
            assertThat(registry.findBySupervisor("no-such")).isEmpty();
        }

        @Test
        @DisplayName("findBySubordinate finds contract containing the subordinate")
        void findBySubordinate() { 
            registry.register(contract("sup-B", List.of("sub-X", "sub-Y"), 
                    SupervisionStrategy.STOP_ALL));
            Optional<SupervisionContract> found = registry.findBySubordinate("sub-X");
            assertThat(found).isPresent(); 
            assertThat(found.get().supervisorAgentId()).isEqualTo("sup-B");
        }

        @Test
        @DisplayName("findBySubordinate returns empty when agent not subordinate of anyone")
        void notASubordinateReturnsEmpty() { 
            assertThat(registry.findBySubordinate("orphan")).isEmpty();
        }

        @Test
        @DisplayName("later register replaces earlier contract for same supervisor")
        void registrationReplaces() { 
            registry.register(contract("sup-C", List.of("sub-1"), SupervisionStrategy.RESTART_ONE));
            SupervisionContract replacement = contract("sup-C", List.of("sub-1", "sub-2"), 
                    SupervisionStrategy.ESCALATE);
            registry.register(replacement); 
            Optional<SupervisionContract> found = registry.findBySupervisor("sup-C");
            assertThat(found).isPresent(); 
            assertThat(found.get().strategy()).isEqualTo(SupervisionStrategy.ESCALATE); 
            assertThat(found.get().subordinateAgentIds()).hasSize(2); 
        }

        @Test
        @DisplayName("deregister removes contract")
        void deregisterRemovesContract() { 
            registry.register(contract("sup-D", List.of("sub-1"), SupervisionStrategy.STOP_ONE));
            registry.deregister("sup-D");
            assertThat(registry.findBySupervisor("sup-D")).isEmpty();
        }

        @Test
        @DisplayName("all 6 strategies are distinct and enumerable")
        void allStrategiesEnumerable() { 
            assertThat(SupervisionStrategy.values()).hasSize(6); 
        }
    }
}
