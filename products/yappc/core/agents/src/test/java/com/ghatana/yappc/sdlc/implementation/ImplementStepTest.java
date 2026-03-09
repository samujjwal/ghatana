package com.ghatana.yappc.sdlc.implementation;

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

@DisplayName("Implementation ImplementStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles implement step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ImplementStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private ImplementStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new ImplementStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should track implementation progress for scaffolded units")
  void shouldTrackImplementationProgress() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("tenantId", "tenant-abc");
    context.put("scaffolds", List.of(Map.of("unitId", "u1", "name", "AuthService")));

    List<Map<String, Object>> mockUnits =
        List.of(
            Map.of(
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

    when(dbClient.query(anyString(), any(), anyInt())).thenReturn(Promise.of(mockUnits));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("status")).isEqualTo("COMPLETED");
  }
}
