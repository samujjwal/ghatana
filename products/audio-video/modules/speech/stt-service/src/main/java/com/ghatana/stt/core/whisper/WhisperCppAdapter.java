package com.ghatana.stt.core.whisper;

import com.ghatana.stt.core.config.EngineConfig;
import com.ghatana.stt.core.model.AudioData;
import com.ghatana.stt.core.model.TranscriptionOptions;
import com.ghatana.stt.core.model.TranscriptionResult;
import org.scijava.nativelib.NativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whisper.cpp integration for cost-optimized speech recognition.
 * Uses MIT-licensed Whisper.cpp library for 50% memory reduction vs ONNX Runtime.
 * 
 * @doc.type component
 * @doc.purpose Speech recognition using Whisper.cpp
 * @doc.layer stt-core
 * @doc.pattern adapter
 */
public class WhisperCppAdapter {
    
    private static final Logger LOG = LoggerFactory.getLogger(WhisperCppAdapter.class);
    private static final boolean NATIVE_LIBRARY_AVAILABLE;
    
    static {
        boolean loaded = false;
        try {
            // Load native Whisper.cpp library
            NativeLoader.loadLibrary("whisper");
            LOG.info("Whisper.cpp native library loaded successfully");
            loaded = true;
        } catch (Exception e) {
            LOG.warn("Whisper.cpp native library not available - functionality will be limited", e);
        }
        NATIVE_LIBRARY_AVAILABLE = loaded;
    }
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Path modelPath;
    private long whisperContext; // Native context pointer
    
    public WhisperCppAdapter(EngineConfig config) {
        if (!NATIVE_LIBRARY_AVAILABLE) {
            throw new IllegalStateException("Whisper.cpp native library is not available. Run './scripts/setup-native-deps.sh' to install.");
        }
        this.modelPath = config.modelPath();
    }
    
    /**
     * Initialize Whisper.cpp with specified model.
     * 
     * @param modelName Model name (e.g., "whisper-base", "whisper-small")
     * @throws RuntimeException if initialization fails
     * 
     * @doc.type method
     * @doc.purpose Initialize Whisper.cpp engine
     * @doc.layer stt-core
     */
    public void initialize(String modelName) {
        if (initialized.get()) {
            LOG.warn("Whisper.cpp already initialized");
            return;
        }
        
        try {
            // Load model file
            File modelFile = modelPath.resolve(modelName + ".bin").toFile();
            if (!modelFile.exists()) {
                throw new RuntimeException("Model file not found: " + modelFile.getAbsolutePath());
            }
            
            // Initialize Whisper.cpp context
            whisperContext = initWhisperContext(modelFile.getAbsolutePath());
            
            if (whisperContext == 0) {
                throw new RuntimeException("Failed to initialize Whisper.cpp context");
            }
            
            // Configure default parameters
            configureDefaultParams();
            
            initialized.set(true);
            LOG.info("Whisper.cpp initialized successfully with model: {}", modelName);
            
        } catch (Exception e) {
            LOG.error("Whisper.cpp initialization failed", e);
            throw new RuntimeException("Whisper.cpp initialization failed", e);
        }
    }
    
    /**
     * Transcribe audio data using Whisper.cpp.
     * 
     * @param audio Audio data to transcribe
     * @param options Transcription options
     * @return Transcription result
     * 
     * @doc.type method
     * @doc.purpose Transcribe audio using Whisper.cpp
     * @doc.layer stt-core
     */
    public TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options) {
        ensureInitialized();
        
        try {
            // Convert audio data to format expected by Whisper.cpp
            float[] audioSamples = convertAudioSamples(audio);
            
            // Set transcription parameters
            setTranscriptionParams(options);
            
            // Perform transcription
            String transcription = performTranscription(whisperContext, audioSamples, audioSamples.length);
            
            // Create result
            return TranscriptionResult.builder()
                .text(transcription)
                .confidence(calculateConfidence(transcription))
                .language(detectedLanguage())
                .processingTime(new com.ghatana.stt.core.model.TranscriptionResult.ProcessingTime(
                    System.currentTimeMillis(), 0, 0, 0))
                .build();
                
        } catch (Exception e) {
            LOG.error("Whisper.cpp transcription failed", e);
            throw new RuntimeException("Transcription failed", e);
        }
    }
    
    /**
     * Check if adapter is initialized.
     * 
     * @return true if initialized
     * 
     * @doc.type method
     * @doc.purpose Check initialization status
     * @doc.layer stt-core
     */
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * Cleanup native resources.
     * 
     * @doc.type method
     * @doc.purpose Cleanup native resources
     * @doc.layer stt-core
     */
    public void cleanup() {
        if (whisperContext != 0) {
            freeWhisperContext(whisperContext);
            whisperContext = 0;
        }
        initialized.set(false);
        LOG.info("Whisper.cpp resources cleaned up");
    }
    
    // Private methods
    
    private void ensureInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException("Whisper.cpp not initialized");
        }
    }
    
    private void configureDefaultParams() {
        // Set default parameters for optimal performance
        setParam(whisperContext, "language", "auto");
        setParam(whisperContext, "translate", false);
        setParam(whisperContext, "print_realtime", false);
        setParam(whisperContext, "print_progress", false);
        setParam(whisperContext, "print_timestamps", false);
    }
    
    private void setTranscriptionParams(com.ghatana.stt.core.model.TranscriptionOptions options) {
        setParam(whisperContext, "language", options.language());
        setParam(whisperContext, "translate", false);
        setParam(whisperContext, "print_timestamps", options.enableTimestamps());
    }
    
    private float[] convertAudioSamples(com.ghatana.stt.core.model.AudioData audio) {
        // Convert audio data to 16kHz mono float array expected by Whisper.cpp
        // This is a simplified conversion - in production, use proper audio processing
        byte[] rawData = audio.data();
        int numSamples = rawData.length / 2; // Assuming 16-bit audio
        float[] samples = new float[numSamples];
        
        for (int i = 0; i < numSamples; i++) {
            int sample = (rawData[i * 2 + 1] << 8) | (rawData[i * 2] & 0xFF);
            samples[i] = sample / 32768.0f; // Convert from 16-bit PCM to float
        }
        return samples;
    }
    
    private double calculateConfidence(String transcription) {
        // Simple confidence calculation based on transcription quality
        if (transcription == null || transcription.trim().isEmpty()) {
            return 0.0;
        }
        
        // Basic heuristics for confidence
        int wordCount = transcription.split("\\s+").length;
        if (wordCount < 3) return 0.3;
        if (wordCount < 10) return 0.7;
        return 0.9;
    }
    
    private String detectedLanguage() {
        // Get detected language from Whisper.cpp
        return getDetectedLanguage(whisperContext);
    }
    
    // Native method declarations
    private native long initWhisperContext(String modelPath);
    private native void freeWhisperContext(long context);
    private native void setParam(long context, String key, Object value);
    private native String performTranscription(long context, float[] samples, int sampleCount);
    private native String getDetectedLanguage(long context);
}
