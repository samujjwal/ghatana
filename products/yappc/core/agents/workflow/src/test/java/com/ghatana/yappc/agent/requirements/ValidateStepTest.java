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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Requirements ValidateStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles validate step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ValidateStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private ValidateStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new ValidateStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("requirements.validate");
  }

  @Test
  @DisplayName("Should validate normalized requirements")
  void shouldValidateRequirements() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("requirementId", "req-001"); // GH-90000
    context.put( // GH-90000
        "normalizedRequirements",
        List.of( // GH-90000
            Map.of( // GH-90000
                "id",
                "r1",
                "text",
                "Authentication support",
                "type",
                "functional",
                "priority",
                "high"),
            Map.of( // GH-90000
                "id",
                "r2",
                "text",
                "Performance < 100ms",
                "type",
                "non-functional",
                "priority",
                "high")));

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("requirementId")).isEqualTo("req-001");
  }

  @Test
  @DisplayName("Should fail when normalized requirements are missing")
  void shouldFailWhenNormalizedRequirementsMissing() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("Input data is required");
  }
}
