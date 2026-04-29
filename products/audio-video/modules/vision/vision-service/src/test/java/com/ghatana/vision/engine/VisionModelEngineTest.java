package com.ghatana.vision.engine;

import com.ghatana.audio.video.vision.detection.VisionDetector;
import com.ghatana.audio.video.vision.detection.VisionDetector.VisionCapability;
import com.ghatana.audio.video.vision.model.BoundingBox;
import com.ghatana.audio.video.vision.model.ClassificationCandidate;
import com.ghatana.audio.video.vision.model.DetectedFace;
import com.ghatana.audio.video.vision.model.DetectionOptions;
import com.ghatana.vision.engine.VisionModelEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;

/**
 * Tests for {@link VisionModelEngine}.
 *
 * @doc.type    class
 * @doc.purpose VisionModelEngine: object detection, classification, OCR, face detection, confidence threshold
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("VisionModelEngineTest")
class VisionModelEngineTest {

    /** Minimal synthetic JPEG-like payload to satisfy non-empty validation. */
    private static final byte[] SAMPLE_IMAGE = buildImage("sample_scene");

    private VisionModelEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new VisionModelEngine("yolov8-base", 0.5); // GH-90000
    }

    // ── Object detection ──────────────────────────────────────────────────────

    @Test
    @DisplayName("detectObjects returns a non-null list for valid image")
    void detectObjectsReturnsResult() { // GH-90000
        assertThat(engine.detectObjects(SAMPLE_IMAGE)).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("all detected objects have confidence above the threshold")
    void detectedObjectsMeetConfidenceThreshold() { // GH-90000
        engine.detectObjects(SAMPLE_IMAGE).forEach(obj -> // GH-90000
                assertThat(obj.confidence()).isGreaterThanOrEqualTo(0.5)); // GH-90000
    }

    @Test
    @DisplayName("detected objects have valid bounding box dimensions (width/height > 0)")
    void detectedObjectsBoundingBoxPositive() { // GH-90000
        engine.detectObjects(SAMPLE_IMAGE).forEach(obj -> { // GH-90000
            assertThat(obj.width()).isGreaterThan(0.0); // GH-90000
            assertThat(obj.height()).isGreaterThan(0.0); // GH-90000
        });
    }

    @Test
    @DisplayName("detected objects have non-blank labels")
    void detectedObjectsHaveLabels() { // GH-90000
        engine.detectObjects(SAMPLE_IMAGE).forEach(obj -> // GH-90000
                assertThat(obj.label()).isNotBlank()); // GH-90000
    }

    @Test
    @DisplayName("higher confidence threshold reduces number of detections")
    void higherThresholdReducesDetections() { // GH-90000
        VisionModelEngine strict = new VisionModelEngine("yolov8-base", 0.9); // GH-90000
        int strictCount = strict.detectObjects(SAMPLE_IMAGE).size(); // GH-90000
        int lenientCount = engine.detectObjects(SAMPLE_IMAGE).size(); // GH-90000
        // strict ≤ lenient
        assertThat(strictCount).isLessThanOrEqualTo(lenientCount); // GH-90000
    }

    // ── Image classification ──────────────────────────────────────────────────

    @Test
    @DisplayName("classify returns a non-null result")
    void classifyReturnsResult() { // GH-90000
        assertThat(engine.classify(SAMPLE_IMAGE)).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("classification label is non-blank")
    void classificationLabelIsNonBlank() { // GH-90000
        ClassificationResult result = engine.classify(SAMPLE_IMAGE); // GH-90000
        assertThat(result.label()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("classification confidence is normalized [0, 1]")
    void classificationConfidenceNormalized() { // GH-90000
        ClassificationResult result = engine.classify(SAMPLE_IMAGE); // GH-90000
        assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
    }

    @Test
    @DisplayName("classification includes non-empty top-label list")
    void classificationTopLabelsNonEmpty() { // GH-90000
        ClassificationResult result = engine.classify(SAMPLE_IMAGE); // GH-90000
        assertThat(result.topLabels()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("top label appears in the top-labels list")
    void topLabelInTopLabelsList() { // GH-90000
        ClassificationResult result = engine.classify(SAMPLE_IMAGE); // GH-90000
        assertThat(result.topLabels()).contains(result.label()); // GH-90000
    }

    // ── OCR ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OCR returns non-null result")
    void ocrReturnsResult() { // GH-90000
        assertThat(engine.extractText(SAMPLE_IMAGE)).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("OCR extracted text is non-null")
    void ocrTextIsNonNull() { // GH-90000
        OcrResult result = engine.extractText(SAMPLE_IMAGE); // GH-90000
        assertThat(result.text()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("OCR confidence is in [0, 1]")
    void ocrConfidenceNormalized() { // GH-90000
        OcrResult result = engine.extractText(SAMPLE_IMAGE); // GH-90000
        assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
    }

    // ── Face detection ────────────────────────────────────────────────────────

    @Test
    @DisplayName("detectFaces returns non-null list")
    void detectFacesReturnsResult() { // GH-90000
        assertThat(engine.detectFaces(SAMPLE_IMAGE)).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("all detected faces meet confidence threshold")
    void detectedFacesMeetThreshold() { // GH-90000
        engine.detectFaces(SAMPLE_IMAGE).forEach(face -> // GH-90000
                assertThat(face.confidence()).isGreaterThanOrEqualTo(0.5)); // GH-90000
    }

    @Test
    @DisplayName("detected faces have positive bounding box dimensions")
    void detectedFacesBoundingBoxPositive() { // GH-90000
        engine.detectFaces(SAMPLE_IMAGE).forEach(face -> { // GH-90000
            assertThat(face.width()).isGreaterThan(0.0); // GH-90000
            assertThat(face.height()).isGreaterThan(0.0); // GH-90000
        });
    }

    @Test
    @DisplayName("detected faces have non-empty landmark map")
    void detectedFacesHaveLandmarks() { // GH-90000
        engine.detectFaces(SAMPLE_IMAGE).forEach(face -> // GH-90000
                assertThat(face.landmarks()).isNotEmpty()); // GH-90000
    }

    // ── Real-detector delegation path ─────────────────────────────────────────

    @Nested
    @DisplayName("Real-detector delegation")
    class RealDetectorDelegation {

        /**
         * Test-only inline detector that supports CLASSIFICATION and FACE_DETECTION.
         * Avoids native library loading while exercising the real delegation code path.
         */
        private VisionDetector fullCapabilityDetector(
                List<ClassificationCandidate> classResults,
                List<DetectedFace> faceResults,
                String ocrText) {
            return new VisionDetector() {
                @Override
                public List<com.ghatana.audio.video.vision.model.DetectedObject> detectObjects(
                        byte[] imageData, DetectionOptions options) {
                    return List.of();
                }

                @Override
                public boolean isInitialized() { return true; }

                @Override
                public boolean supportsCapability(VisionCapability capability) {
                    return capability == VisionCapability.CLASSIFICATION
                            || capability == VisionCapability.FACE_DETECTION
                            || capability == VisionCapability.OCR
                            || capability == VisionCapability.OBJECT_DETECTION;
                }

                @Override
                public List<ClassificationCandidate> classify(
                        byte[] imageData, DetectionOptions options) {
                    return classResults;
                }

                @Override
                public List<DetectedFace> detectFaces(
                        byte[] imageData, DetectionOptions options) {
                    return faceResults;
                }

                @Override
                public String extractText(byte[] imageData, DetectionOptions options) {
                    return ocrText;
                }
            };
        }

        @Test
        @DisplayName("classify delegates to detector when CLASSIFICATION capability is declared")
        void classifyDelegatesToDetector() { // GH-90000
            List<ClassificationCandidate> candidates = List.of(
                    new ClassificationCandidate("vehicle", 0.91),
                    new ClassificationCandidate("outdoor", 0.78));
            VisionDetector det = fullCapabilityDetector(candidates, List.of(), ""); // GH-90000

            VisionModelEngine realEngine = new VisionModelEngine("resnet50", 0.5, det); // GH-90000
            ClassificationResult result = realEngine.classify(SAMPLE_IMAGE); // GH-90000

            assertThat(result.label()).isEqualTo("vehicle"); // GH-90000
            assertThat(result.confidence()).isCloseTo(0.91, offset(0.001)); // GH-90000
            assertThat(result.topLabels()).containsExactly("vehicle", "outdoor"); // GH-90000
        }

        @Test
        @DisplayName("detectFaces delegates to detector when FACE_DETECTION capability is declared")
        void detectFacesDelegatesToDetector() { // GH-90000
            BoundingBox box = BoundingBox.builder().x(0.1).y(0.2).width(0.3).height(0.4).build(); // GH-90000
            DetectedFace face = new DetectedFace(box, 0.88); // GH-90000
            VisionDetector det = fullCapabilityDetector(List.of(), List.of(face), ""); // GH-90000

            VisionModelEngine realEngine = new VisionModelEngine("mediapipe-face", 0.5, det); // GH-90000
            List<FaceDetection> faces = realEngine.detectFaces(SAMPLE_IMAGE); // GH-90000

            assertThat(faces).hasSize(1); // GH-90000
            FaceDetection f = faces.get(0); // GH-90000
            assertThat(f.confidence()).isCloseTo(0.88, offset(0.001)); // GH-90000
            assertThat(f.x()).isCloseTo(0.1, offset(0.001)); // GH-90000
            assertThat(f.y()).isCloseTo(0.2, offset(0.001)); // GH-90000
        }

        @Test
        @DisplayName("extractText delegates to detector when OCR capability is declared")
        void extractTextDelegatesToDetector() { // GH-90000
            VisionDetector det = fullCapabilityDetector(
                    List.of(), List.of(), "Hello World from Tesseract"); // GH-90000

            VisionModelEngine realEngine = new VisionModelEngine("tesseract", 0.5, det); // GH-90000
            OcrResult result = realEngine.extractText(SAMPLE_IMAGE); // GH-90000

            assertThat(result.text()).isEqualTo("Hello World from Tesseract"); // GH-90000
        }

        @Test
        @DisplayName("classify falls back to stub when detector does not support CLASSIFICATION")
        void classifyFallsBackWhenCapabilityAbsent() { // GH-90000
            // detector only supports OBJECT_DETECTION (default supportsCapability behaviour)
            VisionDetector yoloOnly = new VisionDetector() {
                @Override
                public List<com.ghatana.audio.video.vision.model.DetectedObject> detectObjects(
                        byte[] imageData, DetectionOptions options) {
                    return List.of();
                }
                @Override
                public boolean isInitialized() { return true; }
                // supportsCapability defaults: only OBJECT_DETECTION = true
            };

            VisionModelEngine realEngine = new VisionModelEngine("yolov8-base", 0.5, yoloOnly); // GH-90000
            ClassificationResult result = realEngine.classify(SAMPLE_IMAGE); // GH-90000

            // Stub fallback still returns a valid result
            assertThat(result.label()).isNotBlank(); // GH-90000
            assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("engine uses stub for all operations when detector is not initialized")
        void uninitializedDetectorUsesFallback() { // GH-90000
            VisionDetector uninit = new VisionDetector() {
                @Override
                public List<com.ghatana.audio.video.vision.model.DetectedObject> detectObjects(
                        byte[] imageData, DetectionOptions options) {
                    return List.of();
                }
                @Override
                public boolean isInitialized() { return false; }
                @Override
                public boolean supportsCapability(VisionCapability capability) { return true; }
            };

            VisionModelEngine realEngine = new VisionModelEngine("resnet", 0.5, uninit); // GH-90000

            // Must not throw; stub fallback should activate
            assertThatCode(() -> realEngine.classify(SAMPLE_IMAGE)).doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> realEngine.detectFaces(SAMPLE_IMAGE)).doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> realEngine.extractText(SAMPLE_IMAGE)).doesNotThrowAnyException(); // GH-90000
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("null image throws VisionException for detectObjects")
    void nullImageThrowsForDetect() { // GH-90000
        assertThatThrownBy(() -> engine.detectObjects(null)) // GH-90000
                .isInstanceOf(VisionModelEngine.VisionException.class); // GH-90000
    }

    @Test
    @DisplayName("empty image throws VisionException for classify")
    void emptyImageThrowsForClassify() { // GH-90000
        assertThatThrownBy(() -> engine.classify(new byte[0])) // GH-90000
                .isInstanceOf(VisionModelEngine.VisionException.class); // GH-90000
    }

    @Test
    @DisplayName("null image throws VisionException for extractText")
    void nullImageThrowsForOcr() { // GH-90000
        assertThatThrownBy(() -> engine.extractText(null)) // GH-90000
                .isInstanceOf(VisionModelEngine.VisionException.class); // GH-90000
    }

    @Test
    @DisplayName("invalid confidence threshold throws IllegalArgumentException")
    void invalidThresholdThrows() { // GH-90000
        assertThatThrownBy(() -> new VisionModelEngine("yolo", 1.5)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ── Engine metadata ───────────────────────────────────────────────────────

    @Test
    @DisplayName("engine reports correct model ID")
    void engineModelId() { // GH-90000
        assertThat(engine.getModelId()).isEqualTo("yolov8-base");
    }

    @Test
    @DisplayName("engine reports the configured confidence threshold")
    void engineConfidenceThreshold() { // GH-90000
        assertThat(engine.getConfidenceThreshold()).isEqualTo(0.5); // GH-90000
    }

    @ParameterizedTest(name = "threshold={0}") // GH-90000
    @ValueSource(doubles = {0.0, 0.3, 0.5, 0.7, 0.99, 1.0}) // GH-90000
    @DisplayName("valid confidence thresholds are accepted without error")
    void validThresholdsAccepted(double threshold) { // GH-90000
        assertThatCode(() -> new VisionModelEngine("yolo", threshold)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static byte[] buildImage(String label) { // GH-90000
        byte[] data = new byte[512];
        byte[] bytes = label.getBytes(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
        System.arraycopy(bytes, 0, data, 0, Math.min(bytes.length, data.length)); // GH-90000
        // Pad remainder with repeating pattern
        for (int i = bytes.length; i < data.length; i++) { // GH-90000
            data[i] = (byte) (i % 128); // GH-90000
        }
        return data;
    }
}
