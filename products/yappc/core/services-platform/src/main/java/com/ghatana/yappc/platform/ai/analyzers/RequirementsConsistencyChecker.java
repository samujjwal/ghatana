package com.ghatana.yappc.platform.ai.analyzers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.RequirementChangedEvent;
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
 * @doc.purpose Detects incomplete, duplicate, or contradictory requirement changes before they become user-visible noise.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class RequirementsConsistencyChecker {

  private static final TypeReference<List<Map<String, Object>>> ISSUE_LIST_TYPE =
      new TypeReference<>() {};

  private final YAPPCAIService aiService;
  private final ObjectMapper objectMapper;

  public RequirementsConsistencyChecker(YAPPCAIService aiService) {
    this(aiService, new ObjectMapper());
  }

  RequirementsConsistencyChecker(YAPPCAIService aiService, ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<List<AIInsight>> analyze(RequirementChangedEvent event) {
    Objects.requireNonNull(event, "event");

    List<AIInsight> deterministicFindings = runDeterministicChecks(event);
    if (!deterministicFindings.isEmpty()) {
      return Promise.of(deterministicFindings);
    }

    Map<String, Object> context = new LinkedHashMap<>();
    context.put("projectId", event.projectId());
    context.put("tenantId", event.tenantId());
    context.put("requirementId", event.requirementId());
    context.put("title", event.title());
    context.put("relatedRequirementSummaries", event.relatedRequirementSummaries());

    return aiService
        .reason(buildPrompt(event), context)
        .map(response -> parseInsights(response, event));
  }

  private List<AIInsight> runDeterministicChecks(RequirementChangedEvent event) {
    List<AIInsight> findings = new ArrayList<>();
    String normalizedText = normalize(event.requirementText());

    if (normalizedText.length() < 40) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.WARNING,
              "Requirement lacks implementation detail",
              "The updated requirement is too short to communicate reliable acceptance scope.",
              "Expand the requirement with acceptance criteria, constraints, and expected outcomes.",
              List.of("requirements", "detail"),
              0.94));
    }

    if (containsPlaceholder(event.requirementText()) || containsPlaceholder(event.title())) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.WARNING,
              "Requirement contains placeholders",
              "The requirement still contains TODO/TBD style placeholders.",
              "Replace placeholder text before surfacing this requirement to downstream automation.",
              List.of("requirements", "placeholder"),
              0.96));
    }

    boolean duplicate =
        event.relatedRequirementSummaries().stream()
            .map(this::normalize)
            .anyMatch(summary -> !summary.isBlank() && summary.equals(normalizedText));
    if (duplicate) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.ERROR,
              "Potential duplicate requirement",
              "A related requirement already matches this requirement text.",
              "Merge the duplicate or clarify the new requirement so it adds distinct value.",
              List.of("requirements", "duplicate"),
              0.92));
    }

    return findings;
  }

  private List<AIInsight> parseInsights(String response, RequirementChangedEvent event) {
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
              "Manual requirement review recommended",
              response.trim(),
              "Review the AI feedback manually because the structured response could not be parsed.",
              List.of("requirements", "ai-review"),
              0.5));
    }
  }

  private AIInsight fromPayload(Map<String, Object> entry, RequirementChangedEvent event) {
    AIInsight.InsightSeverity severity =
        AIInsight.InsightSeverity.valueOf(
            String.valueOf(entry.getOrDefault("severity", "WARNING")).toUpperCase(Locale.ROOT));
    return createInsight(
        event,
        severity,
        String.valueOf(entry.getOrDefault("title", "Requirement issue")),
        String.valueOf(entry.getOrDefault("description", "")),
        String.valueOf(entry.getOrDefault("suggestion", "Review the requirement update.")),
        List.of("requirements", "ai"),
        asDouble(entry.get("confidence")));
  }

  private AIInsight createInsight(
      RequirementChangedEvent event,
      AIInsight.InsightSeverity severity,
      String title,
      String description,
      String suggestion,
      List<String> tags,
      double confidence) {
    return new AIInsight(
        UUID.randomUUID().toString(),
        event.tenantId(),
        event.projectId(),
        AIInsight.InsightType.REQUIREMENT,
        severity,
        title,
        description,
        suggestion,
        confidence,
        event.sourceRef(),
        0,
        tags,
        Instant.now(),
        false);
  }

  private boolean containsPlaceholder(String value) {
    String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
    return normalized.contains("TODO") || normalized.contains("TBD") || normalized.contains("FIXME");
  }

  private String buildPrompt(RequirementChangedEvent event) {
    return "Review this requirement change for duplication, ambiguity, and consistency issues. Return a JSON array with severity, title, description, suggestion, and confidence."
        + "\nRequirement ID: "
        + event.requirementId()
        + "\nTitle: "
        + event.title()
        + "\nBody: "
        + event.requirementText()
        + "\nRelated requirements: "
        + event.relatedRequirementSummaries();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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