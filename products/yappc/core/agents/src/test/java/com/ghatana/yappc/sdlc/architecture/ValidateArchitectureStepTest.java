package com.ghatana.yappc.sdlc.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

@DisplayName("Architecture ValidateArchitectureStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles validate architecture step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ValidateArchitectureStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private ValidateArchitectureStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new ValidateArchitectureStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("architecture.validate");
  }

  @Test
  @DisplayName("Should validate architecture design")
  void shouldValidateArchitecture() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("architectureId", "arch-001");
    context.put(
        "nfrTargets",
        Map.of(
            "latency", Map.of("target", 100),
            "availability", Map.of("target", 99.9)));
    context.put("deploymentTopology", Map.of("metadata", Map.of("multiAZ", true)));
    context.put(
        "c4Views",
        List.of(
            Map.of("type", "C4_CONTAINER", "diagram", "Auth Service"),
            Map.of("type", "C4_COMPONENT", "diagram", "User Service")));

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null));
    when(dbClient.query(anyString(), any(), anyInt())).thenReturn(Promise.of(List.of()));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("architectureId")).isEqualTo("arch-001");
  }
}
