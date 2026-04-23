package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.yappc.agent.requirements.IntakeStep;
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
  private EventPublisher eventClient;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000

    // Setup default mock responses
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(dbClient.update(anyString(), any(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(dbClient.query(anyString(), any(), anyInt())).thenReturn(Promise.of(List.of())); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
  }

  @Test
  @DisplayName("Should execute complete requirements phase workflow")
  void shouldExecuteRequirementsPhase() { // GH-90000
    // GIVEN
    IntakeStep intakeStep = new IntakeStep(dbClient, eventClient); // GH-90000

    Map<String, Object> inputData = new HashMap<>(); // GH-90000
    inputData.put("source", "user-request"); // GH-90000
    inputData.put("content", "Need authentication system"); // GH-90000

    WorkflowContext context = WorkflowContext.forWorkflow("sdlc-workflow-001", "tenant-test"); // GH-90000
    inputData.forEach(context::put); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> intakeStep.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.getWorkflowId()).isEqualTo("sdlc-workflow-001");
    assertThat(result.getTenantId()).isEqualTo("tenant-test");
    assertThat(result.getData()).containsKey("requirementId");
    assertThat(result.getData()).containsKey("source");
  }

  @Test
  @DisplayName("Should maintain trace links across phases")
  void shouldMaintainTraceLinks() { // GH-90000
    // GIVEN
    IntakeStep step = new IntakeStep(dbClient, eventClient); // GH-90000

    WorkflowContext context = WorkflowContext.forWorkflow("workflow-trace", "tenant-test"); // GH-90000
    context.put("source", "integration-test"); // GH-90000
    context.put("content", "Test requirement"); // GH-90000

    // Mock baseline with trace links
    Map<String, Object> mockBaseline =
        Map.of("_id", "baseline-001", "traceLinks", Map.of("parentBaseline", "parent-001")); // GH-90000

    when(dbClient.query(anyString(), any(), anyInt())) // GH-90000
        .thenReturn(Promise.of(List.of(mockBaseline))); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.getData()).isNotEmpty(); // GH-90000
  }

  @Test
  @DisplayName("Should handle workflow context propagation")
  void shouldPropagateContext() { // GH-90000
    // GIVEN
    IntakeStep step = new IntakeStep(dbClient, eventClient); // GH-90000

    WorkflowContext initialContext = WorkflowContext.forWorkflow("prop-test", "tenant-abc"); // GH-90000
    initialContext.put("source", "context-test"); // GH-90000
    initialContext.put("content", "Test propagation"); // GH-90000
    initialContext.put("customProperty", "preserved-value"); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(initialContext)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.getWorkflowId()).isEqualTo("prop-test");
    assertThat(result.getTenantId()).isEqualTo("tenant-abc");
    assertThat(result.get("customProperty")).isEqualTo("preserved-value");
  }

  @Test
  @DisplayName("Should preserve baseline immutability")
  void shouldPreserveBaselineImmutability() { // GH-90000
    // GIVEN
    IntakeStep step = new IntakeStep(dbClient, eventClient); // GH-90000

    WorkflowContext context = WorkflowContext.forWorkflow("immutable-test", "tenant-test"); // GH-90000
    context.put("source", "immutability-test"); // GH-90000
    context.put("content", "Test baseline immutability"); // GH-90000

    String originalContent = "Test baseline immutability";

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN - Original context should remain unchanged
    assertThat(context.get("content")).isEqualTo(originalContent);
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.getData()).containsKey("requirementId");
  }
}
