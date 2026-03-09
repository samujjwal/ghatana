package com.ghatana.yappc.sdlc.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

@DisplayName("Requirements ValidateStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles validate step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ValidateStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private ValidateStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new ValidateStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("requirements.validate");
  }

  @Test
  @DisplayName("Should validate normalized requirements")
  void shouldValidateRequirements() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("requirementId", "req-001");
    context.put(
        "normalizedRequirements",
        List.of(
            Map.of(
                "id",
                "r1",
                "text",
                "Authentication support",
                "type",
                "functional",
                "priority",
                "high"),
            Map.of(
                "id",
                "r2",
                "text",
                "Performance < 100ms",
                "type",
                "non-functional",
                "priority",
                "high")));

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("requirementId")).isEqualTo("req-001");
  }

  @Test
  @DisplayName("Should fail when normalized requirements are missing")
  void shouldFailWhenNormalizedRequirementsMissing() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Input data required");
  }
}
