package com.ghatana.stt.core.onnx;

import ai.onnxruntime.OrtException;

import com.ghatana.stt.core.api.TranscriptionOptions;
import com.ghatana.stt.core.api.TranscriptionResult;
import com.ghatana.stt.core.api.TranscriptionResult.WordTiming;
import com.ghatana.stt.core.config.ModelConfig;
import com.ghatana.stt.core.dsp.MelSpectrogramExtractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whisper model inference engine using ONNX Runtime.
 *
 * <p>Provides end-to-end speech-to-text transcription using Whisper models
 * converted to ONNX format. Supports both encoder-decoder and combined
 * model architectures.
 *
 * <p><b>Model Architecture:</b>
 * <ul>
 *   <li>Encoder: Processes mel spectrogram to hidden states</li>
 *   <li>Decoder: Autoregressive token generation</li>
 *   <li>Tokenizer: BPE vocabulary for text encoding/decoding</li>
 * </ul>
 *
 * <p><b>Inference Flow:</b>
 * <ol>
 *   <li>Audio → Mel Spectrogram (80 bins, 16kHz)</li>
 *   <li>Mel → Encoder hidden states</li>
 *   <li>Decoder generates tokens autoregressively</li>
 *   <li>Tokens → Text via tokenizer</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Whisper ONNX inference engine
 * @doc.layer pipeline
 * @doc.pattern Strategy
 */
