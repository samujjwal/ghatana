package com.ghatana.kernel.adapter;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import com.ghatana.kernel.adapter.aep.AepKernelAdapterImpl;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.SchemaInfo;
import com.ghatana.kernel.adapter.datacloud.DatasetInfo;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kernel adapter client contracts")
class KernelAdapterClientContractTest extends EventloopTestBase {

    @Test
    @DisplayName("DataCloud adapter client exposes AsyncClient lifecycle defaults")
    void dataCloudClientExposesAsyncClientDefaults() {
        DataCloudKernelAdapterImpl.DataCloudClient client = new DataCloudKernelAdapterImpl.DataCloudClient() {
            @Override
            public CompletableFuture<DataResult> read(String datasetId, String recordId, Map<String, String> options) {
                return CompletableFuture.completedFuture(new DataResult(recordId, new byte[0], Map.of(), Instant.now().toEpochMilli()));
            }

            @Override
            public CompletableFuture<Void> write(String datasetId, String recordId, byte[] data, Map<String, String> metadata) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> delete(String datasetId, String recordId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<DataResult>> query(String datasetId, String query, Map<String, Object> params, int limit, int offset) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletableFuture<Void> createDataset(String datasetId, Map<String, String> schema, Map<String, String> options) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<SchemaInfo> getSchema(String datasetId) {
                return CompletableFuture.completedFuture(new SchemaInfo(datasetId, Map.of(), Instant.now().toEpochMilli(), Instant.now().toEpochMilli()));
            }

            @Override
            public CompletableFuture<List<DatasetInfo>> listDatasets() {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletableFuture<Object> beginTransaction() {
                return CompletableFuture.completedFuture("tx-1");
            }

            @Override
            public CompletableFuture<Void> commitTransaction(Object transaction) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> rollbackTransaction(Object transaction) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Object> openReadStream(String datasetId, Map<String, String> options) {
                return CompletableFuture.completedFuture("read-stream");
            }

            @Override
            public CompletableFuture<Object> openWriteStream(String datasetId, Map<String, String> options) {
                return CompletableFuture.completedFuture("write-stream");
            }

            @Override
            public CompletableFuture<byte[]> readStreamChunk(Object stream) {
                return CompletableFuture.completedFuture(new byte[0]);
            }

            @Override
            public CompletableFuture<Void> writeStreamChunk(Object stream, byte[] data) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> closeStream(Object stream) {
                return CompletableFuture.completedFuture(null);
            }
        };

        runPromise(client::start);
        assertThat(runPromise(client::healthCheck)).isTrue();
        assertThat(client.isRunning()).isTrue();
        runPromise(client::stop);
    }

    @Test
    @DisplayName("AEP adapter client exposes AsyncClient lifecycle defaults")
    void aepClientExposesAsyncClientDefaults() {
        AepKernelAdapterImpl.AepClient client = new AepKernelAdapterImpl.AepClient() {
            @Override
            public CompletableFuture<Void> publishEvent(String streamId, String eventId, String eventType, byte[] payload, Map<String, String> headers, long timestamp) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.InnerSubscription> subscribe(String streamId, AepKernelAdapterImpl.InnerEventHandler handler) {
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerSubscription() {
                    @Override
                    public String getId() {
                        return "sub-1";
                    }

                    @Override
                    public CompletableFuture<Void> unsubscribe() {
                        return CompletableFuture.completedFuture(null);
                    }
                });
            }

            @Override
            public CompletableFuture<Void> createStream(String streamId, String streamType, Map<String, String> config, int partitionCount, Duration retention) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteStream(String streamId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.DeployResult> deployAgent(String agentId, String agentType, String version, Map<String, Object> config, int instanceCount) {
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.DeployResult("in-memory"));
            }

            @Override
            public CompletableFuture<Void> undeployAgent(String agentId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.InnerAgentStatus> getAgentStatus(String agentId) {
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerAgentStatus("RUNNING", 1, 0L, Map.of()));
            }

            @Override
            public CompletableFuture<List<AepKernelAdapter.AgentDeployment>> listAgents() {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.InnerCommandResult> sendCommand(String agentId, String commandId, String commandType, Map<String, Object> parameters) {
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerCommandResult(true, "ok", Map.of()));
            }

            @Override
            public CompletableFuture<Object> createPipeline(String pipelineId, String pipelineType, List<AepKernelAdapter.PipelineStage> stages, Map<String, String> config) {
                return CompletableFuture.completedFuture("pipeline-1");
            }

            @Override
            public CompletableFuture<Void> startPipeline(Object pipeline) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> stopPipeline(Object pipeline) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.InnerPipelineStatus> getPipelineStatus(Object pipeline) {
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerPipelineStatus("RUNNING", 1L, 0L, Map.of()));
            }
        };

        runPromise(client::start);
        assertThat(runPromise(client::healthCheck)).isTrue();
        assertThat(client.isRunning()).isTrue();
        runPromise(client::stop);
    }
}