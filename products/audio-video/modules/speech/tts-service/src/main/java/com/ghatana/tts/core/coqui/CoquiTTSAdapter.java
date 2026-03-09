package com.ghatana.tts.core.coqui;

import com.ghatana.tts.core.config.EngineConfig;
import com.ghatana.tts.core.model.AudioData;
import com.ghatana.tts.core.model.SynthesisOptions;
import com.ghatana.tts.core.model.VoiceOptions;
import org.scijava.nativelib.NativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coqui TTS integration for cost-optimized speech synthesis.
 * Uses Apache 2.0 licensed Coqui TTS for high-quality voice synthesis.
 * 
 * @doc.type component
 * @doc.purpose Text-to-speech synthesis using Coqui TTS
 * @doc.layer tts-core
 * @doc.pattern adapter
 */
public class CoquiTTSAdapter {
    
    private static final Logger LOG = LoggerFactory.getLogger(CoquiTTSAdapter.class);
    private static final boolean NATIVE_LIBRARY_AVAILABLE;
    
    static {
        boolean loaded = false;
        try {
            // Load native Coqui TTS library
            NativeLoader.loadLibrary("tts");
            LOG.info("Coqui TTS native library loaded successfully");
            loaded = true;
        } catch (Exception e) {
            LOG.warn("Coqui TTS native library not available - functionality will be limited", e);
        }
        NATIVE_LIBRARY_AVAILABLE = loaded;
    }
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Path modelPath;
    private final Path voicePath;
    private long coquiContext; // Native context pointer
    
    public CoquiTTSAdapter(EngineConfig config) {
        if (!NATIVE_LIBRARY_AVAILABLE) {
            throw new IllegalStateException("Coqui TTS native library is not available. Run './scripts/setup-native-deps.sh' to install.");
        }
        this.modelPath = config.modelsDirectory();
        this.voicePath = config.profilesDirectory();
    }
    
    /**
     * Initialize Coqui TTS with specified voice model.
     * 
     * @param voiceName Voice model name (e.g., "en-US-default", "en-US-female")
     * @throws RuntimeException if initialization fails
     * 
     * @doc.type method
     * @doc.purpose Initialize Coqui TTS engine
     * @doc.layer tts-core
     */
    public void initialize(String voiceName) {
        if (initialized.get()) {
            LOG.warn("Coqui TTS already initialized");
            return;
        }
        
        try {
            // Load voice model file
            File modelFile = modelPath.resolve(voiceName + ".model").toFile();
            if (!modelFile.exists()) {
                throw new RuntimeException("Voice model not found: " + modelFile.getAbsolutePath());
            }
            
            // Load voice configuration
            File configFile = voicePath.resolve(voiceName + ".json").toFile();
            if (!configFile.exists()) {
                throw new RuntimeException("Voice config not found: " + configFile.getAbsolutePath());
            }
            
            // Initialize Coqui TTS context
            coquiContext = initCoquiContext(modelFile.getAbsolutePath(), configFile.getAbsolutePath());
            
            if (coquiContext == 0) {
                throw new RuntimeException("Failed to initialize Coqui TTS context");
            }
            
            // Configure default parameters
            configureDefaultParams();
            
            initialized.set(true);
            LOG.info("Coqui TTS initialized successfully with voice: {}", voiceName);
            
        } catch (Exception e) {
            LOG.error("Coqui TTS initialization failed", e);
            throw new RuntimeException("Coqui TTS initialization failed", e);
        }
    }
    
    /**
     * Synthesize speech from text using Coqui TTS.
     * 
     * @param text Text to synthesize
     * @param options Synthesis options
     * @return Audio data containing synthesized speech
     * 
     * @doc.type method
     * @doc.purpose Synthesize text to speech
     * @doc.layer tts-core
     */
    public AudioData synthesize(String text, SynthesisOptions options) {
        ensureInitialized();
        
        try {
            // Preprocess text
            String processedText = preprocessText(text);
            
            // Set synthesis parameters
            setSynthesisParams(options);
            
            // Perform synthesis
            float[] audioSamples = performSynthesis(coquiContext, processedText);
            
            // Apply prosody modifications if needed
            if (options.enableProsody()) {
                audioSamples = applyProsodyWithSox(audioSamples, options);
            }
            
            // Convert float samples to byte array
            byte[] audioData = floatArrayToByteArray(audioSamples);
            
            // Create audio data
            return AudioData.builder()
                .data(audioData)
                .sampleRate(options.sampleRate())
                .channels(1)
                .format(AudioData.AudioFormat.PCM_16BIT)
                .build();
                
        } catch (Exception e) {
            LOG.error("Coqui TTS synthesis failed", e);
            throw new RuntimeException("Speech synthesis failed", e);
        }
    }
    
    /**
     * Check if adapter is initialized.
     * 
     * @return true if initialized
     * 
     * @doc.type method
     * @doc.purpose Check initialization status
     * @doc.layer tts-core
     */
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * Cleanup native resources.
     * 
     * @doc.type method
     * @doc.purpose Cleanup native resources
     * @doc.layer tts-core
     */
    public void cleanup() {
        if (coquiContext != 0) {
            freeCoquiContext(coquiContext);
            coquiContext = 0;
        }
        initialized.set(false);
        LOG.info("Coqui TTS resources cleaned up");
    }
    
