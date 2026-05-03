package com.ghatana.yappc.platform.ai.analyzers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose Detects common security issues in changed code using deterministic signatures and AI fallback.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class SecurityPatternDetector {

  private static final TypeReference<List<Map<String, Object>>> ISSUE_LIST_TYPE =
      new TypeReference<>() {};

  private static final Pattern SECRET_PATTERN =
      Pattern.compile("(?i)(api[_-]?key|secret|password)\\s*[:=]\\s*[\"'][^\"']+[\"']");
  private static final Pattern SQL_CONCAT_PATTERN =
      Pattern.compile("(?i)(select|update|delete|insert).*[+].*(request|getParameter|input)");
  private static final Pattern DESERIALIZATION_PATTERN =
      Pattern.compile("ObjectInputStream|readObject\\s*\\(");

  private final YAPPCAIInterface aiService;
  private final ObjectMapper objectMapper;

  public SecurityPatternDetector(YAPPCAIInterface aiService) {
    this(aiService, new ObjectMapper());
  }

  SecurityPatternDetector(YAPPCAIInterface aiService, ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
    Objects.requireNonNull(event, "event");

    List<AIInsight> fastFindings = runRegexChecks(event);
    if (!fastFindings.isEmpty()) {
      return Promise.of(fastFindings);
    }

    Map<String, Object> context = new LinkedHashMap<>();
    context.put("filePath", event.filePath());
    context.put("tenantId", event.tenantId());

    return aiService.reason(buildPrompt(event), context).map(response -> parseInsights(response, event));
  }

  private List<AIInsight> runRegexChecks(CodeChangedEvent event) {
    String diff = event.diff();
    if (SECRET_PATTERN.matcher(diff).find()) {
      return List.of(
          createInsight(
              event,
              AIInsight.InsightSeverity.CRITICAL,
              "Hardcoded secret detected",
              "The changed code appears to embed a secret directly in source.",
              "Move the secret to validated configuration or a secret manager.",
              0,
              0.99));
    }
    if (SQL_CONCAT_PATTERN.matcher(diff).find()) {
      return List.of(
          createInsight(
              event,
              AIInsight.InsightSeverity.ERROR,
              "Potential SQL injection pattern",
              "The query construction concatenates dynamic input into SQL.",
              "Use parameterized queries or repository APIs instead of string concatenation.",
              0,
              0.96));
    }
    if (DESERIALIZATION_PATTERN.matcher(diff).find()) {
      return List.of(
          createInsight(
              event,
              AIInsight.InsightSeverity.ERROR,
              "Unsafe deserialization API",
              "The change introduces Java deserialization primitives that are risky with untrusted data.",
              "Use a safe serialization format and validate payload boundaries.",
              0,
              0.9));
    }
    return List.of();
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
              AIInsight.InsightSeverity.WARNING,
              "Manual security review recommended",
              response.trim(),
              "Review the AI response manually because the structured response could not be parsed.",
              0,
              0.5));
    }
  }

  private AIInsight fromPayload(Map<String, Object> entry, CodeChangedEvent event) {
    AIInsight.InsightSeverity severity =
        AIInsight.InsightSeverity.valueOf(
            String.valueOf(entry.getOrDefault("severity", "WARNING")).toUpperCase(Locale.ROOT));
    return createInsight(
        event,
        severity,
        String.valueOf(entry.getOrDefault("title", "Security issue")),
        String.valueOf(entry.getOrDefault("description", "")),
        String.valueOf(entry.getOrDefault("suggestion", "Review the change.")),
        asInt(entry.get("lineNumber")),
        asDouble(entry.get("confidence")));
  }

  private AIInsight createInsight(
      CodeChangedEvent event,
      AIInsight.InsightSeverity severity,
      String title,
      String description,
      String suggestion,
      int lineNumber,
      double confidence) {
    return new AIInsight(
        UUID.randomUUID().toString(),
        event.tenantId(),
        event.projectId(),
        AIInsight.InsightType.SECURITY,
        severity,
        title,
        description,
        suggestion,
        confidence,
        event.filePath(),
        lineNumber,
        List.of("security", "ai"),
        Instant.now(),
        false);
  }

  private String buildPrompt(CodeChangedEvent event) {
    return "Inspect this code change for security issues. Return a JSON array with severity, title, description, suggestion, lineNumber, and confidence."
        + "\nChanged file: "
        + event.filePath()
        + "\nDiff:\n"
        + event.diff();
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
