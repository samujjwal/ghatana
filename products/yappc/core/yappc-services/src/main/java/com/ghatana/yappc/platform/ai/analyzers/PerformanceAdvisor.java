package com.ghatana.yappc.platform.ai.analyzers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Detects likely performance regressions in changed code and emits proactive optimization guidance.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class PerformanceAdvisor {

  private final YAPPCAIInterface aiService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public PerformanceAdvisor(YAPPCAIInterface aiService) {
    this(aiService, new ObjectMapper(), Clock.systemUTC());
  }

  PerformanceAdvisor(YAPPCAIInterface aiService, ObjectMapper objectMapper, Clock clock) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
    Objects.requireNonNull(event, "event");

    List<AIInsight> deterministicInsights = deterministicInsights(event);
    if (!deterministicInsights.isEmpty()) {
      return Promise.of(deterministicInsights);
    }

    return aiService.reason(buildPrompt(event), buildContext(event)).map(response -> fromAiResponse(event, response));
  }

  private List<AIInsight> deterministicInsights(CodeChangedEvent event) {
    String content = event.diff().toLowerCase(Locale.ROOT);

    if ((content.contains("for (") || content.contains("while (")) && content.contains("await ")) {
      return List.of(
          insight(
              event,
              "Await inside iterative path",
              "The changed code appears to await work inside a loop, which can serialize expensive operations.",
              "Batch the work or use bounded concurrency to avoid per-iteration latency growth.",
              AIInsight.InsightSeverity.WARNING,
              0.84));
    }

    int loopCount = countOccurrences(content, "for (") + countOccurrences(content, "while (");
    if (loopCount >= 2) {
      return List.of(
          insight(
              event,
              "Nested loop hotspot",
              "Multiple loop constructs were introduced in the same change and may create an avoidable hotspot.",
              "Revisit the algorithmic path or add indexing or caching before the workload grows.",
              AIInsight.InsightSeverity.WARNING,
              0.8));
    }

    if (content.contains("setinterval(") && !content.contains("clearinterval(")) {
      return List.of(
          insight(
              event,
              "Timer cleanup missing",
              "A repeating timer appears in the change without a matching cleanup path.",
              "Ensure the interval is cleared on teardown to prevent background work leaks.",
              AIInsight.InsightSeverity.ERROR,
              0.81));
    }

    return List.of();
  }

  private List<AIInsight> fromAiResponse(CodeChangedEvent event, String response) {
    if (response == null || response.isBlank()) {
      return List.of();
    }

    try {
      JsonNode root = objectMapper.readTree(response);
      if (!root.isObject()) {
        return List.of();
      }

      String title = root.path("title").asText();
      String description = root.path("description").asText();
      if (title.isBlank() || description.isBlank()) {
        return List.of();
      }

      return List.of(
          insight(
              event,
              title,
              description,
            textOrDefault(root.path("suggestion"), "Profile before rollout."),
              parseSeverity(root.path("severity").asText()),
              parseConfidence(root.path("confidence").asDouble(0.7))));
    } catch (IOException exception) {
      return List.of();
    }
  }

  private String buildPrompt(CodeChangedEvent event) {
    return "Review the changed code for likely performance regressions and return JSON with title, description, suggestion, severity, and confidence.\n"
        + "Source: "
        + event.sourceRef()
        + "\nDiff:\n"
          + event.diff();
  }

  private Map<String, Object> buildContext(CodeChangedEvent event) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("tenantId", event.tenantId());
    context.put("projectId", event.projectId());
    context.put("sourceRef", event.sourceRef());
    context.put("eventType", "performance");
    return context;
  }

  private AIInsight insight(
      CodeChangedEvent event,
      String title,
      String description,
      String suggestion,
      AIInsight.InsightSeverity severity,
      double confidence) {
    return new AIInsight(
        "performance-" + Integer.toHexString(Objects.hash(event.sourceRef(), title, suggestion)),
        event.tenantId(),
        event.projectId(),
        AIInsight.InsightType.PERFORMANCE,
        severity,
        title,
        description,
        suggestion,
        confidence,
        event.sourceRef(),
        0,
        List.of("performance", "background-analysis"),
        clock.instant(),
        false);
  }

  private AIInsight.InsightSeverity parseSeverity(String value) {
    return switch (Objects.requireNonNullElse(value, "").trim().toUpperCase(Locale.ROOT)) {
      case "CRITICAL" -> AIInsight.InsightSeverity.CRITICAL;
      case "ERROR" -> AIInsight.InsightSeverity.ERROR;
      case "WARNING" -> AIInsight.InsightSeverity.WARNING;
      default -> AIInsight.InsightSeverity.INFO;
    };
  }

  private double parseConfidence(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private int countOccurrences(String value, String token) {
    int count = 0;
    int index = 0;
    while ((index = value.indexOf(token, index)) >= 0) {
      count++;
      index += token.length();
    }
    return count;
  }

  private String textOrDefault(JsonNode node, String fallback) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return fallback;
    }
    String value = node.asText();
    return value.isBlank() ? fallback : value;
  }
}
