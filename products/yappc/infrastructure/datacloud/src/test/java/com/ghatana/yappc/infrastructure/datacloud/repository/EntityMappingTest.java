/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * YAPPC Infrastructure - Data-Cloud Integration Tests
 */
package com.ghatana.yappc.infrastructure.datacloud.repository;

import com.ghatana.yappc.infrastructure.datacloud.entity.PhaseStateEntity;
import com.ghatana.yappc.infrastructure.datacloud.entity.ProjectEntity;
import com.ghatana.yappc.infrastructure.datacloud.entity.TaskEntity;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.yappc.infrastructure.security.EncryptionService;
import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entity Mapping Tests - Validates YappcEntityMapper with domain entities.
 *
 * <p>Tests round-trip conversion of Project, Task, and PhaseState entities
 * through the Jackson-based mapper used by YappcDataCloudRepository.
 *
 * @author Ghatana AI Platform
 * @since 2.0.0

 * @doc.type class
 * @doc.purpose Handles entity mapping test operations
 * @doc.layer platform
 * @doc.pattern Test
*/
class EntityMappingTest {

  private YappcEntityMapper mapper;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() { // GH-90000
    objectMapper = new ObjectMapper(); // GH-90000
    objectMapper.registerModule(new JavaTimeModule()); // GH-90000
    mapper = new YappcEntityMapper(objectMapper); // GH-90000
  }

  @Test
  void testProjectEntityRoundTrip() { // GH-90000
    // Given
    mapper = new YappcEntityMapper(objectMapper, new EncryptionService( // GH-90000
      Base64.getDecoder().decode(EncryptionService.generateKey()))); // GH-90000
    ProjectEntity original = new ProjectEntity("Test Project", "Description", "user-123"); // GH-90000
    original.setTenantId("tenant-1");
    original.setCurrentStage("plan");
    original.setStatus("ACTIVE");
    original.setEnvironmentVariables(Map.of( // GH-90000
      "OPENAI_API_KEY", "sk-roundtrip-secret",
      "DATABASE_URL", "jdbc:postgresql://localhost/yappc"
    ));
    original.advanceStage("execute");

    // When
    Map<String, Object> data = mapper.toEntityData(original); // GH-90000
    DataCloudClient.Entity entity = DataCloudClient.Entity.of(original.getId().toString(), "projects", data); // GH-90000
    ProjectEntity restored = mapper.fromEntity(entity, ProjectEntity.class); // GH-90000

    // Then
    assertEquals(original.getId(), restored.getId()); // GH-90000
    assertEquals(original.getName(), restored.getName()); // GH-90000
    assertEquals(original.getDescription(), restored.getDescription()); // GH-90000
    assertEquals(original.getStatus(), restored.getStatus()); // GH-90000
    assertEquals(original.getCurrentStage(), restored.getCurrentStage()); // GH-90000
    assertEquals(original.getCreatedBy(), restored.getCreatedBy()); // GH-90000
    assertEquals(original.getTenantId(), restored.getTenantId()); // GH-90000
    assertEquals(original.getEnvironmentVariables(), restored.getEnvironmentVariables()); // GH-90000
  }

