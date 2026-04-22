package com.ghatana.audio.video.multimodal.grpc;

import com.ghatana.audio.video.common.observability.MediaProcessingMetrics;
import com.ghatana.audio.video.multimodal.engine.AudioResult;
import com.ghatana.audio.video.multimodal.engine.MultimodalAnalysisEngine;
import com.ghatana.audio.video.multimodal.engine.MultimodalResult;
import com.ghatana.audio.video.multimodal.engine.PlatformMultimodalAdapter;
import com.ghatana.audio.video.multimodal.engine.VideoAudioResult;
import com.ghatana.audio.video.multimodal.engine.VisualResult;
import com.ghatana.audio.video.multimodal.grpc.proto.*;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MultimodalGrpcService}.
 *
 * <p>Uses Mockito to avoid native-library and file-system dependencies.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the multimodal gRPC service layer — covers AV-004 methods
 * @doc.layer product
 * @doc.pattern TestCase
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("MultimodalGrpcService [GH-90000]")
class MultimodalGrpcServiceTest {

    @Mock
    private MultimodalAnalysisEngine mockEngine;

    @Mock
    private PlatformMultimodalAdapter mockAdapter;

    private MultimodalGrpcService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new MultimodalGrpcService(mockEngine, mockAdapter, MediaProcessingMetrics.noop()); // GH-90000
        lenient().when(mockAdapter.backendName()).thenReturn("test-backend [GH-90000]");
        lenient().when(mockAdapter.metricsEnabled()).thenReturn(false); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // processMultimodal (AV-004) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processMultimodal: audio + image → fused response returned [GH-90000]")
    void processMultimodal_withAudioAndImage_returnsFusedResponse() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("hello world [GH-90000]").confidence(0.9).build())
            .visualResult(VisualResult.builder().sceneDescription("A sunny park [GH-90000]").confidence(0.8).build())
            .combinedAnalysis("Speech: hello world; Visual: A sunny park [GH-90000]")
            .processingTimeMs(50L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        MultimodalRequest request = MultimodalRequest.newBuilder() // GH-90000
            .setAudioData(ByteString.copyFrom(new byte[]{1, 2, 3})) // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{4, 5, 6})) // GH-90000
            .build(); // GH-90000
        CapturingObserver<MultimodalResponse> observer = new CapturingObserver<>(); // GH-90000
        service.processMultimodal(request, observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        MultimodalResponse response = observer.getValue(); // GH-90000
        assertThat(response.getCombinedAnalysis()).contains("Speech: hello world [GH-90000]");
        assertThat(response.getAudioAnalysis().getTranscription()).isEqualTo("hello world [GH-90000]");
        assertThat(response.getVisualAnalysis().getSceneDescription()).isEqualTo("A sunny park [GH-90000]");
        assertThat(response.getProcessingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("processMultimodal: engine throws → INTERNAL returned [GH-90000]")
    void processMultimodal_engineThrows_returnsInternal() { // GH-90000
        when(mockEngine.analyse(any())).thenThrow(new RuntimeException("engine failure [GH-90000]"));

        CapturingObserver<MultimodalResponse> observer = new CapturingObserver<>(); // GH-90000
        service.processMultimodal( // GH-90000
            MultimodalRequest.newBuilder() // GH-90000
                .setAudioData(ByteString.copyFrom(new byte[]{1})) // GH-90000
                .build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INTERNAL.getCode()); // GH-90000
    }

    @Test
    @DisplayName("processMultimodal: audio-only request → no visual analysis in response [GH-90000]")
    void processMultimodal_audioOnly_hasNoVisualAnalysis() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("test [GH-90000]").confidence(0.9).build())
            .combinedAnalysis("Speech: test [GH-90000]")
            .processingTimeMs(10L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        CapturingObserver<MultimodalResponse> observer = new CapturingObserver<>(); // GH-90000
        service.processMultimodal( // GH-90000
            MultimodalRequest.newBuilder() // GH-90000
                .setAudioData(ByteString.copyFrom(new byte[]{1})) // GH-90000
                .build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().hasVisualAnalysis()).isFalse(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generateDescription
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateDescription: audio + context → description with key elements [GH-90000]")
    void generateDescription_withAudioAndContext_returnsDescription() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("meeting summary [GH-90000]").confidence(0.9).build())
            .combinedAnalysis("Meeting was productive [GH-90000]")
            .processingTimeMs(20L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        CapturingObserver<DescriptionResponse> observer = new CapturingObserver<>(); // GH-90000
        service.generateDescription( // GH-90000
            DescriptionRequest.newBuilder() // GH-90000
                .setAudioData(ByteString.copyFrom(new byte[]{1, 2})) // GH-90000
                .setContext("quarterly review [GH-90000]")
                .build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        DescriptionResponse response = observer.getValue(); // GH-90000
        assertThat(response.getDescription()).isNotBlank(); // GH-90000
        assertThat(response.getKeyElementsList()).contains("speech [GH-90000]");
        assertThat(response.getConfidence()).isGreaterThan(0.0); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // analyzeVideoWithAudio
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("analyzeVideoWithAudio: valid video → combined narrative returned [GH-90000]")
    void analyzeVideoWithAudio_valid_returnsCombinedNarrative() { // GH-90000
        VideoAudioResult result = VideoAudioResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("narrator speaking [GH-90000]").confidence(0.85).build())
            .videoResult(VisualResult.builder().sceneDescription("outdoor scene [GH-90000]").confidence(0.9).build())
            .temporalAlignments(List.of()) // GH-90000
            .combinedNarrative("narrator speaking with outdoor scene [GH-90000]")
            .processingTimeMs(30L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyseVideoWithAudio(any(), anyBoolean(), anyBoolean(), anyInt())) // GH-90000
            .thenReturn(result); // GH-90000

        CapturingObserver<VideoAudioResponse> observer = new CapturingObserver<>(); // GH-90000
        service.analyzeVideoWithAudio( // GH-90000
            VideoAudioRequest.newBuilder() // GH-90000
                .setVideoData(ByteString.copyFrom(new byte[]{9, 8, 7})) // GH-90000
                .setExtractAudio(true) // GH-90000
                .setAnalyzeFrames(true) // GH-90000
                .build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getCombinedNarrative()).isEqualTo("narrator speaking with outdoor scene [GH-90000]");
        assertThat(observer.getValue().getAudioTranscription()).isEqualTo("narrator speaking [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // analyzeCrossModal (AV-004.4) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("analyzeCrossModal: no audio and no video → INVALID_ARGUMENT [GH-90000]")
    void analyzeCrossModal_noAudioNoVideo_returnsInvalidArgument() { // GH-90000
        CapturingObserver<CrossModalResponse> observer = new CapturingObserver<>(); // GH-90000
        service.analyzeCrossModal(CrossModalRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("analyzeCrossModal: both audio and video → alignment score computed [GH-90000]")
    void analyzeCrossModal_withBothModalities_returnsAlignmentScore() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("a person running [GH-90000]").confidence(0.9).build())
            .visualResult(VisualResult.builder().sceneDescription("a person running fast [GH-90000]").confidence(0.85).build())
            .combinedAnalysis("aligned [GH-90000]")
            .processingTimeMs(25L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        CapturingObserver<CrossModalResponse> observer = new CapturingObserver<>(); // GH-90000
        service.analyzeCrossModal( // GH-90000
            CrossModalRequest.newBuilder() // GH-90000
                .setAudioData(ByteString.copyFrom(new byte[]{1, 2})) // GH-90000
                .setVideoData(ByteString.copyFrom(new byte[]{3, 4})) // GH-90000
                .build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        CrossModalResponse response = observer.getValue(); // GH-90000
        assertThat(response.getAlignmentScore()).isGreaterThan(0.0); // GH-90000
        assertThat(response.getEventsList()).isNotEmpty(); // GH-90000
        assertThat(response.getSummary()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("analyzeCrossModal: audio only → partial alignment score [GH-90000]")
    void analyzeCrossModal_audioOnly_returnsPartialAlignment() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("hello [GH-90000]").confidence(0.9).build())
            .combinedAnalysis("audio only [GH-90000]")
            .processingTimeMs(10L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        CapturingObserver<CrossModalResponse> observer = new CapturingObserver<>(); // GH-90000
        service.analyzeCrossModal( // GH-90000
            CrossModalRequest.newBuilder() // GH-90000
                .setAudioData(ByteString.copyFrom(new byte[]{1})) // GH-90000
                .build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getAlignmentScore()).isEqualTo(0.4); // GH-90000
        assertThat(observer.getValue().getEvents(0).getEventType()).isEqualTo("mismatch [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getInsights (AV-004.5) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getInsights: audio with transcription → topics and sentiment extracted [GH-90000]")
    void getInsights_withAudio_extractsTopicsAndSentiment() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder() // GH-90000
                .transcription("The project is great and the team did a good job [GH-90000]")
                .confidence(0.9).build()) // GH-90000
            .combinedAnalysis("positive meeting [GH-90000]")
            .processingTimeMs(15L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        CapturingObserver<InsightsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getInsights( // GH-90000
            InsightsRequest.newBuilder() // GH-90000
                .setAudioData(ByteString.copyFrom(new byte[]{1})) // GH-90000
                .build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        InsightsResponse response = observer.getValue(); // GH-90000
        assertThat(response.getTopicsList()).contains("speech [GH-90000]");
        assertThat(response.getOverallSentiment()).isEqualTo("positive [GH-90000]");
        assertThat(response.getProcessingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("getInsights: text only → text topic included [GH-90000]")
    void getInsights_withTextOnly_includesTextTopic() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .combinedAnalysis("text only [GH-90000]")
            .processingTimeMs(5L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        CapturingObserver<InsightsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getInsights( // GH-90000
            InsightsRequest.newBuilder().setText("some context [GH-90000]").build(),
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getTopicsList()).contains("text [GH-90000]");
        assertThat(observer.getValue().getOverallSentiment()).isEqualTo("neutral [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadModel (AV-004.1) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadModel: blank modelId → INVALID_ARGUMENT [GH-90000]")
    void loadModel_blankModelId_returnsInvalidArgument() { // GH-90000
        CapturingObserver<LoadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.loadModel(LoadModelRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("loadModel: valid modelId → success response [GH-90000]")
    void loadModel_validModelId_returnsSuccess() { // GH-90000
        CapturingObserver<LoadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.loadModel(LoadModelRequest.newBuilder().setModelId("clip-vit-large [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getSuccess()).isTrue(); // GH-90000
        assertThat(observer.getValue().getModelId()).isEqualTo("clip-vit-large [GH-90000]");
        assertThat(observer.getValue().getLoadTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // unloadModel (AV-004.2) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unloadModel: blank modelId → INVALID_ARGUMENT [GH-90000]")
    void unloadModel_blankModelId_returnsInvalidArgument() { // GH-90000
        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("unloadModel: unregistered modelId → NOT_FOUND [GH-90000]")
    void unloadModel_unregisteredModel_returnsNotFound() { // GH-90000
        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("ghost-model [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    @Test
    @DisplayName("unloadModel: loaded model → success response with freed bytes [GH-90000]")
    void unloadModel_loadedModel_returnsSuccess() { // GH-90000
        // Load first
        service.loadModel(LoadModelRequest.newBuilder().setModelId("clip-vit [GH-90000]").build(),
            new CapturingObserver<>()); // GH-90000

        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("clip-vit [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getSuccess()).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listModels (AV-004.3) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listModels: empty registry → zero count [GH-90000]")
    void listModels_emptyRegistry_returnsZeroModels() { // GH-90000
        CapturingObserver<ListModelsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.listModels(ListModelsRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getTotalCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("listModels: after loadModel → includes loaded model [GH-90000]")
    void listModels_afterLoad_includesModel() { // GH-90000
        service.loadModel(LoadModelRequest.newBuilder().setModelId("whisper-large [GH-90000]").build(),
            new CapturingObserver<>()); // GH-90000

        CapturingObserver<ListModelsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.listModels(ListModelsRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        ListModelsResponse response = observer.getValue(); // GH-90000
        assertThat(response.getTotalCount()).isGreaterThanOrEqualTo(1); // GH-90000
        assertThat(response.getLoadedCount()).isGreaterThanOrEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("listModels: loadedOnly filter → unloaded models excluded [GH-90000]")
    void listModels_loadedOnlyFilter_excludesUnloaded() { // GH-90000
        // Load then unload a model
        service.loadModel(LoadModelRequest.newBuilder().setModelId("test-model [GH-90000]").build(),
            new CapturingObserver<>()); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("test-model [GH-90000]").build(),
            new CapturingObserver<>()); // GH-90000

        CapturingObserver<ListModelsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.listModels( // GH-90000
            ListModelsRequest.newBuilder().setIncludeLoadedOnly(true).build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getTotalCount()).isEqualTo(0); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatus: returns healthy status with backend name [GH-90000]")
    void getStatus_returnsStatus() { // GH-90000
        CapturingObserver<StatusResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getStatus(StatusRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getStatus()).contains("healthy [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // healthCheck
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("healthCheck: always returns healthy = true [GH-90000]")
    void healthCheck_returnsHealthy() { // GH-90000
        CapturingObserver<HealthCheckResponse> observer = new CapturingObserver<>(); // GH-90000
        service.healthCheck(HealthCheckRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getHealthy()).isTrue(); // GH-90000
        assertThat(observer.getValue().getMessage()).isNotBlank(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test infrastructure
    // ─────────────────────────────────────────────────────────────────────────

    static class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override public void onNext(T value) { this.value = value; } // GH-90000
        @Override public void onError(Throwable t) { this.error = t; } // GH-90000
        @Override public void onCompleted() {} // GH-90000

        T getValue() { return value; } // GH-90000
        boolean hasError() { return error != null; } // GH-90000
        Throwable getError() { return error; } // GH-90000
    }
}
