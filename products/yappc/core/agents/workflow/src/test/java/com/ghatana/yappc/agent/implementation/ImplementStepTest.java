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

@DisplayName("Implementation ImplementStep Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles implement step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ImplementStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private ImplementStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new ImplementStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should track implementation progress for scaffolded units [GH-90000]")
  void shouldTrackImplementationProgress() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000
    context.put("scaffolds", List.of(Map.of("unitId", "u1", "name", "AuthService"))); // GH-90000

    List<Map<String, Object>> mockUnits =
        List.of( // GH-90000
            Map.of( // GH-90000
                "unitId",
                "u1",
                "name",
                "AuthService",
                "repo",
                "ghatana",
                "module",
                "auth-service",
                "status",
                "SCAFFOLDED"));

    when(dbClient.query(anyString(), any(), anyInt())).thenReturn(Promise.of(mockUnits)); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("status [GH-90000]")).isEqualTo("COMPLETED [GH-90000]");
  }
}
