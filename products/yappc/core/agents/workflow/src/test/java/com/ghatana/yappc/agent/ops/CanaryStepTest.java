package com.ghatana.yappc.agent.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

@DisplayName("Ops CanaryStep Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles canary step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class CanaryStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private CanaryStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new CanaryStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should perform canary deployment with traffic shifting [GH-90000]")
  void shouldPerformCanaryDeployment() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000
    context.put("deploymentId", "deploy-001"); // GH-90000
    context.put("validated", true); // GH-90000

    Map<String, Object> mockDeployment =
        Map.of( // GH-90000
            "deploymentId",
            "deploy-001",
            "environment",
            "STAGING",
            "validated",
            true,
            "version",
            "v1.2.0",
            "imageTag",
            "v1.2.0");

    when(dbClient.query(anyString(), any(), anyInt())) // GH-90000
        .thenReturn(Promise.of(List.of(mockDeployment))); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("status [GH-90000]")).isEqualTo("COMPLETED [GH-90000]");
    assertThat(result.get("canaryId [GH-90000]")).isNotNull();
  }

  @Test
  @DisplayName("Should fail when deployment is not validated [GH-90000]")
  void shouldFailWhenDeploymentNotValidated() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000
    context.put("validated", false); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessageContaining("not validated [GH-90000]");
  }
}
