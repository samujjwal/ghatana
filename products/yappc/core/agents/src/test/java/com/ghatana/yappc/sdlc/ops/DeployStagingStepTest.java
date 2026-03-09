package com.ghatana.yappc.sdlc.ops;

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

@DisplayName("Ops DeployStagingStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles deploy staging step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DeployStagingStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private DeployStagingStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new DeployStagingStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("ops.deploy_staging");
  }

  @Test
  @DisplayName("Should deploy to staging environment")
  void shouldDeployToStaging() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("tenantId", "tenant-abc"); // Required by step validation
    context.put("baselineId", "test-baseline-001");
    context.put("releaseApproved", true);

    Map<String, Object> baseline =
        Map.of(
            "_id",
            "test-baseline-001",
            "tenantId",
            "tenant-abc",
            "productionReady",
            true,
            "testResults",
            Map.of());
    when(dbClient.query(eq("test_baselines"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(baseline)));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("baselineId")).isEqualTo("test-baseline-001");
  }
}
