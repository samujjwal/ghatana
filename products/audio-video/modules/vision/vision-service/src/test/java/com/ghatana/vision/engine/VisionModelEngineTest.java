package com.ghatana.vision.engine;

import com.ghatana.vision.engine.VisionModelEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

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
    void setUp() {
        engine = new VisionModelEngine("yolov8-base", 0.5);
    }

    // ── Object detection ──────────────────────────────────────────────────────

    @Test
    @DisplayName("detectObjects returns a non-null list for valid image")
    void detectObjectsReturnsResult() {
        assertThat(engine.detectObjects(SAMPLE_IMAGE)).isNotNull();
    }

    @Test
    @DisplayName("all detected objects have confidence above the threshold")
    void detectedObjectsMeetConfidenceThreshold() {
        engine.detectObjects(SAMPLE_IMAGE).forEach(obj ->
                assertThat(obj.confidence()).isGreaterThanOrEqualTo(0.5));
    }

    @Test
    @DisplayName("detected objects have valid bounding box dimensions (width/height > 0)")
    void detectedObjectsBoundingBoxPositive() {
        engine.detectObjects(SAMPLE_IMAGE).forEach(obj -> {
            assertThat(obj.width()).isGreaterThan(0.0);
            assertThat(obj.height()).isGreaterThan(0.0);
        });
    }

    @Test
    @DisplayName("detected objects have non-blank labels")
    void detectedObjectsHaveLabels() {
        engine.detectObjects(SAMPLE_IMAGE).forEach(obj ->
                assertThat(obj.label()).isNotBlank());
    }

    @Test
    @DisplayName("higher confidence threshold reduces number of detections")
    void higherThresholdReducesDetections() {
        VisionModelEngine strict = new VisionModelEngine("yolov8-base", 0.9);
        int strictCount = strict.detectObjects(SAMPLE_IMAGE).size();
        int lenientCount = engine.detectObjects(SAMPLE_IMAGE).size();
        // strict ≤ lenient
        assertThat(strictCount).isLessThanOrEqualTo(lenientCount);
    }

    // ── Image classification ──────────────────────────────────────────────────

    @Test
    @DisplayName("classify returns a non-null result")
    void classifyReturnsResult() {
        assertThat(engine.classify(SAMPLE_IMAGE)).isNotNull();
    }

    @Test
    @DisplayName("classification label is non-blank")
    void classificationLabelIsNonBlank() {
        ClassificationResult result = engine.classify(SAMPLE_IMAGE);
        assertThat(result.label()).isNotBlank();
    }

    @Test
    @DisplayName("classification confidence is normalized [0, 1]")
    void classificationConfidenceNormalized() {
        ClassificationResult result = engine.classify(SAMPLE_IMAGE);
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("classification includes non-empty top-label list")
    void classificationTopLabelsNonEmpty() {
        ClassificationResult result = engine.classify(SAMPLE_IMAGE);
        assertThat(result.topLabels()).isNotEmpty();
    }

    @Test
    @DisplayName("top label appears in the top-labels list")
    void topLabelInTopLabelsList() {
        ClassificationResult result = engine.classify(SAMPLE_IMAGE);
        assertThat(result.topLabels()).contains(result.label());
    }

    // ── OCR ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OCR returns non-null result")
    void ocrReturnsResult() {
        assertThat(engine.extractText(SAMPLE_IMAGE)).isNotNull();
    }

    @Test
    @DisplayName("OCR extracted text is non-null")
    void ocrTextIsNonNull() {
        OcrResult result = engine.extractText(SAMPLE_IMAGE);
        assertThat(result.text()).isNotNull();
    }

    @Test
    @DisplayName("OCR confidence is in [0, 1]")
    void ocrConfidenceNormalized() {
        OcrResult result = engine.extractText(SAMPLE_IMAGE);
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    // ── Face detection ────────────────────────────────────────────────────────

    @Test
    @DisplayName("detectFaces returns non-null list")
    void detectFacesReturnsResult() {
        assertThat(engine.detectFaces(SAMPLE_IMAGE)).isNotNull();
    }

    @Test
    @DisplayName("all detected faces meet confidence threshold")
    void detectedFacesMeetThreshold() {
        engine.detectFaces(SAMPLE_IMAGE).forEach(face ->
                assertThat(face.confidence()).isGreaterThanOrEqualTo(0.5));
    }

    @Test
    @DisplayName("detected faces have positive bounding box dimensions")
    void detectedFacesBoundingBoxPositive() {
        engine.detectFaces(SAMPLE_IMAGE).forEach(face -> {
            assertThat(face.width()).isGreaterThan(0.0);
            assertThat(face.height()).isGreaterThan(0.0);
        });
    }

    @Test
    @DisplayName("detected faces have non-empty landmark map")
    void detectedFacesHaveLandmarks() {
        engine.detectFaces(SAMPLE_IMAGE).forEach(face ->
                assertThat(face.landmarks()).isNotEmpty());
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("null image throws VisionException for detectObjects")
    void nullImageThrowsForDetect() {
        assertThatThrownBy(() -> engine.detectObjects(null))
                .isInstanceOf(VisionModelEngine.VisionException.class);
    }

    @Test
    @DisplayName("empty image throws VisionException for classify")
    void emptyImageThrowsForClassify() {
        assertThatThrownBy(() -> engine.classify(new byte[0]))
                .isInstanceOf(VisionModelEngine.VisionException.class);
    }

    @Test
    @DisplayName("null image throws VisionException for extractText")
    void nullImageThrowsForOcr() {
        assertThatThrownBy(() -> engine.extractText(null))
                .isInstanceOf(VisionModelEngine.VisionException.class);
    }

    @Test
    @DisplayName("invalid confidence threshold throws IllegalArgumentException")
    void invalidThresholdThrows() {
        assertThatThrownBy(() -> new VisionModelEngine("yolo", 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Engine metadata ───────────────────────────────────────────────────────

    @Test
    @DisplayName("engine reports correct model ID")
    void engineModelId() {
        assertThat(engine.getModelId()).isEqualTo("yolov8-base");
    }

    @Test
    @DisplayName("engine reports the configured confidence threshold")
    void engineConfidenceThreshold() {
        assertThat(engine.getConfidenceThreshold()).isEqualTo(0.5);
    }

    @ParameterizedTest(name = "threshold={0}")
    @ValueSource(doubles = {0.0, 0.3, 0.5, 0.7, 0.99, 1.0})
    @DisplayName("valid confidence thresholds are accepted without error")
    void validThresholdsAccepted(double threshold) {
        assertThatCode(() -> new VisionModelEngine("yolo", threshold))
                .doesNotThrowAnyException();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static byte[] buildImage(String label) {
        byte[] data = new byte[512];
        byte[] bytes = label.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, data, 0, Math.min(bytes.length, data.length));
        // Pad remainder with repeating pattern
        for (int i = bytes.length; i < data.length; i++) {
            data[i] = (byte) (i % 128);
        }
        return data;
    }
}
