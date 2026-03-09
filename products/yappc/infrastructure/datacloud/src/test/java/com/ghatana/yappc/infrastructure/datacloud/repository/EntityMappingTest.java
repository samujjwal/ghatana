/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure - Data-Cloud Integration Tests
 */
package com.ghatana.yappc.infrastructure.datacloud.repository;

import com.ghatana.yappc.infrastructure.datacloud.entity.PhaseStateEntity;
import com.ghatana.yappc.infrastructure.datacloud.entity.ProjectEntity;
import com.ghatana.yappc.infrastructure.datacloud.entity.TaskEntity;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    mapper = new YappcEntityMapper(objectMapper);
  }

  @Test
  void testProjectEntityRoundTrip() {
    // Given
    ProjectEntity original = new ProjectEntity("Test Project", "Description", "user-123");
    original.setTenantId("tenant-1");
    original.setCurrentStage("plan");
    original.setStatus("ACTIVE");
    original.advanceStage("execute");

    // When
    var entity = mapper.toEntity(original, "projects", "tenant-1");
    ProjectEntity restored = mapper.fromEntity(entity, ProjectEntity.class);

    // Then
    assertEquals(original.getId(), restored.getId());
    assertEquals(original.getName(), restored.getName());
    assertEquals(original.getDescription(), restored.getDescription());
    assertEquals(original.getStatus(), restored.getStatus());
    assertEquals(original.getCurrentStage(), restored.getCurrentStage());
    assertEquals(original.getCreatedBy(), restored.getCreatedBy());
    assertEquals(original.getTenantId(), restored.getTenantId());
  }

  @Test
  void testTaskEntityRoundTrip() {
    // Given
    UUID projectId = UUID.randomUUID();
    TaskEntity original = new TaskEntity(projectId, "Implement feature", "Task description", "execute");
    original.setAssignedAgentId("agent.yappc.java-expert");
    original.setStatus("IN_PROGRESS");
    original.setPriority("HIGH");
    original.requiresCapability("code-generation");
    original.requiresCapability("test-generation");
    original.withLabel("type", "feature");
    original.setEventCorrelationId("evt-123");

    // When
    var entity = mapper.toEntity(original, "tasks", "tenant-1");
    TaskEntity restored = mapper.fromEntity(entity, TaskEntity.class);

    // Then
    assertEquals(original.getId(), restored.getId());
    assertEquals(original.getProjectId(), restored.getProjectId());
    assertEquals(original.getTitle(), restored.getTitle());
    assertEquals(original.getAssignedAgentId(), restored.getAssignedAgentId());
    assertEquals(original.getStatus(), restored.getStatus());
    assertEquals(original.getPriority(), restored.getPriority());
    assertEquals(original.getStage(), restored.getStage());
    assertEquals(original.getEventCorrelationId(), restored.getEventCorrelationId());
    assertEquals(2, restored.getRequiredCapabilities().size());
    assertTrue(restored.getLabels().containsKey("type"));
  }

  @Test
  void testPhaseStateEntityRoundTrip() {
    // Given
    UUID projectId = UUID.randomUUID();
    PhaseStateEntity original = new PhaseStateEntity(
        projectId, "execute", "plan", "sprint.plan.approved");
    original.setStatus("ACTIVE");
    original.markEntryCriterion("sprint_approved", true);
    original.markEntryCriterion("capacity_available", true);
    original.addProducedArtifact("sprint-plan", "docs/plans/sprint-1.md");
    original.recordGateDecision(
        "agent.yappc.human-in-the-loop-coordinator",
        "human approval received",
        true,
        "Approved by product owner");
    original.setEventCorrelationId("evt-phase-123");

    // When
    var entity = mapper.toEntity(original, "phase_states", "tenant-1");
    PhaseStateEntity restored = mapper.fromEntity(entity, PhaseStateEntity.class);

    // Then
    assertEquals(original.getId(), restored.getId());
    assertEquals(original.getProjectId(), restored.getProjectId());
    assertEquals(original.getStage(), restored.getStage());
    assertEquals(original.getPreviousStage(), restored.getPreviousStage());
    assertEquals(original.getTriggerEvent(), restored.getTriggerEvent());
    assertEquals(original.getStatus(), restored.getStatus());
    assertEquals(original.getEventCorrelationId(), restored.getEventCorrelationId());
    assertEquals(2, restored.getEntryCriteriaMet().size());
    assertTrue(restored.allEntryCriteriaMet());
    assertEquals(1, restored.getProducedArtifacts().size());
    assertEquals(1, restored.getGateDecisions().size());
  }

  @Test
  void testProjectLifecycleTransitions() {
    // Given
    ProjectEntity project = new ProjectEntity("Lifecycle Test", "Test", "user-1");

    // When/Then - Test valid transitions
    assertEquals("intent", project.getCurrentStage());
    assertTrue(project.advanceStage("context"));
    assertEquals("context", project.getCurrentStage());
    assertTrue(project.advanceStage("plan"));
    assertEquals("plan", project.getCurrentStage());
    assertTrue(project.advanceStage("execute"));
    assertEquals("execute", project.getCurrentStage());

    // Invalid transition should fail
    assertFalse(project.advanceStage("intent")); // Can't go backwards
    assertEquals("execute", project.getCurrentStage());
  }

  @Test
  void testTaskStateMachine() {
    // Given
    TaskEntity task = new TaskEntity(UUID.randomUUID(), "Test Task", "Desc", "execute");
    task.setAssignedAgentId("agent.yappc.java-expert");

    // When/Then - Test task lifecycle
    assertEquals("PENDING", task.getStatus());
    assertTrue(task.start());
    assertEquals("IN_PROGRESS", task.getStatus());
    assertNotNull(task.getStartedAt());

    // Complete task
    assertTrue(task.complete("Feature implemented", Map.of("lines_added", 150)));
    assertEquals("COMPLETED", task.getStatus());
    assertNotNull(task.getCompletedAt());
    assertEquals("Feature implemented", task.getResultSummary());
  }

  @Test
  void testTaskRetryLogic() {
    // Given
    TaskEntity task = new TaskEntity(UUID.randomUUID(), "Flaky Task", "Desc", "execute");
    task.setMaxRetries(3);

    // When - Fail twice, succeed on third
    assertTrue(task.fail("Network error")); // Retry 1
    assertEquals("PENDING", task.getStatus());
    assertEquals(1, task.getRetryCount());

    assertTrue(task.fail("Timeout")); // Retry 2
    assertEquals("PENDING", task.getStatus());
    assertEquals(2, task.getRetryCount());

    assertFalse(task.fail("Final failure")); // No more retries
    assertEquals("FAILED", task.getStatus());
    assertEquals(3, task.getRetryCount());
    assertFalse(task.canRetry());
  }

  @Test
  void testPhaseStateGateDecisions() {
    // Given
    PhaseStateEntity phase = new PhaseStateEntity(
        UUID.randomUUID(), "Release", "Test", "release.candidate.ready");

    // When - Record multiple gate decisions
    phase.recordGateDecision(
        "agent.yappc.sentinel",
        "security scan passed",
        true,
        "No critical vulnerabilities");
    phase.recordGateDecision(
        "agent.yappc.quality-guard-agent",
        "coverage > 80%",
        true,
        "Current coverage 85%");
    phase.recordGateDecision(
        "agent.yappc.release-governance-agent",
        "all gates passed",
        true,
        "Release approved");

    // Then
    assertEquals(3, phase.getGateDecisions().size());
    assertTrue(phase.getGateDecisions().stream()
        .allMatch(PhaseStateEntity.GateDecision::approved));
  }

  @Test
  void testEntityCollectionNames() {
    assertEquals("projects", ProjectEntity.getCollectionName());
    assertEquals("tasks", TaskEntity.getCollectionName());
    assertEquals("phase_states", PhaseStateEntity.getCollectionName());
  }

  @Test
  void testProjectTaskRelationship() {
    // Given
    ProjectEntity project = new ProjectEntity("With Tasks", "Test", "user-1");
    UUID taskId1 = UUID.randomUUID();
    UUID taskId2 = UUID.randomUUID();

    // When
    project.addTask(taskId1);
    project.addTask(taskId2);

    // Then
    assertEquals(2, project.getTaskIds().size());
    assertTrue(project.getTaskIds().contains(taskId1));
    assertTrue(project.getTaskIds().contains(taskId2));

    // Remove task
    project.removeTask(taskId1);
    assertEquals(1, project.getTaskIds().size());
    assertFalse(project.getTaskIds().contains(taskId1));
  }
}