  @Test
  void testTaskEntityRoundTrip() { // GH-90000
    // Given
    UUID projectId = UUID.randomUUID(); // GH-90000
    TaskEntity original = new TaskEntity(projectId, "Implement feature", "Task description", "execute"); // GH-90000
    original.setAssignedAgentId("agent.yappc.java-expert");
    original.setStatus("IN_PROGRESS");
    original.setPriority("HIGH");
    original.requiresCapability("code-generation");
    original.requiresCapability("test-generation");
    original.withLabel("type", "feature"); // GH-90000
    original.setEventCorrelationId("evt-123");

    // When
    Map<String, Object> data = mapper.toEntityData(original); // GH-90000
    DataCloudClient.Entity entity = DataCloudClient.Entity.of(original.getId().toString(), "tasks", data); // GH-90000
    TaskEntity restored = mapper.fromEntity(entity, TaskEntity.class); // GH-90000

    // Then
    assertEquals(original.getId(), restored.getId()); // GH-90000
    assertEquals(original.getProjectId(), restored.getProjectId()); // GH-90000
    assertEquals(original.getTitle(), restored.getTitle()); // GH-90000
    assertEquals(original.getAssignedAgentId(), restored.getAssignedAgentId()); // GH-90000
    assertEquals(original.getStatus(), restored.getStatus()); // GH-90000
    assertEquals(original.getPriority(), restored.getPriority()); // GH-90000
    assertEquals(original.getStage(), restored.getStage()); // GH-90000
    assertEquals(original.getEventCorrelationId(), restored.getEventCorrelationId()); // GH-90000
    assertEquals(2, restored.getRequiredCapabilities().size()); // GH-90000
    assertTrue(restored.getLabels().containsKey("type"));
  }

  @Test
  void testPhaseStateEntityRoundTrip() { // GH-90000
    // Given
    UUID projectId = UUID.randomUUID(); // GH-90000
    PhaseStateEntity original = new PhaseStateEntity( // GH-90000
        projectId, "execute", "plan", "sprint.plan.approved");
    original.setStatus("ACTIVE");
    original.markEntryCriterion("sprint_approved", true); // GH-90000
    original.markEntryCriterion("capacity_available", true); // GH-90000
    original.addProducedArtifact("sprint-plan", "docs/plans/sprint-1.md"); // GH-90000
    original.recordGateDecision( // GH-90000
        "agent.yappc.human-in-the-loop-coordinator",
        "human approval received",
        true,
        "Approved by product owner");
    original.setEventCorrelationId("evt-phase-123");

    // When
    Map<String, Object> data = mapper.toEntityData(original); // GH-90000
    DataCloudClient.Entity entity = DataCloudClient.Entity.of(original.getId().toString(), "phase_states", data); // GH-90000
    PhaseStateEntity restored = mapper.fromEntity(entity, PhaseStateEntity.class); // GH-90000

    // Then
    assertEquals(original.getId(), restored.getId()); // GH-90000
    assertEquals(original.getProjectId(), restored.getProjectId()); // GH-90000
    assertEquals(original.getStage(), restored.getStage()); // GH-90000
    assertEquals(original.getPreviousStage(), restored.getPreviousStage()); // GH-90000
    assertEquals(original.getTriggerEvent(), restored.getTriggerEvent()); // GH-90000
    assertEquals(original.getStatus(), restored.getStatus()); // GH-90000
    assertEquals(original.getEventCorrelationId(), restored.getEventCorrelationId()); // GH-90000
    assertEquals(2, restored.getEntryCriteriaMet().size()); // GH-90000
    assertTrue(restored.allEntryCriteriaMet()); // GH-90000
    assertEquals(1, restored.getProducedArtifacts().size()); // GH-90000
    assertEquals(1, restored.getGateDecisions().size()); // GH-90000
  }

  @Test
  void testProjectLifecycleTransitions() { // GH-90000
    // Given
    ProjectEntity project = new ProjectEntity("Lifecycle Test", "Test", "user-1"); // GH-90000

    // When/Then - Test valid transitions
    assertEquals("intent", project.getCurrentStage()); // GH-90000
    assertTrue(project.advanceStage("context"));
    assertEquals("context", project.getCurrentStage()); // GH-90000
    assertTrue(project.advanceStage("plan"));
    assertEquals("plan", project.getCurrentStage()); // GH-90000
    assertTrue(project.advanceStage("execute"));
    assertEquals("execute", project.getCurrentStage()); // GH-90000

    // Invalid transition should fail
    assertFalse(project.advanceStage("intent")); // Can't go backwards
    assertEquals("execute", project.getCurrentStage()); // GH-90000
  }

