package com.ghatana.yappc.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Architecture IntakeStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles intake step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
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
    assertThat(step.getStepId()).isEqualTo("architecture.intake");
  }

  @Test
  @DisplayName("Should load requirements baseline and initiate architecture phase")
  void shouldInitiateArchitecturePhase() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("baselineId", "req-baseline-001");

    Map<String, Object> baseline =
        Map.of(
            "baselineId", "req-baseline-001",
            "status", "PUBLISHED",
            "requirementId", "req-001",
            "version", "1.0",
            "requirements", List.of());
    when(dbClient.query(eq("requirements_published"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(baseline)));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("baselineId")).isEqualTo("req-baseline-001");
  }
}
