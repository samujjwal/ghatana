package com.ghatana.audio.video.multimodal.grpc;

import com.ghatana.audio.video.common.observability.MediaProcessingMetrics;
import com.ghatana.audio.video.multimodal.engine.AudioResult;
import com.ghatana.audio.video.multimodal.engine.MultimodalAnalysisEngine;
import com.ghatana.audio.video.multimodal.engine.MultimodalResult;
import com.ghatana.audio.video.multimodal.engine.PlatformMultimodalAdapter;
import com.ghatana.audio.video.multimodal.grpc.proto.MultimodalRequest;
import com.ghatana.audio.video.multimodal.grpc.proto.MultimodalResponse;
import com.ghatana.audio.video.multimodal.grpc.proto.MultimodalServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose In-process gRPC integration coverage for multimodal service contract
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("MultimodalGrpcService In-Process Integration")
class MultimodalGrpcServiceInProcessIntegrationTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        MultimodalAnalysisEngine engine = mock(MultimodalAnalysisEngine.class);
        PlatformMultimodalAdapter adapter = mock(PlatformMultimodalAdapter.class);

        when(engine.analyse(any())).thenReturn(
            MultimodalResult.builder()
                .audioResult(AudioResult.builder().transcription("hello").confidence(0.9).build())
                .combinedAnalysis("audio analysis ready")
                .processingTimeMs(25L)
                .build()
        );
        lenient().when(adapter.backendName()).thenReturn("test-backend");
        lenient().when(adapter.metricsEnabled()).thenReturn(false);

        MultimodalGrpcService service = new MultimodalGrpcService(engine, adapter, MediaProcessingMetrics.noop());

        server = ServerBuilder.forPort(0)
            .directExecutor()
            .addService(service)
            .build()
            .start();

        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
            .usePlaintext()
            .directExecutor()
            .build();
    }

    @Test
    @DisplayName("processMultimodal returns fused response over gRPC transport")
    void processMultimodalOverGrpc() {
        MultimodalServiceGrpc.MultimodalServiceBlockingStub stub = MultimodalServiceGrpc.newBlockingStub(channel);

        MultimodalResponse response = stub.processMultimodal(
            MultimodalRequest.newBuilder()
                .setAudioData(ByteString.copyFrom(new byte[]{0x01}))
                .build()
        );

        assertThat(response.getCombinedAnalysis()).contains("audio analysis");
        assertThat(response.getAudioAnalysis().getTranscription()).isEqualTo("hello");
        assertThat(response.getProcessingTimeMs()).isEqualTo(25L);
    }

    @Test
    @DisplayName("processMultimodal engine failure is mapped to INTERNAL")
    void processMultimodalEngineFailureReturnsInternal() throws Exception {
        MultimodalAnalysisEngine failingEngine = mock(MultimodalAnalysisEngine.class);
        PlatformMultimodalAdapter adapter = mock(PlatformMultimodalAdapter.class);
        when(failingEngine.analyse(any())).thenThrow(new RuntimeException("boom"));
        lenient().when(adapter.backendName()).thenReturn("test-backend");
        lenient().when(adapter.metricsEnabled()).thenReturn(false);

        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }

        server = ServerBuilder.forPort(0)
            .directExecutor()
            .addService(new MultimodalGrpcService(failingEngine, adapter, MediaProcessingMetrics.noop()))
            .build()
            .start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
            .usePlaintext()
            .directExecutor()
            .build();

        MultimodalServiceGrpc.MultimodalServiceBlockingStub stub = MultimodalServiceGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> stub.processMultimodal(
            MultimodalRequest.newBuilder().setAudioData(ByteString.copyFrom(new byte[]{0x01})).build()
        ))
            .isInstanceOf(StatusRuntimeException.class)
            .matches(ex -> ((StatusRuntimeException) ex).getStatus().getCode() == Status.INTERNAL.getCode());
    }
}
