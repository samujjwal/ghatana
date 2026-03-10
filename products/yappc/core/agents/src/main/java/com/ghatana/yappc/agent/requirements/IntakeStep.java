package com.ghatana.yappc.agent.requirements;

// ✅ Use EXISTING interfaces from libs/java
import static com.ghatana.yappc.agent.EventCloudHelper.publish;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.yappc.agent.WorkflowContextAdapter;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: REQUIREMENTS / Intake.
 *
 * <p>Ingests raw requirements from external sources for processing.
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
 * @doc.purpose Requirements phase intake step - ingests raw requirements
 * @doc.layer product
 * @doc.pattern Service
 */
public final class IntakeStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public IntakeStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "requirements.intake";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    Instant startTime = Instant.now();
    String stepId = getStepId();

    return validateInput(context)
        .then(this::extractRequirements)
        .then(this::persistToDataCloud)
        .then(this::publishEvents)
        .then(
            result -> {
              // Build output context
              Map<String, Object> updatedData =
                  new HashMap<>(WorkflowContextAdapter.wrap(context).getData());
              updatedData.putAll(result);
              updatedData.put("_stepCompleted", stepId);
              updatedData.put("_completedAt", Instant.now().toString());

              return Promise.of(
                  new WorkflowContextAdapter.Builder()
                      .tenantId(context.getTenantId())
                      .workflowId(context.getWorkflowId())
                      .putAll(updatedData)
                      .build());
            })
        .whenException(
            error -> {
              // Handle errors gracefully
              Map<String, Object> errorEvent =
                  Map.of(
                      "stepId", stepId,
                      "error", error.getMessage(),
                      "timestamp", Instant.now().toString());
              publish(eventClient, "requirements.intake.failed", errorEvent);
            });
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = WorkflowContextAdapter.wrap(context).getData();

    // Validate required fields
    if (data == null || data.isEmpty()) {
      return Promise.ofException(new IllegalArgumentException("Input data is required"));
    }

    if (!data.containsKey("source")) {
      return Promise.ofException(new IllegalArgumentException("Field 'source' is required"));
    }

    return Promise.of(context);
  }

  private Promise<Map<String, Object>> extractRequirements(WorkflowContext context) {
    Map<String, Object> data = WorkflowContextAdapter.wrap(context).getData();
    String source = (String) data.get("source");
    String rawContent = (String) data.getOrDefault("content", "");

    // Extract and structure requirements
    Map<String, Object> extracted = new HashMap<>();
    extracted.put("requirementId", UUID.randomUUID().toString());
    extracted.put("source", source);
    extracted.put("rawContent", rawContent);
    extracted.put("extractedAt", Instant.now().toString());
    extracted.put("status", "extracted");

    return Promise.of(extracted);
  }

  private Promise<Map<String, Object>> persistToDataCloud(Map<String, Object> extracted) {
    String requirementId = (String) extracted.get("requirementId");

    // Persist to Data-Cloud requirements collection
    return dbClient
        .insert("requirements_raw", extracted)
        .map(
            insertResult -> {
              Map<String, Object> result = new HashMap<>(extracted);
              result.put("persisted", true);
              result.put("requirementId", requirementId);
              return result;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    String requirementId = (String) data.get("requirementId");

    Map<String, Object> event =
        Map.of(
            "eventType",
            "requirements.ingested",
            "requirementId",
            requirementId,
            "source",
            data.get("source"),
            "timestamp",
            Instant.now().toString());

    return publish(eventClient, "requirements.ingested", event).map($ -> data);
  }
}
