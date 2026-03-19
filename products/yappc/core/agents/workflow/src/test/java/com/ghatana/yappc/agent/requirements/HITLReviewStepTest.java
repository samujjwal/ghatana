package com.ghatana.yappc.agent.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
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
  private EventCloud eventClient;
  private HITLReviewStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new HITLReviewStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("requirements.hitlreview");
  }

  @Test
  @DisplayName("Should create HITL review for requirements")
  void shouldCreateHitlReview() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("wf-123", "tenant-abc");
    context.put("requirementId", "req-001");
    context.put("action", "create");
    context.put("reviewerId", "reviewer-001");

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("Should fail when requirementId is missing")
  void shouldFailWhenRequirementIdMissing() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("wf-123", "tenant-abc");
    context.put("action", "create");

    // WHEN / THEN
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> runPromise(() -> step.execute(context)))
        .withMessageContaining("requirementId");
  }
}
