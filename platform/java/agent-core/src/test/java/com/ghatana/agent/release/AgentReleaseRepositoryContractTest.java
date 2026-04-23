/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * the implementation via {@link #createRepository()}. The default test class uses // GH-90000
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

    protected AgentReleaseRepository createRepository() { // GH-90000
        return new InMemoryAgentReleaseRepository(); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        repo = createRepository(); // GH-90000
        if (repo instanceof InMemoryAgentReleaseRepository inMem) { // GH-90000
            inMem.clear(); // GH-90000
        }
    }

    private AgentRelease minimalRelease(String agentId, String version) { // GH-90000
        return new AgentReleaseBuilder() // GH-90000
                .agentId(agentId) // GH-90000
                .releaseVersion(version) // GH-90000
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // save + findById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {

        @Test
        @DisplayName("saved release is found by its ID")
        void savedReleaseFoundById() { // GH-90000
            AgentRelease release = minimalRelease("agent-001", "1.0.0"); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            Optional<AgentRelease> found = runPromise(() -> repo.findById(release.agentReleaseId())); // GH-90000

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().agentReleaseId()).isEqualTo(release.agentReleaseId()); // GH-90000
        }

        @Test
        @DisplayName("findById returns empty for unknown ID")
        void findByIdUnknown() { // GH-90000
            Optional<AgentRelease> found = runPromise(() -> repo.findById("unknown-id"));
            assertThat(found).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("save is idempotent — re-saving same release does not duplicate")
        void saveIdempotent() { // GH-90000
            AgentRelease release = minimalRelease("agent-001", "1.0.0"); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            List<AgentRelease> byAgent = runPromise(() -> repo.findByAgentId("agent-001"));
            assertThat(byAgent).hasSize(1); // GH-90000
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
        void returnsAllReleases() { // GH-90000
            AgentRelease r1 = minimalRelease("agent-A", "1.0.0"); // GH-90000
            AgentRelease r2 = minimalRelease("agent-A", "2.0.0"); // GH-90000
            AgentRelease r3 = minimalRelease("agent-B", "1.0.0"); // GH-90000
            runPromise(() -> repo.save(r1)); // GH-90000
            runPromise(() -> repo.save(r2)); // GH-90000
            runPromise(() -> repo.save(r3)); // GH-90000

            List<AgentRelease> found = runPromise(() -> repo.findByAgentId("agent-A"));

            assertThat(found).hasSize(2) // GH-90000
                    .allMatch(r -> "agent-A".equals(r.agentId())); // GH-90000
        }

        @Test
        @DisplayName("returns empty list for unknown agent ID")
        void unknownAgentEmpty() { // GH-90000
            List<AgentRelease> found = runPromise(() -> repo.findByAgentId("no-such-agent"));
            assertThat(found).isEmpty(); // GH-90000
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
        void returnsActiveRelease() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.ACTIVE) // GH-90000
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .build(); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            Optional<AgentRelease> active = runPromise(() -> // GH-90000
                    repo.findActiveRelease("agent-001", "tenant-1")); // GH-90000

            assertThat(active).isPresent(); // GH-90000
            assertThat(active.get().state()).isEqualTo(AgentReleaseState.ACTIVE); // GH-90000
        }

        @Test
        @DisplayName("returns empty when no ACTIVE release exists")
        void emptyWhenNoneActive() { // GH-90000
            AgentRelease draft = minimalRelease("agent-001", "1.0.0"); // GH-90000
            runPromise(() -> repo.save(draft)); // GH-90000

            Optional<AgentRelease> active = runPromise(() -> // GH-90000
                    repo.findActiveRelease("agent-001", "tenant-1")); // GH-90000

            assertThat(active).isEmpty(); // GH-90000
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
        void validTransitionUpdatesState() { // GH-90000
            AgentRelease release = minimalRelease("agent-001", "1.0.0"); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            AgentRelease updated = runPromise(() -> // GH-90000
                    repo.transition(release.agentReleaseId(), AgentReleaseState.VALIDATED, "admin@ghatana.ai")); // GH-90000

            assertThat(updated.state()).isEqualTo(AgentReleaseState.VALIDATED); // GH-90000
        }

        @Test
        @DisplayName("invalid transition propagates as exception")
        void invalidTransitionFails() { // GH-90000
            AgentRelease release = minimalRelease("agent-001", "1.0.0"); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    repo.transition(release.agentReleaseId(), AgentReleaseState.ACTIVE, "admin@ghatana.ai"))) // GH-90000
                    .hasMessageContaining("DRAFT")
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("transition for unknown release ID fails")
        void unknownReleaseFails() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    repo.transition("no-such-id", AgentReleaseState.VALIDATED, "admin@ghatana.ai"))) // GH-90000
                    .hasMessageContaining("no-such-id");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByState
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByState returns only releases in the given state")
    void findByState() { // GH-90000
        AgentRelease draft1 = minimalRelease("agent-001", "1.0.0"); // GH-90000
        AgentRelease draft2 = minimalRelease("agent-002", "1.0.0"); // GH-90000
        AgentRelease active = new AgentReleaseBuilder() // GH-90000
                .agentId("agent-003")
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.ACTIVE)                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")                .build();
        runPromise(() -> repo.save(draft1)); // GH-90000
        runPromise(() -> repo.save(draft2)); // GH-90000
        runPromise(() -> repo.save(active)); // GH-90000

        List<AgentRelease> drafts = runPromise(() -> repo.findByState(AgentReleaseState.DRAFT)); // GH-90000

        assertThat(drafts).hasSize(2).allMatch(r -> r.state() == AgentReleaseState.DRAFT); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findGoverningRelease
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findGoverningRelease")
    class FindGoverningRelease {

        @Test
        @DisplayName("returns ACTIVE release as governing release")
        void returnsActiveRelease() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-gov")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.ACTIVE) // GH-90000
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .build(); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            Optional<AgentRelease> governing = runPromise(() -> // GH-90000
                    repo.findGoverningRelease("agent-gov", "tenant-1")); // GH-90000

            assertThat(governing).isPresent(); // GH-90000
            assertThat(governing.get().state()).isEqualTo(AgentReleaseState.ACTIVE); // GH-90000
        }

        @Test
        @DisplayName("returns BLOCKED release as governing release")
        void returnsBlockedRelease() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-gov")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.BLOCKED) // GH-90000
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .build(); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            Optional<AgentRelease> governing = runPromise(() -> // GH-90000
                    repo.findGoverningRelease("agent-gov", "tenant-1")); // GH-90000

            assertThat(governing).isPresent(); // GH-90000
            assertThat(governing.get().state()).isEqualTo(AgentReleaseState.BLOCKED); // GH-90000
        }

        @Test
        @DisplayName("returns empty for DRAFT release (not a governing state)")
        void emptyForDraftRelease() { // GH-90000
            AgentRelease draft = minimalRelease("agent-gov-draft", "1.0.0"); // GH-90000
            runPromise(() -> repo.save(draft)); // GH-90000

            Optional<AgentRelease> governing = runPromise(() -> // GH-90000
                    repo.findGoverningRelease("agent-gov-draft", "tenant-1")); // GH-90000

            assertThat(governing).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty when no release exists for agent")
        void emptyWhenNoRelease() { // GH-90000
            Optional<AgentRelease> governing = runPromise(() -> // GH-90000
                    repo.findGoverningRelease("no-such-agent", "tenant-1")); // GH-90000

            assertThat(governing).isEmpty(); // GH-90000
        }
    }
}
