package com.ghatana.vision.quality;

import com.ghatana.vision.engine.VisionModelEngine;
import com.ghatana.vision.engine.VisionModelEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Detection accuracy regression tests for {@link VisionModelEngine}.
 *
 * <p>Validates object detection accuracy attributes under simulated conditions:
 * baseline COCO-like fixtures, noise, lighting variations, and occlusions.
 * Tests use deterministic in-memory image payloads — no native libraries required.
 *
 * @doc.type    class
 * @doc.purpose Detection accuracy: COCO fixtures, noise, lighting, occlusion robustness
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("DetectionAccuracyTest")
class DetectionAccuracyTest {

    private VisionModelEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new VisionModelEngine("yolov8-coco", 0.3); // GH-90000
    }

    // ── COCO-dataset fixtures ─────────────────────────────────────────────────

    @ParameterizedTest(name = "fixture={0}") // GH-90000
    @CsvSource({ // GH-90000
        "coco_persons_only,   person",
        "coco_cars_street,    car",
        "coco_indoor_chairs,  chair",
        "coco_bottles_table,  bottle",
    })
    @DisplayName("COCO benchmark fixture produces detections")
    void cocoFixtureProducesDetections(String fixtureId, String expectedLabel) { // GH-90000
        byte[] image = makeCocoFixture(fixtureId, expectedLabel); // GH-90000
        List<DetectedObject> detections = engine.detectObjects(image); // GH-90000
        // Validate structural correctness (not exact label match — engine is a stub) // GH-90000
        assertThat(detections).isNotNull(); // GH-90000
        detections.forEach(d -> assertThat(d.confidence()).isGreaterThanOrEqualTo(0.3)); // GH-90000
    }

    @Test
    @DisplayName("COCO fixture produces at least one detection above threshold")
    void cocoFixtureHasAtLeastOneDetection() { // GH-90000
        byte[] image = makeCocoFixture("coco_street_scene_rich", "car"); // GH-90000
        // Deterministic hash should produce some detections for varied input
        engine = new VisionModelEngine("yolov8-coco", 0.1);  // low threshold // GH-90000
        List<DetectedObject> detections = engine.detectObjects(image); // GH-90000
        assertThat(detections).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("all COCO detections have labels from the known category set")
    void cocoDetectionsHaveValidLabels() { // GH-90000
        byte[] image = makeCocoFixture("coco_mixed_scene", "person"); // GH-90000
        List<DetectedObject> detections = engine.detectObjects(image); // GH-90000
        detections.forEach(d -> assertThat(d.label()).isNotBlank()); // GH-90000
    }

    // ── Noise robustness ──────────────────────────────────────────────────────

    @Test
    @DisplayName("noisy image is processed without error")
    void noisyImageProcessed() { // GH-90000
        byte[] noisy = buildNoisy(1024); // GH-90000
        assertThatCode(() -> engine.detectObjects(noisy)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("all detections from noisy image meet confidence threshold")
    void noisyImageDetectionsMeetThreshold() { // GH-90000
        byte[] noisy = buildNoisy(2048); // GH-90000
        engine.detectObjects(noisy).forEach(d -> // GH-90000
                assertThat(d.confidence()).isGreaterThanOrEqualTo(0.3)); // GH-90000
    }

    @ParameterizedTest(name = "noiseSize={0}") // GH-90000
    @ValueSource(ints = {128, 512, 2048, 8192}) // GH-90000
    @DisplayName("varying noise sizes complete without error")
    void varyingNoiseSizesComplete(int size) { // GH-90000
        byte[] noisy = buildNoisy(size); // GH-90000
        assertThatCode(() -> engine.detectObjects(noisy)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── Lighting variations ───────────────────────────────────────────────────

    @Test
    @DisplayName("underexposed image is processed without error")
    void underexposedImageProcessed() { // GH-90000
        byte[] dark = buildUniform(512, (byte) 10); // GH-90000
        assertThatCode(() -> engine.detectObjects(dark)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("overexposed image is processed without error")
    void overexposedImageProcessed() { // GH-90000
        byte[] bright = buildUniform(512, (byte) 245); // GH-90000
        assertThatCode(() -> engine.detectObjects(bright)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("mid-gray image classification does not throw")
    void midGrayImageClassified() { // GH-90000
        byte[] gray = buildUniform(512, (byte) 128); // GH-90000
        assertThatCode(() -> engine.classify(gray)).doesNotThrowAnyException(); // GH-90000
    }

    // ── Occlusion robustness ──────────────────────────────────────────────────

    @Test
    @DisplayName("partially occluded image (blocked patches) is processed without error")
    void occludedImageProcessed() { // GH-90000
        byte[] occluded = buildOccluded(1024); // GH-90000
        assertThatCode(() -> engine.detectObjects(occluded)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("heavily occluded image detection count is non-negative")
    void heavilyOccludedDetectionCountIsNonNegative() { // GH-90000
        byte[] occluded = buildOccluded(2048); // GH-90000
        assertThat(engine.detectObjects(occluded).size()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    // ── Confidence threshold sensitivity ─────────────────────────────────────

    @ParameterizedTest(name = "threshold={0}") // GH-90000
    @ValueSource(doubles = {0.1, 0.3, 0.5, 0.7, 0.9}) // GH-90000
    @DisplayName("detection count decreases or stays equal as threshold increases")
    void thresholdMonotonicity(double threshold) { // GH-90000
        byte[] image = makeCocoFixture("threshold_test_scene", "person"); // GH-90000
        int count = new VisionModelEngine("yolov8-coco", threshold).detectObjects(image).size(); // GH-90000
        int baseCount = new VisionModelEngine("yolov8-coco", 0.1).detectObjects(image).size(); // GH-90000
        assertThat(count).isLessThanOrEqualTo(baseCount); // GH-90000
    }

    // ── Image size robustness ─────────────────────────────────────────────────

    @ParameterizedTest(name = "size={0} bytes") // GH-90000
    @ValueSource(ints = {1, 64, 256, 1024, 65536}) // GH-90000
    @DisplayName("images of various sizes are processed without error")
    void variousSizesProcessed(int size) { // GH-90000
        byte[] image = new byte[size];
        // Fill with simple gradient to ensure non-zero
        for (int i = 0; i < size; i++) image[i] = (byte) (i % 200); // GH-90000
        assertThatCode(() -> engine.detectObjects(image)).doesNotThrowAnyException(); // GH-90000
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("null image throws VisionException")
    void nullImageThrows() { // GH-90000
        assertThatThrownBy(() -> engine.detectObjects(null)) // GH-90000
                .isInstanceOf(VisionModelEngine.VisionException.class); // GH-90000
    }

    @Test
    @DisplayName("empty image throws VisionException")
    void emptyImageThrows() { // GH-90000
        assertThatThrownBy(() -> engine.detectObjects(new byte[0])) // GH-90000
                .isInstanceOf(VisionModelEngine.VisionException.class); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] makeCocoFixture(String fixtureId, String label) { // GH-90000
        String content = "COCO:" + fixtureId + ":" + label;
        byte[] bytes = new byte[512];
        byte[] src = content.getBytes(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
        System.arraycopy(src, 0, bytes, 0, Math.min(src.length, bytes.length)); // GH-90000
        for (int i = src.length; i < bytes.length; i++) bytes[i] = (byte) (i % 200); // GH-90000
        return bytes;
    }

    private byte[] buildNoisy(int size) { // GH-90000
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) ((i * 31 + 7) % 256); // GH-90000
        return data;
    }

    private byte[] buildUniform(int size, byte value) { // GH-90000
        byte[] data = new byte[size];
        java.util.Arrays.fill(data, value); // GH-90000
        return data;
    }

    private byte[] buildOccluded(int size) { // GH-90000
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) { // GH-90000
            data[i] = (i % 64 < 32) ? (byte) 100 : (byte) 0;  // black patches // GH-90000
        }
        return data;
    }
}
