package com.ghatana.yappc.agent.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.yappc.agent.EventPublisher;
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
  private EventPublisher eventClient;
  private IntakeStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new IntakeStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { // GH-90000
    String stepId = step.getStepId(); // GH-90000
    assertThat(stepId).isEqualTo("requirements.intake");
  }

  @Test
  @DisplayName("Should construct with non-null dependencies")
  void shouldConstructWithNonNullDependencies() { // GH-90000
    assertThat(step).isNotNull(); // GH-90000
  }

  @Test
  @DisplayName("Should successfully ingest requirements")
  void shouldIngestRequirements() { // GH-90000
    // GIVEN
    Map<String, Object> inputData = new HashMap<>(); // GH-90000
    inputData.put("source", "user-input"); // GH-90000
    inputData.put("content", "User needs authentication feature"); // GH-90000

    WorkflowContext context = WorkflowContext.forWorkflow("workflow-456", "tenant-123"); // GH-90000
    inputData.forEach(context::put); // GH-90000

    when(dbClient.insert(eq("requirements_raw"), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.getData()).containsKey("requirementId");
    assertThat(result.getData()).containsEntry("source", "user-input"); // GH-90000
    assertThat(result.getData()).containsEntry("persisted", true); // GH-90000

    verify(dbClient).insert(eq("requirements_raw"), any());
    verify(eventClient).publish(eq("requirements.ingested"), any());
  }

  @Test
  @DisplayName("Should fail when source is missing")
  void shouldFailWhenSourceMissing() { // GH-90000
    // GIVEN
    Map<String, Object> inputData = new HashMap<>(); // GH-90000
    inputData.put("content", "Some content without source"); // GH-90000

    WorkflowContext context = WorkflowContext.forWorkflow("workflow-456", "tenant-123"); // GH-90000
    inputData.forEach(context::put); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("Field 'source' is required");
  }

  @Test
  @DisplayName("Should fail when input data is empty")
  void shouldFailWhenInputIsEmpty() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-456", "tenant-123"); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("Input data is required");
  }
}
