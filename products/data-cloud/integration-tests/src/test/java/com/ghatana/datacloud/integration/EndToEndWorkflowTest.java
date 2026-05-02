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
@DisplayName("End-to-End Workflow Integration Tests")
class EndToEndWorkflowTest extends EventloopTestBase {

    private static final String TENANT_A = "workflow-tenant-a";
    private static final String TENANT_B = "workflow-tenant-b";

    @TempDir
    Path tempDir;

    private DataCloudConfig config;
    private DataCloudClient client;
    private DataCloudRuntimePluginManager runtimePluginManager;

    @BeforeEach
    void setUp() { 
        config = DataCloudConfig.builder() 
            .profile(DataCloudProfile.SOVEREIGN) 
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("sovereign-store").toString()))
            .build(); 
        startRuntime(); 
    }

    @AfterEach
    void tearDown() { 
        stopRuntime(); 
    }

    @Test
    @DisplayName("workflow execution snapshot and logs survive plugin and client restart")
    void workflowExecutionSnapshotAndLogsSurvivePluginAndClientRestart() { 
        String pipelineId = "pipeline-" + UUID.randomUUID(); 
        savePipeline(TENANT_A, pipelineId, "Orders workflow"); 

        WorkflowExecutionCapability.ExecutionSnapshot snapshot = runPromise(() -> workflowExecution().execute( 
            TENANT_A,
            pipelineId,
            Map.of("collectionId", "orders", "dryRun", false))); 
        Optional<DataCloudClient.Entity> persistedExecution = runPromise(() -> client.findById( 
            TENANT_A,
            "dc_workflow_executions",
            snapshot.id())); 
        Optional<DataCloudClient.Entity> persistedLogs = runPromise(() -> client.findById( 
            TENANT_A,
            "dc_workflow_execution_logs",
            snapshot.id())); 

        assertThat(snapshot.status()).isEqualTo("COMPLETED");
        assertThat(snapshot.nodeStatuses()).hasSize(3); 
        assertThat(persistedExecution).isPresent(); 
        assertThat(persistedLogs).isPresent(); 

        restartRuntime(); 

        Optional<WorkflowExecutionCapability.ExecutionSnapshot> reloaded = runPromise(() -> workflowExecution().getExecution( 
            TENANT_A,
            snapshot.id())); 
        List<WorkflowExecutionCapability.ExecutionSnapshot> executions = runPromise(() -> workflowExecution().listExecutions( 
            TENANT_A,
            pipelineId));
        List<WorkflowExecutionCapability.ExecutionLogEntry> logs = runPromise(() -> workflowExecution().getExecutionLogs( 
            TENANT_A,
            snapshot.id())); 

        assertThat(reloaded).isPresent(); 
        assertThat(reloaded.orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(executions).extracting(WorkflowExecutionCapability.ExecutionSnapshot::id).contains(snapshot.id()); 
        assertThat(logs) 
            .isNotEmpty() 
            .extracting(WorkflowExecutionCapability.ExecutionLogEntry::message) 
            .contains("Workflow execution started");
    }

    @Test
    @DisplayName("workflow execution records remain tenant scoped")
    void workflowExecutionRecordsRemainTenantScoped() { 
        String pipelineId = "pipeline-" + UUID.randomUUID(); 
        savePipeline(TENANT_A, pipelineId, "Tenant A workflow"); 

        WorkflowExecutionCapability.ExecutionSnapshot snapshot = runPromise(() -> workflowExecution().execute( 
            TENANT_A,
            pipelineId,
            Map.of("collectionId", "tenant-a-collection"))); 

        Optional<WorkflowExecutionCapability.ExecutionSnapshot> crossTenantRead = runPromise(() -> workflowExecution().getExecution( 
            TENANT_B,
            snapshot.id())); 
        List<WorkflowExecutionCapability.ExecutionSnapshot> crossTenantList = runPromise(() -> workflowExecution().listExecutions( 
            TENANT_B,
            pipelineId));
        Optional<DataCloudClient.Entity> crossTenantStoreRead = runPromise(() -> client.findById( 
            TENANT_B,
            "dc_workflow_executions",
            snapshot.id())); 

        assertThat(crossTenantRead).isEmpty(); 
        assertThat(crossTenantList).isEmpty(); 
        assertThat(crossTenantStoreRead).isEmpty(); 
    }

    private WorkflowExecutionCapability workflowExecution() { 
        return runtimePluginManager.findCapability(WorkflowExecutionCapability.class) 
            .orElseThrow(() -> new AssertionError("workflow execution capability missing"));
    }

    private void savePipeline(String tenantId, String pipelineId, String workflowName) { 
        runPromise(() -> client.save(tenantId, "dc_pipelines", Map.of( 
            "id", pipelineId,
            "name", workflowName,
            "nodes", List.of( 
                Map.of("id", "extract", "type", "EXTRACT", "label", "Extract"), 
                Map.of("id", "validate", "type", "VALIDATE", "label", "Validate"), 
                Map.of("id", "publish", "type", "PUBLISH", "label", "Publish") 
            )
        )));
    }

    private void restartRuntime() { 
        stopRuntime(); 
        startRuntime(); 
    }

    private void startRuntime() { 
        client = DataCloud.create(config); 
        runtimePluginManager = new DataCloudRuntimePluginManager(); 
        runtimePluginManager.registerWorkflowPlugin(client); 
    }

    private void stopRuntime() { 
        if (runtimePluginManager != null) { 
            runtimePluginManager.close(); 
            runtimePluginManager = null;
        }
        if (client != null) { 
            client.close(); 
            client = null;
        }
    }
}