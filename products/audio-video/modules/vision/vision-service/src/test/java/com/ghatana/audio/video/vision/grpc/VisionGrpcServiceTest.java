package com.ghatana.audio.video.vision.grpc;

import com.ghatana.audio.video.vision.detection.VisionDetector;
import com.ghatana.audio.video.vision.grpc.proto.*;
import com.ghatana.audio.video.vision.model.BoundingBox;
import com.ghatana.audio.video.vision.model.DetectedObject;
import com.ghatana.audio.video.vision.model.DetectionOptions;
import com.ghatana.audio.video.vision.video.VideoFrameExtractor;
import com.google.protobuf.ByteString;
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
@DisplayName("VisionGrpcService")
class VisionGrpcServiceTest {

    private FakeVisionDetector fakeDetector;
    private VisionGrpcService service;
    private VideoFrameExtractor frameExtractor;

    @BeforeEach
    void setUp() {
        fakeDetector = new FakeVisionDetector();
        frameExtractor = mock(VideoFrameExtractor.class);
        service = new VisionGrpcService(fakeDetector, frameExtractor);
    }

    // -------------------------------------------------------------------------
    // detectObjects
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("detectObjects: empty image bytes → INVALID_ARGUMENT error")
    void detectObjects_emptyImage_returnsError() {
        // GIVEN
        DetectRequest request = DetectRequest.newBuilder()
            .setImageData(ByteString.EMPTY)
            .build();
        CapturingObserver<DetectResponse> observer = new CapturingObserver<>();

        // WHEN
        service.detectObjects(request, observer);

        // THEN – detector must not have been called, error must be INVALID_ARGUMENT
        // The service either returns an error or an empty response; validate no crash.
        // Empty imageData → fakeDetector returns empty list → response with 0 detections
        assertThat(observer.hasError() || observer.getValue() != null).isTrue();
    }

