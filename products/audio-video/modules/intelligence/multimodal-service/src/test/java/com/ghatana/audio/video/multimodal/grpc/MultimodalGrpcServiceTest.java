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
@DisplayName("MultimodalGrpcService")
class MultimodalGrpcServiceTest {

    @Mock
    private MultimodalAnalysisEngine mockEngine;

    @Mock
    private PlatformMultimodalAdapter mockAdapter;

    private MultimodalGrpcService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new MultimodalGrpcService(mockEngine, mockAdapter, MediaProcessingMetrics.noop()); // GH-90000
        lenient().when(mockAdapter.backendName()).thenReturn("test-backend");
        lenient().when(mockAdapter.metricsEnabled()).thenReturn(false); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // processMultimodal (AV-004) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processMultimodal: audio + image → fused response returned")
    void processMultimodal_withAudioAndImage_returnsFusedResponse() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("hello world").confidence(0.9).build())
            .visualResult(VisualResult.builder().sceneDescription("A sunny park").confidence(0.8).build())
            .combinedAnalysis("Speech: hello world; Visual: A sunny park")
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
        assertThat(response.getCombinedAnalysis()).contains("Speech: hello world");
        assertThat(response.getAudioAnalysis().getTranscription()).isEqualTo("hello world");
        assertThat(response.getVisualAnalysis().getSceneDescription()).isEqualTo("A sunny park");
        assertThat(response.getProcessingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("processMultimodal: engine throws → INTERNAL returned")
    void processMultimodal_engineThrows_returnsInternal() { // GH-90000
        when(mockEngine.analyse(any())).thenThrow(new RuntimeException("engine failure"));

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
    @DisplayName("processMultimodal: audio-only request → no visual analysis in response")
    void processMultimodal_audioOnly_hasNoVisualAnalysis() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("test").confidence(0.9).build())
            .combinedAnalysis("Speech: test")
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
    @DisplayName("generateDescription: audio + context → description with key elements")
    void generateDescription_withAudioAndContext_returnsDescription() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("meeting summary").confidence(0.9).build())
            .combinedAnalysis("Meeting was productive")
            .processingTimeMs(20L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        CapturingObserver<DescriptionResponse> observer = new CapturingObserver<>(); // GH-90000
        service.generateDescription( // GH-90000
            DescriptionRequest.newBuilder() // GH-90000
                .setAudioData(ByteString.copyFrom(new byte[]{1, 2})) // GH-90000
                .setContext("quarterly review")
                .build(), // GH-90000
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        DescriptionResponse response = observer.getValue(); // GH-90000
        assertThat(response.getDescription()).isNotBlank(); // GH-90000
        assertThat(response.getKeyElementsList()).contains("speech");
        assertThat(response.getConfidence()).isGreaterThan(0.0); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // analyzeVideoWithAudio
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("analyzeVideoWithAudio: valid video → combined narrative returned")
    void analyzeVideoWithAudio_valid_returnsCombinedNarrative() { // GH-90000
        VideoAudioResult result = VideoAudioResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("narrator speaking").confidence(0.85).build())
            .videoResult(VisualResult.builder().sceneDescription("outdoor scene").confidence(0.9).build())
            .temporalAlignments(List.of()) // GH-90000
            .combinedNarrative("narrator speaking with outdoor scene")
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
        assertThat(observer.getValue().getCombinedNarrative()).isEqualTo("narrator speaking with outdoor scene");
        assertThat(observer.getValue().getAudioTranscription()).isEqualTo("narrator speaking");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // analyzeCrossModal (AV-004.4) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("analyzeCrossModal: no audio and no video → INVALID_ARGUMENT")
    void analyzeCrossModal_noAudioNoVideo_returnsInvalidArgument() { // GH-90000
        CapturingObserver<CrossModalResponse> observer = new CapturingObserver<>(); // GH-90000
        service.analyzeCrossModal(CrossModalRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("analyzeCrossModal: both audio and video → alignment score computed")
    void analyzeCrossModal_withBothModalities_returnsAlignmentScore() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("a person running").confidence(0.9).build())
            .visualResult(VisualResult.builder().sceneDescription("a person running fast").confidence(0.85).build())
            .combinedAnalysis("aligned")
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
    @DisplayName("analyzeCrossModal: audio only → partial alignment score")
    void analyzeCrossModal_audioOnly_returnsPartialAlignment() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder().transcription("hello").confidence(0.9).build())
            .combinedAnalysis("audio only")
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
        assertThat(observer.getValue().getEvents(0).getEventType()).isEqualTo("mismatch");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getInsights (AV-004.5) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getInsights: audio with transcription → topics and sentiment extracted")
    void getInsights_withAudio_extractsTopicsAndSentiment() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .audioResult(AudioResult.builder() // GH-90000
                .transcription("The project is great and the team did a good job")
                .confidence(0.9).build()) // GH-90000
            .combinedAnalysis("positive meeting")
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
        assertThat(response.getTopicsList()).contains("speech");
        assertThat(response.getOverallSentiment()).isEqualTo("positive");
        assertThat(response.getProcessingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("getInsights: text only → text topic included")
    void getInsights_withTextOnly_includesTextTopic() { // GH-90000
        MultimodalResult result = MultimodalResult.builder() // GH-90000
            .combinedAnalysis("text only")
            .processingTimeMs(5L) // GH-90000
            .build(); // GH-90000
        when(mockEngine.analyse(any())).thenReturn(result); // GH-90000

        CapturingObserver<InsightsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getInsights( // GH-90000
            InsightsRequest.newBuilder().setText("some context").build(),
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getTopicsList()).contains("text");
        assertThat(observer.getValue().getOverallSentiment()).isEqualTo("neutral");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadModel (AV-004.1) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadModel: blank modelId → INVALID_ARGUMENT")
    void loadModel_blankModelId_returnsInvalidArgument() { // GH-90000
        CapturingObserver<LoadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.loadModel(LoadModelRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("loadModel: valid modelId → success response")
    void loadModel_validModelId_returnsSuccess() { // GH-90000
        CapturingObserver<LoadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.loadModel(LoadModelRequest.newBuilder().setModelId("clip-vit-large").build(), observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getSuccess()).isTrue(); // GH-90000
        assertThat(observer.getValue().getModelId()).isEqualTo("clip-vit-large");
        assertThat(observer.getValue().getLoadTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // unloadModel (AV-004.2) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unloadModel: blank modelId → INVALID_ARGUMENT")
    void unloadModel_blankModelId_returnsInvalidArgument() { // GH-90000
        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("unloadModel: unregistered modelId → NOT_FOUND")
    void unloadModel_unregisteredModel_returnsNotFound() { // GH-90000
        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("ghost-model").build(), observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    @Test
    @DisplayName("unloadModel: loaded model → success response with freed bytes")
    void unloadModel_loadedModel_returnsSuccess() { // GH-90000
        // Load first
        service.loadModel(LoadModelRequest.newBuilder().setModelId("clip-vit").build(),
            new CapturingObserver<>()); // GH-90000

        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("clip-vit").build(), observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getSuccess()).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listModels (AV-004.3) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listModels: empty registry → zero count")
    void listModels_emptyRegistry_returnsZeroModels() { // GH-90000
        CapturingObserver<ListModelsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.listModels(ListModelsRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getTotalCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("listModels: after loadModel → includes loaded model")
    void listModels_afterLoad_includesModel() { // GH-90000
        service.loadModel(LoadModelRequest.newBuilder().setModelId("whisper-large").build(),
            new CapturingObserver<>()); // GH-90000

        CapturingObserver<ListModelsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.listModels(ListModelsRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        ListModelsResponse response = observer.getValue(); // GH-90000
        assertThat(response.getTotalCount()).isGreaterThanOrEqualTo(1); // GH-90000
        assertThat(response.getLoadedCount()).isGreaterThanOrEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("listModels: loadedOnly filter → unloaded models excluded")
    void listModels_loadedOnlyFilter_excludesUnloaded() { // GH-90000
        // Load then unload a model
        service.loadModel(LoadModelRequest.newBuilder().setModelId("test-model").build(),
            new CapturingObserver<>()); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("test-model").build(),
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
    @DisplayName("getStatus: returns healthy status with backend name")
    void getStatus_returnsStatus() { // GH-90000
        CapturingObserver<StatusResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getStatus(StatusRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getStatus()).contains("healthy");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // healthCheck
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("healthCheck: always returns healthy = true")
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