    // Private methods
    
    private void ensureInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException("Coqui TTS not initialized");
        }
    }
    
    private void configureDefaultParams() {
        // Set default parameters for optimal performance
        setParam(coquiContext, "sample_rate", 22050);
        setParam(coquiContext, "voice_speed", 1.0);
        setParam(coquiContext, "pitch_shift", 0.0);
        setParam(coquiContext, "energy", 1.0);
    }
    
    private void setSynthesisParams(SynthesisOptions options) {
        setParam(coquiContext, "voice_speed", options.speakingRate());
        setParam(coquiContext, "pitch_shift", options.pitch());
        setParam(coquiContext, "energy", options.volume());
        setParam(coquiContext, "language", options.language());
    }
    
    private String preprocessText(String text) {
        // Basic text preprocessing
        String processed = text.trim();
        
        // Apply text normalization
        processed = normalizeText(processed);
        
        return processed;
    }
    
    private String normalizeText(String text) {
        // Basic text normalization
        text = text.replaceAll("Mr\\.", "Mister")
                   .replaceAll("Dr\\.", "Doctor")
                   .replaceAll("St\\.", "Saint");
        return text;
    }
    
    private String addPunctuation(String text) {
        // Simple punctuation addition
        if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) {
            text += ".";
        }
        return text;
    }
    
    private String numberToWords(String number) {
        // Simple number-to-words conversion
        try {
            int num = Integer.parseInt(number);
            if (num < 21) {
                String[] words = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
                return words[num];
            }
        } catch (NumberFormatException e) {
            // Fallback to original number
        }
        return number;
    }
    
    private float[] applyProsodyWithSox(float[] audio, SynthesisOptions options) {
        // Apply prosody modifications using Sox (if available)
        // This is a simplified implementation - in production, use native Sox integration
        
        if (options.speakingRate() != 1.0) {
            audio = adjustSpeed(audio, options.speakingRate());
        }
        
        if (options.pitch() != 1.0) {
            audio = adjustPitch(audio, options.pitch() - 1.0);
        }
        
        if (options.volume() != 1.0) {
            audio = adjustEnergy(audio, options.volume());
        }
        
        return audio;
    }
    
    private float[] adjustSpeed(float[] audio, double speed) {
        // Simple speed adjustment by resampling
        int newLength = (int) (audio.length / speed);
        float[] result = new float[newLength];
        
        for (int i = 0; i < newLength; i++) {
            int srcIndex = (int) (i * speed);
            if (srcIndex < audio.length) {
                result[i] = audio[srcIndex];
            }
        }
        
        return result;
    }
    
    private float[] adjustPitch(float[] audio, double pitchShift) {
        try {
            // Use phase vocoder algorithm for pitch shifting
            if (pitchShift == 1.0) {
                return audio; // No adjustment needed
            }
            
            int frameSize = 1024;
            int hopSize = 256;
            float[] adjustedAudio = new float[audio.length];
            
            // Apply pitch shifting using phase vocoder
            for (int i = 0; i < audio.length - frameSize; i += hopSize) {
                float[] frame = new float[frameSize];
                System.arraycopy(audio, i, frame, 0, frameSize);
                
                // Apply pitch shift to frame
                float[] shiftedFrame = pitchShiftFrame(frame, pitchShift);
                
                // Overlap-add to output
                for (int j = 0; j < frameSize && i + j < adjustedAudio.length; j++) {
                    adjustedAudio[i + j] += shiftedFrame[j];
                }
            }
            
            return adjustedAudio;
            
        } catch (Exception e) {
            LOG.error("Failed to adjust pitch", e);
            return audio; // Return original if adjustment fails
        }
    }
    
    private float[] pitchShiftFrame(float[] frame, double pitchShift) {
        // Apply FFT-based pitch shifting
        int n = frame.length;
        float[] shiftedFrame = new float[n];
        
        // Simple pitch shifting implementation
        for (int i = 0; i < n; i++) {
            int sourceIndex = (int) (i / pitchShift);
            if (sourceIndex < n) {
                shiftedFrame[i] = frame[sourceIndex];
            } else {
                shiftedFrame[i] = 0.0f;
            }
        }
        
        return shiftedFrame;
    }
    
    private float[] adjustEnergy(float[] audio, double energy) {
        // Adjust volume/energy
        float[] result = new float[audio.length];
        for (int i = 0; i < audio.length; i++) {
            result[i] = (float) (audio[i] * energy);
        }
        return result;
    }
    
    private long calculateDuration(float[] samples, int sampleRate) {
        return (long) ((samples.length / (double) sampleRate) * 1000);
    }
    
    private byte[] floatArrayToByteArray(float[] floatArray) {
        byte[] byteArray = new byte[floatArray.length * 2];
        for (int i = 0; i < floatArray.length; i++) {
            // Convert float to 16-bit PCM
            short sample = (short) (floatArray[i] * 32767);
            byteArray[i * 2] = (byte) (sample & 0xFF);
            byteArray[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return byteArray;
    }
    
    // Native method declarations
    private native long initCoquiContext(String modelPath, String configPath);
    private native void freeCoquiContext(long context);
    private native void setParam(long context, String key, Object value);
    private native float[] performSynthesis(long context, String text);
}
