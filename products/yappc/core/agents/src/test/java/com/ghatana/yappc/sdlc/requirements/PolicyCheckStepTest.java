package com.ghatana.yappc.sdlc.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
  private EventCloud eventClient;
  private PolicyCheckStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new PolicyCheckStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("requirements.policycheck");
  }

  @Test
  @DisplayName("Should run policy checks and pass requirements")
  void shouldRunPolicyChecks() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("requirementId", "req-001");
    context.put(
        "functionalRequirements",
        List.of("User shall be able to login", "System shall support user registration"));
    context.put("nonFunctionalRequirements", List.of("Response time < 100ms"));
    context.put("acceptanceCriteria", List.of("Login successful", "Registration complete"));

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("overallAction")).isNotNull();
    assertThat(result.get("requirementId")).isEqualTo("req-001");
  }

  @Test
  @DisplayName("Should fail when requirementId is missing")
  void shouldFailWhenRequirementIdMissing() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("functionalRequirements", List.of("Some requirement"));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requirementId");
  }

  @Test
  @DisplayName("Should detect forbidden content in requirements")
  void shouldDetectForbiddenContent() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("requirementId", "req-002");
    context.put(
        "functionalRequirements", List.of("User shall disable audit logging for performance"));
    context.put("nonFunctionalRequirements", List.of());
    context.put("acceptanceCriteria", List.of());

    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    // Policy should flag forbidden content
    assertThat(result.get("overallAction")).isIn("WARN", "REQUIRE_REVIEW", "BLOCK");
  }
}
