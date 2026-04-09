/**
 * @doc.type factory
 * @doc.purpose Factory for creating Vision Engine instances
 * @doc.layer platform
 * @doc.pattern Factory
 */
package com.ghatana.media.vision.api;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.config.VisionConfig;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.activej.promise.Promise;

/**
 * Factory for creating Vision Engine instances.
 */
public final class VisionEngineFactory {

    private static final Logger LOG = Logger.getLogger(VisionEngineFactory.class.getName());

    private VisionEngineFactory() {}

    /**
     * Create a new Vision Engine with the given configuration.
     */
    public static VisionEngine create(VisionConfig config, AudioVideoLibrary.LibraryState libraryState) {
        LOG.info("Creating Vision Engine with model: " + config.modelId());

        // Check if ONNX model path is available
        if (config.modelPath() != null && config.modelPath().toFile().exists()) {
            try {
                return new com.ghatana.media.vision.engine.onnx.YoloOnnxEngine(config, libraryState);
            } catch (Exception e) {
                LOG.warning("Failed to load ONNX Vision engine, falling back to stub: " + e.getMessage());
            }
        }

        // Fallback to stub implementation
        LOG.info("Using stub Vision engine (no model found at " + config.modelPath() + ")");
        return new StubVisionEngine(config, libraryState);
    }

    /**
     * Stub Vision Engine implementation.
     */
    private static class StubVisionEngine implements VisionEngine {
        private final VisionConfig config;
        private final AtomicLong requestCount = new AtomicLong(0);
        private final Semaphore concurrencyLimiter;
        private final ExecutorService executor;
        private final AtomicReference<EngineStatus.State> state = new AtomicReference<>(EngineStatus.State.READY);

        StubVisionEngine(VisionConfig config, AudioVideoLibrary.LibraryState libraryState) {
            this.config = config;
            this.concurrencyLimiter = new Semaphore(config.maxConcurrentRequests());
            this.executor = Executors.newVirtualThreadPerTaskExecutor();

            LOG.info("Vision Engine initialized");
        }

        @Override
        public DetectionResult detect(ImageData image, DetectionOptions options) {
            ensureReady();
            validateImage(image);

            long startTime = System.currentTimeMillis();
            requestCount.incrementAndGet();

            try {
                concurrencyLimiter.acquire();
                try {
                    // Simulate detection
                    Thread.sleep(50);

                    var obj = new DetectedObject(
                        "object",
                        0.85,
                        new BoundingBox(10, 10, 100, 100, 0.85)
                    );

                    return new DetectionResult(
                        List.of(obj),
                        image.width(),
                        image.height(),
                        System.currentTimeMillis() - startTime,
                        config.modelId()
                    );
                } finally {
                    concurrencyLimiter.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InferenceError("Detection interrupted", e, true);
            }
        }

        @Override
        public Promise<DetectionResult> detectAsync(ImageData image, DetectionOptions options) {
            return Promise.ofBlocking(executor, () -> detect(image, options));
        }

        @Override
        public StreamingDetectionSession createStreamingSession(DetectionOptions options, Consumer<DetectionResult> resultCallback) {
            ensureReady();
            return new StubStreamingSession(options, resultCallback, config);
        }

        @Override
        public String caption(ImageData image) {
            return "An image with detected objects";
        }

        @Override
        public List<Classification> classify(ImageData image, int topK) {
            return List.of(
                new Classification("object", 0.9),
                new Classification("scene", 0.7)
            );
        }

        @Override
        public List<DetectionModelInfo> getAvailableModels() {
            return List.of(
                new DetectionModelInfo(
                    "yolov8n",
                    "YOLOv8 Nano",
                    "8.0",
                    new String[]{"person", "car", "dog", "cat"},
                    6000000L,
                    true,
                    640, 640,
                    Optional.of("Fast, lightweight detection model")
                ),
                new DetectionModelInfo(
                    "yolov8s",
                    "YOLOv8 Small",
                    "8.0",
                    new String[]{"person", "car", "dog", "cat", "bicycle"},
                    22000000L,
                    true,
                    640, 640,
                    Optional.of("Balanced speed and accuracy")
                )
            );
        }

        @Override
        public void loadModel(String modelId) {
            LOG.info("Loading vision model: " + modelId);
        }

        @Override
        public DetectionModelInfo getActiveModel() {
            return new DetectionModelInfo(
                config.modelId(),
                config.modelId(),
                "1.0",
                new String[0],
                0L,
                config.useGpu(),
                config.inputSize(),
                config.inputSize(),
                Optional.empty()
            );
        }

        @Override
        public void warmup() {
            LOG.info("Warming up Vision Engine...");
        }

        @Override
        public void close() {
            LOG.info("Closing Vision Engine...");
            state.set(EngineStatus.State.CLOSED);
            executor.shutdown();
        }

        @Override
        public EngineStatus getStatus() {
            return new EngineStatus(
                state.get(),
                config.modelId(),
                "1.0.0",
                0L,
                null
            );
        }

        @Override
        public EngineMetrics getMetrics() {
            return new EngineMetrics(
                requestCount.get(),
                0L,
                50.0,
                config.maxConcurrentRequests() - concurrencyLimiter.availablePermits(),
                0L
            );
        }

        private void ensureReady() {
            if (state.get() != EngineStatus.State.READY) {
                throw new IllegalStateException("Vision Engine not ready");
            }
        }

        private void validateImage(ImageData image) {
            if (image == null) {
                throw new ValidationError("Image data cannot be null");
            }
        }
    }

    /**
     * Stub streaming detection session.
     */
    private static class StubStreamingSession implements StreamingDetectionSession {
        private final DetectionOptions options;
        private final Consumer<DetectionResult> callback;
        private final AtomicReference<Boolean> active = new AtomicReference<>(true);

        StubStreamingSession(DetectionOptions options, Consumer<DetectionResult> callback, VisionConfig config) {
            this.options = options;
            this.callback = callback;
        }

        @Override
        public void feedFrame(ImageData frame, long frameNumber) {
            if (!active.get()) return;

            // Simulate detection
            var result = new DetectionResult(
                List.of(new DetectedObject(
                    "frame-object",
                    0.8,
                    new BoundingBox(0, 0, frame.width() / 2, frame.height() / 2, 0.8)
                )),
                frame.width(),
                frame.height(),
                50L,
                "stub-model"
            );

            callback.accept(result);
        }

        @Override
        public void endStream() {
            active.set(false);
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void close() {
            active.set(false);
        }
    }
}
