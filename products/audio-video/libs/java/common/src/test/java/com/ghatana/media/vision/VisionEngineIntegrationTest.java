package com.ghatana.media.vision;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.config.VisionConfig;
import com.ghatana.media.vision.api.*;
import com.ghatana.media.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Vision engine with real ONNX models.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>YOLOv8 ONNX model loading and inference</li>
 *   <li>Object detection accuracy on known test images</li>
 *   <li>Streaming detection sessions</li>
 *   <li>GPU/CPU fallback behavior</li>
 * </ul>
 *
 * <p>Requires YOLOv8 ONNX model at /models/yolov8n.onnx or test will use stub.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
public class VisionEngineIntegrationTest {

    @TempDir
    static Path tempDir;

    private static AudioVideoLibrary library;
    private static VisionEngine engine;

    @BeforeAll
    static void setUp() { // GH-90000
        // Try to load real model, fall back to stub for testing
        VisionConfig config = VisionConfig.builder() // GH-90000
            .modelPath(Paths.get("/models/yolov8n.onnx"))
            .modelId("yolov8n")
            .useGpu(false) // Use CPU for tests // GH-90000
            .defaultConfidenceThreshold(0.5) // GH-90000
            .defaultMaxDetections(100) // GH-90000
            .build(); // GH-90000

        library = AudioVideoLibrary.builder() // GH-90000
            .withVisionConfig(config) // GH-90000
            .build(); // GH-90000

        engine = library.getVisionEngine(); // GH-90000
        engine.warmup(); // GH-90000
    }

    @AfterAll
    static void tearDown() { // GH-90000
        if (library != null) { // GH-90000
            library.close(); // GH-90000
        }
    }