  @Test
  void testTaskStateMachine() { // GH-90000
    // Given
    TaskEntity task = new TaskEntity(UUID.randomUUID(), "Test Task", "Desc", "execute"); // GH-90000
    task.setAssignedAgentId("agent.yappc.java-expert");

    // When/Then - Test task lifecycle
    assertEquals("PENDING", task.getStatus()); // GH-90000
    assertTrue(task.start()); // GH-90000
    assertEquals("IN_PROGRESS", task.getStatus()); // GH-90000
    assertNotNull(task.getStartedAt()); // GH-90000

    // Complete task
    assertTrue(task.complete("Feature implemented", Map.of("lines_added", 150))); // GH-90000
    assertEquals("COMPLETED", task.getStatus()); // GH-90000
    assertNotNull(task.getCompletedAt()); // GH-90000
    assertEquals("Feature implemented", task.getResultSummary()); // GH-90000
  }

  @Test
  void testTaskRetryLogic() { // GH-90000
    // Given
    TaskEntity task = new TaskEntity(UUID.randomUUID(), "Flaky Task", "Desc", "execute"); // GH-90000
    task.setMaxRetries(3); // GH-90000

    // When - Fail twice, succeed on third
    assertTrue(task.fail("Network error")); // Retry 1
    assertEquals("PENDING", task.getStatus()); // GH-90000
    assertEquals(1, task.getRetryCount()); // GH-90000

    assertTrue(task.fail("Timeout")); // Retry 2
    assertEquals("PENDING", task.getStatus()); // GH-90000
    assertEquals(2, task.getRetryCount()); // GH-90000

    assertFalse(task.fail("Final failure")); // No more retries
    assertEquals("FAILED", task.getStatus()); // GH-90000
    assertEquals(3, task.getRetryCount()); // GH-90000
    assertFalse(task.canRetry()); // GH-90000
  }

  @Test
  void testPhaseStateGateDecisions() { // GH-90000
    // Given
    PhaseStateEntity phase = new PhaseStateEntity( // GH-90000
        UUID.randomUUID(), "Release", "Test", "release.candidate.ready"); // GH-90000

    // When - Record multiple gate decisions
    phase.recordGateDecision( // GH-90000
        "agent.yappc.sentinel",
        "security scan passed",
        true,
        "No critical vulnerabilities");
    phase.recordGateDecision( // GH-90000
        "agent.yappc.quality-guard-agent",
        "coverage > 80%",
        true,
        "Current coverage 85%");
    phase.recordGateDecision( // GH-90000
        "agent.yappc.release-governance-agent",
        "all gates passed",
        true,
        "Release approved");

    // Then
    assertEquals(3, phase.getGateDecisions().size()); // GH-90000
    assertTrue(phase.getGateDecisions().stream() // GH-90000
        .allMatch(PhaseStateEntity.GateDecision::approved)); // GH-90000
  }

  @Test
  void testEntityCollectionNames() { // GH-90000
    assertEquals("projects", ProjectEntity.getCollectionName()); // GH-90000
    assertEquals("tasks", TaskEntity.getCollectionName()); // GH-90000
    assertEquals("phase_states", PhaseStateEntity.getCollectionName()); // GH-90000
  }

  @Test
  void testProjectTaskRelationship() { // GH-90000
    // Given
    ProjectEntity project = new ProjectEntity("With Tasks", "Test", "user-1"); // GH-90000
    UUID taskId1 = UUID.randomUUID(); // GH-90000
    UUID taskId2 = UUID.randomUUID(); // GH-90000

    // When
    project.addTask(taskId1); // GH-90000
    project.addTask(taskId2); // GH-90000

    // Then
    assertEquals(2, project.getTaskIds().size()); // GH-90000
    assertTrue(project.getTaskIds().contains(taskId1)); // GH-90000
    assertTrue(project.getTaskIds().contains(taskId2)); // GH-90000

    // Remove task
    project.removeTask(taskId1); // GH-90000
    assertEquals(1, project.getTaskIds().size()); // GH-90000
    assertFalse(project.getTaskIds().contains(taskId1)); // GH-90000
  }
}
