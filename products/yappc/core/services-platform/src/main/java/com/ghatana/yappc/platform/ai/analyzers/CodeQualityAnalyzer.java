package com.ghatana.yappc.platform.ai.analyzers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Detects maintainability and readability issues in changed code using deterministic rules and AI fallback.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class CodeQualityAnalyzer {

  private static final TypeReference<List<Map<String, Object>>> ISSUE_LIST_TYPE =
      new TypeReference<>() {};

  private final YAPPCAIService aiService;
  private final ObjectMapper objectMapper;

  public CodeQualityAnalyzer(YAPPCAIService aiService) {
    this(aiService, new ObjectMapper());
  }

  CodeQualityAnalyzer(YAPPCAIService aiService, ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
    Objects.requireNonNull(event, "event");

    List<AIInsight> fastFindings = runDeterministicChecks(event);
    if (!fastFindings.isEmpty()) {
      return Promise.of(fastFindings);
    }

    Map<String, Object> context = new LinkedHashMap<>();
    context.put("filePath", event.filePath());
    context.put("projectId", event.projectId());
    context.put("tenantId", event.tenantId());

    return aiService.reason(buildPrompt(event), context).map(response -> parseInsights(response, event));
  }

  private List<AIInsight> runDeterministicChecks(CodeChangedEvent event) {
    List<AIInsight> findings = new ArrayList<>();
    String diff = event.diff();

    if (diff.contains("TODO") || diff.contains("FIXME")) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.WARNING,
              "Outstanding implementation marker",
              "The change still contains TODO or FIXME markers.",
              "Replace the placeholder with production logic or track it explicitly before merge.",
              extractFirstLine(diff, "TODO"),
              List.of("maintainability", "todo"),
              0.93));
    }

    long conditionalCount = diff.lines().filter(line -> line.contains(" if ") || line.trim().startsWith("if(") || line.trim().startsWith("if (")).count();
    if (conditionalCount >= 4) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.WARNING,
              "High conditional complexity",
              "The changed hunk introduces multiple conditional branches.",
              "Split the logic into smaller units or isolate branch-heavy code behind named helpers.",
              0,
              List.of("complexity"),
              0.88));
    }

    if (diff.contains("import *") || diff.contains(".*;")) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.INFO,
              "Wildcard import introduced",
              "Wildcard imports make dependencies harder to audit.",
              "Prefer explicit imports for readability and smaller change diffs.",
              extractFirstLine(diff, "import"),
              List.of("readability", "imports"),
              0.72));
    }

    return findings;
  }

  private List<AIInsight> parseInsights(String response, CodeChangedEvent event) {
    if (response == null || response.isBlank()) {
      return List.of();
    }

    try {
      List<Map<String, Object>> payload = objectMapper.readValue(response, ISSUE_LIST_TYPE);
      return payload.stream().map(entry -> fromPayload(entry, event)).toList();
    } catch (IOException exception) {
      return List.of(
          createInsight(
              event,
              AIInsight.InsightSeverity.INFO,
              "Manual review recommended",
              response.trim(),
              "Review the AI feedback manually because the structured response could not be parsed.",
              0,
              List.of("ai-review"),
              0.5));
    }
  }

  private AIInsight fromPayload(Map<String, Object> entry, CodeChangedEvent event) {
    AIInsight.InsightSeverity severity =
        AIInsight.InsightSeverity.valueOf(
            String.valueOf(entry.getOrDefault("severity", "INFO")).toUpperCase(Locale.ROOT));
    return createInsight(
        event,
        severity,
        String.valueOf(entry.getOrDefault("title", "Code quality issue")),
        String.valueOf(entry.getOrDefault("description", "")),
        String.valueOf(entry.getOrDefault("suggestion", "Review this change.")),
        asInt(entry.get("lineNumber")),
        List.of("ai", "code-quality"),
        asDouble(entry.get("confidence")));
  }

  private AIInsight createInsight(
      CodeChangedEvent event,
      AIInsight.InsightSeverity severity,
      String title,
      String description,
      String suggestion,
      int lineNumber,
      List<String> tags,
      double confidence) {
    return new AIInsight(
        UUID.randomUUID().toString(),
        event.tenantId(),
        event.projectId(),
        AIInsight.InsightType.CODE_QUALITY,
        severity,
        title,
        description,
        suggestion,
        confidence,
        event.filePath(),
        lineNumber,
        tags,
        Instant.now(),
        false);
  }

  private String buildPrompt(CodeChangedEvent event) {
    return "Review this code change for quality issues. Return a JSON array with severity, title, description, suggestion, lineNumber, and confidence."
        + "\nChanged file: "
        + event.filePath()
        + "\nDiff:\n"
        + event.diff();
  }

  private int extractFirstLine(String diff, String marker) {
    int lineNumber = 1;
    for (String line : diff.split("\\R")) {
      if (line.contains(marker)) {
        return lineNumber;
      }
      lineNumber++;
    }
    return 0;
  }

  private int asInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null) {
      return 0.0;
    }
    return Double.parseDouble(String.valueOf(value));
  }
}
