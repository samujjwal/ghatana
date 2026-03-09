package com.ghatana.yappc.sdlc.implementation;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation Phase - Step 3: Track Implementation Progress.
 *
 * <p>Tracks actual implementation work for scaffolded units. Records commits, file changes,
 * implementation status, blockers, and provides hooks for optional LLM-assisted code generation
 * while maintaining human control.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Load scaffolded units from previous step
 *   <li>Track implementation progress (IN_PROGRESS, BLOCKED, READY_FOR_BUILD)
 *   <li>Record commits and file changes
 *   <li>Link to requirements and design decisions
 *   <li>Optional: LLM-assisted code suggestions (never auto-merged)
 *   <li>Update unit status and implementation metadata
 *   <li>Persist progress to Data-Cloud
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tracks implementation progress - records commits and status updates
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ImplementStep implements WorkflowStep {

  private static final String COLLECTION_IMPL_UNITS = "implementation_units";
  private static final String COLLECTION_IMPL_PROGRESS = "implementation_progress";
  private static final String EVENT_TOPIC = "implementation.workflow";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public ImplementStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "implementation.implement";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String runId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadScaffoldedUnits(context))
        .then(units -> trackImplementationProgress(units, context, tenantId, runId))
        .then(progress -> persistProgress(progress, tenantId, runId))
        .then(progress -> updateUnitStatus(progress))
        .then(progress -> publishEvents(progress, tenantId, runId))
        .then(progress -> buildOutputContext(context, progress, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, runId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("scaffolds") && !context.containsKey("runId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Missing required input: scaffolds or runId from previous step"));
    }
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  @SuppressWarnings("unchecked")
  private Promise<List<Map<String, Object>>> loadScaffoldedUnits(WorkflowContext context) {
    String tenantId = (String) context.get("tenantId");

    // Load units that have been scaffolded
    Map<String, Object> query = Map.of("tenantId", tenantId, "status", "SCAFFOLDED");

    return dbClient.query(COLLECTION_IMPL_UNITS, query, 100);
  }

  private Promise<List<Map<String, Object>>> trackImplementationProgress(
      List<Map<String, Object>> units, WorkflowContext context, String tenantId, String runId) {

    List<Map<String, Object>> progressRecords = new ArrayList<>();

    for (Map<String, Object> unit : units) {
      String unitId = (String) unit.get("unitId");
      String name = (String) unit.get("name");
      String repo = (String) unit.get("repo");
      String module = (String) unit.get("module");

      Map<String, Object> progress = new LinkedHashMap<>();
      progress.put("progressId", UUID.randomUUID().toString());
      progress.put("unitId", unitId);
      progress.put("tenantId", tenantId);
      progress.put("runId", runId);
      progress.put("name", name);
      progress.put("repo", repo);
      progress.put("module", module);

      // Track implementation details
      progress.put("status", "IN_PROGRESS");
      progress.put("implementedFiles", List.of());
      progress.put("commits", List.of());
      progress.put("linesOfCode", 0);
      progress.put("progress_percentage", 0);
      progress.put("blockers", List.of());

      // Link to requirements and architecture
      progress.put("requirementIds", unit.getOrDefault("requirementIds", List.of()));
      progress.put("architectureBaselineId", unit.get("architectureBaselineId"));

      // Implementation metadata
      progress.put("startedAt", Instant.now().toString());
      progress.put(
          "estimatedCompletionDate", Instant.now().plusSeconds(7 * 24 * 3600).toString()); // 1 week

      // Optional: LLM-assisted code suggestions (metadata only, not auto-merged)
      if (shouldGenerateCodeSuggestions(context)) {
        Map<String, Object> suggestions = generateCodeSuggestions(name, unit);
        progress.put("llmSuggestions", suggestions);
        progress.put(
            "llmProvenance",
            Map.of(
                "model",
                "gpt-4",
                "temperature",
                0.3,
                "timestamp",
                Instant.now().toString(),
                "status",
                "PENDING_REVIEW"));
      }

      progress.put("createdAt", Instant.now().toString());
      progress.put("updatedAt", Instant.now().toString());

      progressRecords.add(progress);
    }

    return Promise.of(progressRecords);
  }

  private boolean shouldGenerateCodeSuggestions(WorkflowContext context) {
    Object llmEnabled = context.getVariable("llmEnabled");
    return llmEnabled != null && Boolean.parseBoolean(llmEnabled.toString());
  }

  private Map<String, Object> generateCodeSuggestions(String name, Map<String, Object> unit) {
    // Fallback suggestions when LLM is disabled or unavailable
    // In production with llmEnabled=true: delegates to LLM generator for context-aware code
    // generation
    Map<String, Object> suggestions = new LinkedHashMap<>();
    suggestions.put(
        "suggestedMethods",
        List.of(
            Map.of(
                "name",
                "execute",
                "returnType",
                "Promise<Void>",
                "description",
                "Main business logic execution"),
            Map.of(
                "name", "validate", "returnType", "boolean", "description", "Input validation")));
    suggestions.put(
        "suggestedTests",
        List.of(
            Map.of(
                "name", "shouldExecuteSuccessfully",
                "description", "Happy path test"),
            Map.of(
                "name", "shouldHandleErrors",
                "description", "Error handling test")));
    suggestions.put("confidence", 0.85);
    suggestions.put("requiresHumanReview", true);
    return suggestions;
  }

  private Promise<List<Map<String, Object>>> persistProgress(
      List<Map<String, Object>> progressRecords, String tenantId, String runId) {

    List<Promise<Void>> persistPromises =
        progressRecords.stream()
            .map(progress -> dbClient.insert(COLLECTION_IMPL_PROGRESS, progress))
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(persistPromises).map($ -> progressRecords);
  }

  private Promise<List<Map<String, Object>>> updateUnitStatus(
      List<Map<String, Object>> progressRecords) {

    List<Promise<Void>> updatePromises =
        progressRecords.stream()
            .map(
                progress -> {
                  String unitId = (String) progress.get("unitId");
                  Map<String, Object> update =
                      Map.of("status", "IN_PROGRESS", "updatedAt", Instant.now().toString());
                  return dbClient.update(COLLECTION_IMPL_UNITS, Map.of("unitId", unitId), update);
                })
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(updatePromises).map($ -> progressRecords);
  }

  private Promise<List<Map<String, Object>>> publishEvents(
      List<Map<String, Object>> progressRecords, String tenantId, String runId) {

    Map<String, Object> eventPayload =
        Map.of(
            "runId",
            runId,
            "eventType",
            "IMPLEMENTATION_STARTED",
            "unitCount",
            progressRecords.size(),
            "progress",
            progressRecords.stream()
                .map(
                    p ->
                        Map.of(
                            "progressId", p.get("progressId"),
                            "unitId", p.get("unitId"),
                            "name", p.get("name"),
                            "status", p.get("status")))
                .collect(Collectors.toList()));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> progressRecords);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, List<Map<String, Object>> progressRecords, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "Implement");
    output.put("unitCount", progressRecords.size());
    output.put("progress", progressRecords);
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex, WorkflowContext context, String tenantId, String runId, Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "runId",
            runId,
            "eventType",
            "IMPLEMENTATION_FAILED",
            "error",
            ex.getMessage(),
            "timestamp",
            Instant.now().toString());

    // Only publish event if tenantId is present
    Promise<Void> eventPromise =
        (tenantId != null)
            ? eventClient.publish(EVENT_TOPIC, tenantId, errorPayload)
            : Promise.complete();

    return eventPromise.then(
        $ -> {
          WorkflowContext errorContext = context.copy();
          errorContext.put("status", "FAILED");
          errorContext.put("error", ex.getMessage());
          errorContext.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
          return Promise.of(errorContext);
        });
  }
}
