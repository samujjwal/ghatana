package com.ghatana.yappc.agent.implementation;

import static org.assertj.core.api.Assertions.assertThat;
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

@DisplayName("Implementation ScaffoldStep Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles scaffold step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ScaffoldStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private ScaffoldStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new ScaffoldStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID [GH-90000]")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("implementation.scaffold [GH-90000]");
  }

  @Test
  @DisplayName("Should scaffold codebase from implementation plan [GH-90000]")
  void shouldScaffoldCodebase() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("implementationPlanId", "plan-001"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000
    context.put( // GH-90000
        "units",
        List.of( // GH-90000
            Map.of( // GH-90000
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
            Map.of( // GH-90000
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

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("implementationPlanId [GH-90000]")).isEqualTo("plan-001 [GH-90000]");
  }
}
