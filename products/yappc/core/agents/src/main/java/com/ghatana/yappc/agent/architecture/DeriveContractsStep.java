package com.ghatana.yappc.agent.architecture;

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
 * AEP Step: ARCHITECTURE / DeriveContracts.
 *
 * <p>Derives API contracts and interface definitions from architecture.
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
 * @doc.purpose Architecture phase derive contracts step - generates API contracts
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DeriveContractsStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public DeriveContractsStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "architecture.derivecontracts";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::deriveGraphQLSchema)
        .then(this::deriveRESTContracts)
        .then(this::deriveEventSchemas)
        .then(this::versionContracts)
        .then(this::persistContracts)
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

  private Promise<Map<String, Object>> deriveGraphQLSchema(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    @SuppressWarnings("unchecked")
    List<String> funcReqs = (List<String>) data.getOrDefault("functionalRequirements", List.of());

    StringBuilder schema = new StringBuilder();
    schema.append("type Query {\n");
    schema.append("  # Core queries\n");
    for (int i = 0; i < Math.min(3, funcReqs.size()); i++) {
      String entityName = extractEntityName(funcReqs.get(i));
      schema
          .append("  ")
          .append(entityName.toLowerCase())
          .append("s: [")
          .append(entityName)
          .append("!]!\n");
      schema
          .append("  ")
          .append(entityName.toLowerCase())
          .append("(id: ID!): ")
          .append(entityName)
          .append("\n");
    }
    schema.append("}\n\n");

    schema.append("type Mutation {\n");
    schema.append("  # Core mutations\n");
    for (int i = 0; i < Math.min(3, funcReqs.size()); i++) {
      String entityName = extractEntityName(funcReqs.get(i));
      schema
          .append("  create")
          .append(entityName)
          .append("(input: ")
          .append(entityName)
          .append("Input!): ")
          .append(entityName)
          .append("!\n");
      schema
          .append("  update")
          .append(entityName)
          .append("(id: ID!, input: ")
          .append(entityName)
          .append("Input!): ")
          .append(entityName)
          .append("!\n");
      schema.append("  delete").append(entityName).append("(id: ID!): Boolean!\n");
    }
    schema.append("}\n");

    Map<String, Object> graphQLContract =
        Map.of(
            "contractId", UUID.randomUUID().toString(),
            "style", "GRAPHQL",
            "schema", schema.toString(),
            "version", "v1.0",
            "status", "DRAFT");

    Map<String, Object> result = new HashMap<>(data);
    result.put("graphqlContract", graphQLContract);
    return Promise.of(result);
  }

  private Promise<Map<String, Object>> deriveRESTContracts(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    List<String> funcReqs = (List<String>) data.getOrDefault("functionalRequirements", List.of());

    List<Map<String, Object>> endpoints = new ArrayList<>();
    for (String req : funcReqs.subList(0, Math.min(3, funcReqs.size()))) {
      String entityName = extractEntityName(req);
      String resource = "/" + entityName.toLowerCase() + "s";

      endpoints.add(Map.of("method", "GET", "path", resource, "operation", "list"));
      endpoints.add(Map.of("method", "GET", "path", resource + "/{id}", "operation", "get"));
      endpoints.add(Map.of("method", "POST", "path", resource, "operation", "create"));
      endpoints.add(Map.of("method", "PUT", "path", resource + "/{id}", "operation", "update"));
      endpoints.add(Map.of("method", "DELETE", "path", resource + "/{id}", "operation", "delete"));
    }

    Map<String, Object> restContract =
        Map.of(
            "contractId", UUID.randomUUID().toString(),
            "style", "REST",
            "endpoints", endpoints,
            "version", "v1.0",
            "status", "DRAFT");

    data.put("restContract", restContract);
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> deriveEventSchemas(Map<String, Object> data) {
    List<Map<String, Object>> eventSchemas =
        List.of(
            Map.of(
                "eventType",
                "entity.created",
                "schema",
                "{id: String, type: String, timestamp: ISO8601}"),
            Map.of(
                "eventType",
                "entity.updated",
                "schema",
                "{id: String, changes: Object, timestamp: ISO8601}"),
            Map.of("eventType", "entity.deleted", "schema", "{id: String, timestamp: ISO8601}"));

    Map<String, Object> eventContract =
        Map.of(
            "contractId", UUID.randomUUID().toString(),
            "style", "EVENT",
            "schemas", eventSchemas,
            "version", "v1.0",
            "status", "DRAFT");

    data.put("eventContract", eventContract);
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> versionContracts(Map<String, Object> data) {
    data.put("contractVersion", "v1.0.0");
    data.put("backwardCompatible", true);
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> persistContracts(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    Map<String, Object> graphqlContract = (Map<String, Object>) data.get("graphqlContract");
    @SuppressWarnings("unchecked")
    Map<String, Object> restContract = (Map<String, Object>) data.get("restContract");
    @SuppressWarnings("unchecked")
    Map<String, Object> eventContract = (Map<String, Object>) data.get("eventContract");

    List<Promise<Void>> persistOps =
        List.of(
            dbClient.insert("api_contracts", graphqlContract).toVoid(),
            dbClient.insert("api_contracts", restContract).toVoid(),
            dbClient.insert("api_contracts", eventContract).toVoid());
    return io.activej.promise.Promises.all(persistOps)
        .map(
            $ -> {
              data.put("contractsPersisted", true);
              return data;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    return eventClient
        .publish(
            "architecture.contracts.derived",
            Map.of(
                "eventType",
                "architecture.contracts.derived",
                "architectureId",
                data.get("architectureId"),
                "contractCount",
                3,
                "timestamp",
                Instant.now().toString()))
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
            "eventType", "architecture.contracts.error",
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
