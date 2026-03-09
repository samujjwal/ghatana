package com.ghatana.stt.core.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.SessionOptions.ExecutionMode;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;

import com.ghatana.stt.core.config.ModelConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages ONNX Runtime environment and session lifecycle.
 *
 * <p>This class provides thread-safe management of ONNX Runtime sessions,
 * including model loading, caching, GPU/CPU execution provider selection,
 * and resource cleanup.
 *
 * <p><b>Thread Safety:</b> All public methods are thread-safe. Session access
 * uses read-write locks to allow concurrent inference while serializing
 * model loading operations.
 *
 * <p><b>Resource Management:</b> Implements {@link AutoCloseable} for proper
 * cleanup. Sessions are cached and reused; call {@link #close()} to release
 * all resources.
 *
 * @doc.type class
 * @doc.purpose ONNX Runtime lifecycle management
 * @doc.layer infrastructure
 * @doc.pattern Singleton, Resource Manager
 */
public final class OnnxRuntimeManager implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OnnxRuntimeManager.class);

    private static final String ENV_NAME = "ghatana-stt";

    private final OrtEnvironment environment;
    private final ModelConfig modelConfig;
    private final Map<String, OnnxModelSession> loadedSessions;
    private final ReentrantReadWriteLock sessionLock;
    private final AtomicBoolean closed;

    /**
     * Creates a new ONNX Runtime manager with the specified configuration.
     *
     * @param modelConfig the model configuration
     * @throws OrtException if ONNX Runtime initialization fails
     */
    public OnnxRuntimeManager(ModelConfig modelConfig) throws OrtException {
        this.modelConfig = modelConfig;
        this.environment = OrtEnvironment.getEnvironment(ENV_NAME);
        this.loadedSessions = new ConcurrentHashMap<>();
        this.sessionLock = new ReentrantReadWriteLock();
        this.closed = new AtomicBoolean(false);

        LOG.info("ONNX Runtime initialized: version={}, threads={}, gpu={}",
            environment.getVersion(),
            modelConfig.numThreads(),
            modelConfig.useGpu());
    }

    /**
     * Loads an ONNX model from the specified path.
     *
     * <p>If the model is already loaded, returns the cached session.
     * Thread-safe: concurrent loads of the same model will only load once.
     *
     * @param modelId unique identifier for the model
     * @param modelPath path to the ONNX model file
     * @return the loaded model session
     * @throws OrtException if model loading fails
     * @throws IOException if the model file cannot be read
     */
    public OnnxModelSession loadModel(String modelId, Path modelPath) throws OrtException, IOException {
        ensureNotClosed();

        // Fast path: check if already loaded
        OnnxModelSession existing = loadedSessions.get(modelId);
        if (existing != null) {
            LOG.debug("Model already loaded: {}", modelId);
            return existing;
        }

        // Slow path: load with write lock
        sessionLock.writeLock().lock();
        try {
            // Double-check after acquiring lock
            existing = loadedSessions.get(modelId);
            if (existing != null) {
                return existing;
            }

            LOG.info("Loading ONNX model: {} from {}", modelId, modelPath);

            if (!Files.exists(modelPath)) {
                throw new IOException("Model file not found: " + modelPath);
            }

            SessionOptions options = createSessionOptions();
            long startTime = System.currentTimeMillis();

            OrtSession session = environment.createSession(modelPath.toString(), options);

            long loadTime = System.currentTimeMillis() - startTime;
            LOG.info("Model loaded in {}ms: {} (inputs={}, outputs={})",
                loadTime, modelId,
                session.getInputNames().size(),
                session.getOutputNames().size());

            OnnxModelSession modelSession = new OnnxModelSession(
                modelId,
                modelPath,
                session,
                session.getInputNames(),
                session.getOutputNames()
            );

            loadedSessions.put(modelId, modelSession);
            return modelSession;

        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * Gets a loaded model session by ID.
     *
     * @param modelId the model identifier
     * @return the model session, or empty if not loaded
     */
    public Optional<OnnxModelSession> getSession(String modelId) {
        ensureNotClosed();
        return Optional.ofNullable(loadedSessions.get(modelId));
    }

    /**
     * Unloads a model and releases its resources.
     *
     * @param modelId the model identifier
     * @return true if the model was unloaded, false if it wasn't loaded
     */
    public boolean unloadModel(String modelId) {
        ensureNotClosed();

        sessionLock.writeLock().lock();
        try {
            OnnxModelSession session = loadedSessions.remove(modelId);
            if (session != null) {
                try {
                    session.close();
                    LOG.info("Unloaded model: {}", modelId);
                    return true;
                } catch (Exception e) {
                    LOG.warn("Error closing model session: {}", modelId, e);
                }
            }
            return false;
        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * Gets the total memory used by loaded models.
     *
     * @return estimated memory usage in bytes
     */
    public long getMemoryUsage() {
        return loadedSessions.values().stream()
            .mapToLong(OnnxModelSession::estimatedMemoryBytes)
            .sum();
    }

    /**
     * Gets the number of loaded models.
     *
     * @return count of loaded models
     */
    public int getLoadedModelCount() {
        return loadedSessions.size();
    }

    /**
     * Checks if a model is loaded.
     *
     * @param modelId the model identifier
     * @return true if the model is loaded
     */
    public boolean isModelLoaded(String modelId) {
        return loadedSessions.containsKey(modelId);
    }

    /**
     * Creates session options based on configuration.
     */
    private SessionOptions createSessionOptions() throws OrtException {
        SessionOptions options = new SessionOptions();

        // Set optimization level
        switch (modelConfig.optimizationLevel()) {
            case 0 -> options.setOptimizationLevel(OptLevel.NO_OPT);
            case 1 -> options.setOptimizationLevel(OptLevel.BASIC_OPT);
            case 2 -> options.setOptimizationLevel(OptLevel.EXTENDED_OPT);
            default -> options.setOptimizationLevel(OptLevel.ALL_OPT);
        }

        // Set execution mode
        options.setExecutionMode(ExecutionMode.SEQUENTIAL);

        // Set thread count
        options.setIntraOpNumThreads(modelConfig.numThreads());

        // Enable GPU if available and configured
        if (modelConfig.useGpu()) {
            try {
                // Try CUDA first
                options.addCUDA(0);
                LOG.info("CUDA execution provider enabled");
            } catch (OrtException e) {
                LOG.debug("CUDA not available, trying DirectML");
                try {
                    // Try DirectML on Windows
                    options.addDirectML(0);
                    LOG.info("DirectML execution provider enabled");
                } catch (OrtException e2) {
                    LOG.info("GPU acceleration not available, using CPU");
                }
            }
        }

        // Enable memory pattern optimization
        options.setMemoryPatternOptimization(true);

        return options;
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("OnnxRuntimeManager is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            LOG.info("Closing ONNX Runtime manager");

            sessionLock.writeLock().lock();
            try {
                for (OnnxModelSession session : loadedSessions.values()) {
                    try {
                        session.close();
                    } catch (Exception e) {
                        LOG.warn("Error closing session: {}", session.modelId(), e);
                    }
                }
                loadedSessions.clear();
            } finally {
                sessionLock.writeLock().unlock();
            }

            try {
                environment.close();
            } catch (Exception e) {
                LOG.warn("Error closing ONNX environment", e);
            }

            LOG.info("ONNX Runtime manager closed");
        }
    }
}
