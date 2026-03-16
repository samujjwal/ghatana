package com.ghatana.appplatform.workflow;

import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.workflow.SagaPolicy;
import com.ghatana.platform.workflow.runtime.WorkflowDefinitionRegistry;
import com.ghatana.platform.workflow.runtime.WorkflowStepDefinition;
import com.ghatana.platform.workflow.runtime.WorkflowStepKind;
import com.ghatana.platform.workflow.runtime.WorkflowTriggerType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.json.PlatformObjectMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Core workflow orchestration — defines, stores, and versions workflow DSL documents.
 *              Workflow defined in YAML/JSON with steps: TASK, DECISION, PARALLEL, WAIT, SUB_WORKFLOW.
 *              Supports references to registered step templates, human-task form schemas, and
 *              value catalogs so jurisdictions/operators can adjust step order, choices, and
 *              routing data without engine code changes.
 *              Full version history with co-existing versions for in-flight instances.
 *              Emits WorkflowDefined event to K-05 on every publish.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class WorkflowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);
    private static final ObjectMapper MAPPER = PlatformObjectMapper.instance();

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface DslParserPort {
        /** Parse a YAML or JSON workflow definition. Returns normalized definition JSON. */
        Promise<String> parse(String rawContent, String format);  // format: "YAML" | "JSON"
    }

    public interface StepTemplateRegistryPort {
        /** Validate that steps referencing templates resolve to known registered templates. */
        Promise<List<String>> validateTemplateRefs(String definitionJson);  // returns list of unresolved refs
    }

    public interface ValueCatalogPort {
        /** Validate that value catalog refs in the definition exist in K-02. */
        Promise<List<String>> validateCatalogRefs(String definitionJson);   // returns list of unresolved refs
    }

    public interface EventBusPort {
        Promise<Void> publish(String topic, String eventType, String payloadJson);
    }

    // -----------------------------------------------------------------------
    // Records and Enums
    // -----------------------------------------------------------------------

    public enum TriggerType { EVENT, SCHEDULE, MANUAL, API_WEBHOOK }

    public enum StepType { TASK, DECISION, PARALLEL, WAIT, SUB_WORKFLOW }

    public record WorkflowStep(
        String stepId,
        StepType type,
        String name,
        String taskRef,          // for TASK: microservice call reference
        String condition,        // for DECISION: CEL expression
        List<String> branches,   // for PARALLEL: sub-step IDs
        String waitEventType,    // for WAIT: correlated event type
        String waitDuration,     // for WAIT: ISO 8601 duration
        String subWorkflowId,    // for SUB_WORKFLOW: target workflow ID
        String errorHandling     // JSON: {retry, catch, compensation}
    ) {}

    public record WorkflowDefinition(
        String definitionId,
        String workflowId,
        String name,
        int version,
        TriggerType triggerType,
        String triggerConfig,    // JSON: topic/cron/webhook config
        List<WorkflowStep> steps,
        String errorHandling,    // top-level error handling JSON
        String timeoutIso,
        String status,           // DRAFT | ACTIVE | DEPRECATED | ARCHIVED
        String createdBy,
        String createdAt
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final WorkflowDefinitionRegistry definitionRegistry;
    private final Executor executor;
    private final DslParserPort dslParser;
    private final StepTemplateRegistryPort templateRegistry;
    private final ValueCatalogPort valueCatalog;
    private final EventBusPort eventBus;
    private final AuditBusPort auditPort;

    private final Counter definedTotal;
    private final Counter parseFailedTotal;

    private static final String TOPIC_WORKFLOW_EVENTS = "platform.workflow.events";

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public WorkflowDefinitionService(WorkflowDefinitionRegistry definitionRegistry,
                                      Executor executor,
                                      MeterRegistry meterRegistry,
                                      DslParserPort dslParser,
                                      StepTemplateRegistryPort templateRegistry,
                                      ValueCatalogPort valueCatalog,
                                      EventBusPort eventBus,
                                      AuditBusPort auditPort) {
        this.definitionRegistry = definitionRegistry;
        this.executor        = executor;
        this.dslParser       = dslParser;
        this.templateRegistry = templateRegistry;
        this.valueCatalog    = valueCatalog;
        this.eventBus        = eventBus;
        this.auditPort       = auditPort;

        this.definedTotal    = Counter.builder("workflow.definitions.defined_total")
                .description("Total workflow definitions published")
                .register(meterRegistry);
        this.parseFailedTotal = Counter.builder("workflow.definitions.parse_failed_total")
                .description("Workflow DSL parse/validation failures")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Publish a new workflow definition (creates a new version if workflowId already exists).
     * Pipeline: parse DSL → validate template refs → validate catalog refs → persist → emit event.
     */
    public Promise<WorkflowDefinition> publishDefinition(String workflowId, String name,
                                                          String rawContent, String format,
                                                          TriggerType triggerType,
                                                          String triggerConfigJson,
                                                          String timeoutIso, String createdBy) {
        return dslParser.parse(rawContent, format)
            .then(definitionJson -> templateRegistry.validateTemplateRefs(definitionJson)
                .then(unresolvedTemplates -> {
                    if (!unresolvedTemplates.isEmpty()) {
                        parseFailedTotal.increment();
                        return Promise.ofException(new IllegalArgumentException(
                            "Unresolved step template references: " + unresolvedTemplates));
                    }
                    return valueCatalog.validateCatalogRefs(definitionJson);
                })
                .then(unresolvedCatalogs -> {
                    if (!unresolvedCatalogs.isEmpty()) {
                        parseFailedTotal.increment();
                        return Promise.ofException(new IllegalArgumentException(
                            "Unresolved value catalog references: " + unresolvedCatalogs));
                    }
                    List<WorkflowStepDefinition> platformSteps = toPlatformSteps(definitionJson);
                    String entryStepId = resolveEntryStepId(definitionJson, platformSteps);
                    return definitionRegistry.findLatest(workflowId)
                        .then(existing -> {
                            int version = existing.map(d -> d.version() + 1).orElse(1);
                            var platformDef = new com.ghatana.platform.workflow.runtime.WorkflowDefinition(
                                workflowId, name, version,
                                toPlatformTrigger(triggerType), triggerConfigJson,
                                platformSteps, entryStepId,
                                timeoutIso != null ? Duration.parse(timeoutIso) : null,
                                SagaPolicy.NONE, Map.of("createdBy", createdBy), Instant.now(), true);
                            return definitionRegistry.register(platformDef).map(v -> {
                                definedTotal.increment();
                                var appDef = toAppDefinition(platformDef, definitionJson);
                                eventBus.publish(TOPIC_WORKFLOW_EVENTS, "WorkflowDefined",
                                    buildDefinedEventJson(workflowId + "@" + version, workflowId, name, version));
                                auditPort.emit(AuditEvent.builder()
                                    .eventType("WORKFLOW_DEFINED").principal(createdBy)
                                    .resourceId(workflowId + "@" + version)
                                    .resourceType("WORKFLOW_DEFINITION")
                                    .details(Map.of("version", String.valueOf(version)))
                                    .build());
                                return appDef;
                            });
                        });
                }));
    }

    /** Activate a specific version (marks it as the default for new instances). */
    public Promise<Void> activateVersion(String workflowId, int version, String activatedBy) {
        return definitionRegistry.findByVersion(workflowId, version)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new RuntimeException(
                        "Definition not found: " + workflowId + "@" + version));
                }
                var def = opt.get();
                var enabled = new com.ghatana.platform.workflow.runtime.WorkflowDefinition(
                    def.workflowId(), def.name(), def.version(), def.triggerType(),
                    def.triggerFilter(), def.steps(), def.entryStepId(), def.timeout(),
                    def.sagaPolicy(), def.metadata(), def.createdAt(), true);
                return definitionRegistry.register(enabled).map(v -> {
                    auditPort.emit(AuditEvent.builder().eventType("WORKFLOW_ACTIVATED")
                        .principal(activatedBy).resourceId(workflowId + "@" + version)
                        .resourceType("WORKFLOW_DEFINITION").details(Map.of()).build());
                    return (Void) null;
                });
            });
    }

    /** Deprecate a version: existing instances on that version continue, new triggers rejected. */
    public Promise<Void> deprecateVersion(String workflowId, int version, String deprecatedBy) {
        return definitionRegistry.findByVersion(workflowId, version)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new RuntimeException(
                        "Definition not found: " + workflowId + "@" + version));
                }
                var def = opt.get();
                var disabled = new com.ghatana.platform.workflow.runtime.WorkflowDefinition(
                    def.workflowId(), def.name(), def.version(), def.triggerType(),
                    def.triggerFilter(), def.steps(), def.entryStepId(), def.timeout(),
                    def.sagaPolicy(), def.metadata(), def.createdAt(), false);
                return definitionRegistry.register(disabled).map(v -> {
                    auditPort.emit(AuditEvent.builder().eventType("WORKFLOW_DEPRECATED")
                        .principal(deprecatedBy).resourceId(workflowId + "@" + version)
                        .resourceType("WORKFLOW_DEFINITION").details(Map.of()).build());
                    return (Void) null;
                });
            });
    }

    /** Get a specific workflow definition version. */
    public Promise<WorkflowDefinition> getDefinition(String workflowId, int version) {
        return definitionRegistry.findByVersion(workflowId, version)
            .map(opt -> opt.map(this::toAppDefinitionSimple).orElseThrow(
                () -> new RuntimeException("Definition not found: " + workflowId + "@" + version)));
    }

    /** Get the active (latest ACTIVE) version of a workflow. */
    public Promise<WorkflowDefinition> getActiveDefinition(String workflowId) {
        return definitionRegistry.findLatest(workflowId)
            .map(opt -> opt.filter(com.ghatana.platform.workflow.runtime.WorkflowDefinition::enabled)
                .map(this::toAppDefinitionSimple)
                .orElseThrow(() -> new RuntimeException("No active definition for: " + workflowId)));
    }

    /** List all versions of a workflow (returns latest only via platform registry). */
    public Promise<List<WorkflowDefinition>> listVersions(String workflowId) {
        return definitionRegistry.listAll()
            .map(all -> all.stream()
                .filter(d -> d.workflowId().equals(workflowId))
                .map(this::toAppDefinitionSimple)
                .toList());
    }

    // -----------------------------------------------------------------------
    // Private helpers — platform model conversion
    // -----------------------------------------------------------------------

    private WorkflowTriggerType toPlatformTrigger(TriggerType appTrigger) {
        return switch (appTrigger) {
            case EVENT       -> WorkflowTriggerType.EVENT;
            case SCHEDULE    -> WorkflowTriggerType.SCHEDULE;
            case MANUAL      -> WorkflowTriggerType.MANUAL;
            case API_WEBHOOK -> WorkflowTriggerType.API;
        };
    }

    private TriggerType toAppTrigger(WorkflowTriggerType platformTrigger) {
        return switch (platformTrigger) {
            case EVENT        -> TriggerType.EVENT;
            case SCHEDULE     -> TriggerType.SCHEDULE;
            case MANUAL       -> TriggerType.MANUAL;
            case API          -> TriggerType.API_WEBHOOK;
            case SUB_WORKFLOW -> TriggerType.API_WEBHOOK;
        };
    }

    private StepType toAppStepType(WorkflowStepKind kind) {
        return switch (kind) {
            case ACTION, COMPENSATION, LOOP, END -> StepType.TASK;
            case DECISION    -> StepType.DECISION;
            case PARALLEL    -> StepType.PARALLEL;
            case WAIT        -> StepType.WAIT;
            case SUB_WORKFLOW -> StepType.SUB_WORKFLOW;
        };
    }

    private WorkflowStepKind toPlatformStepKind(StepType appType) {
        return switch (appType) {
            case TASK         -> WorkflowStepKind.ACTION;
            case DECISION     -> WorkflowStepKind.DECISION;
            case PARALLEL     -> WorkflowStepKind.PARALLEL;
            case WAIT         -> WorkflowStepKind.WAIT;
            case SUB_WORKFLOW -> WorkflowStepKind.SUB_WORKFLOW;
        };
    }
    private List<WorkflowStepDefinition> toPlatformSteps(String definitionJson) {
        List<WorkflowStepDefinition> result = new ArrayList<>();
        if (definitionJson == null || definitionJson.isBlank()) return result;
        try {
            JsonNode root = MAPPER.readTree(definitionJson);
            JsonNode stepsArr = root.path("steps");
            if (!stepsArr.isArray()) return result;
            for (JsonNode s : stepsArr) {
                String stepId = s.path("stepId").asText("unknown");
                String stepName = s.path("name").asText(stepId);
                String stepType = s.path("type").asText("TASK");
                WorkflowStepKind kind = toPlatformStepKind(StepType.valueOf(stepType));
                String operatorId = s.path("taskRef").asText(null);
                String condition = s.path("condition").asText(null);
                String subWorkflowId = s.path("subWorkflowId").asText(null);

                // Resolve next step
                JsonNode nextNode = s.path("next");
                String nextStep = null;
                String nextOnTrue = null;
                String nextOnFalse = null;
                if (nextNode.isTextual()) {
                    nextStep = nextNode.asText();
                } else if (nextNode.isObject()) {
                    nextOnTrue = nextNode.path("true").asText(null);
                    nextOnFalse = nextNode.path("false").asText(null);
                }

                result.add(new WorkflowStepDefinition(
                    stepId, stepName, kind, operatorId, condition,
                    nextOnTrue, nextOnFalse, nextStep, subWorkflowId,
                    0, null, null, null, Map.of()));
            }
        } catch (Exception e) {
            log.warn("[WorkflowDefinition] Failed to parse steps: {}", e.getMessage());
        }
        return result;
    }

    private String resolveEntryStepId(String definitionJson, List<WorkflowStepDefinition> steps) {
        try {
            JsonNode root = MAPPER.readTree(definitionJson);
            JsonNode startNode = root.path("startStepId");
            if (!startNode.isMissingNode()) return startNode.asText();
        } catch (Exception ignored) { }
        return steps.isEmpty() ? "start" : steps.getFirst().stepId();
    }

    private WorkflowDefinition toAppDefinition(
            com.ghatana.platform.workflow.runtime.WorkflowDefinition platformDef,
            String definitionJson) {
        List<WorkflowStep> appSteps = parseStepsFromJson(definitionJson);
        return new WorkflowDefinition(
            platformDef.workflowId() + "@" + platformDef.version(),
            platformDef.workflowId(), platformDef.name(), platformDef.version(),
            toAppTrigger(platformDef.triggerType()), platformDef.triggerFilter(),
            appSteps, null,
            platformDef.timeout() != null ? platformDef.timeout().toString() : null,
            platformDef.enabled() ? "ACTIVE" : "DEPRECATED",
            platformDef.metadata().getOrDefault("createdBy", "system"),
            platformDef.createdAt().toString());
    }

    private WorkflowDefinition toAppDefinitionSimple(
            com.ghatana.platform.workflow.runtime.WorkflowDefinition platformDef) {
        List<WorkflowStep> appSteps = platformDef.steps().stream()
            .map(s -> new WorkflowStep(
                s.stepId(), toAppStepType(s.kind()), s.name(),
                s.operatorId(), s.celCondition(), List.of(),
                null, null, s.subWorkflowId(), null))
            .toList();
        return new WorkflowDefinition(
            platformDef.workflowId() + "@" + platformDef.version(),
            platformDef.workflowId(), platformDef.name(), platformDef.version(),
            toAppTrigger(platformDef.triggerType()), platformDef.triggerFilter(),
            appSteps, null,
            platformDef.timeout() != null ? platformDef.timeout().toString() : null,
            platformDef.enabled() ? "ACTIVE" : "DEPRECATED",
            platformDef.metadata().getOrDefault("createdBy", "system"),
            platformDef.createdAt().toString());
    }

    private List<WorkflowStep> parseStepsFromJson(String definitionJson) {
        List<WorkflowStep> result = new ArrayList<>();
        if (definitionJson == null || definitionJson.isBlank()) return result;
        try {
            JsonNode root = MAPPER.readTree(definitionJson);
            JsonNode stepsArr = root.path("steps");
            if (!stepsArr.isArray()) return result;
            for (JsonNode s : stepsArr) {
                String stepType = s.path("type").asText("TASK");
                List<String> branches = new ArrayList<>();
                JsonNode branchesNode = s.path("branches");
                if (branchesNode.isArray()) {
                    for (JsonNode b : branchesNode) branches.add(b.asText());
                }
                result.add(new WorkflowStep(
                    s.path("stepId").asText(null),
                    StepType.valueOf(stepType),
                    s.path("name").asText(null),
                    s.path("taskRef").asText(null),
                    s.path("condition").asText(null),
                    List.copyOf(branches),
                    s.path("waitEventType").asText(null),
                    s.path("waitDuration").asText(null),
                    s.path("subWorkflowId").asText(null),
                    s.path("errorHandling").isMissingNode() ? null
                            : MAPPER.writeValueAsString(s.get("errorHandling"))
                ));
            }
        } catch (Exception e) {
            log.warn("[WorkflowDefinition] Failed to parse steps from definition_json: {}", e.getMessage());
        }
        return result;
    }

    private String buildDefinedEventJson(String definitionId, String workflowId,
                                          String name, int version) {
        return String.format(
            "{\"definitionId\":\"%s\",\"workflowId\":\"%s\",\"name\":\"%s\",\"version\":%d}",
            definitionId, workflowId, name, version
        );
    }
}
