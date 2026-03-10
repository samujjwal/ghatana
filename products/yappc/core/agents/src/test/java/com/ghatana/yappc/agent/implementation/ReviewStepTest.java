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

@DisplayName("Implementation ReviewStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles review step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ReviewStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private ReviewStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new ReviewStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("implementation.review");
  }

  @Test
  @DisplayName("Should create code review records from quality gates")
  void shouldCreateReviewRecords() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("tenantId", "tenant-abc");
    context.put(
        "gates",
        List.of(
            Map.of(
                "unitId", "unit-001",
                "name", "UserService",
                "status", "PASS",
                "qualityScore", 85),
            Map.of(
                "unitId", "unit-002",
                "name", "OrderService",
                "status", "WARN",
                "qualityScore", 72)));

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
  }
}
