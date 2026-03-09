package com.ghatana.stt.core.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wrapper for an ONNX Runtime session with inference utilities.
 *
 * <p>Provides type-safe tensor creation and inference execution for
 * speech-to-text models. Tracks inference statistics for monitoring.
 *
 * <p><b>Thread Safety:</b> ONNX Runtime sessions are thread-safe for
 * concurrent inference. This wrapper adds atomic counters for statistics.
 *
 * @doc.type class
 * @doc.purpose ONNX model session wrapper with inference utilities
 * @doc.layer infrastructure
 * @doc.pattern Wrapper, Facade
 */
public final class OnnxModelSession implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OnnxModelSession.class);

    private final String modelId;
    private final Path modelPath;
    private final OrtSession session;
    private final Set<String> inputNames;
    private final Set<String> outputNames;
    private final AtomicLong inferenceCount;
    private final AtomicLong totalInferenceTimeNs;
    private final long loadedAtMs;
    private final long estimatedMemory;

    /**
     * Creates a new model session wrapper.
     *
     * @param modelId unique identifier for the model
     * @param modelPath path to the model file
     * @param session the ONNX Runtime session
     * @param inputNames names of input tensors
     * @param outputNames names of output tensors
     */
    public OnnxModelSession(
            String modelId,
            Path modelPath,
            OrtSession session,
            Set<String> inputNames,
            Set<String> outputNames) {
        this.modelId = modelId;
        this.modelPath = modelPath;
        this.session = session;
        this.inputNames = Set.copyOf(inputNames);
        this.outputNames = Set.copyOf(outputNames);
        this.inferenceCount = new AtomicLong(0);
        this.totalInferenceTimeNs = new AtomicLong(0);
        this.loadedAtMs = System.currentTimeMillis();
        this.estimatedMemory = estimateMemoryFromPath(modelPath);
    }

    /**
     * Runs inference with the provided inputs.
     *
     * @param inputs map of input name to tensor
     * @return inference result containing output tensors
     * @throws OrtException if inference fails
     */
    public Result run(Map<String, OnnxTensor> inputs) throws OrtException {
        long startNs = System.nanoTime();
        try {
            Result result = session.run(inputs);
            return result;
        } finally {
            long elapsedNs = System.nanoTime() - startNs;
            inferenceCount.incrementAndGet();
            totalInferenceTimeNs.addAndGet(elapsedNs);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Inference completed in {}ms", elapsedNs / 1_000_000);
            }
        }
    }

    /**
     * Runs inference for a Whisper-style encoder model.
     *
     * <p>Expects mel spectrogram input and returns encoder hidden states.
     *
     * @param melSpectrogram mel spectrogram [batch, n_mels, time]
     * @return encoder output tensor
     * @throws OrtException if inference fails
     */
    public float[][] runEncoder(float[][] melSpectrogram) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();

        // Reshape to [1, n_mels, time] for batch size 1
        int nMels = melSpectrogram.length;
        int timeSteps = melSpectrogram[0].length;
        float[] flatInput = new float[nMels * timeSteps];

        for (int i = 0; i < nMels; i++) {
            System.arraycopy(melSpectrogram[i], 0, flatInput, i * timeSteps, timeSteps);
        }

        long[] shape = {1, nMels, timeSteps};

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flatInput), shape)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(getEncoderInputName(), inputTensor);

            try (Result result = run(inputs)) {
                // Get first output (encoder hidden states)
                float[][][] output = (float[][][]) result.get(0).getValue();
                return output[0]; // Remove batch dimension
            }
        }
    }

    /**
     * Runs inference for a Whisper-style decoder model.
     *
     * <p>Takes encoder output and previous tokens, returns next token logits.
     *
     * @param encoderOutput encoder hidden states [seq_len, hidden_dim]
     * @param inputIds previous token IDs [seq_len]
     * @return logits for next token [vocab_size]
     * @throws OrtException if inference fails
     */
    public float[] runDecoder(float[][] encoderOutput, long[] inputIds) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();

        // Prepare encoder output tensor [1, seq_len, hidden_dim]
        int seqLen = encoderOutput.length;
        int hiddenDim = encoderOutput[0].length;
        float[] flatEncoder = new float[seqLen * hiddenDim];

        for (int i = 0; i < seqLen; i++) {
            System.arraycopy(encoderOutput[i], 0, flatEncoder, i * hiddenDim, hiddenDim);
        }

        long[] encoderShape = {1, seqLen, hiddenDim};
        long[] inputIdsShape = {1, inputIds.length};

        try (OnnxTensor encoderTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flatEncoder), encoderShape);
             OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), inputIdsShape)) {

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(getDecoderEncoderInputName(), encoderTensor);
            inputs.put(getDecoderInputIdsName(), inputIdsTensor);

            try (Result result = run(inputs)) {
                // Get logits output [1, seq_len, vocab_size]
                float[][][] logits = (float[][][]) result.get(0).getValue();
                // Return logits for last position
                return logits[0][logits[0].length - 1];
            }
        }
    }

    /**
     * Creates a float tensor from a 2D array.
     *
     * @param data the 2D float array
     * @return the ONNX tensor
     * @throws OrtException if tensor creation fails
     */
    public OnnxTensor createTensor(float[][] data) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        int rows = data.length;
        int cols = data[0].length;
        float[] flat = new float[rows * cols];

        for (int i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, flat, i * cols, cols);
        }

        return OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), new long[]{rows, cols});
    }

    /**
     * Creates a float tensor from a 3D array with batch dimension.
     *
     * @param data the 2D float array
     * @return the ONNX tensor with shape [1, rows, cols]
     * @throws OrtException if tensor creation fails
     */
    public OnnxTensor createBatchedTensor(float[][] data) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        int rows = data.length;
        int cols = data[0].length;
        float[] flat = new float[rows * cols];

        for (int i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, flat, i * cols, cols);
        }

        return OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), new long[]{1, rows, cols});
    }

    // Input/output name helpers for Whisper models
    private String getEncoderInputName() {
        return inputNames.stream()
            .filter(n -> n.contains("mel") || n.contains("input") || n.contains("audio"))
            .findFirst()
            .orElse(inputNames.iterator().next());
    }

    private String getDecoderEncoderInputName() {
        return inputNames.stream()
            .filter(n -> n.contains("encoder") || n.contains("hidden"))
            .findFirst()
            .orElse("encoder_hidden_states");
    }

    private String getDecoderInputIdsName() {
        return inputNames.stream()
            .filter(n -> n.contains("input_ids") || n.contains("tokens"))
            .findFirst()
            .orElse("input_ids");
    }

    private long estimateMemoryFromPath(Path path) {
        try {
            return java.nio.file.Files.size(path) * 2; // Rough estimate: 2x file size
        } catch (Exception e) {
            return 100 * 1024 * 1024; // Default 100MB
        }
    }

    // Getters

    public String modelId() {
        return modelId;
    }

    public Path modelPath() {
        return modelPath;
    }

    public Set<String> inputNames() {
        return inputNames;
    }

    public Set<String> outputNames() {
        return outputNames;
    }

    public long inferenceCount() {
        return inferenceCount.get();
    }

    public double averageInferenceTimeMs() {
        long count = inferenceCount.get();
        if (count == 0) return 0.0;
        return (totalInferenceTimeNs.get() / 1_000_000.0) / count;
    }

    public long loadedAtMs() {
        return loadedAtMs;
    }

    public long estimatedMemoryBytes() {
        return estimatedMemory;
    }

    @Override
    public void close() throws OrtException {
        LOG.debug("Closing model session: {} (inferences={}, avgTime={}ms)",
            modelId, inferenceCount.get(), String.format("%.2f", averageInferenceTimeMs()));
        session.close();
    }
}
