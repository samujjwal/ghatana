package com.ghatana.yappc.sdlc.enhancement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

@DisplayName("Enhancement AnalyzeStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles analyze step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class AnalyzeStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private AnalyzeStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new AnalyzeStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should analyze feedback and generate insights")
  void shouldAnalyzeFeedback() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("feedbackId", "feedback-001");
    context.put("runId", "run-001");

    Map<String, Object> mockFeedback =
        Map.of(
            "_id",
            "feedback-001",
            "feedbackId",
            "feedback-001",
            "aggregated",
            Map.of(
                "byFeature",
                Map.of(
                    "login",
                    List.of(
                        Map.of("type", "BUG_REPORT", "severity", "HIGH", "text", "Login is slow"),
                        Map.of("type", "USER_SATISFACTION", "rating", 5, "text", "Not great")),
                    "dashboard",
                    List.of(
                        Map.of("type", "FEATURE_REQUEST", "rating", 4, "text", "Add charts")))));

    when(dbClient.query(anyString(), any(), anyInt()))
        .thenReturn(Promise.of(List.of(mockFeedback)));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("status")).isEqualTo("ANALYZED");
    assertThat(result.get("insightCount")).isNotNull();
  }

  @Test
  @DisplayName("Should fail when feedbackId is missing")
  void shouldFailWhenFeedbackIdMissing() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("runId", "run-001");
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("feedbackId");
  }
}