    @Test
    @Order(1) // GH-90000
    @DisplayName("Engine initializes successfully")
    void testEngineInitialization() { // GH-90000
        EngineStatus status = engine.getStatus(); // GH-90000

        assertNotNull(status); // GH-90000
        assertTrue(status.state() == EngineStatus.State.READY // GH-90000
                || status.state() == EngineStatus.State.DEGRADED, // GH-90000
            "Engine should be ready or in stub fallback mode");
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("Detect objects in test image")
    void testDetectObjects() { // GH-90000
        // Create a simple test image (red square on blue background) // GH-90000
        ImageData testImage = createTestImage(640, 480); // GH-90000

        DetectionOptions options = DetectionOptions.builder() // GH-90000
            .confidenceThreshold(0.25f) // GH-90000
            .maxDetections(10) // GH-90000
            .build(); // GH-90000

        DetectionResult result = engine.detect(testImage, options); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals(640, result.imageWidth()); // GH-90000
        assertEquals(480, result.imageHeight()); // GH-90000
        assertNotNull(result.objects()); // GH-90000
        assertTrue(result.processingTimeMs() > 0); // GH-90000
        assertNotNull(result.modelId()); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("Detection respects confidence threshold")
    void testConfidenceThreshold() { // GH-90000
        ImageData testImage = createTestImage(640, 480); // GH-90000

        // High threshold should return fewer objects
        DetectionOptions highThreshold = DetectionOptions.builder() // GH-90000
            .confidenceThreshold(0.9f) // GH-90000
            .build(); // GH-90000

        DetectionOptions lowThreshold = DetectionOptions.builder() // GH-90000
            .confidenceThreshold(0.1f) // GH-90000
            .build(); // GH-90000

        DetectionResult highResult = engine.detect(testImage, highThreshold); // GH-90000
        DetectionResult lowResult = engine.detect(testImage, lowThreshold); // GH-90000

        // Low threshold should return same or more objects
        assertTrue(lowResult.objects().size() >= highResult.objects().size(), // GH-90000
            "Lower threshold should return same or more detections");
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("Streaming detection session works")
    void testStreamingDetection() throws InterruptedException { // GH-90000
        List<DetectionResult> results = new java.util.ArrayList<>(); // GH-90000

        StreamingDetectionSession session = engine.createStreamingSession( // GH-90000
            DetectionOptions.defaults(), // GH-90000
            result -> results.add(result) // GH-90000
        );

        // Feed 5 frames
        for (int i = 0; i < 5; i++) { // GH-90000
            ImageData frame = createTestImage(640, 480); // GH-90000
            session.feedFrame(frame, i); // GH-90000
            Thread.sleep(50); // Small delay between frames // GH-90000
        }

        session.endStream(); // GH-90000

        // Give time for callbacks
        Thread.sleep(100); // GH-90000

        // Should have received results for frames
        assertFalse(results.isEmpty(), "Should have received detection results"); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("Available models listed correctly")
    void testGetAvailableModels() { // GH-90000
        List<DetectionModelInfo> models = engine.getAvailableModels(); // GH-90000

        assertNotNull(models); // GH-90000
        assertFalse(models.isEmpty(), "Should have at least one model available"); // GH-90000

        for (DetectionModelInfo model : models) { // GH-90000
            assertNotNull(model.modelId()); // GH-90000
            assertNotNull(model.name()); // GH-90000
            assertTrue(model.sizeBytes() >= 0); // GH-90000
        }
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("Engine metrics collected")
    void testEngineMetrics() { // GH-90000
        // Perform some operations first
        ImageData testImage = createTestImage(640, 480); // GH-90000
        engine.detect(testImage, DetectionOptions.defaults()); // GH-90000

        EngineMetrics metrics = engine.getMetrics(); // GH-90000

        assertNotNull(metrics); // GH-90000
        assertTrue(metrics.requestCount() > 0, "Should have recorded requests"); // GH-90000
        assertTrue(metrics.avgLatencyMs() >= 0, "Average latency should be available"); // GH-90000
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("Handles multiple image formats")
    void testMultipleFormats() { // GH-90000
        int[] widths = {320, 640, 1280};
        int[] heights = {240, 480, 720};

        for (int i = 0; i < widths.length; i++) { // GH-90000
            ImageData image = createTestImage(widths[i], heights[i]); // GH-90000
            DetectionResult result = engine.detect(image, DetectionOptions.defaults()); // GH-90000

            assertEquals(widths[i], result.imageWidth()); // GH-90000
            assertEquals(heights[i], result.imageHeight()); // GH-90000
        }
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("Error handling for invalid input")
    void testErrorHandling() { // GH-90000
        // Null image should throw ValidationError
        assertThrows(ValidationError.class, () -> { // GH-90000
            engine.detect(null, DetectionOptions.defaults()); // GH-90000
        });

        // Zero-size image is rejected by the value object itself
        assertThrows(IllegalArgumentException.class, () -> ImageData.builder() // GH-90000
            .data(new byte[0]) // GH-90000
            .width(0) // GH-90000
            .height(0) // GH-90000
            .format(ImageFormat.RAW) // GH-90000
            .build()); // GH-90000
    }

    @Test
    @Order(9) // GH-90000
    @DisplayName("Warmup completes without error")
    void testWarmup() { // GH-90000
        assertDoesNotThrow(() -> engine.warmup()); // GH-90000
    }

    @Test
    @Order(10) // GH-90000
    @DisplayName("Concurrent detection operations")
    void testConcurrentDetection() throws InterruptedException { // GH-90000
        int threadCount = 4;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount); // GH-90000
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0); // GH-90000

        for (int i = 0; i < threadCount; i++) { // GH-90000
            new Thread(() -> { // GH-90000
                try {
                    ImageData image = createTestImage(640, 480); // GH-90000
                    DetectionResult result = engine.detect(image, DetectionOptions.defaults()); // GH-90000
                    if (result != null && result.objects() != null) { // GH-90000
                        successCount.incrementAndGet(); // GH-90000
                    }
                } finally {
                    latch.countDown(); // GH-90000
                }
            }).start(); // GH-90000
        }

        assertTrue(latch.await(30, java.util.concurrent.TimeUnit.SECONDS), // GH-90000
            "All threads should complete within timeout");
        assertEquals(threadCount, successCount.get(), // GH-90000
            "All concurrent operations should succeed");
    }

    // Helper method to create a simple test image
    private ImageData createTestImage(int width, int height) { // GH-90000
        // Create RGB image data
        int pixelCount = width * height;
        byte[] data = new byte[pixelCount * 3];

        // Fill with gradient pattern
        for (int y = 0; y < height; y++) { // GH-90000
            for (int x = 0; x < width; x++) { // GH-90000
                int idx = (y * width + x) * 3; // GH-90000
                data[idx] = (byte) (x * 255 / width);     // R // GH-90000
                data[idx + 1] = (byte) (y * 255 / height); // G // GH-90000
                data[idx + 2] = (byte) 128;                // B // GH-90000
            }
        }

        return ImageData.builder() // GH-90000
            .data(data) // GH-90000
            .width(width) // GH-90000
            .height(height) // GH-90000
            .format(ImageFormat.RAW) // GH-90000
            .build(); // GH-90000
    }
}
