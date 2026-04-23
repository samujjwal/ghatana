package com.ghatana.yappc.agent.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.*;
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

@DisplayName("Requirements HITLReviewStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles hitl review step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class HITLReviewStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private HITLReviewStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new HITLReviewStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("requirements.hitlreview");
  }

  @Test
  @DisplayName("Should create HITL review for requirements")
  void shouldCreateHitlReview() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("wf-123", "tenant-abc"); // GH-90000
    context.put("requirementId", "req-001"); // GH-90000
    context.put("action", "create"); // GH-90000
    context.put("reviewerId", "reviewer-001"); // GH-90000

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
  }

  @Test
  @DisplayName("Should fail when requirementId is missing")
  void shouldFailWhenRequirementIdMissing() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("wf-123", "tenant-abc"); // GH-90000
    context.put("action", "create"); // GH-90000

    // WHEN / THEN
    assertThatExceptionOfType(IllegalArgumentException.class) // GH-90000
        .isThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .withMessageContaining("requirementId");
  }
}
