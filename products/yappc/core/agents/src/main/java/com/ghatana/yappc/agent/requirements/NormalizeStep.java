package com.ghatana.yappc.agent.requirements;

// ✅ Use EXISTING interfaces from libs/java
import static com.ghatana.yappc.agent.EventCloudHelper.publish;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.yappc.agent.ValidationResult;
import com.ghatana.yappc.agent.WorkflowContextAdapter;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: REQUIREMENTS / Normalize.
 *
 * <p>Normalizes and standardizes requirements format and structure.
 *
 * <p>✅ Implements WorkflowStep from libs:workflow-api (EXISTING) ✅ Uses DatabaseClient from
 * libs:database (EXISTING) ✅ Uses EventCloud from libs:event-cloud (EXISTING)
 *
 * <h3>Implementation Checklist:</h3>
 *
 * <ol>
 *   <li>Validate input against JSON schema (contract.inputSchemaRef) – fail fast.
 *   <li>Deterministic gates (rules + policies) BEFORE any intelligent call.
 *   <li>Optional intelligent calls (LLM/ML) must:
 *       <ul>
 *         <li>use pinned versions from config snapshot
 *         <li>enforce budget, retries, circuit breakers
 *         <li>record provenance (model/prompt hashes)
 *       </ul>
 *   <li>Persist entity mutations via Data-Cloud (repo).
 *   <li>Emit workflow + entity events + audit events.
 *   <li>Safe degradation: fallback provider / heuristic-only / review-only.
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Requirements phase normalize step - standardizes requirements format
 * @doc.layer product
 * @doc.pattern Service
 */
public final class NormalizeStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public NormalizeStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "requirements.normalize";
  }

  public ValidationResult validateInput(Map<String, Object> input) {
    if (input == null || input.isEmpty()) {
      return ValidationResult.fail("input is required");
    }
    if (!input.containsKey("requirementId")) {
      return ValidationResult.fail("Field 'requirementId' is required from intake step");
    }
    return ValidationResult.success();
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInputContext(context)
        .then(this::normalizeRequirements)
        .then(this::persistNormalized)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  /** Validates that requirementId exists in context from previous step. */
  private Promise<WorkflowContext> validateInputContext(WorkflowContext context) {
    Map<String, Object> data = WorkflowContextAdapter.wrap(context).getData();

    if (data == null || data.isEmpty()) {
      return Promise.ofException(
          new IllegalArgumentException("Input data is required for normalization"));
    }

    if (!data.containsKey("requirementId")) {
      return Promise.ofException(
          new IllegalArgumentException("Field 'requirementId' is required from intake step"));
    }

    return Promise.of(context);
  }

  /**
   * Normalizes requirement into standard structure. Extracts structured fields, categorizes
   * content, applies templates.
   */
  private Promise<Map<String, Object>> normalizeRequirements(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String requirementId = (String) data.get("requirementId");
    String content = (String) data.getOrDefault("content", "");
    String source = (String) data.getOrDefault("source", "unknown");

    // Normalize structure
    Map<String, Object> normalized = new HashMap<>();
    normalized.put("requirementId", requirementId);
    normalized.put("originalContent", content);
    normalized.put("normalizedContent", normalizeContent(content));
    normalized.put("source", source);
    normalized.put("category", categorizeRequirement(content));
    normalized.put("priority", extractPriority(content));
    normalized.put("type", determineRequirementType(content));
    normalized.put("normalizedAt", Instant.now().toString());
    normalized.put("tenantId", context.getTenantId());

    return Promise.of(normalized);
  }

  /** Persists normalized requirement to Data-Cloud. */
  private Promise<Map<String, Object>> persistNormalized(Map<String, Object> normalized) {
    return dbClient
        .insert("requirements_normalized", normalized)
        .map(
            result -> {
              normalized.put("persisted", true);
              normalized.put("collection", "requirements_normalized");
              return normalized;
            });
  }

  /** Publishes normalization completed event. */
  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    Map<String, Object> event =
        Map.of(
            "eventType", "requirements.normalized",
            "requirementId", data.get("requirementId"),
            "category", data.get("category"),
            "type", data.get("type"),
            "priority", data.get("priority"),
            "timestamp", Instant.now().toString());

    return publish(eventClient, "requirements.normalized", event).map($ -> data);
  }

  /** Builds output context with normalized data. */
  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext originalContext, Map<String, Object> normalized) {
    return Promise.of(
        new WorkflowContextAdapter.Builder()
            .tenantId(originalContext.getTenantId())
            .workflowId(originalContext.getWorkflowId())
            .putAll(normalized)
            .build());
  }

  /** Handles errors during normalization. */
  private Promise<WorkflowContext> handleError(Throwable error, WorkflowContext context) {
    Map<String, Object> errorEvent =
        Map.of(
            "eventType", "requirements.normalization.failed",
            "requirementId", context.getData().getOrDefault("requirementId", "unknown"),
            "error", error.getMessage(),
            "timestamp", Instant.now().toString());

    return eventClient
        .publish("requirements.errors", errorEvent)
        .then(
            $ ->
                Promise.ofException(
                    error instanceof Exception ? (Exception) error : new RuntimeException(error)));
  }

  // --- Helper Methods ---

  private String normalizeContent(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    // Normalize whitespace, remove special chars, standardize format
    return content.trim().replaceAll("\\s+", " ").replaceAll("[^a-zA-Z0-9\\s.,;:!?()-]", "");
  }

  private String categorizeRequirement(String content) {
    String lower = content.toLowerCase();
    if (lower.contains("security") || lower.contains("auth")) {
      return "security";
    } else if (lower.contains("performance") || lower.contains("speed")) {
      return "performance";
    } else if (lower.contains("ui") || lower.contains("interface")) {
      return "interface";
    } else if (lower.contains("data") || lower.contains("database")) {
      return "data";
    }
    return "functional";
  }

  private String extractPriority(String content) {
    String lower = content.toLowerCase();
    if (lower.contains("critical") || lower.contains("urgent")) {
      return "high";
    } else if (lower.contains("important") || lower.contains("should")) {
      return "medium";
    }
    return "low";
  }

  private String determineRequirementType(String content) {
    String lower = content.toLowerCase();
    if (lower.contains("must") || lower.contains("shall")) {
      return "mandatory";
    } else if (lower.contains("should") || lower.contains("prefer")) {
      return "recommended";
    }
    return "optional";
  }
}
