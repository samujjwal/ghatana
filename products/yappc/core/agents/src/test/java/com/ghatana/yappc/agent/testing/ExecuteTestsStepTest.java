package com.ghatana.yappc.agent.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

@DisplayName("Testing ExecuteTestsStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles execute tests step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ExecuteTestsStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private ExecuteTestsStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new ExecuteTestsStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("testing.execute_tests");
  }

  @Test
  @DisplayName("Should execute tests")
  void shouldExecuteTests() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("runId", "test-run-001");
    context.put("tenantId", "tenant-abc"); // Required by step validation

    List<Map<String, Object>> testCases =
        List.of(Map.of("_id", "test-001", "runId", "test-run-001", "status", "READY"));
    when(dbClient.query(eq("test_cases"), any(), anyInt())).thenReturn(Promise.of(testCases));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("runId")).isEqualTo("test-run-001");
  }
}
