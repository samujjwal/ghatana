package com.ghatana.yappc.agent.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.yappc.agent.EventPublisher;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import io.activej.promise.Promise;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Requirements PolicyCheckStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles policy check step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PolicyCheckStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private PolicyCheckStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new PolicyCheckStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("requirements.policycheck");
  }

  @Test
  @DisplayName("Should run policy checks and pass requirements")
  void shouldRunPolicyChecks() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("requirementId", "req-001"); // GH-90000
    context.put( // GH-90000
        "functionalRequirements",
        List.of("User shall be able to login", "System shall support user registration")); // GH-90000
    context.put("nonFunctionalRequirements", List.of("Response time < 100ms"));
    context.put("acceptanceCriteria", List.of("Login successful", "Registration complete")); // GH-90000

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("overallAction")).isNotNull();
    assertThat(result.get("requirementId")).isEqualTo("req-001");
  }

  @Test
  @DisplayName("Should fail when requirementId is missing")
  void shouldFailWhenRequirementIdMissing() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("functionalRequirements", List.of("Some requirement"));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("requirementId");
  }

  @Test
  @DisplayName("Should detect forbidden content in requirements")
  void shouldDetectForbiddenContent() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("requirementId", "req-002"); // GH-90000
    context.put( // GH-90000
        "functionalRequirements", List.of("User shall disable audit logging for performance"));
    context.put("nonFunctionalRequirements", List.of()); // GH-90000
    context.put("acceptanceCriteria", List.of()); // GH-90000

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    // Policy should flag forbidden content
    assertThat(result.get("overallAction")).isIn("WARN", "REQUIRE_REVIEW", "BLOCK");
  }
}
