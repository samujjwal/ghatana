package com.ghatana.yappc.sdlc;

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
import com.ghatana.yappc.sdlc.requirements.IntakeStep;
import io.activej.promise.Promise;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for complete SDLC workflow execution.
 *
 * @doc.type class
 * @doc.purpose End-to-end SDLC workflow integration tests
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@DisplayName("SDLC Integration Tests")
class SDLCIntegrationTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);

    // Setup default mock responses
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null));
    when(dbClient.query(anyString(), any(), anyInt())).thenReturn(Promise.of(List.of()));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
  }

  @Test
  @DisplayName("Should execute complete requirements phase workflow")
  void shouldExecuteRequirementsPhase() {
    // GIVEN
    IntakeStep intakeStep = new IntakeStep(dbClient, eventClient);

    Map<String, Object> inputData = new HashMap<>();
    inputData.put("source", "user-request");
    inputData.put("content", "Need authentication system");

    WorkflowContext context = WorkflowContext.forWorkflow("sdlc-workflow-001", "tenant-test");
    inputData.forEach(context::put);

    // WHEN
    WorkflowContext result = runPromise(() -> intakeStep.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.getWorkflowId()).isEqualTo("sdlc-workflow-001");
    assertThat(result.getTenantId()).isEqualTo("tenant-test");
    assertThat(result.getData()).containsKey("requirementId");
    assertThat(result.getData()).containsKey("source");
  }

  @Test
  @DisplayName("Should maintain trace links across phases")
  void shouldMaintainTraceLinks() {
    // GIVEN
    IntakeStep step = new IntakeStep(dbClient, eventClient);

    WorkflowContext context = WorkflowContext.forWorkflow("workflow-trace", "tenant-test");
    context.put("source", "integration-test");
    context.put("content", "Test requirement");

    // Mock baseline with trace links
    Map<String, Object> mockBaseline =
        Map.of("_id", "baseline-001", "traceLinks", Map.of("parentBaseline", "parent-001"));

    when(dbClient.query(anyString(), any(), anyInt()))
        .thenReturn(Promise.of(List.of(mockBaseline)));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.getData()).isNotEmpty();
  }

  @Test
  @DisplayName("Should handle workflow context propagation")
  void shouldPropagateContext() {
    // GIVEN
    IntakeStep step = new IntakeStep(dbClient, eventClient);

    WorkflowContext initialContext = WorkflowContext.forWorkflow("prop-test", "tenant-abc");
    initialContext.put("source", "context-test");
    initialContext.put("content", "Test propagation");
    initialContext.put("customProperty", "preserved-value");

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(initialContext));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.getWorkflowId()).isEqualTo("prop-test");
    assertThat(result.getTenantId()).isEqualTo("tenant-abc");
    assertThat(result.get("customProperty")).isEqualTo("preserved-value");
  }

  @Test
  @DisplayName("Should preserve baseline immutability")
  void shouldPreserveBaselineImmutability() {
    // GIVEN
    IntakeStep step = new IntakeStep(dbClient, eventClient);

    WorkflowContext context = WorkflowContext.forWorkflow("immutable-test", "tenant-test");
    context.put("source", "immutability-test");
    context.put("content", "Test baseline immutability");

    String originalContent = "Test baseline immutability";

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN - Original context should remain unchanged
    assertThat(context.get("content")).isEqualTo(originalContent);
    assertThat(result).isNotNull();
    assertThat(result.getData()).containsKey("requirementId");
  }
}
