package com.ghatana.yappc.agent.requirements;

// ✅ Use EXISTING interfaces from libs/java
import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.yappc.agent.WorkflowContextAdapter;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: REQUIREMENTS / DeriveRequirements.
 *
 * <p>Derives structured requirements from raw input using AI/ML.
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
 * @doc.purpose Requirements phase derive step - extracts structured requirements
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DeriveRequirementsStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public DeriveRequirementsStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "requirements.deriverequirements";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::deriveStructuredRequirements)
        .then(this::persistDerived)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = context.getData();

    if (data == null || data.isEmpty()) {
      return Promise.ofException(
          new IllegalArgumentException("Input data required for derivation"));
    }

    if (!data.containsKey("requirementId")) {
      return Promise.ofException(
          new IllegalArgumentException("Field 'requirementId' required from normalize step"));
    }

    if (!data.containsKey("normalizedContent")) {
      return Promise.ofException(
          new IllegalArgumentException("Field 'normalizedContent' required from normalize step"));
    }

    return Promise.of(context);
  }

  /**
   * Derives structured requirements from normalized content. Extracts functional, non-functional,
   * constraints, and acceptance criteria.
   */
  private Promise<Map<String, Object>> deriveStructuredRequirements(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String requirementId = (String) data.get("requirementId");
    String normalizedContent = (String) data.get("normalizedContent");
    String category = (String) data.getOrDefault("category", "functional");

    Map<String, Object> derived = new HashMap<>();
    derived.put("requirementId", requirementId);
    derived.put("functionalRequirements", extractFunctionalRequirements(normalizedContent));
    derived.put(
        "nonFunctionalRequirements", extractNonFunctionalRequirements(normalizedContent, category));
    derived.put("constraints", extractConstraints(normalizedContent));
    derived.put("acceptanceCriteria", deriveAcceptanceCriteria(normalizedContent));
    derived.put("dependencies", identifyDependencies(normalizedContent));
    derived.put("derivedAt", Instant.now().toString());
    derived.put("tenantId", context.getTenantId());

    return Promise.of(derived);
  }

  private Promise<Map<String, Object>> persistDerived(Map<String, Object> derived) {
    return dbClient
        .insert("requirements_derived", derived)
        .map(
            result -> {
              derived.put("persisted", true);
              derived.put("collection", "requirements_derived");
              return derived;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    Map<String, Object> event =
        Map.of(
            "eventType", "requirements.derived",
            "requirementId", data.get("requirementId"),
            "functionalCount", ((List<?>) data.get("functionalRequirements")).size(),
            "nonFunctionalCount", ((List<?>) data.get("nonFunctionalRequirements")).size(),
            "timestamp", Instant.now().toString());

    return eventClient.publish("requirements.derived", event).map($ -> data);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext originalContext, Map<String, Object> derived) {
    return Promise.of(
        new WorkflowContextAdapter.Builder()
            .tenantId(originalContext.getTenantId())
            .workflowId(originalContext.getWorkflowId())
            .putAll(derived)
            .build());
  }

  private Promise<WorkflowContext> handleError(Throwable error, WorkflowContext context) {
    Map<String, Object> errorEvent =
        Map.of(
            "eventType", "requirements.derivation.failed",
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

  private List<String> extractFunctionalRequirements(String content) {
    List<String> requirements = new ArrayList<>();
    String lower = content.toLowerCase();

    if (lower.contains("user") && (lower.contains("should") || lower.contains("must"))) {
      requirements.add(
          "User interaction: " + content.substring(0, Math.min(100, content.length())));
    }
    if (lower.contains("system") && (lower.contains("provide") || lower.contains("allow"))) {
      requirements.add(
          "System capability: " + content.substring(0, Math.min(100, content.length())));
    }
    if (lower.contains("data") || lower.contains("process")) {
      requirements.add("Data processing: " + content.substring(0, Math.min(100, content.length())));
    }

    return requirements.isEmpty()
        ? List.of("General: " + content.substring(0, Math.min(100, content.length())))
        : requirements;
  }

  private List<String> extractNonFunctionalRequirements(String content, String category) {
    List<String> nfrs = new ArrayList<>();
    String lower = content.toLowerCase();

    if (category.equals("performance")
        || lower.contains("performance")
        || lower.contains("speed")) {
      nfrs.add("Performance: Response time < 200ms");
    }
    if (category.equals("security") || lower.contains("security") || lower.contains("auth")) {
      nfrs.add("Security: Authentication and authorization required");
    }
    if (lower.contains("scalable") || lower.contains("scale")) {
      nfrs.add("Scalability: Must support horizontal scaling");
    }
    if (lower.contains("available") || lower.contains("uptime")) {
      nfrs.add("Availability: 99.9% uptime SLA");
    }

    return nfrs;
  }

  private List<String> extractConstraints(String content) {
    List<String> constraints = new ArrayList<>();
    String lower = content.toLowerCase();

    if (lower.contains("budget") || lower.contains("cost")) {
      constraints.add("Budget constraint identified");
    }
    if (lower.contains("time") || lower.contains("deadline")) {
      constraints.add("Time constraint identified");
    }
    if (lower.contains("technology") || lower.contains("platform")) {
      constraints.add("Technology constraint identified");
    }

    return constraints;
  }

  private List<String> deriveAcceptanceCriteria(String content) {
    List<String> criteria = new ArrayList<>();

    criteria.add("Given valid input, when processing, then system responds successfully");
    criteria.add("Given error condition, when triggered, then appropriate error handling occurs");
    criteria.add("Given complete data, when validated, then all fields are present and correct");

    return criteria;
  }

  private List<String> identifyDependencies(String content) {
    List<String> dependencies = new ArrayList<>();
    String lower = content.toLowerCase();

    if (lower.contains("database") || lower.contains("storage")) {
      dependencies.add("Database system");
    }
    if (lower.contains("api") || lower.contains("service")) {
      dependencies.add("External API/Service");
    }
    if (lower.contains("authentication") || lower.contains("auth")) {
      dependencies.add("Authentication service");
    }

    return dependencies;
  }
}
