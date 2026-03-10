package com.ghatana.yappc.agent.implementation;

import static org.assertj.core.api.Assertions.assertThat;
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

@DisplayName("Implementation ScaffoldStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles scaffold step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ScaffoldStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private ScaffoldStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new ScaffoldStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("implementation.scaffold");
  }

  @Test
  @DisplayName("Should scaffold codebase from implementation plan")
  void shouldScaffoldCodebase() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("implementationPlanId", "plan-001");
    context.put("tenantId", "tenant-abc");
    context.put(
        "units",
        List.of(
            Map.of(
                "unitId",
                "u1",
                "type",
                "CONTAINER",
                "name",
                "ApiGateway",
                "module",
                "products/api-gateway",
                "repo",
                "ghatana"),
            Map.of(
                "unitId",
                "u2",
                "type",
                "COMPONENT",
                "name",
                "AuthService",
                "module",
                "products/auth-service",
                "repo",
                "ghatana")));

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("implementationPlanId")).isEqualTo("plan-001");
  }
}