public final class WhisperInferenceEngine implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(WhisperInferenceEngine.class);

    private static final int MAX_TOKENS = 448;
    private static final int SAMPLE_RATE = 16000;
    private static final int N_MELS = 80;
    private static final int HOP_LENGTH = 160;
    private static final int N_FFT = 400;
    private static final int CHUNK_LENGTH_SECONDS = 30;

    private final OnnxRuntimeManager runtimeManager;
    private final WhisperTokenizer tokenizer;
    private final MelSpectrogramExtractor melExtractor;
    private final ModelConfig config;

    private OnnxModelSession encoderSession;
    private OnnxModelSession decoderSession;
    private String activeModelId;

    /**
     * Creates a new Whisper inference engine.
     *
     * @param config model configuration
     * @throws OrtException if ONNX Runtime initialization fails
     */
    public WhisperInferenceEngine(ModelConfig config) throws OrtException {
        this.config = config;
        this.runtimeManager = new OnnxRuntimeManager(config);
        this.tokenizer = new WhisperTokenizer();
        this.melExtractor = new MelSpectrogramExtractor(SAMPLE_RATE, N_FFT, HOP_LENGTH, N_MELS);
    }

    /**
     * Loads a Whisper model from the specified directory.
     *
     * <p>Expects either a combined model (whisper.onnx) or separate
     * encoder/decoder models (encoder.onnx, decoder.onnx).
     *
     * @param modelId unique identifier for the model
     * @param modelDir directory containing ONNX model files
     * @throws OrtException if model loading fails
     * @throws IOException if model files cannot be read
     */
    public void loadModel(String modelId, Path modelDir) throws OrtException, IOException {
        LOG.info("Loading Whisper model: {} from {}", modelId, modelDir);

        Path encoderPath = modelDir.resolve("encoder.onnx");
        Path decoderPath = modelDir.resolve("decoder.onnx");
        Path combinedPath = modelDir.resolve("whisper.onnx");

        if (java.nio.file.Files.exists(combinedPath)) {
            // Combined model
            LOG.info("Loading combined Whisper model");
            encoderSession = runtimeManager.loadModel(modelId + "-combined", combinedPath);
            decoderSession = null;
        } else if (java.nio.file.Files.exists(encoderPath) && java.nio.file.Files.exists(decoderPath)) {
            // Separate encoder/decoder
            LOG.info("Loading separate encoder/decoder models");
            encoderSession = runtimeManager.loadModel(modelId + "-encoder", encoderPath);
            decoderSession = runtimeManager.loadModel(modelId + "-decoder", decoderPath);
        } else {
            throw new IOException("No valid Whisper model found in: " + modelDir);
        }

        activeModelId = modelId;
        LOG.info("Whisper model loaded successfully: {}", modelId);
    }

    /**
     * Transcribes audio samples to text.
     *
     * @param audioSamples normalized audio samples [-1, 1] at 16kHz
     * @param options transcription options
     * @return transcription result
     */
    public TranscriptionResult transcribe(float[] audioSamples, TranscriptionOptions options) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Extract mel spectrogram
            float[][] melSpec = melExtractor.extract(audioSamples);
            LOG.debug("Mel spectrogram: {} frames x {} bins", melSpec.length, melSpec[0].length);

            // 2. Run encoder
            float[][] encoderOutput = runEncoder(melSpec);
            LOG.debug("Encoder output: {} x {}", encoderOutput.length, encoderOutput[0].length);

            // 3. Decode tokens
            String language = options != null && options.language() != null ? options.language() : "en";
            List<Integer> tokens = decodeTokens(encoderOutput, language, options);

            // 4. Convert tokens to text
            String text = tokenizer.decode(tokens);

            // 5. Post-process
            text = postProcess(text, options);

            long processingTime = System.currentTimeMillis() - startTime;
            float audioDurationMs = (float) audioSamples.length / SAMPLE_RATE * 1000;
            float rtf = processingTime / audioDurationMs;

            LOG.info("Transcription complete: {} chars in {}ms (RTF={:.3f})",
                text.length(), processingTime, rtf);

            return TranscriptionResult.builder()
                .text(text)
                .confidence(calculateConfidence(tokens))
                .isFinal(true)
                .processingTimeMs(processingTime)
                .modelUsed(activeModelId)
                .wordTimings(extractWordTimings(tokens, audioDurationMs))
                .build();

        } catch (Exception e) {
            LOG.error("Transcription failed", e);
            return TranscriptionResult.builder()
                .text("")
                .confidence(0.0f)
                .isFinal(true)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .modelUsed(activeModelId)
                .build();
        }
    }

    /**
     * Transcribes audio in chunks for long audio.
     *
     * @param audioSamples full audio samples
     * @param options transcription options
     * @return combined transcription result
     */
    public TranscriptionResult transcribeLongAudio(float[] audioSamples, TranscriptionOptions options) {
        int chunkSamples = CHUNK_LENGTH_SECONDS * SAMPLE_RATE;
        int overlap = SAMPLE_RATE * 2; // 2 second overlap

        if (audioSamples.length <= chunkSamples) {
            return transcribe(audioSamples, options);
        }

        StringBuilder fullText = new StringBuilder();
        List<WordTiming> allTimings = new ArrayList<>();
        float totalConfidence = 0;
        int chunkCount = 0;
        long totalProcessingTime = 0;

        int offset = 0;
        while (offset < audioSamples.length) {
            int end = Math.min(offset + chunkSamples, audioSamples.length);
            float[] chunk = new float[end - offset];
            System.arraycopy(audioSamples, offset, chunk, 0, chunk.length);

            TranscriptionResult chunkResult = transcribe(chunk, options);

            if (!chunkResult.text().isEmpty()) {
                if (fullText.length() > 0) {
                    fullText.append(" ");
                }
                fullText.append(chunkResult.text());
            }

            totalConfidence += chunkResult.confidence();
            totalProcessingTime += chunkResult.processingTimeMs();
            chunkCount++;

            // Adjust timings for offset
            float offsetMs = (float) offset / SAMPLE_RATE * 1000;
            if (chunkResult.wordTimings() != null) {
                for (WordTiming timing : chunkResult.wordTimings()) {
                    allTimings.add(new WordTiming(
                        timing.word(),
                        timing.startMs() + (long) offsetMs,
                        timing.endMs() + (long) offsetMs,
                        timing.confidence()
                    ));
                }
            }

            offset += chunkSamples - overlap;
        }

        return TranscriptionResult.builder()
            .text(fullText.toString())
            .confidence(chunkCount > 0 ? totalConfidence / chunkCount : 0)
            .isFinal(true)
            .processingTimeMs(totalProcessingTime)
            .modelUsed(activeModelId)
            .wordTimings(allTimings)
            .build();
    }

    private float[][] runEncoder(float[][] melSpec) throws OrtException {
        if (encoderSession == null) {
            throw new IllegalStateException("Model not loaded");
        }

        // Transpose mel spectrogram for Whisper: [n_mels, time] -> [time, n_mels]
        float[][] transposed = new float[melSpec[0].length][melSpec.length];
        for (int i = 0; i < melSpec.length; i++) {
            for (int j = 0; j < melSpec[i].length; j++) {
                transposed[j][i] = melSpec[i][j];
            }
        }

        return encoderSession.runEncoder(transposed);
    }

    private List<Integer> decodeTokens(float[][] encoderOutput, String language, TranscriptionOptions options) throws OrtException {
        List<Integer> tokens = new ArrayList<>();

        // Get initial tokens
        boolean useTimestamps = options != null && options.enableWordTimings();
        long[] initialTokens = tokenizer.getInitialTokens(language, "transcribe", useTimestamps);

        // Convert to list
        List<Long> currentTokens = new ArrayList<>();
        for (long t : initialTokens) {
            currentTokens.add(t);
        }

        // Autoregressive decoding
        for (int i = 0; i < MAX_TOKENS; i++) {
            long[] inputIds = currentTokens.stream().mapToLong(Long::longValue).toArray();

            float[] logits;
            if (decoderSession != null) {
                logits = decoderSession.runDecoder(encoderOutput, inputIds);
            } else {
                // Combined model - use encoder session for full inference
                logits = runCombinedDecoder(encoderOutput, inputIds);
            }

            // Greedy decoding: select token with highest probability
            int nextToken = argmax(logits);

            // Check for end of transcript
            if (tokenizer.isEndOfTranscript(nextToken)) {
                break;
            }

            tokens.add(nextToken);
            currentTokens.add((long) nextToken);
        }

        return tokens;
    }

    private float[] runCombinedDecoder(float[][] encoderOutput, long[] inputIds) throws OrtException {
        // For combined models, we need to run the full model
        // This is a simplified implementation
        return encoderSession.runDecoder(encoderOutput, inputIds);
    }

    private int argmax(float[] logits) {
        int maxIdx = 0;
        float maxVal = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > maxVal) {
                maxVal = logits[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private float calculateConfidence(List<Integer> tokens) {
        // Simplified confidence based on token count
        // In production, would use softmax probabilities
        if (tokens.isEmpty()) return 0.0f;
        return Math.min(0.95f, 0.5f + (tokens.size() / 100.0f));
    }

    private List<WordTiming> extractWordTimings(List<Integer> tokens, float audioDurationMs) {
        // Simplified word timing extraction
        // In production, would use attention weights or timestamp tokens
        List<WordTiming> timings = new ArrayList<>();

        String text = tokenizer.decode(tokens);
        String[] words = text.split("\\s+");

        if (words.length == 0) return timings;

        float timePerWord = audioDurationMs / words.length;
        float currentTime = 0;

        for (String word : words) {
            if (!word.isEmpty()) {
                timings.add(new WordTiming(
                    word,
                    (long) currentTime,
                    (long) (currentTime + timePerWord),
                    0.9f
                ));
                currentTime += timePerWord;
            }
        }

        return timings;
    }

    private String postProcess(String text, TranscriptionOptions options) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Trim and normalize whitespace
        text = text.trim().replaceAll("\\s+", " ");

        // Capitalize first letter
        if (!text.isEmpty()) {
            text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }

        // Add punctuation if enabled
        if (options != null && options.enablePunctuation()) {
            if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) {
                text = text + ".";
            }
        }

        return text;
    }

    /**
     * Gets the active model ID.
     *
     * @return the model ID, or empty if no model is loaded
     */
    public Optional<String> getActiveModelId() {
        return Optional.ofNullable(activeModelId);
    }

    /**
     * Checks if a model is loaded.
     *
     * @return true if a model is loaded
     */
    public boolean isModelLoaded() {
        return encoderSession != null;
    }

    @Override
    public void close() {
        LOG.info("Closing Whisper inference engine");
        runtimeManager.close();
        encoderSession = null;
        decoderSession = null;
        activeModelId = null;
    }
}
