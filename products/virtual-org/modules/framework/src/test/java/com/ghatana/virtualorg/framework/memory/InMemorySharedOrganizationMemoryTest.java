package com.ghatana.virtualorg.framework.memory;

import com.ghatana.platform.testing.activej.EventloopTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for InMemorySharedOrganizationMemory.
 *
 * @doc.type class
 * @doc.purpose Test shared organization memory
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("InMemorySharedOrganizationMemory Tests")
class InMemorySharedOrganizationMemoryTest extends EventloopTestBase {

    private InMemorySharedOrganizationMemory memory;

    @BeforeEach
    void setUp() {
        memory = new InMemorySharedOrganizationMemory();
    }

    // ========== Knowledge Tests ==========
    @Test
    @DisplayName("Should share and retrieve knowledge")
    void shouldShareAndRetrieveKnowledge() {
        // GIVEN
        runPromise(() -> memory.shareKnowledge(
                "deployment",
                "Use blue-green deployment for production",
                "devops-001"
        ));

        // WHEN
        List<SharedOrganizationMemory.Knowledge> knowledge = runPromise(()
                -> memory.getKnowledge("deployment", 10)
        );

        // THEN
        assertThat(knowledge).hasSize(1);
        assertThat(knowledge.get(0).topic()).isEqualTo("deployment");
        assertThat(knowledge.get(0).content()).contains("blue-green");
        assertThat(knowledge.get(0).contributor()).isEqualTo("devops-001");
    }

    @Test
    @DisplayName("Should search knowledge across topics")
    void shouldSearchKnowledgeAcrossTopics() {
        // GIVEN
        runPromise(() -> memory.shareKnowledge("deployment", "Use Kubernetes for orchestration", "devops-001"));
        runPromise(() -> memory.shareKnowledge("architecture", "Microservices with Kubernetes", "architect-001"));
        runPromise(() -> memory.shareKnowledge("testing", "Integration tests required", "qa-001"));

        // WHEN
        List<SharedOrganizationMemory.Knowledge> results = runPromise(()
                -> memory.searchKnowledge("Kubernetes", 10)
        );

        // THEN
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(k -> k.content().contains("Kubernetes"));
    }

    @Test
    @DisplayName("Should return knowledge ordered by timestamp")
    void shouldReturnKnowledgeOrderedByTimestamp() throws InterruptedException {
        // GIVEN
        runPromise(() -> memory.shareKnowledge("topic", "First", "agent-1"));
        Thread.sleep(10); // Ensure different timestamps
        runPromise(() -> memory.shareKnowledge("topic", "Second", "agent-2"));
        Thread.sleep(10);
        runPromise(() -> memory.shareKnowledge("topic", "Third", "agent-3"));

        // WHEN
        List<SharedOrganizationMemory.Knowledge> knowledge = runPromise(()
                -> memory.getKnowledge("topic", 10)
        );

        // THEN
        assertThat(knowledge).hasSize(3);
        assertThat(knowledge.get(0).content()).isEqualTo("Third"); // Most recent first
        assertThat(knowledge.get(2).content()).isEqualTo("First");
    }

