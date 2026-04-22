package com.ghatana.yappc.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.yappc.agent.EventPublisher;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Architecture IntakeStep Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles intake step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
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
  @DisplayName("Should return correct step ID [GH-90000]")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("architecture.intake [GH-90000]");
  }

  @Test
  @DisplayName("Should load requirements baseline and initiate architecture phase [GH-90000]")
  void shouldInitiateArchitecturePhase() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("baselineId", "req-baseline-001"); // GH-90000

    Map<String, Object> baseline =
        Map.of( // GH-90000
            "baselineId", "req-baseline-001",
            "status", "PUBLISHED",
            "requirementId", "req-001",
            "version", "1.0",
            "requirements", List.of()); // GH-90000
    when(dbClient.query(eq("requirements_published [GH-90000]"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(baseline))); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("baselineId [GH-90000]")).isEqualTo("req-baseline-001 [GH-90000]");
  }
}
