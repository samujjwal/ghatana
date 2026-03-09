package com.ghatana.yappc.sdlc.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import io.activej.promise.Promise;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for IntakeStep.
 *
 * @doc.type class
 * @doc.purpose Test requirements phase intake step
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Requirements IntakeStep Tests")
class IntakeStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private IntakeStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new IntakeStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    String stepId = step.getStepId();
    assertThat(stepId).isEqualTo("requirements.intake");
  }

  @Test
  @DisplayName("Should construct with non-null dependencies")
  void shouldConstructWithNonNullDependencies() {
    assertThat(step).isNotNull();
  }

  @Test
  @DisplayName("Should successfully ingest requirements")
  void shouldIngestRequirements() {
    // GIVEN
    Map<String, Object> inputData = new HashMap<>();
    inputData.put("source", "user-input");
    inputData.put("content", "User needs authentication feature");

    WorkflowContext context = WorkflowContext.forWorkflow("workflow-456", "tenant-123");
    inputData.forEach(context::put);

    when(dbClient.insert(eq("requirements_raw"), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.getData()).containsKey("requirementId");
    assertThat(result.getData()).containsEntry("source", "user-input");
    assertThat(result.getData()).containsEntry("persisted", true);

    verify(dbClient).insert(eq("requirements_raw"), any());
    verify(eventClient).publish(eq("requirements.ingested"), any());
  }

  @Test
  @DisplayName("Should fail when source is missing")
  void shouldFailWhenSourceMissing() {
    // GIVEN
    Map<String, Object> inputData = new HashMap<>();
    inputData.put("content", "Some content without source");

    WorkflowContext context = WorkflowContext.forWorkflow("workflow-456", "tenant-123");
    inputData.forEach(context::put);

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field 'source' is required");
  }

  @Test
  @DisplayName("Should fail when input data is empty")
  void shouldFailWhenInputIsEmpty() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-456", "tenant-123");

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Input data is required");
  }
}
