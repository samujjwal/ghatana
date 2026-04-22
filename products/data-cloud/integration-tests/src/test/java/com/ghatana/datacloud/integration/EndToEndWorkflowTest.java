package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Exercises workflow execution with real sovereign-backed persistence
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("End-to-End Workflow Integration Tests [GH-90000]")
class EndToEndWorkflowTest extends EventloopTestBase {

    private static final String TENANT_A = "workflow-tenant-a";
    private static final String TENANT_B = "workflow-tenant-b";

    @TempDir
    Path tempDir;

    private DataCloudConfig config;
    private DataCloudClient client;
    private DataCloudRuntimePluginManager runtimePluginManager;

    @BeforeEach
    void setUp() { // GH-90000
        config = DataCloudConfig.builder() // GH-90000
            .profile(DataCloudProfile.SOVEREIGN) // GH-90000
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("sovereign-store [GH-90000]").toString()))
            .build(); // GH-90000
        startRuntime(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        stopRuntime(); // GH-90000
    }

    @Test
    @DisplayName("workflow execution snapshot and logs survive plugin and client restart [GH-90000]")
    void workflowExecutionSnapshotAndLogsSurvivePluginAndClientRestart() { // GH-90000
        String pipelineId = "pipeline-" + UUID.randomUUID(); // GH-90000
        savePipeline(TENANT_A, pipelineId, "Orders workflow"); // GH-90000

        WorkflowExecutionCapability.ExecutionSnapshot snapshot = runPromise(() -> workflowExecution().execute( // GH-90000
            TENANT_A,
            pipelineId,
            Map.of("collectionId", "orders", "dryRun", false))); // GH-90000
        Optional<DataCloudClient.Entity> persistedExecution = runPromise(() -> client.findById( // GH-90000
            TENANT_A,
            "dc_workflow_executions",
            snapshot.id())); // GH-90000
        Optional<DataCloudClient.Entity> persistedLogs = runPromise(() -> client.findById( // GH-90000
            TENANT_A,
            "dc_workflow_execution_logs",
            snapshot.id())); // GH-90000

        assertThat(snapshot.status()).isEqualTo("COMPLETED [GH-90000]");
        assertThat(snapshot.nodeStatuses()).hasSize(3); // GH-90000
        assertThat(persistedExecution).isPresent(); // GH-90000
        assertThat(persistedLogs).isPresent(); // GH-90000

        restartRuntime(); // GH-90000

        Optional<WorkflowExecutionCapability.ExecutionSnapshot> reloaded = runPromise(() -> workflowExecution().getExecution( // GH-90000
            TENANT_A,
            snapshot.id())); // GH-90000
        List<WorkflowExecutionCapability.ExecutionSnapshot> executions = runPromise(() -> workflowExecution().listExecutions( // GH-90000
            TENANT_A,
            pipelineId));
        List<WorkflowExecutionCapability.ExecutionLogEntry> logs = runPromise(() -> workflowExecution().getExecutionLogs( // GH-90000
            TENANT_A,
            snapshot.id())); // GH-90000

        assertThat(reloaded).isPresent(); // GH-90000
        assertThat(reloaded.orElseThrow().status()).isEqualTo("COMPLETED [GH-90000]");
        assertThat(executions).extracting(WorkflowExecutionCapability.ExecutionSnapshot::id).contains(snapshot.id()); // GH-90000
        assertThat(logs) // GH-90000
            .isNotEmpty() // GH-90000
            .extracting(WorkflowExecutionCapability.ExecutionLogEntry::message) // GH-90000
            .contains("Workflow execution started [GH-90000]");
    }

    @Test
    @DisplayName("workflow execution records remain tenant scoped [GH-90000]")
    void workflowExecutionRecordsRemainTenantScoped() { // GH-90000
        String pipelineId = "pipeline-" + UUID.randomUUID(); // GH-90000
        savePipeline(TENANT_A, pipelineId, "Tenant A workflow"); // GH-90000

        WorkflowExecutionCapability.ExecutionSnapshot snapshot = runPromise(() -> workflowExecution().execute( // GH-90000
            TENANT_A,
            pipelineId,
            Map.of("collectionId", "tenant-a-collection"))); // GH-90000

        Optional<WorkflowExecutionCapability.ExecutionSnapshot> crossTenantRead = runPromise(() -> workflowExecution().getExecution( // GH-90000
            TENANT_B,
            snapshot.id())); // GH-90000
        List<WorkflowExecutionCapability.ExecutionSnapshot> crossTenantList = runPromise(() -> workflowExecution().listExecutions( // GH-90000
            TENANT_B,
            pipelineId));
        Optional<DataCloudClient.Entity> crossTenantStoreRead = runPromise(() -> client.findById( // GH-90000
            TENANT_B,
            "dc_workflow_executions",
            snapshot.id())); // GH-90000

        assertThat(crossTenantRead).isEmpty(); // GH-90000
        assertThat(crossTenantList).isEmpty(); // GH-90000
        assertThat(crossTenantStoreRead).isEmpty(); // GH-90000
    }

    private WorkflowExecutionCapability workflowExecution() { // GH-90000
        return runtimePluginManager.findCapability(WorkflowExecutionCapability.class) // GH-90000
            .orElseThrow(() -> new AssertionError("workflow execution capability missing [GH-90000]"));
    }

    private void savePipeline(String tenantId, String pipelineId, String workflowName) { // GH-90000
        runPromise(() -> client.save(tenantId, "dc_pipelines", Map.of( // GH-90000
            "id", pipelineId,
            "name", workflowName,
            "nodes", List.of( // GH-90000
                Map.of("id", "extract", "type", "EXTRACT", "label", "Extract"), // GH-90000
                Map.of("id", "validate", "type", "VALIDATE", "label", "Validate"), // GH-90000
                Map.of("id", "publish", "type", "PUBLISH", "label", "Publish") // GH-90000
            )
        )));
    }

    private void restartRuntime() { // GH-90000
        stopRuntime(); // GH-90000
        startRuntime(); // GH-90000
    }

    private void startRuntime() { // GH-90000
        client = DataCloud.create(config); // GH-90000
        runtimePluginManager = new DataCloudRuntimePluginManager(); // GH-90000
        runtimePluginManager.registerWorkflowPlugin(client); // GH-90000
    }

    private void stopRuntime() { // GH-90000
        if (runtimePluginManager != null) { // GH-90000
            runtimePluginManager.close(); // GH-90000
            runtimePluginManager = null;
        }
        if (client != null) { // GH-90000
            client.close(); // GH-90000
            client = null;
        }
    }
}