package com.ghatana.yappc.agent.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.yappc.agent.EventPublisher;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Requirements DeriveRequirementsStep Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles derive requirements step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DeriveRequirementsStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private DeriveRequirementsStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new DeriveRequirementsStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID [GH-90000]")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("requirements.deriverequirements [GH-90000]");
  }

  @Test
  @DisplayName("Should derive structured requirements from normalized content [GH-90000]")
  void shouldDeriveStructuredRequirements() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("requirementId", "req-001"); // GH-90000
    context.put("normalizedContent", "User login with email and password"); // GH-90000
    context.put("category", "functional"); // GH-90000

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("requirementId [GH-90000]")).isEqualTo("req-001 [GH-90000]");
    assertThat(result.get("persisted [GH-90000]")).isEqualTo(true);
  }

  @Test
  @DisplayName("Should fail when requirementId is missing [GH-90000]")
  void shouldFailWhenRequirementIdMissing() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("normalizedContent", "Some content"); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("requirementId [GH-90000]");
  }

  @Test
  @DisplayName("Should fail when normalizedContent is missing [GH-90000]")
  void shouldFailWhenNormalizedContentMissing() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("requirementId", "req-001"); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("normalizedContent [GH-90000]");
  }
}
