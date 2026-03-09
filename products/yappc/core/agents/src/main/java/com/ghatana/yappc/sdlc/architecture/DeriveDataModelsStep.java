package com.ghatana.yappc.sdlc.architecture;

// ✅ Use EXISTING interfaces from libs/java
import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.yappc.sdlc.WorkflowContextAdapter;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: ARCHITECTURE / DeriveDataModels.
 *
 * <p>Derives data models and entity definitions from architecture.
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
 * @doc.purpose Architecture phase derive data models step - generates entity schemas
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DeriveDataModelsStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public DeriveDataModelsStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "architecture.derivedatamodels";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::deriveLogicalModel)
        .then(this::derivePhysicalModel)
        .then(this::generateMigrations)
        .then(this::createTraceLinks)
        .then(this::persistDataModels)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    if (data == null || !data.containsKey("architectureId")) {
      return Promise.ofException(new IllegalArgumentException("architectureId required"));
    }
    return Promise.of(context);
  }

  private Promise<Map<String, Object>> deriveLogicalModel(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    @SuppressWarnings("unchecked")
    List<String> funcReqs = (List<String>) data.getOrDefault("functionalRequirements", List.of());

    List<Map<String, Object>> entities = new ArrayList<>();
    for (String req : funcReqs.subList(0, Math.min(3, funcReqs.size()))) {
      String entityName = extractEntityName(req);
      entities.add(
          Map.of(
              "name", entityName,
              "attributes",
                  List.of(
                      Map.of("name", "id", "type", "UUID", "required", true),
                      Map.of("name", "name", "type", "String", "required", true),
                      Map.of("name", "description", "type", "String", "required", false),
                      Map.of("name", "createdAt", "type", "Timestamp", "required", true),
                      Map.of("name", "updatedAt", "type", "Timestamp", "required", true)),
              "relationships", List.of()));
    }

    Map<String, Object> logicalModel =
        Map.of(
            "entities", entities,
            "relationships", List.of(),
            "constraints", List.of("All entities must have id and timestamps"));

    Map<String, Object> result = new HashMap<>(data);
    result.put("logicalModel", logicalModel);
    return Promise.of(result);
  }

  private Promise<Map<String, Object>> derivePhysicalModel(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    Map<String, Object> logicalModel = (Map<String, Object>) data.get("logicalModel");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entities = (List<Map<String, Object>>) logicalModel.get("entities");

    List<Map<String, Object>> tables = new ArrayList<>();
    for (Map<String, Object> entity : entities) {
      String tableName = entity.get("name").toString().toLowerCase() + "s";
      tables.add(
          Map.of(
              "tableName", tableName,
              "columns", entity.get("attributes"),
              "indexes",
                  List.of(
                      Map.of(
                          "name",
                          "idx_" + tableName + "_id",
                          "columns",
                          List.of("id"),
                          "unique",
                          true),
                      Map.of(
                          "name",
                          "idx_" + tableName + "_created",
                          "columns",
                          List.of("createdAt"),
                          "unique",
                          false)),
              "constraints",
                  List.of(
                      Map.of("type", "PRIMARY_KEY", "columns", List.of("id")),
                      Map.of(
                          "type",
                          "NOT_NULL",
                          "columns",
                          List.of("id", "name", "createdAt", "updatedAt")))));
    }

    Map<String, Object> physicalModel =
        Map.of(
            "database",
            "PostgreSQL",
            "tables",
            tables,
            "indexes",
            tables.stream().mapToLong(t -> ((List<?>) t.get("indexes")).size()).sum(),
            "schema",
            "public");

    data.put("physicalModel", physicalModel);
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> generateMigrations(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    Map<String, Object> physicalModel = (Map<String, Object>) data.get("physicalModel");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> tables = (List<Map<String, Object>>) physicalModel.get("tables");

    List<String> migrations = new ArrayList<>();
    for (Map<String, Object> table : tables) {
      String tableName = table.get("tableName").toString();
      StringBuilder migration = new StringBuilder();
      migration.append("CREATE TABLE ").append(tableName).append(" (\n");
      migration.append("  id UUID PRIMARY KEY,\n");
      migration.append("  name VARCHAR(255) NOT NULL,\n");
      migration.append("  description TEXT,\n");
      migration.append("  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n");
      migration.append("  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP\n");
      migration.append(");");
      migrations.add(migration.toString());

      // Add index creation
      migrations.add("CREATE UNIQUE INDEX idx_" + tableName + "_id ON " + tableName + " (id);");
      migrations.add(
          "CREATE INDEX idx_" + tableName + "_created ON " + tableName + " (created_at);");
    }

    data.put("migrations", migrations);
    data.put("migrationCount", migrations.size());
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> createTraceLinks(Map<String, Object> data) {
    String architectureId = data.get("architectureId").toString();
    @SuppressWarnings("unchecked")
    List<String> funcReqs = (List<String>) data.getOrDefault("functionalRequirements", List.of());

    List<Map<String, Object>> traceLinks = new ArrayList<>();
    for (int i = 0; i < funcReqs.size(); i++) {
      traceLinks.add(
          Map.of(
              "sourcId", "FR-" + (i + 1),
              "sourceType", "functional_requirement",
              "targetId", architectureId,
              "targetType", "data_model",
              "linkType", "realizes"));
    }

    data.put("traceLinks", traceLinks);
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> persistDataModels(Map<String, Object> data) {
    return dbClient
        .insert("data_models", data)
        .map(
            $ -> {
              data.put("modelsPersisted", true);
              return data;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    return eventClient
        .publish(
            "architecture.datamodels.derived",
            Map.of(
                "eventType", "architecture.datamodels.derived",
                "architectureId", data.get("architectureId"),
                "entityCount",
                    ((List<?>) ((Map<?, ?>) data.get("logicalModel")).get("entities")).size(),
                "timestamp", Instant.now().toString()))
        .map($ -> data);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext originalContext, Map<String, Object> results) {
    return Promise.of(
        WorkflowContextAdapter.builder()
            .tenantId(originalContext.getTenantId())
            .workflowId(originalContext.getWorkflowId())
            .putAll(results)
            .build());
  }

  private void handleError(Throwable error, WorkflowContext context) {
    eventClient.publish(
        "architecture.errors",
        Map.of(
            "eventType", "architecture.datamodels.error",
            "error", error.getMessage(),
            "timestamp", Instant.now().toString()));
  }

  private String extractEntityName(String requirement) {
    String[] words = requirement.split(" ");
    for (String word : words) {
      if (word.length() > 3 && Character.isUpperCase(word.charAt(0))) {
        return word.replaceAll("[^a-zA-Z]", "");
      }
    }
    return "Entity";
  }
}
