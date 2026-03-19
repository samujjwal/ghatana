package com.ghatana.yappc.agent.enhancement;

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

@DisplayName("Enhancement IngestFeedbackStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles ingest feedback step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class IngestFeedbackStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private IngestFeedbackStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new IngestFeedbackStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("enhancement.ingest_feedback");
  }

  @Test
  @DisplayName("Should ingest feedback from multiple sources")
  void shouldIngestFeedback() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("opsBaselineId", "ops-baseline-001");
    context.put("runId", "run-001"); // Required by IngestFeedbackStep

    Map<String, Object> baseline =
        Map.of(
            "_id",
            "ops-baseline-001",
            "productionVerified",
            true,
            "summary",
            Map.of(
                "totalDeployments", 21,
                "incidentsResolved", 8,
                "avgIncidentResponseTime", 30,
                "avgRecoveryTime", 60),
            "metrics",
            Map.of());
    when(dbClient.query(eq("ops_baselines"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(baseline)));
    when(dbClient.query(eq("feedback"), any(), anyInt())).thenReturn(Promise.of(List.of()));
    when(dbClient.query(eq("incidents"), any(), anyInt())).thenReturn(Promise.of(List.of()));
    when(dbClient.query(eq("app_usage"), any(), anyInt())).thenReturn(Promise.of(List.of()));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("opsBaselineId")).isEqualTo("ops-baseline-001");
  }
}