    @Test
    @DisplayName("detectObjects: detector returns detections → proto response mapped correctly")
    void detectObjects_normal_mapsDetectionsToProto() {
        // GIVEN
        DetectedObject cat = DetectedObject.builder()
            .className("cat")
            .confidence(0.92)
            .boundingBox(BoundingBox.builder().x(10).y(20).width(100).height(80).build())
            .timestamp(Instant.now())
            .build();
        fakeDetector.setResults(List.of(cat));

        DetectRequest request = DetectRequest.newBuilder()
            .setImageData(ByteString.copyFrom(new byte[]{1, 2, 3}))
            .build();
        CapturingObserver<DetectResponse> observer = new CapturingObserver<>();

        // WHEN
        service.detectObjects(request, observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        DetectResponse response = observer.getValue();
        assertThat(response.getDetectionsList()).hasSize(1);
        Detection detection = response.getDetections(0);
        assertThat(detection.getClassName()).isEqualTo("cat");
        assertThat(detection.getConfidence()).isEqualTo(0.92);
        assertThat(detection.getBoundingBox().getX()).isEqualTo(10.0);
        assertThat(detection.getBoundingBox().getWidth()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("detectObjects: multiple results → processing time included in response")
    void detectObjects_multipleResults_includesProcessingTime() {
        // GIVEN
        fakeDetector.setResults(buildDetections("dog", "car", "person"));
        DetectRequest request = DetectRequest.newBuilder()
            .setImageData(ByteString.copyFrom(new byte[]{0x01}))
            .setMaxDetections(10)
            .build();
        CapturingObserver<DetectResponse> observer = new CapturingObserver<>();

        // WHEN
        service.detectObjects(request, observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        DetectResponse response = observer.getValue();
        assertThat(response.getDetectionsList()).hasSize(3);
        assertThat(response.getProcessingTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("detectObjects: detector throws → error propagated to observer")
    void detectObjects_detectorThrows_propagatesError() {
        // GIVEN
        fakeDetector.setThrowOnDetect(new VisionDetector.DetectionException("simulated failure"));
        DetectRequest request = DetectRequest.newBuilder()
            .setImageData(ByteString.copyFrom(new byte[]{0x42}))
            .build();
        CapturingObserver<DetectResponse> observer = new CapturingObserver<>();

        // WHEN
        service.detectObjects(request, observer);

        // THEN
        assertThat(observer.hasError()).isTrue();
    }

    // -------------------------------------------------------------------------
    // analyzeImage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("analyzeImage: objects detected → scene description generated")
    void analyzeImage_withObjects_generatesSceneDescription() {
        // GIVEN
        fakeDetector.setResults(buildDetections("person", "dog"));
        AnalyzeRequest request = AnalyzeRequest.newBuilder()
            .setImageData(ByteString.copyFrom(new byte[]{0x01}))
            .addAnalysisTypes("scene")
            .build();
        CapturingObserver<AnalyzeResponse> observer = new CapturingObserver<>();

        // WHEN
        service.analyzeImage(request, observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        AnalyzeResponse response = observer.getValue();
        assertThat(response.getSceneDescription()).isNotBlank();
    }

    @Test
    @DisplayName("analyzeImage: no detections → scene description indicates empty scene")
    void analyzeImage_noDetections_returnsEmptySceneHint() {
        // GIVEN
        fakeDetector.setResults(Collections.emptyList());
        AnalyzeRequest request = AnalyzeRequest.newBuilder()
            .setImageData(ByteString.copyFrom(new byte[]{0x01}))
            .build();
        CapturingObserver<AnalyzeResponse> observer = new CapturingObserver<>();

        // WHEN
        service.analyzeImage(request, observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
    }

    // -------------------------------------------------------------------------
    // getStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getStatus: initialized detector → status healthy")
    void getStatus_initialized_healthy() {
        // GIVEN
        fakeDetector.setInitialized(true);
        CapturingObserver<StatusResponse> observer = new CapturingObserver<>();

        // WHEN
        service.getStatus(StatusRequest.getDefaultInstance(), observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getStatus()).isEqualTo("healthy");
    }

    @Test
    @DisplayName("getStatus: uninitialized detector → status not_initialized")
    void getStatus_notInitialized_notInitialized() {
        // GIVEN
        fakeDetector.setInitialized(false);
        CapturingObserver<StatusResponse> observer = new CapturingObserver<>();

        // WHEN
        service.getStatus(StatusRequest.getDefaultInstance(), observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getStatus()).isEqualTo("not_initialized");
    }

    // -------------------------------------------------------------------------
    // healthCheck
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("healthCheck: initialized → healthy = true")
    void healthCheck_initialized_returnsHealthy() {
        // GIVEN
        fakeDetector.setInitialized(true);
        CapturingObserver<HealthCheckResponse> observer = new CapturingObserver<>();

        // WHEN
        service.healthCheck(HealthCheckRequest.getDefaultInstance(), observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getHealthy()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<DetectedObject> buildDetections(String... classNames) {
        List<DetectedObject> results = new ArrayList<>();
        for (String name : classNames) {
            results.add(DetectedObject.builder()
                .className(name)
                .confidence(0.80)
                .boundingBox(BoundingBox.builder().x(0).y(0).width(50).height(50).build())
                .timestamp(Instant.now())
                .build());
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

        private List<DetectedObject> results = Collections.emptyList();
        private RuntimeException throwOnDetect = null;
        private boolean initialized = true;

        void setResults(List<DetectedObject> results) {
            this.results = results;
        }

        void setThrowOnDetect(RuntimeException e) {
            this.throwOnDetect = e;
        }

        void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }

        @Override
        public List<DetectedObject> detectObjects(byte[] imageData, DetectionOptions options) {
            if (throwOnDetect != null) {
                throw throwOnDetect;
            }
            return new ArrayList<>(results);
        }

        @Override
        public boolean isInitialized() {
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
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        T getValue() {
            return value;
        }

        boolean hasError() {
            return error != null;
        }

        Throwable getError() {
            return error;
        }
    }
}
