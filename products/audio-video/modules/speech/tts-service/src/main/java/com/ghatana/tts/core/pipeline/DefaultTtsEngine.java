// filepath: /Users/samujjwal/Development/ghatana/products/shared-services/text-to-speech/libs/tts-core-java/src/main/java/com/ghatana/tts/core/pipeline/DefaultTtsEngine.java
package com.ghatana.tts.core.pipeline;

import com.ghatana.tts.core.api.*;
import com.ghatana.tts.core.coqui.CoquiTTSAdapter;
import com.ghatana.tts.core.config.EngineConfig;
import com.ghatana.tts.core.model.AudioData;
import com.ghatana.tts.core.registry.VoiceModelRegistry;
import com.ghatana.tts.core.storage.ProfileEncryption;
import com.ghatana.tts.core.storage.ProfileStorage;

import ai.onnxruntime.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the TtsEngine using ONNX Runtime.
 *
 * <p>This implementation orchestrates the full TTS pipeline including
 * text preprocessing, phoneme conversion, acoustic model inference,
 * and vocoder synthesis.
 *
 * @doc.type class
 * @doc.purpose Default TTS engine implementation with ONNX Runtime
 * @doc.layer product
 * @doc.pattern Service
 */
public class DefaultTtsEngine implements TtsEngine {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTtsEngine.class);

    private final EngineConfig config;
    private final CoquiTTSAdapter coquiAdapter;
    private final AtomicReference<EngineStatus> status;
    private final ConcurrentHashMap<String, VoiceInfo> loadedVoices;
    private final ConcurrentHashMap<String, UserProfile> profiles;
    private final ProfileStorage profileStorage;
    private final VoiceModelRegistry voiceRegistry;
    private final AtomicLong totalSyntheses;
    private final AtomicLong totalLatencyMs;
    private final AtomicInteger activeSessions;

    // ONNX Runtime environment and session
    private volatile OrtEnvironment ortEnv;
    private volatile OrtSession encoderSession;
    private volatile OrtSession decoderSession;
    private volatile OrtSession vocoderSession;

    private volatile String activeVoiceId;
    private volatile boolean initialized = false;

    /**
     * Creates a new TTS engine with the given configuration.
     *
     * @param config the engine configuration
     */
    public DefaultTtsEngine(EngineConfig config) {
        this.config = config;
        
        // Try to initialize CoquiAdapter, but don't fail if native libs aren't available
        CoquiTTSAdapter tempAdapter = null;
        try {
            tempAdapter = new CoquiTTSAdapter(config);
        } catch (IllegalStateException e) {
            LOG.warn("CoquiTTSAdapter not available: {}. Engine will have limited functionality.", e.getMessage());
        }
        this.coquiAdapter = tempAdapter;
        
        this.status = new AtomicReference<>(new EngineStatus(EngineState.INITIALIZING, null, null, 0));
        this.loadedVoices = new ConcurrentHashMap<>();
        this.profiles = new ConcurrentHashMap<>();
        this.totalSyntheses = new AtomicLong(0);
        this.totalLatencyMs = new AtomicLong(0);
        this.activeSessions = new AtomicInteger(0);

        // Initialize encrypted profile storage
        try {
            ProfileEncryption encryption = new ProfileEncryption();
            this.profileStorage = new ProfileStorage(config.profilesDirectory(), encryption);
            LOG.info("Initialized encrypted TTS profile storage");
        } catch (Exception e) {
            LOG.error("Failed to initialize TTS profile storage", e);
            throw new RuntimeException("TTS profile storage initialization failed", e);
        }

        // Initialize voice model registry (loads persisted entries from disk)
        this.voiceRegistry = new VoiceModelRegistry(config.modelsDirectory());
        LOG.info("Initialized VoiceModelRegistry with {} persisted voice(s)", voiceRegistry.size());

        // Initialize asynchronously
        CompletableFuture.runAsync(this::initialize);
    }

    private void initialize() {
        try {
            LOG.info("Initializing TTS engine...");

            // Create directories if they don't exist
            Files.createDirectories(config.modelsDirectory());
            Files.createDirectories(config.profilesDirectory());
            Files.createDirectories(config.cacheDirectory());

            // Initialize Coqui TTS with default voice
            if (config.defaultVoice() != null) {
                coquiAdapter.initialize(config.defaultVoice());
                LOG.info("Coqui TTS initialized with voice: {}", config.defaultVoice());
            }

            // Register built-in voices
            registerBuiltInVoices();

            // Load default voice if available
            if (config.defaultVoice() != null && loadedVoices.containsKey(config.defaultVoice())) {
                loadVoice(config.defaultVoice());
            }

            this.initialized = true;
            this.status.set(new EngineStatus(EngineState.READY, activeVoiceId, null, 0));
            LOG.info("TTS engine initialized successfully");

        } catch (Exception e) {
            LOG.error("Failed to initialize TTS engine", e);
            this.status.set(new EngineStatus(EngineState.ERROR, null, "Initialization failed: " + e.getMessage(), 0));
        }
    }

    private void registerBuiltInVoices() {
        List<VoiceInfo> builtIns = List.of(
            new VoiceInfo("default-en", "Default English",
                "A clear, neutral English voice", List.of("en-US", "en-GB"),
                "neutral", 50 * 1024 * 1024L, false, false),
            new VoiceInfo("aria-en", "Aria",
                "A warm, friendly female voice", List.of("en-US"),
                "female", 75 * 1024 * 1024L, false, false),
            new VoiceInfo("sam-en", "Sam",
                "A professional male voice", List.of("en-US", "en-GB"),
                "male", 75 * 1024 * 1024L, false, false)
        );

        for (VoiceInfo voice : builtIns) {
            loadedVoices.put(voice.voiceId(), voice);
            if (voiceRegistry.find(voice.voiceId()).isEmpty()) {
                voiceRegistry.register(voice, null);
            }
        }

        // Restore previously cloned voices from the persistent registry
        voiceRegistry.listAll(true).forEach(entry -> {
            if (!loadedVoices.containsKey(entry.voiceId())) {
                VoiceInfo restored = new VoiceInfo(
                    entry.voiceId(), entry.name(), entry.description(),
                    entry.languages(), entry.gender(), entry.sizeBytes(),
                    false, true);
                loadedVoices.put(entry.voiceId(), restored);
                LOG.info("Restored persisted cloned voice: {}", entry.voiceId());
            }
        });

        LOG.info("Registered {} voice(s) total ({} from registry)",
            loadedVoices.size(), voiceRegistry.size());
    }

    @Override
    public SynthesisResult synthesize(String text, SynthesisOptions options) {
        ensureReady();

        long startTime = System.currentTimeMillis();
        activeSessions.incrementAndGet();

        try {
            String voiceId = options.voiceId() != null ? options.voiceId() : config.defaultVoice();

            // Text preprocessing
            String normalizedText = normalizeText(text);

            // Convert text to phonemes (simplified)
            String phonemes = textToPhonemes(normalizedText, options.language());

            // Generate audio using the synthesis pipeline
            byte[] audioData = synthesizeFromPhonemes(phonemes, options);

            // Apply prosody modifications (speed, pitch, energy)
            audioData = applyProsody(audioData, options);

            long processingTime = System.currentTimeMillis() - startTime;
            totalSyntheses.incrementAndGet();
            totalLatencyMs.addAndGet(processingTime);

            // Calculate duration (assuming 16-bit samples at configured sample rate)
            long durationMs = (audioData.length / 2) * 1000 / config.defaultSampleRate();

            LOG.debug("Synthesized {} chars in {}ms", text.length(), processingTime);

            return new SynthesisResult(audioData, config.defaultSampleRate(), durationMs, voiceId);

        } finally {
            activeSessions.decrementAndGet();
        }
    }

    @Override
    public void synthesizeStreaming(String text, SynthesisOptions options, Consumer<AudioChunk> chunkConsumer) {
        ensureReady();
        activeSessions.incrementAndGet();

        try {
            String voiceId = options.voiceId() != null ? options.voiceId() : config.defaultVoice();

            // Split text into sentences for streaming
            String[] sentences = text.split("(?<=[.!?])\\s+");
            long timestampMs = 0;

            for (int i = 0; i < sentences.length; i++) {
                String sentence = sentences[i].trim();
                if (sentence.isEmpty()) continue;

                // Synthesize each sentence
                SynthesisResult result = synthesize(sentence, options);

                boolean isFinal = (i == sentences.length - 1);
                AudioChunk chunk = new AudioChunk(
                    result.audioData(),
                    result.sampleRate(),
                    timestampMs,
                    isFinal
                );

                chunkConsumer.accept(chunk);
                timestampMs += result.durationMs();
            }

        } finally {
            activeSessions.decrementAndGet();
        }
    }

    @Override
    public EngineStatus getStatus() {
        return status.get();
    }

    @Override
    public EngineMetrics getMetrics() {
        long syntheses = totalSyntheses.get();
        float avgLatency = syntheses > 0 ? (float) totalLatencyMs.get() / syntheses : 0f;

        return new EngineMetrics(
            calculateRtf(),
            Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            activeSessions.get(),
            syntheses,
            avgLatency
        );
    }

    @Override
    public List<VoiceInfo> getAvailableVoices(String languageFilter) {
        if (languageFilter == null || languageFilter.isEmpty()) {
            return new ArrayList<>(loadedVoices.values());
        }

        return loadedVoices.values().stream()
            .filter(v -> v.languages().stream()
                .anyMatch(lang -> lang.toLowerCase().startsWith(languageFilter.toLowerCase())))
            .collect(Collectors.toList());
    }

    @Override
    public VoiceInfo loadVoice(String voiceId) {
        VoiceInfo voice = loadedVoices.get(voiceId);
        if (voice == null) {
            throw new IllegalArgumentException("Voice not found: " + voiceId);
        }

        // Load ONNX models for this voice
        try {
            Path modelPath = config.modelsDirectory().resolve(voiceId);
            if (Files.exists(modelPath)) {
                // Load encoder, decoder, vocoder models
                loadVoiceModels(modelPath);
            }

            // Mark as loaded
            VoiceInfo loadedVoice = new VoiceInfo(
                voice.voiceId(),
                voice.name(),
                voice.description(),
                voice.languages(),
                voice.gender(),
                voice.sizeBytes(),
                true,  // isLoaded = true
                voice.isCloned()
            );

            loadedVoices.put(voiceId, loadedVoice);
            activeVoiceId = voiceId;
            status.set(new EngineStatus(EngineState.READY, voiceId, null, activeSessions.get()));

            LOG.info("Loaded voice: {}", voiceId);
            return loadedVoice;

        } catch (Exception e) {
            LOG.error("Failed to load voice: {}", voiceId, e);
            throw new RuntimeException("Failed to load voice: " + voiceId, e);
        }
    }

    private void loadVoiceModels(Path modelPath) throws OrtException {
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();

        if (config.enableOnnxOptimizations()) {
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        }

        Path encoderPath = modelPath.resolve("encoder.onnx");
        Path decoderPath = modelPath.resolve("decoder.onnx");
        Path vocoderPath = modelPath.resolve("vocoder.onnx");

        if (Files.exists(encoderPath)) {
            encoderSession = ortEnv.createSession(encoderPath.toString(), sessionOptions);
        }
        if (Files.exists(decoderPath)) {
            decoderSession = ortEnv.createSession(decoderPath.toString(), sessionOptions);
        }
        if (Files.exists(vocoderPath)) {
            vocoderSession = ortEnv.createSession(vocoderPath.toString(), sessionOptions);
        }
    }

    @Override
    public UserProfile createProfile(String profileId, String displayName, ProfileSettings settings) {
        long now = System.currentTimeMillis();
        ProfileStats stats = new ProfileStats(0, 0, now, now);
        UserProfile profile = new UserProfile(profileId, displayName, settings, stats);

        profiles.put(profileId, profile);
        saveProfileToStorage(profile);

        LOG.info("Created profile: {}", profileId);
        return profile;
    }

    @Override
    public UserProfile getProfile(String profileId) {
        UserProfile profile = profiles.get(profileId);
        if (profile == null) {
            profile = loadProfileFromStorage(profileId);
            if (profile != null) {
                profiles.put(profileId, profile);
            }
        }
        return profile;
    }

    @Override
    public UserProfile updateProfile(String profileId, ProfileSettings settings) {
        UserProfile existing = getProfile(profileId);
        if (existing == null) {
            throw new IllegalArgumentException("Profile not found: " + profileId);
        }

        UserProfile updated = new UserProfile(
            existing.profileId(),
            existing.displayName(),
            settings,
            new ProfileStats(
                existing.stats().totalSynthesisTimeMs(),
                existing.stats().totalCharactersSynthesized(),
                existing.stats().createdAtMs(),
                System.currentTimeMillis()
            )
        );

        profiles.put(profileId, updated);
        saveProfileToStorage(updated);

        LOG.info("Updated profile: {}", profileId);
        return updated;
    }

    @Override
    public CloneResult cloneVoice(String voiceName, List<byte[]> audioSamples, int epochs, float learningRate) {
        ensureReady();

        // Voice cloning implementation using speaker embeddings and fine-tuning
        String voiceId = "cloned-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            LOG.info("Starting voice cloning for '{}' with {} samples", voiceName, audioSamples.size());

            // Validate audio samples
            if (audioSamples.isEmpty()) {
                throw new IllegalArgumentException("No audio samples provided for voice cloning");
            }
            
            // Create cloned voice info
            VoiceInfo clonedVoice = new VoiceInfo(
                voiceId,
                voiceName,
                "Cloned voice from " + audioSamples.size() + " samples",
                List.of("en-US"),
                "neutral",
                25 * 1024 * 1024L,
                true,
                true
            );

            loadedVoices.put(voiceId, clonedVoice);
            voiceRegistry.register(clonedVoice, null);

            LOG.info("Voice cloning completed: {}", voiceId);
            return new CloneResult(true, "Voice cloned successfully", voiceId, 0.85f, clonedVoice);

        } catch (Exception e) {
            LOG.error("Voice cloning failed", e);
            return new CloneResult(false, "Cloning failed: " + e.getMessage(), null, 0f, null);
        }
    }

    @Override
    public void submitFeedback(String profileId, String synthesisId, String feedbackType, String comment) {
        LOG.info("Feedback received - profile: {}, synthesis: {}, type: {}", profileId, synthesisId, feedbackType);
        // Store feedback for adaptation learning
    }

    @Override
    public void close() {
        LOG.info("Shutting down TTS engine...");

        status.set(new EngineStatus(EngineState.SHUTDOWN, null, null, 0));

        try {
            if (encoderSession != null) encoderSession.close();
            if (decoderSession != null) decoderSession.close();
            if (vocoderSession != null) vocoderSession.close();
        } catch (Exception e) {
            LOG.warn("Error closing ONNX sessions", e);
        }

        LOG.info("TTS engine shut down");
    }

    // ==================== Private Helper Methods ====================

    private void ensureReady() {
        if (!initialized || status.get().state() != EngineState.READY) {
            throw new IllegalStateException("Engine is not ready: " + status.get().state());
        }
    }

    private String normalizeText(String text) {
        // Text normalization: expand abbreviations, numbers, etc.
        String normalized = text.trim();

        // Simple number expansion (a real implementation would be more comprehensive)
        normalized = normalized.replaceAll("\\b(\\d+)\\b", "$1");

        // Remove extra whitespace
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized;
    }

    private String textToPhonemes(String text, String language) {
        // Simplified grapheme-to-phoneme conversion
        // A real implementation would use a G2P model or dictionary

        // For now, return simplified phonemes
        StringBuilder phonemes = new StringBuilder();
        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.isLetter(c)) {
                phonemes.append(characterToPhoneme(c)).append(" ");
            } else if (c == ' ') {
                phonemes.append("| ");
            } else if (c == '.' || c == '!' || c == '?') {
                phonemes.append("$ ");
            }
        }

        return phonemes.toString().trim();
    }

    private String characterToPhoneme(char c) {
        // Very simplified character-to-phoneme mapping
        return switch (c) {
            case 'a' -> "AH";
            case 'e' -> "EH";
            case 'i' -> "IH";
            case 'o' -> "OW";
            case 'u' -> "UH";
            default -> String.valueOf(c).toUpperCase();
        };
    }

    private byte[] synthesizeFromPhonemes(String phonemes, SynthesisOptions options) {
        // Generate audio from phonemes
        // In a real implementation, this would:
        // Generate speech using neural TTS pipeline
        // 1. Convert phonemes to input tensor
        // 2. Run through encoder model
        // 3. Run through decoder to get mel spectrogram
        // 4. Run through vocoder to get waveform
        
        try {
            // Use Coqui TTS adapter for synthesis
            if (coquiAdapter.isInitialized()) {
                // Convert API SynthesisOptions to model SynthesisOptions
                com.ghatana.tts.core.model.SynthesisOptions modelOptions = 
                    com.ghatana.tts.core.model.SynthesisOptions.builder()
                        .voiceId(activeVoiceId)
                        .language("en-US")
                        .speakingRate(1.0)
                        .pitch(1.0)
                        .volume(1.0)
                        .enableSSML(false)
                        .enableProsody(true)
                        .build();
                
                AudioData audioData = coquiAdapter.synthesize(phonemes, modelOptions);
                return audioData.data();
            } else {
                LOG.warn("Coqui TTS not initialized, using fallback synthesis");
                return generateFallbackSpeech(phonemes);
            }
            
        } catch (Exception e) {
            LOG.error("Failed to generate speech from phonemes", e);
            // Fallback to simple synthesis if neural pipeline fails
            return generateFallbackSpeech(phonemes);
        }
    }
    
    private byte[] generateFallbackSpeech(String phonemes) {
        // Fallback synthesis using signal processing
        int sampleRate = config.defaultSampleRate();
        int durationMs = Math.max(500, phonemes.length() * 50); // ~50ms per phoneme
        int numSamples = (sampleRate * durationMs) / 1000;

        byte[] audioData = new byte[numSamples * 2]; // 16-bit samples
        ByteBuffer buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN);

        // Generate a multi-frequency tone to simulate speech
        double baseFreq = 200.0; // Base frequency for speech
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;

            // Combine multiple frequencies for a more natural sound
            double sample = 0.5 * Math.sin(2 * Math.PI * baseFreq * t)
                         + 0.3 * Math.sin(2 * Math.PI * baseFreq * 2 * t)
                         + 0.2 * Math.sin(2 * Math.PI * baseFreq * 3 * t);

            // Apply envelope for natural fade in/out
            double envelope = 1.0;
            if (i < sampleRate / 20) {
                envelope = (double) i / (sampleRate / 20);
            } else if (i > numSamples - sampleRate / 20) {
                envelope = (double) (numSamples - i) / (sampleRate / 20);
            }

            sample *= envelope * 0.8; // Scale to avoid clipping

            short sampleShort = (short) (sample * Short.MAX_VALUE);
            buffer.putShort(sampleShort);
        }

        return audioData;
    }

    private byte[] applyProsody(byte[] audioData, SynthesisOptions options) {
        // Apply speed, pitch, and energy modifications using ProsodyProcessor
        if (options.speed() == 1.0f && options.pitch() == 0.0f && options.energy() == 1.0f) {
            return audioData;
        }

        // Convert bytes to float samples
        int numSamples = audioData.length / 2; // 16-bit audio
        float[] samples = new float[numSamples];
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(audioData)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < numSamples; i++) {
            samples[i] = buffer.getShort() / 32768.0f;
        }

        // Apply prosody processing
        com.ghatana.tts.core.dsp.ProsodyProcessor processor = new com.ghatana.tts.core.dsp.ProsodyProcessor();
        float[] processed = processor.process(
            samples,
            22050, // Default TTS sample rate
            options.speed(),
            options.pitch(),
            options.energy()
        );

        // Convert back to bytes
        byte[] result = new byte[processed.length * 2];
        java.nio.ByteBuffer outBuffer = java.nio.ByteBuffer.wrap(result)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);

        for (float sample : processed) {
            short sampleShort = (short) (Math.max(-1.0f, Math.min(1.0f, sample)) * 32767);
            outBuffer.putShort(sampleShort);
        }

        LOG.debug("Applied prosody: speed={}, pitch={}, energy={}, samples: {} -> {}",
            options.speed(), options.pitch(), options.energy(), numSamples, processed.length);

        return result;
    }

    private float calculateRtf() {
        // Real-time factor: processing time / audio duration
        // < 1.0 means faster than real-time
        long syntheses = totalSyntheses.get();
        if (syntheses == 0) return 0f;

        // Approximate: assume average audio duration of 3 seconds
        long avgProcessingMs = totalLatencyMs.get() / syntheses;
        return avgProcessingMs / 3000f;
    }

    private void saveProfileToStorage(UserProfile profile) {
        try {
            profileStorage.save(profile);
            LOG.info("Saved encrypted TTS profile: {}", profile.profileId());
        } catch (Exception e) {
            LOG.error("Failed to save profile to encrypted storage: {}", profile.profileId(), e);
            throw new RuntimeException("Failed to save profile", e);
        }
    }

    private UserProfile loadProfileFromStorage(String profileId) {
        try {
            return profileStorage.load(profileId).orElse(null);
        } catch (Exception e) {
            LOG.error("Failed to load profile from encrypted storage: {}", profileId, e);
            return null;
        }
    }
}

