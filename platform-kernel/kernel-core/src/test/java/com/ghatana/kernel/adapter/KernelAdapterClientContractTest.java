package com.ghatana.kernel.adapter;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import com.ghatana.kernel.adapter.aep.AepKernelAdapterImpl;
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
    void dataCloudClientExposesAsyncClientDefaults() { // GH-90000
        DataCloudKernelAdapterImpl.DataCloudClient client = new DataCloudKernelAdapterImpl.DataCloudClient() { // GH-90000
            @Override
            public CompletableFuture<DataResult> read(String datasetId, String recordId, Map<String, String> options) { // GH-90000
                return CompletableFuture.completedFuture(new DataResult(recordId, new byte[0], Map.of(), Instant.now().toEpochMilli())); // GH-90000
            }

            @Override
            public CompletableFuture<Void> write(String datasetId, String recordId, byte[] data, Map<String, String> metadata) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<Void> delete(String datasetId, String recordId) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<List<DataResult>> query(String datasetId, String query, Map<String, Object> params, int limit, int offset) { // GH-90000
                return CompletableFuture.completedFuture(List.of()); // GH-90000
            }

            @Override
            public CompletableFuture<Void> createDataset(String datasetId, Map<String, String> schema, Map<String, String> options) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<SchemaInfo> getSchema(String datasetId) { // GH-90000
                return CompletableFuture.completedFuture(new SchemaInfo(datasetId, Map.of(), Instant.now().toEpochMilli(), Instant.now().toEpochMilli())); // GH-90000
            }

            @Override
            public CompletableFuture<List<DatasetInfo>> listDatasets() { // GH-90000
                return CompletableFuture.completedFuture(List.of()); // GH-90000
            }

            @Override
            public CompletableFuture<Object> beginTransaction() { // GH-90000
                return CompletableFuture.completedFuture("tx-1");
            }

            @Override
            public CompletableFuture<Void> commitTransaction(Object transaction) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<Void> rollbackTransaction(Object transaction) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<Object> openReadStream(String datasetId, Map<String, String> options) { // GH-90000
                return CompletableFuture.completedFuture("read-stream");
            }

            @Override
            public CompletableFuture<Object> openWriteStream(String datasetId, Map<String, String> options) { // GH-90000
                return CompletableFuture.completedFuture("write-stream");
            }

            @Override
            public CompletableFuture<byte[]> readStreamChunk(Object stream) { // GH-90000
                return CompletableFuture.completedFuture(new byte[0]); // GH-90000
            }

            @Override
            public CompletableFuture<Void> writeStreamChunk(Object stream, byte[] data) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<Void> closeStream(Object stream) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }
        };

        runPromise(client::start); // GH-90000
        assertThat(runPromise(client::healthCheck)).isTrue(); // GH-90000
        assertThat(client.isRunning()).isTrue(); // GH-90000
        runPromise(client::stop); // GH-90000
    }

    @Test
    @DisplayName("AEP adapter client exposes AsyncClient lifecycle defaults")
    void aepClientExposesAsyncClientDefaults() { // GH-90000
        AepKernelAdapterImpl.AepClient client = new AepKernelAdapterImpl.AepClient() { // GH-90000
            @Override
            public CompletableFuture<Void> publishEvent(String streamId, String eventId, String eventType, byte[] payload, Map<String, String> headers, long timestamp) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.InnerSubscription> subscribe(String streamId, AepKernelAdapterImpl.InnerEventHandler handler) { // GH-90000
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerSubscription() { // GH-90000
                    @Override
                    public String getId() { // GH-90000
                        return "sub-1";
                    }

                    @Override
                    public CompletableFuture<Void> unsubscribe() { // GH-90000
                        return CompletableFuture.completedFuture(null); // GH-90000
                    }
                });
            }

            @Override
            public CompletableFuture<Void> createStream(String streamId, String streamType, Map<String, String> config, int partitionCount, Duration retention) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<Void> deleteStream(String streamId) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.DeployResult> deployAgent(String agentId, String agentType, String version, Map<String, Object> config, int instanceCount) { // GH-90000
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.DeployResult("in-memory"));
            }

            @Override
            public CompletableFuture<Void> undeployAgent(String agentId) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.InnerAgentStatus> getAgentStatus(String agentId) { // GH-90000
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerAgentStatus("RUNNING", 1, 0L, Map.of())); // GH-90000
            }

            @Override
            public CompletableFuture<List<AepKernelAdapter.AgentDeployment>> listAgents() { // GH-90000
                return CompletableFuture.completedFuture(List.of()); // GH-90000
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.InnerCommandResult> sendCommand(String agentId, String commandId, String commandType, Map<String, Object> parameters) { // GH-90000
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerCommandResult(true, "ok", Map.of())); // GH-90000
            }

            @Override
            public CompletableFuture<Object> createPipeline(String pipelineId, String pipelineType, List<AepKernelAdapter.PipelineStage> stages, Map<String, String> config) { // GH-90000
                return CompletableFuture.completedFuture("pipeline-1");
            }

            @Override
            public CompletableFuture<Void> startPipeline(Object pipeline) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<Void> stopPipeline(Object pipeline) { // GH-90000
                return CompletableFuture.completedFuture(null); // GH-90000
            }

            @Override
            public CompletableFuture<AepKernelAdapterImpl.InnerPipelineStatus> getPipelineStatus(Object pipeline) { // GH-90000
                return CompletableFuture.completedFuture(new AepKernelAdapterImpl.InnerPipelineStatus("RUNNING", 1L, 0L, Map.of())); // GH-90000
            }
        };

        runPromise(client::start); // GH-90000
        assertThat(runPromise(client::healthCheck)).isTrue(); // GH-90000
        assertThat(client.isRunning()).isTrue(); // GH-90000
        runPromise(client::stop); // GH-90000
    }
}
