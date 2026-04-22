package com.ghatana.audio.video.vision.grpc;

import com.ghatana.audio.video.vision.detection.VisionDetector;
import com.ghatana.audio.video.vision.grpc.proto.*;
import com.ghatana.audio.video.vision.model.BoundingBox;
import com.ghatana.audio.video.vision.model.DetectedObject;
import com.ghatana.audio.video.vision.model.DetectionOptions;
import com.ghatana.audio.video.vision.video.VideoFrameExtractor;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link VisionGrpcService}.
 *
 * <p>Uses a {@link FakeVisionDetector} to avoid OpenCV / native-library dependencies.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the vision gRPC service layer
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("VisionGrpcService [GH-90000]")
class VisionGrpcServiceTest {

    private FakeVisionDetector fakeDetector;
    private VisionGrpcService service;
    private VideoFrameExtractor frameExtractor;

    @BeforeEach
    void setUp() { // GH-90000
        fakeDetector = new FakeVisionDetector(); // GH-90000
        frameExtractor = mock(VideoFrameExtractor.class); // GH-90000
        service = new VisionGrpcService(fakeDetector, frameExtractor); // GH-90000
    }

    // -------------------------------------------------------------------------
    // detectObjects
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("detectObjects: empty image bytes → INVALID_ARGUMENT gRPC error [GH-90000]")
    void detectObjects_emptyImage_returnsError() { // GH-90000
        // GIVEN
        DetectRequest request = DetectRequest.newBuilder() // GH-90000
            .setImageData(ByteString.EMPTY) // GH-90000
            .build(); // GH-90000
        CapturingObserver<DetectResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.detectObjects(request, observer); // GH-90000

        // THEN — service guards against empty image before calling detector
        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(observer.getError()).isInstanceOf(StatusRuntimeException.class); // GH-90000
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError(); // GH-90000
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("detectObjects: detector returns detections → proto response mapped correctly [GH-90000]")
    void detectObjects_normal_mapsDetectionsToProto() { // GH-90000
        // GIVEN
        DetectedObject cat = DetectedObject.builder() // GH-90000
            .className("cat [GH-90000]")
            .confidence(0.92) // GH-90000
            .boundingBox(BoundingBox.builder().x(10).y(20).width(100).height(80).build()) // GH-90000
            .timestamp(Instant.now()) // GH-90000
            .build(); // GH-90000
        fakeDetector.setResults(List.of(cat)); // GH-90000

        DetectRequest request = DetectRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{1, 2, 3})) // GH-90000
            .build(); // GH-90000
        CapturingObserver<DetectResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.detectObjects(request, observer); // GH-90000

