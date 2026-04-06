package com.ghatana.yappc.platform.ai.analyzers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.ArchitectureChangedEvent;
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
 * @doc.purpose Surfaces architecture drift and boundary risk from structural changes before they harden into product debt.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class ArchitectureAdvisor {

  private static final TypeReference<List<Map<String, Object>>> ISSUE_LIST_TYPE =
      new TypeReference<>() {};

  private final YAPPCAIService aiService;
  private final ObjectMapper objectMapper;

  public ArchitectureAdvisor(YAPPCAIService aiService) {
    this(aiService, new ObjectMapper());
  }

  ArchitectureAdvisor(YAPPCAIService aiService, ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<List<AIInsight>> analyze(ArchitectureChangedEvent event) {
    Objects.requireNonNull(event, "event");

    List<AIInsight> deterministicFindings = runDeterministicChecks(event);
    if (!deterministicFindings.isEmpty()) {
      return Promise.of(deterministicFindings);
    }

    Map<String, Object> context = new LinkedHashMap<>();
    context.put("projectId", event.projectId());
    context.put("tenantId", event.tenantId());
    context.put("componentName", event.componentName());
    context.put("affectedModules", event.affectedModules());
    context.put("crossBoundaryChange", event.crossBoundaryChange());

    return aiService
        .reason(buildPrompt(event), context)
        .map(response -> parseInsights(response, event));
  }

  private List<AIInsight> runDeterministicChecks(ArchitectureChangedEvent event) {
    List<AIInsight> findings = new ArrayList<>();

    if (event.crossBoundaryChange()) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.WARNING,
              "Cross-boundary change detected",
              "The structural change crosses an established module boundary.",
              "Confirm the dependency direction is intentional and keep transport/domain concerns separated.",
              List.of("architecture", "boundaries"),
              0.95));
    }

    if (event.affectedModules().size() >= 4) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.WARNING,
              "High architecture blast radius",
              "The change impacts multiple modules and is likely to ripple across teams or services.",
              "Validate the change against the owning contracts and capture migration notes before rollout.",
              List.of("architecture", "blast-radius"),
              0.91));
    }

    String normalizedSummary = event.changeSummary().toLowerCase(Locale.ROOT);
    if (normalizedSummary.contains("helper") || normalizedSummary.contains("misc")) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.INFO,
              "Vague architecture abstraction",
              "The change summary suggests a generic helper-style abstraction.",
              "Use a domain-specific boundary or capability name so ownership stays explicit.",
              List.of("architecture", "naming"),
              0.74));
    }

    return findings;
  }

  private List<AIInsight> parseInsights(String response, ArchitectureChangedEvent event) {
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
              "Manual architecture review recommended",
              response.trim(),
              "Review the AI response manually because the structured response could not be parsed.",
              List.of("architecture", "ai-review"),
              0.5));
    }
  }

  private AIInsight fromPayload(Map<String, Object> entry, ArchitectureChangedEvent event) {
    AIInsight.InsightSeverity severity =
        AIInsight.InsightSeverity.valueOf(
            String.valueOf(entry.getOrDefault("severity", "WARNING")).toUpperCase(Locale.ROOT));
    return createInsight(
        event,
        severity,
        String.valueOf(entry.getOrDefault("title", "Architecture issue")),
        String.valueOf(entry.getOrDefault("description", "")),
        String.valueOf(entry.getOrDefault("suggestion", "Review the architecture update.")),
        List.of("architecture", "ai"),
        asDouble(entry.get("confidence")));
  }

  private AIInsight createInsight(
      ArchitectureChangedEvent event,
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
        AIInsight.InsightType.ARCHITECTURE,
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

  private String buildPrompt(ArchitectureChangedEvent event) {
    return "Review this architecture change for boundary violations, ownership drift, and maintainability risks. Return a JSON array with severity, title, description, suggestion, and confidence."
        + "\nComponent: "
        + event.componentName()
        + "\nSummary: "
        + event.changeSummary()
        + "\nAffected modules: "
        + event.affectedModules()
        + "\nCross boundary: "
        + event.crossBoundaryChange();
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