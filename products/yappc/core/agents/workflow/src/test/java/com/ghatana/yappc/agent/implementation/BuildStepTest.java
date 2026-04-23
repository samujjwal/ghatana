package com.ghatana.yappc.agent.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

@DisplayName("Implementation BuildStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles build step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class BuildStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private BuildStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new BuildStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("implementation.build");
  }

  @Test
  @DisplayName("Should execute CI builds for implementation units")
  void shouldExecuteBuilds() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000
    context.put( // GH-90000
        "progress",
        List.of( // GH-90000
            Map.of( // GH-90000
                "unitId", "unit-001",
                "name", "UserService",
                "repo", "services/user",
                "module", "user-api"),
            Map.of( // GH-90000
                "unitId", "unit-002",
                "name", "OrderService",
                "repo", "services/order",
                "module", "order-api")));

    Map<String, Object> mockUnit =
        Map.of( // GH-90000
            "unitId", "unit-001",
            "tenantId", "tenant-abc",
            "status", "IN_PROGRESS",
            "name", "UserService",
            "repo", "services/user",
            "module", "user-api");

    when(dbClient.query(anyString(), any(), anyInt())).thenReturn(Promise.of(List.of(mockUnit))); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
  }
}