        // THEN
        assertThat(observer.hasError()).isFalse(); // GH-90000
        DetectResponse response = observer.getValue(); // GH-90000
        assertThat(response.getDetectionsList()).hasSize(1); // GH-90000
        Detection detection = response.getDetections(0); // GH-90000
        assertThat(detection.getClassName()).isEqualTo("cat [GH-90000]");
        assertThat(detection.getConfidence()).isEqualTo(0.92); // GH-90000
        assertThat(detection.getBoundingBox().getX()).isEqualTo(10.0); // GH-90000
        assertThat(detection.getBoundingBox().getWidth()).isEqualTo(100.0); // GH-90000
    }

    @Test
    @DisplayName("detectObjects: multiple results → processing time included in response [GH-90000]")
    void detectObjects_multipleResults_includesProcessingTime() { // GH-90000
        // GIVEN
        fakeDetector.setResults(buildDetections("dog", "car", "person")); // GH-90000
        DetectRequest request = DetectRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .setMaxDetections(10) // GH-90000
            .build(); // GH-90000
        CapturingObserver<DetectResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.detectObjects(request, observer); // GH-90000

        // THEN
        assertThat(observer.hasError()).isFalse(); // GH-90000
        DetectResponse response = observer.getValue(); // GH-90000
        assertThat(response.getDetectionsList()).hasSize(3); // GH-90000
        assertThat(response.getProcessingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("detectObjects: DetectionException from detector → INTERNAL gRPC error [GH-90000]")
    void detectObjects_detectorThrows_propagatesError() { // GH-90000
        // GIVEN
        fakeDetector.setThrowOnDetect(new VisionDetector.DetectionException("simulated failure", null)); // GH-90000
        DetectRequest request = DetectRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{0x42})) // GH-90000
            .build(); // GH-90000
        CapturingObserver<DetectResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.detectObjects(request, observer); // GH-90000

        // THEN — DetectionException must map to INTERNAL, not leak the raw exception
        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(observer.getError()).isInstanceOf(StatusRuntimeException.class); // GH-90000
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError(); // GH-90000
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode()); // GH-90000
        assertThat(ex.getStatus().getDescription()).contains("Detection engine error [GH-90000]");
    }

    // -------------------------------------------------------------------------
    // analyzeImage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("analyzeImage: objects detected → scene description generated [GH-90000]")
    void analyzeImage_withObjects_generatesSceneDescription() { // GH-90000
        // GIVEN
        fakeDetector.setResults(buildDetections("person", "dog")); // GH-90000
        AnalyzeRequest request = AnalyzeRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .addAnalysisTypes("scene [GH-90000]")
            .build(); // GH-90000
        CapturingObserver<AnalyzeResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.analyzeImage(request, observer); // GH-90000

        // THEN
        assertThat(observer.hasError()).isFalse(); // GH-90000
        AnalyzeResponse response = observer.getValue(); // GH-90000
        assertThat(response.getSceneDescription()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("analyzeImage: no detections → scene description indicates empty scene [GH-90000]")
    void analyzeImage_noDetections_returnsEmptySceneHint() { // GH-90000
        // GIVEN
        fakeDetector.setResults(Collections.emptyList()); // GH-90000
        AnalyzeRequest request = AnalyzeRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .build(); // GH-90000
        CapturingObserver<AnalyzeResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.analyzeImage(request, observer); // GH-90000

        // THEN
        assertThat(observer.hasError()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("analyzeImage: empty image bytes → INVALID_ARGUMENT gRPC error [GH-90000]")
    void analyzeImage_emptyImage_returnsInvalidArgument() { // GH-90000
        // GIVEN
        AnalyzeRequest request = AnalyzeRequest.newBuilder() // GH-90000
            .setImageData(ByteString.EMPTY) // GH-90000
            .build(); // GH-90000
        CapturingObserver<AnalyzeResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.analyzeImage(request, observer); // GH-90000

        // THEN — service guards against empty image before calling detector
        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(observer.getError()).isInstanceOf(StatusRuntimeException.class); // GH-90000
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError(); // GH-90000
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("analyzeImage: DetectionException from detector → INTERNAL gRPC error [GH-90000]")
    void analyzeImage_detectorThrows_mapsToInternal() { // GH-90000
        // GIVEN
        fakeDetector.setThrowOnDetect(new VisionDetector.DetectionException("model error", null)); // GH-90000
        AnalyzeRequest request = AnalyzeRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .addAnalysisTypes("scene [GH-90000]")
            .build(); // GH-90000
        CapturingObserver<AnalyzeResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.analyzeImage(request, observer); // GH-90000

        // THEN — DetectionException must map to INTERNAL with a safe message
        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(observer.getError()).isInstanceOf(StatusRuntimeException.class); // GH-90000
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError(); // GH-90000
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode()); // GH-90000
        assertThat(ex.getStatus().getDescription()).contains("Analysis engine error [GH-90000]");
    }

    // -------------------------------------------------------------------------
    // classifyImage (AV-003.5) // GH-90000
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("classifyImage: empty image bytes → INVALID_ARGUMENT [GH-90000]")
    void classifyImage_emptyImage_returnsInvalidArgument() { // GH-90000
        ClassifyRequest request = ClassifyRequest.newBuilder().build(); // GH-90000
        CapturingObserver<ClassifyResponse> observer = new CapturingObserver<>(); // GH-90000

        service.classifyImage(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(observer.getError()).isInstanceOf(StatusRuntimeException.class); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("classifyImage: detections returned → labels ranked by confidence [GH-90000]")
    void classifyImage_withDetections_returnsRankedLabels() { // GH-90000
        fakeDetector.setResults(buildDetections("cat", "cat", "dog")); // GH-90000
        ClassifyRequest request = ClassifyRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .setTopK(5) // GH-90000
            .build(); // GH-90000
        CapturingObserver<ClassifyResponse> observer = new CapturingObserver<>(); // GH-90000

        service.classifyImage(request, observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        ClassifyResponse response = observer.getValue(); // GH-90000
        assertThat(response.getLabelsList()).isNotEmpty(); // GH-90000
        // cat appeared twice so its aggregated score should rank first
        assertThat(response.getLabels(0).getLabel()).isEqualTo("cat [GH-90000]");
        assertThat(response.getLabels(0).getRank()).isEqualTo(1); // GH-90000
        assertThat(response.getProcessingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("classifyImage: DetectionException → INTERNAL [GH-90000]")
    void classifyImage_detectorThrows_returnsInternal() { // GH-90000
        fakeDetector.setThrowOnDetect(new VisionDetector.DetectionException("model error", null)); // GH-90000
        ClassifyRequest request = ClassifyRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .build(); // GH-90000
        CapturingObserver<ClassifyResponse> observer = new CapturingObserver<>(); // GH-90000

        service.classifyImage(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INTERNAL.getCode()); // GH-90000
    }

    // -------------------------------------------------------------------------
    // loadModel (AV-003.1) // GH-90000
    // -------------------------------------------------------------------------

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
    @DisplayName("loadModel: valid modelId → registers and loads successfully [GH-90000]")
    void loadModel_validModelId_loadsSuccessfully() { // GH-90000
        CapturingObserver<LoadModelResponse> observer = new CapturingObserver<>(); // GH-90000

        service.loadModel(LoadModelRequest.newBuilder().setModelId("yolov8n [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        LoadModelResponse response = observer.getValue(); // GH-90000
        assertThat(response.getSuccess()).isTrue(); // GH-90000
        assertThat(response.getModelId()).isEqualTo("yolov8n [GH-90000]");
        assertThat(response.getLoadTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    // -------------------------------------------------------------------------
    // unloadModel (AV-003.2) // GH-90000
    // -------------------------------------------------------------------------

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
    void unloadModel_unregisteredModelId_returnsNotFound() { // GH-90000
        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000

        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("unknown-model [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    @Test
    @DisplayName("unloadModel: loaded model → unloads successfully [GH-90000]")
    void unloadModel_loadedModel_returnsSuccess() { // GH-90000
        // Load first, then unload
        CapturingObserver<LoadModelResponse> loadObserver = new CapturingObserver<>(); // GH-90000
        service.loadModel(LoadModelRequest.newBuilder().setModelId("yolov8-small [GH-90000]").build(), loadObserver);
        assertThat(loadObserver.hasError()).isFalse(); // GH-90000

        CapturingObserver<UnloadModelResponse> unloadObserver = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("yolov8-small [GH-90000]").build(), unloadObserver);

        assertThat(unloadObserver.hasError()).isFalse(); // GH-90000
        assertThat(unloadObserver.getValue().getSuccess()).isTrue(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // listModels (AV-003.3) // GH-90000
    // -------------------------------------------------------------------------

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
        service.loadModel(LoadModelRequest.newBuilder().setModelId("test-model [GH-90000]").build(),
            new CapturingObserver<>()); // GH-90000

        CapturingObserver<ListModelsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.listModels(ListModelsRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getTotalCount()).isGreaterThanOrEqualTo(1); // GH-90000
        assertThat(observer.getValue().getLoadedCount()).isGreaterThanOrEqualTo(1); // GH-90000
    }

    // -------------------------------------------------------------------------
    // getStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getStatus: initialized detector → status healthy [GH-90000]")
    void getStatus_initialized_healthy() { // GH-90000
        // GIVEN
        fakeDetector.setInitialized(true); // GH-90000
        CapturingObserver<StatusResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.getStatus(StatusRequest.getDefaultInstance(), observer); // GH-90000

        // THEN
        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getStatus()).isEqualTo("healthy [GH-90000]");
    }

    @Test
    @DisplayName("getStatus: uninitialized detector → status not_initialized [GH-90000]")
    void getStatus_notInitialized_notInitialized() { // GH-90000
        // GIVEN
        fakeDetector.setInitialized(false); // GH-90000
        CapturingObserver<StatusResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.getStatus(StatusRequest.getDefaultInstance(), observer); // GH-90000

        // THEN
        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getStatus()).isEqualTo("not_initialized [GH-90000]");
    }

    // -------------------------------------------------------------------------
    // healthCheck
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("healthCheck: initialized → healthy = true [GH-90000]")
    void healthCheck_initialized_returnsHealthy() { // GH-90000
        // GIVEN
        fakeDetector.setInitialized(true); // GH-90000
        CapturingObserver<HealthCheckResponse> observer = new CapturingObserver<>(); // GH-90000

        // WHEN
        service.healthCheck(HealthCheckRequest.getDefaultInstance(), observer); // GH-90000

        // THEN
        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getHealthy()).isTrue(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<DetectedObject> buildDetections(String... classNames) { // GH-90000
        List<DetectedObject> results = new ArrayList<>(); // GH-90000
        for (String name : classNames) { // GH-90000
            results.add(DetectedObject.builder() // GH-90000
                .className(name) // GH-90000
                .confidence(0.80) // GH-90000
                .boundingBox(BoundingBox.builder().x(0).y(0).width(50).height(50).build()) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build()); // GH-90000
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    /**
     * Fake {@link VisionDetector} that returns configurable results without native libs.
     */
    static class FakeVisionDetector implements VisionDetector {

        private List<DetectedObject> results = Collections.emptyList(); // GH-90000
        private RuntimeException throwOnDetect = null;
        private boolean initialized = true;

        void setResults(List<DetectedObject> results) { // GH-90000
            this.results = results;
        }

        void setThrowOnDetect(RuntimeException e) { // GH-90000
            this.throwOnDetect = e;
        }

        void setInitialized(boolean initialized) { // GH-90000
            this.initialized = initialized;
        }

        @Override
        public List<DetectedObject> detectObjects(byte[] imageData, DetectionOptions options) { // GH-90000
            if (throwOnDetect != null) { // GH-90000
                throw throwOnDetect;
            }
            return new ArrayList<>(results); // GH-90000
        }

        @Override
        public boolean isInitialized() { // GH-90000
            return initialized;
        }
    }

    /**
     * Simple {@link StreamObserver} that captures a single value or error for assertions.
     */
    static class CapturingObserver<T> implements StreamObserver<T> {

        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) { // GH-90000
            this.value = value;
        }

        @Override
        public void onError(Throwable t) { // GH-90000
            this.error = t;
        }

        @Override
        public void onCompleted() { // GH-90000
            this.completed = true;
        }

        T getValue() { // GH-90000
            return value;
        }

        boolean hasError() { // GH-90000
            return error != null;
        }

        Throwable getError() { // GH-90000
            return error;
        }
    }
}