    // ========== Decision Tests ==========
    @Test
    @DisplayName("Should record and retrieve decisions")
    void shouldRecordAndRetrieveDecisions() {
        // GIVEN
        SharedOrganizationMemory.OrgDecision decision = SharedOrganizationMemory.OrgDecision.builder()
                .topic("architecture")
                .decision("Adopt event-driven architecture")
                .rationale("Improves scalability and decoupling")
                .decidedBy("cto-001")
                .participants(List.of("architect-001", "lead-001"))
                .build();

        runPromise(() -> memory.recordOrgDecision(decision));

        // WHEN
        List<SharedOrganizationMemory.OrgDecision> decisions = runPromise(()
                -> memory.getRecentDecisions(10)
        );

        // THEN
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).topic()).isEqualTo("architecture");
        assertThat(decisions.get(0).decision()).contains("event-driven");
        assertThat(decisions.get(0).decidedBy()).isEqualTo("cto-001");
    }

    @Test
    @DisplayName("Should filter decisions by topic")
    void shouldFilterDecisionsByTopic() {
        // GIVEN
        runPromise(() -> memory.recordOrgDecision(SharedOrganizationMemory.OrgDecision.builder()
                .topic("architecture")
                .decision("Use microservices")
                .decidedBy("cto-001")
                .build()));
        runPromise(() -> memory.recordOrgDecision(SharedOrganizationMemory.OrgDecision.builder()
                .topic("hiring")
                .decision("Hire 5 engineers")
                .decidedBy("ceo-001")
                .build()));
        runPromise(() -> memory.recordOrgDecision(SharedOrganizationMemory.OrgDecision.builder()
                .topic("architecture")
                .decision("Use gRPC")
                .decidedBy("architect-001")
                .build()));

        // WHEN
        List<SharedOrganizationMemory.OrgDecision> archDecisions = runPromise(()
                -> memory.getDecisionsByTopic("architecture", 10)
        );

        // THEN
        assertThat(archDecisions).hasSize(2);
        assertThat(archDecisions).allMatch(d -> d.topic().contains("architecture"));
    }

    // ========== Active Context Tests ==========
    @Test
    @DisplayName("Should set and get active context")
    void shouldSetAndGetActiveContext() {
        // GIVEN
        runPromise(() -> memory.setActiveContext("current_sprint", "Sprint 42"));
        runPromise(() -> memory.setActiveContext("release_version", "v2.0.0"));

        // WHEN
        Optional<Object> sprint = runPromise(() -> memory.getActiveContext("current_sprint"));
        Optional<Object> version = runPromise(() -> memory.getActiveContext("release_version"));

        // THEN
        assertThat(sprint).isPresent().contains("Sprint 42");
        assertThat(version).isPresent().contains("v2.0.0");
    }

    @Test
    @DisplayName("Should get all active context")
    void shouldGetAllActiveContext() {
        // GIVEN
        runPromise(() -> memory.setActiveContext("key1", "value1"));
        runPromise(() -> memory.setActiveContext("key2", 123));
        runPromise(() -> memory.setActiveContext("key3", true));

        // WHEN
        Map<String, Object> context = runPromise(memory::getAllActiveContext);

        // THEN
        assertThat(context)
                .hasSize(3)
                .containsEntry("key1", "value1")
                .containsEntry("key2", 123)
                .containsEntry("key3", true);
    }

    @Test
    @DisplayName("Should clear active context")
    void shouldClearActiveContext() {
        // GIVEN
        runPromise(() -> memory.setActiveContext("temp_key", "temp_value"));

        // WHEN
        runPromise(() -> memory.clearActiveContext("temp_key"));

        // THEN
        Optional<Object> result = runPromise(() -> memory.getActiveContext("temp_key"));
        assertThat(result).isEmpty();
    }

    // ========== Project Status Tests ==========
    @Test
    @DisplayName("Should update and get project status")
    void shouldUpdateAndGetProjectStatus() {
        // GIVEN
        SharedOrganizationMemory.ProjectStatus status = SharedOrganizationMemory.ProjectStatus.builder()
                .projectId("proj-001")
                .name("Feature X")
                .status("active")
                .phase("development")
                .owner("pm-001")
                .assignees(List.of("eng-001", "eng-002"))
                .metrics(Map.of("completion", 45, "velocity", 8))
                .build();

        runPromise(() -> memory.updateProjectStatus("proj-001", status));

        // WHEN
        Optional<SharedOrganizationMemory.ProjectStatus> result = runPromise(()
                -> memory.getProjectStatus("proj-001")
        );

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Feature X");
        assertThat(result.get().status()).isEqualTo("active");
        assertThat(result.get().assignees()).containsExactly("eng-001", "eng-002");
    }

    @Test
    @DisplayName("Should list active projects")
    void shouldListActiveProjects() {
        // GIVEN
        runPromise(() -> memory.updateProjectStatus("proj-1",
                SharedOrganizationMemory.ProjectStatus.builder()
                        .projectId("proj-1")
                        .name("Active Project 1")
                        .status("active")
                        .build()));
        runPromise(() -> memory.updateProjectStatus("proj-2",
                SharedOrganizationMemory.ProjectStatus.builder()
                        .projectId("proj-2")
                        .name("In Progress Project")
                        .status("in-progress")
                        .build()));
        runPromise(() -> memory.updateProjectStatus("proj-3",
                SharedOrganizationMemory.ProjectStatus.builder()
                        .projectId("proj-3")
                        .name("Completed Project")
                        .status("completed")
                        .build()));

        // WHEN
        List<SharedOrganizationMemory.ProjectStatus> activeProjects = runPromise(
                memory::listActiveProjects
        );

        // THEN
        assertThat(activeProjects).hasSize(2);
        assertThat(activeProjects).extracting("name")
                .containsExactlyInAnyOrder("Active Project 1", "In Progress Project");
    }

    // ========== Stats Tests ==========
    @Test
    @DisplayName("Should track knowledge count")
    void shouldTrackKnowledgeCount() {
        // GIVEN
        runPromise(() -> memory.shareKnowledge("topic1", "Content 1", "agent-1"));
        runPromise(() -> memory.shareKnowledge("topic1", "Content 2", "agent-2"));
        runPromise(() -> memory.shareKnowledge("topic2", "Content 3", "agent-1"));

        // WHEN/THEN
        assertThat(memory.getKnowledgeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should track decision count")
    void shouldTrackDecisionCount() {
        // GIVEN
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            runPromise(() -> memory.recordOrgDecision(
                    SharedOrganizationMemory.OrgDecision.builder()
                            .topic("topic")
                            .decision("Decision " + idx)
                            .decidedBy("agent")
                            .build()
            ));
        }

        // WHEN/THEN
        assertThat(memory.getDecisionCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should clear all data")
    void shouldClearAllData() {
        // GIVEN
        runPromise(() -> memory.shareKnowledge("topic", "content", "agent"));
        runPromise(() -> memory.recordOrgDecision(SharedOrganizationMemory.OrgDecision.builder()
                .topic("topic").decision("decision").decidedBy("agent").build()));
        runPromise(() -> memory.setActiveContext("key", "value"));
        runPromise(() -> memory.updateProjectStatus("proj",
                SharedOrganizationMemory.ProjectStatus.builder()
                        .projectId("proj").name("name").build()));

        // WHEN
        memory.clear();

        // THEN
        assertThat(memory.getKnowledgeCount()).isZero();
        assertThat(memory.getDecisionCount()).isZero();
        assertThat(runPromise(memory::getAllActiveContext)).isEmpty();
        assertThat(runPromise(memory::listActiveProjects)).isEmpty();
    }
}
