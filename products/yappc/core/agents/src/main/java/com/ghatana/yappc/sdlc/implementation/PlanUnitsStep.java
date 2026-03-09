package com.ghatana.yappc.sdlc.implementation;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation Phase - Step 1: Plan Implementation Units.
 *
 * <p>Maps approved architecture components to concrete implementation units with repo/module
 * targets. Analyzes architecture baseline, breaks down containers/components into executable work
 * units, estimates complexity, and persists the unit plan for downstream scaffolding and
 * implementation.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Load architecture baseline (architecture_published)
 *   <li>Extract containers and components from C4 views
 *   <li>Map each component to implementation units (classes, modules, endpoints)
 *   <li>Assign repo/branch/module targets
 *   <li>Estimate complexity (LOC, story points)
 *   <li>Persist to implementation_units collection
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Plans implementation units from architecture - work breakdown structure
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PlanUnitsStep implements WorkflowStep {

  private static final String COLLECTION_IMPL_UNITS = "implementation_units";
  private static final String COLLECTION_ARCH_PUBLISHED = "architecture_published";
  private static final String EVENT_TOPIC = "implementation.workflow";

  private static final Pattern COMPONENT_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");
  private static final Pattern CONTAINER_PATTERN = Pattern.compile("Container\\(([^,]+),");

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public PlanUnitsStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "implementation.plan_units";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String runId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadArchitectureBaseline(context))
        .then(archData -> planImplementationUnits(archData, tenantId, runId))
        .then(units -> persistUnits(units, tenantId, runId))
        .then(units -> publishEvents(units, tenantId, runId))
        .then(units -> buildOutputContext(context, units, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, runId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("architectureBaselineId")) {
      return Promise.ofException(
          new IllegalArgumentException("Missing required input: architectureBaselineId"));
    }
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  private Promise<Map<String, Object>> loadArchitectureBaseline(WorkflowContext context) {
    String baselineId = (String) context.get("architectureBaselineId");
    String tenantId = (String) context.get("tenantId");

    Map<String, Object> query =
        Map.of(
            "baselineId", baselineId,
            "tenantId", tenantId);

    return dbClient
        .query(COLLECTION_ARCH_PUBLISHED, query, 1)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("Architecture baseline not found: " + baselineId);
              }
              return results.get(0);
            });
  }

  private Promise<List<Map<String, Object>>> planImplementationUnits(
      Map<String, Object> archData, String tenantId, String runId) {

    List<Map<String, Object>> units = new ArrayList<>();

    // Extract architecture details
    String architectureBaselineId = (String) archData.get("baselineId");
    @SuppressWarnings("unchecked")
    Map<String, Object> archContent = (Map<String, Object>) archData.get("content");

    // Extract containers from C4 Container view
    String c4Container = (String) archContent.getOrDefault("c4ContainerView", "");
    List<String> containers = extractContainers(c4Container);

    // Extract components from C4 Component view
    String c4Component = (String) archContent.getOrDefault("c4ComponentView", "");
    List<String> components = extractComponents(c4Component);

    // Plan units for each container
    for (String container : containers) {
      String unitId = UUID.randomUUID().toString();
      Map<String, Object> unit = new LinkedHashMap<>();
      unit.put("unitId", unitId);
      unit.put("tenantId", tenantId);
      unit.put("runId", runId);
      unit.put("architectureBaselineId", architectureBaselineId);
      unit.put("type", "CONTAINER");
      unit.put("name", container);
      unit.put("repo", determineRepo(container));
      unit.put("module", determineModule(container));
      unit.put("branch", "feature/impl-" + sanitize(container));
      unit.put("estimatedLOC", estimateComplexity(container, "container"));
      unit.put("status", "PLANNED");
      unit.put("components", List.of());
      unit.put("createdAt", Instant.now().toString());
      unit.put("updatedAt", Instant.now().toString());
      units.add(unit);
    }

    // Plan units for each component
    for (String component : components) {
      String unitId = UUID.randomUUID().toString();
      Map<String, Object> unit = new LinkedHashMap<>();
      unit.put("unitId", unitId);
      unit.put("tenantId", tenantId);
      unit.put("runId", runId);
      unit.put("architectureBaselineId", architectureBaselineId);
      unit.put("type", "COMPONENT");
      unit.put("name", component);
      unit.put("repo", determineRepo(component));
      unit.put("module", determineModule(component));
      unit.put("branch", "feature/impl-" + sanitize(component));
      unit.put("estimatedLOC", estimateComplexity(component, "component"));
      unit.put("status", "PLANNED");
      unit.put("classes", List.of());
      unit.put("createdAt", Instant.now().toString());
      unit.put("updatedAt", Instant.now().toString());
      units.add(unit);
    }

    return Promise.of(units);
  }

  private List<String> extractContainers(String c4ContainerView) {
    List<String> containers = new ArrayList<>();
    Matcher matcher = CONTAINER_PATTERN.matcher(c4ContainerView);
    while (matcher.find()) {
      String container = matcher.group(1).trim();
      if (!container.isEmpty()) {
        containers.add(container);
      }
    }
    return containers;
  }

  private List<String> extractComponents(String c4ComponentView) {
    List<String> components = new ArrayList<>();
    Matcher matcher = COMPONENT_PATTERN.matcher(c4ComponentView);
    while (matcher.find()) {
      String component = matcher.group(1).trim();
      if (!component.isEmpty() && !component.contains(":")) {
        components.add(component);
      }
    }
    return components;
  }

  private String determineRepo(String name) {
    String lower = name.toLowerCase();
    if (lower.contains("api") || lower.contains("service")) {
      return "ghatana-services";
    } else if (lower.contains("ui") || lower.contains("frontend")) {
      return "ghatana-frontend";
    } else if (lower.contains("database") || lower.contains("storage")) {
      return "ghatana-data";
    }
    return "ghatana-core";
  }

  private String determineModule(String name) {
    return "products/" + sanitize(name);
  }

  private String sanitize(String name) {
    return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  private int estimateComplexity(String name, String type) {
    int base = type.equals("container") ? 500 : 200;
    if (name.toLowerCase().contains("api")) {
      return base + 300;
    } else if (name.toLowerCase().contains("database")) {
      return base + 200;
    } else if (name.toLowerCase().contains("ui")) {
      return base + 150;
    }
    return base;
  }

  private Promise<List<Map<String, Object>>> persistUnits(
      List<Map<String, Object>> units, String tenantId, String runId) {

    List<Promise<Void>> persistPromises =
        units.stream()
            .map(unit -> dbClient.insert(COLLECTION_IMPL_UNITS, unit))
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(persistPromises).map($ -> units);
  }

  private Promise<List<Map<String, Object>>> publishEvents(
      List<Map<String, Object>> units, String tenantId, String runId) {

    Map<String, Object> eventPayload =
        Map.of(
            "runId",
            runId,
            "eventType",
            "PLAN_UNITS_COMPLETED",
            "unitCount",
            units.size(),
            "units",
            units.stream()
                .map(
                    u ->
                        Map.of(
                            "unitId", u.get("unitId"),
                            "type", u.get("type"),
                            "name", u.get("name"),
                            "estimatedLOC", u.get("estimatedLOC")))
                .collect(Collectors.toList()));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> units);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, List<Map<String, Object>> units, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "PlanUnits");
    output.put("unitCount", units.size());
    output.put("units", units);
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
            "PLAN_UNITS_FAILED",
            "error",
            ex.getMessage(),
            "timestamp",
            Instant.now().toString());

    return eventClient
        .publish(EVENT_TOPIC, tenantId, errorPayload)
        .then(
            $ -> {
              WorkflowContext errorContext = context.copy();
              errorContext.put("status", "FAILED");
              errorContext.put("error", ex.getMessage());
              errorContext.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
              return Promise.of(errorContext);
            });
  }
}
