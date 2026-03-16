package com.ghatana.appplatform.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
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

    private final DataSource dataSource;
    private final Executor executor;
    private final DslParserPort dslParser;
    private final StepTemplateRegistryPort templateRegistry;
    private final ValueCatalogPort valueCatalog;
    private final EventBusPort eventBus;
    private final AuditPort auditPort;

    private final Counter definedTotal;
    private final Counter parseFailedTotal;

    private static final String TOPIC_WORKFLOW_EVENTS = "platform.workflow.events";

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public WorkflowDefinitionService(DataSource dataSource,
                                      Executor executor,
                                      MeterRegistry meterRegistry,
                                      DslParserPort dslParser,
                                      StepTemplateRegistryPort templateRegistry,
                                      ValueCatalogPort valueCatalog,
                                      EventBusPort eventBus,
                                      AuditPort auditPort) {
        this.dataSource      = dataSource;
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
                    return Promise.ofBlocking(executor, () -> {
                        int version = nextVersionBlocking(workflowId);
                        String definitionId = insertDefinitionBlocking(workflowId, name, version,
                            definitionJson, triggerType, triggerConfigJson, timeoutIso, createdBy);
                        definedTotal.increment();
                        WorkflowDefinition def = queryDefinitionById(definitionId);
                        eventBus.publish(TOPIC_WORKFLOW_EVENTS, "WorkflowDefined",
                            buildDefinedEventJson(definitionId, workflowId, name, version));
                        auditPort.log("WORKFLOW_DEFINED", createdBy, definitionId, "WORKFLOW_DEFINITION",
                                      null, definitionJson);
                        return def;
                    });
                }));
    }

    /** Activate a specific version (marks it as the default for new instances). */
    public Promise<Void> activateVersion(String workflowId, int version, String activatedBy) {
        return Promise.ofBlocking(executor, () -> {
            setVersionStatus(workflowId, version, "ACTIVE");
            auditPort.log("WORKFLOW_ACTIVATED", activatedBy, workflowId + "@" + version,
                          "WORKFLOW_DEFINITION", null, null);
            return null;
        });
    }

    /** Deprecate a version: existing instances on that version continue, new triggers rejected. */
    public Promise<Void> deprecateVersion(String workflowId, int version, String deprecatedBy) {
        return Promise.ofBlocking(executor, () -> {
            setVersionStatus(workflowId, version, "DEPRECATED");
            auditPort.log("WORKFLOW_DEPRECATED", deprecatedBy, workflowId + "@" + version,
                          "WORKFLOW_DEFINITION", null, null);
            return null;
        });
    }

    /** Get a specific workflow definition version. */
    public Promise<WorkflowDefinition> getDefinition(String workflowId, int version) {
        return Promise.ofBlocking(executor, () -> queryDefinitionByVersion(workflowId, version));
    }

    /** Get the active (latest ACTIVE) version of a workflow. */
    public Promise<WorkflowDefinition> getActiveDefinition(String workflowId) {
        return Promise.ofBlocking(executor, () -> queryActiveDefinition(workflowId));
    }

    /** List all versions of a workflow with their statuses. */
    public Promise<List<WorkflowDefinition>> listVersions(String workflowId) {
        return Promise.ofBlocking(executor, () -> queryAllVersions(workflowId));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private int nextVersionBlocking(String workflowId) {
        String sql = "SELECT COALESCE(MAX(version), 0) + 1 FROM workflow_definitions WHERE workflow_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute next version for " + workflowId, e);
        }
    }

    private String insertDefinitionBlocking(String workflowId, String name, int version,
                                             String definitionJson, TriggerType triggerType,
                                             String triggerConfigJson, String timeoutIso,
                                             String createdBy) {
        String sql = """
            INSERT INTO workflow_definitions
                (definition_id, workflow_id, name, version, definition_json, trigger_type,
                 trigger_config, timeout_iso, status, created_by, created_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, 'DRAFT', ?, now())
            RETURNING definition_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setString(2, name);
            ps.setInt(3, version);
            ps.setString(4, definitionJson);
            ps.setString(5, triggerType.name());
            ps.setString(6, triggerConfigJson);
            ps.setString(7, timeoutIso);
            ps.setString(8, createdBy);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("definition_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert workflow definition for " + workflowId, e);
        }
    }

    private void setVersionStatus(String workflowId, int version, String status) {
        String sql = """
            UPDATE workflow_definitions SET status = ?
             WHERE workflow_id = ? AND version = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, workflowId);
            ps.setInt(3, version);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to set status for " + workflowId + "@" + version, e);
        }
    }

    private WorkflowDefinition queryDefinitionById(String definitionId) {
        String sql = """
            SELECT definition_id, workflow_id, name, version, trigger_type, trigger_config::text,
                   timeout_iso, status, created_by, created_at::text
              FROM workflow_definitions
             WHERE definition_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, definitionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("Definition not found: " + definitionId);
                return mapDefinition(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query definition " + definitionId, e);
        }
    }

    private WorkflowDefinition queryDefinitionByVersion(String workflowId, int version) {
        String sql = """
            SELECT definition_id, workflow_id, name, version, trigger_type, trigger_config::text,
                   timeout_iso, status, created_by, created_at::text
              FROM workflow_definitions
             WHERE workflow_id = ? AND version = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException(
                    "Definition not found: " + workflowId + "@" + version);
                return mapDefinition(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query definition " + workflowId + "@" + version, e);
        }
    }

    private WorkflowDefinition queryActiveDefinition(String workflowId) {
        String sql = """
            SELECT definition_id, workflow_id, name, version, trigger_type, trigger_config::text,
                   timeout_iso, status, created_by, created_at::text
              FROM workflow_definitions
             WHERE workflow_id = ? AND status = 'ACTIVE'
             ORDER BY version DESC
             LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("No active definition for: " + workflowId);
                return mapDefinition(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query active definition for " + workflowId, e);
        }
    }

    private List<WorkflowDefinition> queryAllVersions(String workflowId) {
        String sql = """
            SELECT definition_id, workflow_id, name, version, trigger_type, trigger_config::text,
                   timeout_iso, status, created_by, created_at::text
              FROM workflow_definitions
             WHERE workflow_id = ?
             ORDER BY version DESC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                List<WorkflowDefinition> result = new ArrayList<>();
                while (rs.next()) result.add(mapDefinition(rs));
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list versions for " + workflowId, e);
        }
    }

    private WorkflowDefinition mapDefinition(ResultSet rs) throws Exception {
        String definitionJson = rs.getString("definition_json");
        List<WorkflowStep> steps = parseStepsFromDefinitionJson(definitionJson);
        return new WorkflowDefinition(
            rs.getString("definition_id"),
            rs.getString("workflow_id"),
            rs.getString("name"),
            rs.getInt("version"),
            TriggerType.valueOf(rs.getString("trigger_type")),
            rs.getString("trigger_config"),
            steps,
            null, null,
            rs.getString("status"),
            rs.getString("created_by"),
            rs.getString("created_at")
        );
    }

    /**
     * Parse the {@code steps} array from a definition JSON document.
     *
     * <p>Expected format:
     * <pre>{@code
     * {
     *   "startStepId": "step-001",
     *   "steps": [
     *     { "stepId": "step-001", "type": "TASK", "name": "...", "taskRef": "...", "next": "step-002" },
     *     { "stepId": "step-002", "type": "DECISION", "condition": "...",
     *       "next": { "true": "step-003", "false": "step-004" } }
     *   ]
     * }
     * }</pre>
     *
     * <p>Unknown or missing fields default to null/empty — they are simply ignored.
     */
    static List<WorkflowStep> parseStepsFromDefinitionJson(String definitionJson) {
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
