package com.ghatana.yappc.agent.enhancement;

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

@DisplayName("Enhancement AnalyzeStep Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles analyze step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class AnalyzeStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private AnalyzeStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new AnalyzeStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should analyze feedback and generate insights [GH-90000]")
  void shouldAnalyzeFeedback() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("feedbackId", "feedback-001"); // GH-90000
    context.put("runId", "run-001"); // GH-90000

    Map<String, Object> mockFeedback =
        Map.of( // GH-90000
            "_id",
            "feedback-001",
            "feedbackId",
            "feedback-001",
            "aggregated",
            Map.of( // GH-90000
                "byFeature",
                Map.of( // GH-90000
                    "login",
                    List.of( // GH-90000
                        Map.of("type", "BUG_REPORT", "severity", "HIGH", "text", "Login is slow"), // GH-90000
                        Map.of("type", "USER_SATISFACTION", "rating", 5, "text", "Not great")), // GH-90000
                    "dashboard",
                    List.of( // GH-90000
                        Map.of("type", "FEATURE_REQUEST", "rating", 4, "text", "Add charts"))))); // GH-90000

    when(dbClient.query(anyString(), any(), anyInt())) // GH-90000
        .thenReturn(Promise.of(List.of(mockFeedback))); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("status [GH-90000]")).isEqualTo("ANALYZED [GH-90000]");
    assertThat(result.get("insightCount [GH-90000]")).isNotNull();
  }

  @Test
  @DisplayName("Should fail when feedbackId is missing [GH-90000]")
  void shouldFailWhenFeedbackIdMissing() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("runId", "run-001"); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessageContaining("feedbackId [GH-90000]");
  }
}
