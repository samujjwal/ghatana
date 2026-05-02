/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.agent.release;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Abstract contract test for {@link AgentReleaseRepository}.
 *
 * <p>Any implementation should be testable by subclassing this test and providing
 * the implementation via {@link #createRepository()}. The default test class uses 
 * {@link InMemoryAgentReleaseRepository} as the vehicle.
 *
 * @doc.type class
 * @doc.purpose Contract tests for AgentReleaseRepository SPI
 * @doc.layer platform
 * @doc.pattern ContractTest
 */
@DisplayName("AgentReleaseRepository contract")
class AgentReleaseRepositoryContractTest extends EventloopTestBase {

    private AgentReleaseRepository repo;

    protected AgentReleaseRepository createRepository() { 
        return new InMemoryAgentReleaseRepository(); 
    }

    @BeforeEach
    void setUp() { 
        repo = createRepository(); 
        if (repo instanceof InMemoryAgentReleaseRepository inMem) { 
            inMem.clear(); 
        }
    }

    private AgentRelease minimalRelease(String agentId, String version) { 
        return new AgentReleaseBuilder() 
                .agentId(agentId) 
                .releaseVersion(version) 
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // save + findById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {

        @Test
        @DisplayName("saved release is found by its ID")
        void savedReleaseFoundById() { 
            AgentRelease release = minimalRelease("agent-001", "1.0.0"); 
            runPromise(() -> repo.save(release)); 

            Optional<AgentRelease> found = runPromise(() -> repo.findById(release.agentReleaseId())); 

            assertThat(found).isPresent(); 
            assertThat(found.get().agentReleaseId()).isEqualTo(release.agentReleaseId()); 
        }

        @Test
        @DisplayName("findById returns empty for unknown ID")
        void findByIdUnknown() { 
            Optional<AgentRelease> found = runPromise(() -> repo.findById("unknown-id"));
            assertThat(found).isEmpty(); 
        }

        @Test
        @DisplayName("save is idempotent — re-saving same release does not duplicate")
        void saveIdempotent() { 
            AgentRelease release = minimalRelease("agent-001", "1.0.0"); 
            runPromise(() -> repo.save(release)); 
            runPromise(() -> repo.save(release)); 

            List<AgentRelease> byAgent = runPromise(() -> repo.findByAgentId("agent-001"));
            assertThat(byAgent).hasSize(1); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByAgentId
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByAgentId")
    class FindByAgentId {

        @Test
        @DisplayName("returns all releases for a given agent ID")
        void returnsAllReleases() { 
            AgentRelease r1 = minimalRelease("agent-A", "1.0.0"); 
            AgentRelease r2 = minimalRelease("agent-A", "2.0.0"); 
            AgentRelease r3 = minimalRelease("agent-B", "1.0.0"); 
            runPromise(() -> repo.save(r1)); 
            runPromise(() -> repo.save(r2)); 
            runPromise(() -> repo.save(r3)); 

            List<AgentRelease> found = runPromise(() -> repo.findByAgentId("agent-A"));

            assertThat(found).hasSize(2) 
                    .allMatch(r -> "agent-A".equals(r.agentId())); 
        }

        @Test
        @DisplayName("returns empty list for unknown agent ID")
        void unknownAgentEmpty() { 
            List<AgentRelease> found = runPromise(() -> repo.findByAgentId("no-such-agent"));
            assertThat(found).isEmpty(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findActiveRelease
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findActiveRelease")
    class FindActiveRelease {

        @Test
        @DisplayName("returns ACTIVE release when one exists")
        void returnsActiveRelease() { 
            AgentRelease release = new AgentReleaseBuilder() 
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.ACTIVE) 
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .build(); 
            runPromise(() -> repo.save(release)); 

            Optional<AgentRelease> active = runPromise(() -> 
                    repo.findActiveRelease("agent-001", "tenant-1")); 

            assertThat(active).isPresent(); 
            assertThat(active.get().state()).isEqualTo(AgentReleaseState.ACTIVE); 
        }

        @Test
        @DisplayName("returns empty when no ACTIVE release exists")
        void emptyWhenNoneActive() { 
            AgentRelease draft = minimalRelease("agent-001", "1.0.0"); 
            runPromise(() -> repo.save(draft)); 

            Optional<AgentRelease> active = runPromise(() -> 
                    repo.findActiveRelease("agent-001", "tenant-1")); 

            assertThat(active).isEmpty(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // transition
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("transition")
    class Transition {

        @Test
        @DisplayName("valid transition updates state")
        void validTransitionUpdatesState() { 
            AgentRelease release = minimalRelease("agent-001", "1.0.0"); 
            runPromise(() -> repo.save(release)); 

            AgentRelease updated = runPromise(() -> 
                    repo.transition(release.agentReleaseId(), AgentReleaseState.VALIDATED, "admin@ghatana.ai")); 

            assertThat(updated.state()).isEqualTo(AgentReleaseState.VALIDATED); 
        }

        @Test
        @DisplayName("invalid transition propagates as exception")
        void invalidTransitionFails() { 
            AgentRelease release = minimalRelease("agent-001", "1.0.0"); 
            runPromise(() -> repo.save(release)); 

            assertThatThrownBy(() -> runPromise(() -> 
                    repo.transition(release.agentReleaseId(), AgentReleaseState.ACTIVE, "admin@ghatana.ai"))) 
                    .hasMessageContaining("DRAFT")
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("transition for unknown release ID fails")
        void unknownReleaseFails() { 
            assertThatThrownBy(() -> runPromise(() -> 
                    repo.transition("no-such-id", AgentReleaseState.VALIDATED, "admin@ghatana.ai"))) 
                    .hasMessageContaining("no-such-id");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByState
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByState returns only releases in the given state")
    void findByState() { 
        AgentRelease draft1 = minimalRelease("agent-001", "1.0.0"); 
        AgentRelease draft2 = minimalRelease("agent-002", "1.0.0"); 
        AgentRelease active = new AgentReleaseBuilder() 
                .agentId("agent-003")
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.ACTIVE)                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")                .build();
        runPromise(() -> repo.save(draft1)); 
        runPromise(() -> repo.save(draft2)); 
        runPromise(() -> repo.save(active)); 

        List<AgentRelease> drafts = runPromise(() -> repo.findByState(AgentReleaseState.DRAFT)); 

        assertThat(drafts).hasSize(2).allMatch(r -> r.state() == AgentReleaseState.DRAFT); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findGoverningRelease
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findGoverningRelease")
    class FindGoverningRelease {

        @Test
        @DisplayName("returns ACTIVE release as governing release")
        void returnsActiveRelease() { 
            AgentRelease release = new AgentReleaseBuilder() 
                    .agentId("agent-gov")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.ACTIVE) 
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .build(); 
            runPromise(() -> repo.save(release)); 

            Optional<AgentRelease> governing = runPromise(() -> 
                    repo.findGoverningRelease("agent-gov", "tenant-1")); 

            assertThat(governing).isPresent(); 
            assertThat(governing.get().state()).isEqualTo(AgentReleaseState.ACTIVE); 
        }

        @Test
        @DisplayName("returns BLOCKED release as governing release")
        void returnsBlockedRelease() { 
            AgentRelease release = new AgentReleaseBuilder() 
                    .agentId("agent-gov")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.BLOCKED) 
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .build(); 
            runPromise(() -> repo.save(release)); 

            Optional<AgentRelease> governing = runPromise(() -> 
                    repo.findGoverningRelease("agent-gov", "tenant-1")); 

            assertThat(governing).isPresent(); 
            assertThat(governing.get().state()).isEqualTo(AgentReleaseState.BLOCKED); 
        }

        @Test
        @DisplayName("returns empty for DRAFT release (not a governing state)")
        void emptyForDraftRelease() { 
            AgentRelease draft = minimalRelease("agent-gov-draft", "1.0.0"); 
            runPromise(() -> repo.save(draft)); 

            Optional<AgentRelease> governing = runPromise(() -> 
                    repo.findGoverningRelease("agent-gov-draft", "tenant-1")); 

            assertThat(governing).isEmpty(); 
        }

        @Test
        @DisplayName("returns empty when no release exists for agent")
        void emptyWhenNoRelease() { 
            Optional<AgentRelease> governing = runPromise(() -> 
                    repo.findGoverningRelease("no-such-agent", "tenant-1")); 

            assertThat(governing).isEmpty(); 
        }
    }
}
