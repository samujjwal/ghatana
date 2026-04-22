package com.ghatana.yappc.agent.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

@DisplayName("Ops DeployStagingStep Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles deploy staging step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DeployStagingStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private DeployStagingStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new DeployStagingStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID [GH-90000]")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("ops.deploy_staging [GH-90000]");
  }

  @Test
  @DisplayName("Should deploy to staging environment [GH-90000]")
  void shouldDeployToStaging() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("tenantId", "tenant-abc"); // Required by step validation // GH-90000
    context.put("baselineId", "test-baseline-001"); // GH-90000
    context.put("releaseApproved", true); // GH-90000

    Map<String, Object> baseline =
        Map.of( // GH-90000
            "_id",
            "test-baseline-001",
            "tenantId",
            "tenant-abc",
            "productionReady",
            true,
            "testResults",
            Map.of()); // GH-90000
    when(dbClient.query(eq("test_baselines [GH-90000]"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(baseline))); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("baselineId [GH-90000]")).isEqualTo("test-baseline-001 [GH-90000]");
  }
}
