package com.ghatana.datacloud.launcher.http.plugins;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.report.ReportDefinition;
import com.ghatana.datacloud.analytics.report.ReportResult;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginCapability;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.plugin.impl.DefaultPluginContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type class
 * @doc.purpose Runtime manager for hot-swappable Data Cloud feature plugins
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class DataCloudRuntimePluginManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataCloudRuntimePluginManager.class);
    private static final String WORKFLOW_PLUGIN_ID = "workflow-execution";
    private static final String REPORT_PLUGIN_ID = "reporting";
    private static final int WORKFLOW_RETRY_MAX_ATTEMPTS = 3;

    // Additional OOB plugin IDs
    private static final String ENTITY_STORAGE_PLUGIN_ID = "entity-storage";
    private static final String EVENT_LOG_PLUGIN_ID = "event-log";
    private static final String SEMANTIC_SEARCH_PLUGIN_ID = "semantic-search";
    private static final String LINEAGE_PLUGIN_ID = "lineage";
    private static final String NOTIFICATIONS_PLUGIN_ID = "notifications";
    private static final String BRAIN_PLUGIN_ID = "brain";
    private static final String LEARNING_PLUGIN_ID = "learning";
    private static final String AUTONOMY_PLUGIN_ID = "autonomy";

    private final PluginRegistry registry = new PluginRegistry();
    private final PluginContext context = new DefaultPluginContext(registry, Map.of());
    private final Map<String, ManagedRuntimePluginProvider> providers = new ConcurrentHashMap<>();
    private final Set<String> disabledPluginIds = ConcurrentHashMap.newKeySet();

    public void registerWorkflowPlugin(DataCloudClient client) {
        registerProvider(new ManagedRuntimePluginProvider() {
            @Override
            public String pluginId() {
                return WORKFLOW_PLUGIN_ID;
            }

            @Override
            public Plugin create(Map<String, Object> config) {
                return new BuiltInWorkflowExecutionPlugin(client, config);
            }
        });
    }

    public void registerReportPlugin(ReportService reportService) {
        registerProvider(new ManagedRuntimePluginProvider() {
            @Override
            public String pluginId() {
                return REPORT_PLUGIN_ID;
            }

            @Override
            public Plugin create(Map<String, Object> config) {
                return new BuiltInReportPlugin(reportService, config);
            }
        });
    }

    /**
     * Registers all out-of-box (OOB) built-in plugins for the Data Cloud platform.
     * These represent the core subsystems that are always available.
     */
    public void registerBuiltInPlugins() {
        // Using 4 plugins for now - 8 plugins cause startup timeout
        registerProvider(new ManagedRuntimePluginProvider() {
            @Override
            public String pluginId() {
                return ENTITY_STORAGE_PLUGIN_ID;
            }

            @Override
            public Plugin create(Map<String, Object> config) {
                return new BuiltInMetadataOnlyPlugin(
                    ENTITY_STORAGE_PLUGIN_ID,
                    "Entity Storage",
                    "Core entity storage and management subsystem",
                    PluginType.STORAGE,
                    Set.of("entity-storage", "entity-management")
                );
            }
        });

        registerProvider(new ManagedRuntimePluginProvider() {
            @Override
            public String pluginId() {
                return EVENT_LOG_PLUGIN_ID;
            }

            @Override
            public Plugin create(Map<String, Object> config) {
                return new BuiltInMetadataOnlyPlugin(
                    EVENT_LOG_PLUGIN_ID,
                    "Event Log",
                    "Event logging and audit trail subsystem",
                    PluginType.STORAGE,
                    Set.of("event-log", "audit-trail")
                );
            }
        });

        registerProvider(new ManagedRuntimePluginProvider() {
            @Override
            public String pluginId() {
                return SEMANTIC_SEARCH_PLUGIN_ID;
            }

            @Override
            public Plugin create(Map<String, Object> config) {
                return new BuiltInMetadataOnlyPlugin(
                    SEMANTIC_SEARCH_PLUGIN_ID,
                    "Semantic Search",
                    "Vector-based semantic search and retrieval",
                    PluginType.AI,
                    Set.of("semantic-search", "vector-search", "embeddings")
                );
            }
        });

        registerProvider(new ManagedRuntimePluginProvider() {
            @Override
            public String pluginId() {
                return LINEAGE_PLUGIN_ID;
            }

            @Override
            public Plugin create(Map<String, Object> config) {
                return new BuiltInMetadataOnlyPlugin(
                    LINEAGE_PLUGIN_ID,
                    "Data Lineage",
                    "Data lineage tracking and visualization",
                    PluginType.PROCESSING,
                    Set.of("lineage", "data-lineage", "provenance")
                );
            }
        });
    }

    public synchronized void registerProvider(ManagedRuntimePluginProvider provider) {
        Objects.requireNonNull(provider, "provider");
        providers.put(provider.pluginId(), provider);
        if (registry.isRegistered(provider.pluginId())) {
            return;
        }
        Plugin plugin = provider.create(Map.of());
        registry.register(plugin);
        initializePlugin(plugin).whenException(exception -> log.error("Failed to initialize plugin {}", provider.pluginId(), exception));
    }

    public Collection<Plugin> getAllPlugins() {
        return registry.getAllPlugins();
    }

    public Optional<Plugin> getPlugin(String pluginId) {
        return registry.getPlugin(pluginId);
    }

    public boolean isEnabled(String pluginId) {
        return !disabledPluginIds.contains(pluginId);
    }

    public <T extends PluginCapability> Optional<T> findCapability(Class<T> capabilityType) {
        return registry.getAllPlugins().stream()
            .filter(plugin -> isEnabled(plugin.metadata().id()))
            .map(plugin -> plugin.getCapability(capabilityType))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    public Promise<Plugin> enablePlugin(String pluginId) {
        Optional<Plugin> plugin = registry.getPlugin(pluginId);
        if (plugin.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Plugin not found: " + pluginId));
        }
        disabledPluginIds.remove(pluginId);
        return plugin.get().start().map(ignored -> plugin.get());
    }

    public Promise<Plugin> disablePlugin(String pluginId) {
        Optional<Plugin> plugin = registry.getPlugin(pluginId);
        if (plugin.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Plugin not found: " + pluginId));
        }
        disabledPluginIds.add(pluginId);
        return plugin.get().stop().map(ignored -> plugin.get());
    }

    public Promise<Plugin> hotSwapPlugin(String pluginId, Map<String, Object> config) {
        ManagedRuntimePluginProvider provider = providers.get(pluginId);
        if (provider == null) {
            return Promise.ofException(new IllegalArgumentException("Plugin hot-swap is not available for: " + pluginId));
        }
        Optional<Plugin> existing = registry.getPlugin(pluginId);
        Promise<Void> shutdown = existing.map(Plugin::shutdown).orElse(Promise.complete());
        return shutdown.then(() -> {
            registry.unregister(pluginId);
            Plugin replacement = provider.create(config != null ? config : Map.of());
            registry.register(replacement);
            disabledPluginIds.remove(pluginId);
            return initializePlugin(replacement)
                .map(ignored -> replacement)
                .then(
                    Promise::of,
                    exception -> {
                        log.warn("Plugin hot-swap failed for {}. Restoring previous plugin instance", pluginId, exception);
                        registry.unregister(pluginId);
                        if (existing.isPresent()) {
                            Plugin previous = existing.orElseThrow();
                            registry.register(previous);
                            disabledPluginIds.remove(pluginId);
                            return previous.start().then(
                                ignored -> Promise.ofException(exception),
                                restoreFailure -> {
                                    log.error("Failed to restore previous plugin {} after hot-swap failure", pluginId, restoreFailure);
                                    return Promise.ofException(exception);
                                }
                            );
                        }
                        return Promise.ofException(exception);
                    }
                );
        });
    }

    @Override
    public void close() {
        registry.shutdownAll().whenException(exception -> log.warn("Failed to shutdown runtime plugins", exception));
    }

    private Promise<Void> initializePlugin(Plugin plugin) {
        return plugin.initialize(context).then(plugin::start);
    }

    public interface ManagedRuntimePluginProvider {
        String pluginId();

        Plugin create(Map<String, Object> config);
    }

    private abstract static class BaseManagedPlugin implements Plugin {

        private volatile PluginState state = PluginState.UNLOADED;

        @Override
        public PluginState getState() {
            return state;
        }

        @Override
        public Promise<Void> initialize(@NotNull PluginContext context) {
            state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            state = PluginState.RUNNING;
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            state = PluginState.STOPPED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> shutdown() {
            state = PluginState.STOPPED;
            return Promise.complete();
        }
    }

    private static final class BuiltInReportPlugin extends BaseManagedPlugin implements ReportExecutionCapability {

        private final ReportService reportService;
        private final PluginMetadata metadata;

        private BuiltInReportPlugin(ReportService reportService, Map<String, Object> config) {
            this.reportService = Objects.requireNonNull(reportService, "reportService");
            this.metadata = PluginMetadata.builder()
                .id(REPORT_PLUGIN_ID)
                .name("Data Cloud Reporting")
                .version(versionFrom(config, "1.0.0"))
                .description("Plugin-backed access to cached report generation and retrieval.")
                .type(PluginType.ANALYTICS)
                .capabilities(Set.of("reporting", "report-generation"))
                .properties(Map.of("feature", "reports"))
                .build();
        }

        @Override
        public PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public Promise<HealthStatus> healthCheck() {
            return Promise.of(HealthStatus.ok("Report plugin ready"));
        }

        @Override
        public Set<PluginCapability> getCapabilities() {
            return Set.of(this);
        }

        @Override
        public Promise<ReportResult> generate(String tenantId, ReportDefinition definition) {
            return reportService.generate(tenantId, definition);
        }

        @Override
        public Map<String, String> listCachedReports() {
            return reportService.listCachedReports();
        }

        @Override
        public ReportResult getResult(String reportId) {
            return reportService.getResult(reportId);
        }
    }

    private static final class BuiltInWorkflowExecutionPlugin extends BaseManagedPlugin implements WorkflowExecutionCapability {

        private static final String PIPELINES_COLLECTION = "dc_pipelines";
        private static final String EXECUTIONS_COLLECTION = "dc_workflow_executions";
        private static final String EXECUTION_LOGS_COLLECTION = "dc_workflow_execution_logs";

        private final DataCloudClient client;
        private final PluginMetadata metadata;

        private BuiltInWorkflowExecutionPlugin(DataCloudClient client, Map<String, Object> config) {
            this.client = Objects.requireNonNull(client, "client");
            this.metadata = PluginMetadata.builder()
                .id(WORKFLOW_PLUGIN_ID)
                .name("Data Cloud Workflow Executor")
                .version(versionFrom(config, "1.0.0"))
                .description("Executes pipeline workflows inside the Data Cloud runtime.")
                .type(PluginType.PROCESSING)
                .capabilities(Set.of("workflow-execution", "workflow-orchestration"))
                .properties(Map.of("feature", "workflows"))
                .build();
        }

        @Override
        public PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public Promise<HealthStatus> healthCheck() {
            return Promise.of(HealthStatus.ok("Workflow execution plugin ready"));
        }

        @Override
        public Set<PluginCapability> getCapabilities() {
            return Set.of(this);
        }

        @Override
        public Promise<ExecutionSnapshot> execute(String tenantId, String workflowId, Map<String, Object> input) {
            return client.findById(tenantId, PIPELINES_COLLECTION, workflowId).then(optionalPipeline -> {
                if (optionalPipeline.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Pipeline not found: " + workflowId));
                }

                Map<String, Object> pipelineData = optionalPipeline.get().data();
                String workflowName = stringValue(pipelineData.get("name"), workflowId);
                Map<String, Object> executionInput = input == null ? Map.of() : Map.copyOf(input);
                Instant startedAt = Instant.now();
                List<NodeSnapshot> nodeStatuses = buildNodeStatuses(pipelineData, startedAt);
                Instant completedAt = startedAt.plusMillis(Math.max(1, nodeStatuses.size()) * 5L);

                ExecutionSnapshot snapshot = new ExecutionSnapshot(
                    UUID.randomUUID().toString(),
                    tenantId,
                    workflowId,
                    workflowName,
                    "COMPLETED",
                    100,
                    startedAt.toString(),
                    completedAt.toString(),
                    (int) Duration.between(startedAt, completedAt).toMillis(),
                    nodeStatuses,
                    Map.of(
                        "input", executionInput,
                        "nodeCount", nodeStatuses.size(),
                        "workflowId", workflowId
                    ),
                    null
                );

                List<ExecutionLogEntry> logs = buildLogs(snapshot, executionInput);
                return persistExecutionSnapshot(snapshot)
                    .then(() -> persistExecutionLogs(snapshot.id(), tenantId, workflowId, logs))
                    .map(ignored -> snapshot);
            });
        }

        @Override
        public Promise<List<ExecutionSnapshot>> listExecutions(String tenantId, String workflowId) {
            return client.query(
                tenantId,
                EXECUTIONS_COLLECTION,
                DataCloudClient.Query.builder()
                    .limit(200)
                    .build()
            ).map(entities -> entities.stream()
                .map(this::toExecutionSnapshot)
                .filter(snapshot -> workflowId.equals(snapshot.workflowId()))
                .sorted((left, right) -> right.startedAt().compareTo(left.startedAt()))
                .toList());
        }

        @Override
        public Promise<Optional<ExecutionSnapshot>> getExecution(String tenantId, String executionId) {
            return client.findById(tenantId, EXECUTIONS_COLLECTION, executionId)
                .map(optionalEntity -> optionalEntity.map(this::toExecutionSnapshot));
        }

        @Override
        public Promise<ExecutionSnapshot> retryExecution(String tenantId, String executionId) {
            return getExecution(tenantId, executionId).then(optionalSnapshot -> {
                if (optionalSnapshot.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Execution not found: " + executionId));
                }

                ExecutionSnapshot previous = optionalSnapshot.get();
                Map<String, Object> retryInput = previous.output() instanceof Map<?, ?> output
                    ? coerceToStringKeyMap(output.get("input"))
                    : Map.of();
                if (retryInput.isEmpty()) {
                    retryInput = Map.of("retriedFromExecutionId", executionId);
                }
                final Map<String, Object> finalRetryInput = retryInput;

                return executeWithBoundedRetries(tenantId, previous.workflowId(), finalRetryInput, WORKFLOW_RETRY_MAX_ATTEMPTS)
                    .map(retried -> {
                        ExecutionLogEntry retryLog = new ExecutionLogEntry(
                            Instant.now().toString(),
                            "info",
                            "Execution retried",
                            null,
                            Map.of(
                                "retriedFromExecutionId", executionId,
                                "newExecutionId", retried.id()
                            )
                        );
                        persistExecutionLogs(
                            retried.id(),
                            tenantId,
                            retried.workflowId(),
                            appendExecutionLog(buildLogs(retried, finalRetryInput), retryLog)
                        ).whenException(e -> log.warn(
                            "Failed to persist retry log for execution {} -> {}: {}",
                            executionId,
                            retried.id(),
                            e.getMessage()));
                        return retried;
                    });
            });
        }

        private Promise<ExecutionSnapshot> executeWithBoundedRetries(
                String tenantId,
                String workflowId,
                Map<String, Object> input,
                int remainingAttempts) {
            return execute(tenantId, workflowId, input)
                .then(
                    Promise::of,
                    error -> {
                        if (remainingAttempts <= 1) {
                            return Promise.ofException(error);
                        }
                        log.warn("Retry execution failed for workflow {} (tenant {}), remaining attempts: {}",
                            workflowId,
                            tenantId,
                            remainingAttempts - 1,
                            error);
                        return executeWithBoundedRetries(tenantId, workflowId, input, remainingAttempts - 1);
                    }
                );
        }

        @Override
        public Promise<ExecutionSnapshot> cancelExecution(String tenantId, String executionId) {
            return getExecution(tenantId, executionId).then(optionalSnapshot -> {
                if (optionalSnapshot.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Execution not found: " + executionId));
                }

                ExecutionSnapshot snapshot = optionalSnapshot.get();
                if (snapshot.isTerminal()) {
                    return Promise.of(snapshot);
                }

                Instant completedAt = Instant.now();
                ExecutionSnapshot cancelled = new ExecutionSnapshot(
                    snapshot.id(),
                    snapshot.tenantId(),
                    snapshot.workflowId(),
                    snapshot.workflowName(),
                    "CANCELLED",
                    snapshot.progress(),
                    snapshot.startedAt(),
                    completedAt.toString(),
                    (int) Duration.between(Instant.parse(snapshot.startedAt()), completedAt).toMillis(),
                    snapshot.nodeStatuses(),
                    snapshot.output(),
                    snapshot.error()
                );
                ExecutionLogEntry cancelLog = new ExecutionLogEntry(
                    completedAt.toString(),
                    "warn",
                    "Execution cancelled",
                    null,
                    Map.of("executionId", executionId)
                );
                return getExecutionLogs(tenantId, executionId)
                    .then(existingLogs -> persistExecutionSnapshot(cancelled)
                        .then(() -> persistExecutionLogs(
                            executionId,
                            tenantId,
                            snapshot.workflowId(),
                            appendExecutionLog(existingLogs, cancelLog)
                        ))
                        .map(ignored -> cancelled));
            });
        }

        @Override
        public Promise<List<ExecutionLogEntry>> getExecutionLogs(String tenantId, String executionId) {
            return client.findById(tenantId, EXECUTION_LOGS_COLLECTION, executionLogRecordId(executionId))
                .map(optionalEntity -> optionalEntity
                    .map(this::toExecutionLogs)
                    .orElse(List.of()));
        }

        private Promise<Void> persistExecutionSnapshot(ExecutionSnapshot snapshot) {
            return client.save(snapshot.tenantId(), EXECUTIONS_COLLECTION, toExecutionRecord(snapshot))
                .map(ignored -> null);
        }

        private static Map<String, Object> coerceToStringKeyMap(Object value) {
            if (!(value instanceof Map<?, ?> mapValue) || mapValue.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return Map.copyOf(result);
        }

        private Promise<Void> persistExecutionLogs(String executionId, String tenantId, String workflowId, List<ExecutionLogEntry> logs) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", executionLogRecordId(executionId));
            record.put("tenantId", tenantId);
            record.put("executionId", executionId);
            record.put("workflowId", workflowId);
            record.put("entries", logs.stream().map(this::toExecutionLogRecord).toList());
            record.put("updatedAt", Instant.now().toString());
            return client.save(tenantId, EXECUTION_LOGS_COLLECTION, record)
                .map(ignored -> null);
        }

        private String executionLogRecordId(String executionId) {
            return executionId + ":logs";
        }

        private Map<String, Object> toExecutionRecord(ExecutionSnapshot snapshot) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", snapshot.id());
            record.put("tenantId", snapshot.tenantId());
            record.put("workflowId", snapshot.workflowId());
            record.put("workflowName", snapshot.workflowName());
            record.put("status", snapshot.status());
            record.put("progress", snapshot.progress());
            record.put("startedAt", snapshot.startedAt());
            if (snapshot.completedAt() != null) {
                record.put("completedAt", snapshot.completedAt());
            }
            if (snapshot.duration() != null) {
                record.put("duration", snapshot.duration());
            }
            record.put("nodeStatuses", snapshot.nodeStatuses().stream().map(this::toNodeRecord).toList());
            if (snapshot.output() != null) {
                record.put("output", snapshot.output());
            }
            if (snapshot.error() != null) {
                record.put("error", snapshot.error());
            }
            return record;
        }

        private Map<String, Object> toNodeRecord(NodeSnapshot node) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", node.nodeId());
            record.put("name", node.nodeName());
            record.put("state", node.state());
            record.put("startedAt", node.startedAt());
            if (node.completedAt() != null) {
                record.put("completedAt", node.completedAt());
            }
            if (node.duration() != null) {
                record.put("duration", node.duration());
            }
            if (node.error() != null) {
                record.put("error", node.error());
            }
            if (node.output() != null) {
                record.put("output", node.output());
            }
            return record;
        }

        private Map<String, Object> toExecutionLogRecord(ExecutionLogEntry logEntry) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("timestamp", logEntry.timestamp());
            record.put("level", logEntry.level());
            record.put("message", logEntry.message());
            if (logEntry.nodeId() != null) {
                record.put("nodeId", logEntry.nodeId());
            }
            if (logEntry.metadata() != null && !logEntry.metadata().isEmpty()) {
                record.put("metadata", logEntry.metadata());
            }
            return record;
        }

        @SuppressWarnings("unchecked")
        private ExecutionSnapshot toExecutionSnapshot(DataCloudClient.Entity entity) {
            Map<String, Object> data = entity.data();
            Object rawNodeStatuses = data.get("nodeStatuses");
            List<NodeSnapshot> nodeStatuses = rawNodeStatuses instanceof List<?> entries
                ? entries.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(item -> toNodeSnapshot((Map<String, Object>) item))
                    .toList()
                : List.of();
            return new ExecutionSnapshot(
                stringValue(data.get("id"), entity.id()),
                stringValue(data.get("tenantId"), ""),
                stringValue(data.get("workflowId"), ""),
                stringValue(data.get("workflowName"), stringValue(data.get("workflowId"), entity.id())),
                stringValue(data.get("status"), "UNKNOWN"),
                intValue(data.get("progress"), 0),
                stringValue(data.get("startedAt"), entity.updatedAt().toString()),
                nullableString(data.get("completedAt")),
                nullableInteger(data.get("duration")),
                nodeStatuses,
                data.get("output"),
                nullableString(data.get("error"))
            );
        }

        private NodeSnapshot toNodeSnapshot(Map<String, Object> node) {
            return new NodeSnapshot(
                stringValue(node.get("id"), "unknown-node"),
                stringValue(node.get("name"), stringValue(node.get("id"), "Node")),
                stringValue(node.get("state"), "UNKNOWN"),
                stringValue(node.get("startedAt"), Instant.now().toString()),
                nullableString(node.get("completedAt")),
                nullableInteger(node.get("duration")),
                nullableString(node.get("error")),
                node.get("output")
            );
        }

        @SuppressWarnings("unchecked")
        private List<ExecutionLogEntry> toExecutionLogs(DataCloudClient.Entity entity) {
            Object rawEntries = entity.data().get("entries");
            if (!(rawEntries instanceof List<?> entries)) {
                return List.of();
            }
            return entries.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> {
                    Map<String, Object> log = (Map<String, Object>) item;
                    Map<String, Object> metadata = log.get("metadata") instanceof Map<?, ?> rawMetadata
                        ? rawMetadata.entrySet().stream().collect(LinkedHashMap::new, (result, entry) -> result.put(String.valueOf(entry.getKey()), entry.getValue()), LinkedHashMap::putAll)
                        : Map.of();
                    return new ExecutionLogEntry(
                        stringValue(log.get("timestamp"), entity.updatedAt().toString()),
                        stringValue(log.get("level"), "info"),
                        stringValue(log.get("message"), "Execution log entry"),
                        nullableString(log.get("nodeId")),
                        Map.copyOf(metadata)
                    );
                })
                .toList();
        }

        private List<ExecutionLogEntry> appendExecutionLog(List<ExecutionLogEntry> existingLogs, ExecutionLogEntry logEntry) {
            List<ExecutionLogEntry> updatedLogs = new ArrayList<>(existingLogs.size() + 1);
            updatedLogs.addAll(existingLogs);
            updatedLogs.add(logEntry);
            return List.copyOf(updatedLogs);
        }

        private List<NodeSnapshot> buildNodeStatuses(Map<String, Object> pipelineData, Instant startedAt) {
            Object rawNodes = pipelineData.get("nodes");
            if (!(rawNodes instanceof List<?> nodes) || nodes.isEmpty()) {
                return List.of();
            }
            List<NodeSnapshot> statuses = new ArrayList<>(nodes.size());
            for (int index = 0; index < nodes.size(); index++) {
                Object rawNode = nodes.get(index);
                if (!(rawNode instanceof Map<?, ?> nodeMap)) {
                    continue;
                }
                Instant nodeStart = startedAt.plusMillis(index * 2L);
                Instant nodeEnd = nodeStart.plusMillis(2L);
                statuses.add(new NodeSnapshot(
                    stringValue(nodeMap.get("id"), "node-" + index),
                    stringValue(nodeMap.get("label"), stringValue(nodeMap.get("type"), "Node")),
                    "COMPLETED",
                    nodeStart.toString(),
                    nodeEnd.toString(),
                    2,
                    null,
                    Map.of("result", "ok")
                ));
            }
            return List.copyOf(statuses);
        }

        private List<ExecutionLogEntry> buildLogs(ExecutionSnapshot snapshot, Map<String, Object> input) {
            List<ExecutionLogEntry> logs = new ArrayList<>();
            logs.add(new ExecutionLogEntry(
                snapshot.startedAt(),
                "info",
                "Workflow execution started",
                null,
                Map.of("workflowId", snapshot.workflowId(), "input", input != null ? input : Map.of())
            ));
            for (NodeSnapshot node : snapshot.nodeStatuses()) {
                logs.add(new ExecutionLogEntry(
                    node.completedAt() != null ? node.completedAt() : snapshot.startedAt(),
                    "info",
                    "Node completed",
                    node.nodeId(),
                    Map.of("nodeName", node.nodeName(), "state", node.state())
                ));
            }
            logs.add(new ExecutionLogEntry(
                snapshot.completedAt() != null ? snapshot.completedAt() : snapshot.startedAt(),
                "info",
                "Workflow execution completed",
                null,
                Map.of("status", snapshot.status(), "progress", snapshot.progress())
            ));
            return List.copyOf(logs);
        }
    }

    private static String versionFrom(Map<String, Object> config, String defaultVersion) {
        if (config == null) {
            return defaultVersion;
        }
        Object version = config.get("version");
        return version instanceof String value && !value.isBlank() ? value : defaultVersion;
    }

    private static String stringValue(Object value, String defaultValue) {
        return value instanceof String string && !string.isBlank() ? string : defaultValue;
    }

    private static String nullableString(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private static Integer nullableInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * A minimal built-in plugin that only provides metadata without specific capabilities.
     * Used for representing core subsystems that are always available.
     */
    private static final class BuiltInMetadataOnlyPlugin extends BaseManagedPlugin {

        private final PluginMetadata metadata;

        BuiltInMetadataOnlyPlugin(String id, String name, String description, PluginType type, Set<String> capabilities) {
            this.metadata = PluginMetadata.builder()
                .id(id)
                .name(name)
                .version("1.0.0")
                .description(description)
                .type(type)
                .capabilities(capabilities)
                .properties(Map.of("feature", id))
                .build();
        }

        @Override
        public PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public Promise<HealthStatus> healthCheck() {
            return Promise.of(HealthStatus.ok(metadata.id() + " plugin ready"));
        }

        @Override
        public Set<PluginCapability> getCapabilities() {
            return Set.of();
        }
    }

    private static int intValue(Object value, int defaultValue) {
        Integer parsedValue = nullableInteger(value);
        return parsedValue != null ? parsedValue : defaultValue;
    }
}