package com.ghatana.yappc.agent.architecture;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Architecture DeriveContractsStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles derive contracts step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DeriveContractsStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private DeriveContractsStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new DeriveContractsStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("architecture.derivecontracts");
  }

  @Test
  @DisplayName("Should derive API contracts from architecture")
  void shouldDeriveAPIContracts() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("architectureId", "arch-001");
    context.put(
        "functionalRequirements",
        List.of("User management", "Order processing", "Inventory tracking"));

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("architectureId")).isEqualTo("arch-001");
  }

  @Test
  @DisplayName("Should fail when architectureId is missing")
  void shouldFailWhenArchitectureIdMissing() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("architectureId");
  }
}
