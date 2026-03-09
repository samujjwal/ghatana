package com.ghatana.stt.core.pipeline;

import com.ghatana.stt.core.api.*;
import com.ghatana.stt.core.whisper.WhisperCppAdapter;
import com.ghatana.stt.core.config.EngineConfig;
import com.ghatana.stt.core.storage.ProfileEncryption;
import com.ghatana.stt.core.storage.ProfileStorage;
import com.ghatana.stt.core.metrics.MetricsCollector;
import com.ghatana.stt.core.adaptation.AdaptationEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import io.activej.promise.Promise;

/**
 * Default implementation of the AdaptiveSTTEngine.
 * 
 * <p>This implementation orchestrates the full STT pipeline including
 * audio preprocessing, feature extraction, inference, and adaptation.
 * 
 * @doc.type class
 * @doc.purpose Default STT engine implementation
 * @doc.layer pipeline
 */
public class DefaultAdaptiveSTTEngine implements AdaptiveSTTEngine {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAdaptiveSTTEngine.class);

    private final EngineConfig config;
    private final AtomicReference<EngineStatus> status;
    private final ConcurrentHashMap<String, StreamingSession> activeSessions;
    private final ConcurrentHashMap<String, UserProfile> loadedProfiles;
    private final ProfileStorage profileStorage;
    private final MetricsCollector metricsCollector;
    private final WhisperCppAdapter whisperAdapter;
    private final AdaptationEngine adaptationEngine;
    private final ConcurrentHashMap<String, ModelInfo> availableModels;
    private volatile String activeModelId;

    public DefaultAdaptiveSTTEngine(EngineConfig config) {
        this.config = config;
        this.status = new AtomicReference<>(EngineStatus.initializing());
        this.activeSessions = new ConcurrentHashMap<>();
        this.loadedProfiles = new ConcurrentHashMap<>();
        
        // Try to initialize WhisperAdapter, but don't fail if native libs aren't available
        WhisperCppAdapter tempAdapter = null;
        try {
            tempAdapter = new WhisperCppAdapter(config);
        } catch (IllegalStateException e) {
            LOG.warn("WhisperCppAdapter not available: {}. Engine will have limited functionality.", e.getMessage());
        }
        this.whisperAdapter = tempAdapter;

        this.metricsCollector = new MetricsCollector();
        this.adaptationEngine = new AdaptationEngine(AdaptationEngine.AdaptationConfig.defaults());
        this.availableModels = new ConcurrentHashMap<>();
        this.activeModelId = config.defaultModel();

        try {
            ProfileEncryption encryption = new ProfileEncryption();
            this.profileStorage = new ProfileStorage(config.dataPath().resolve("profiles"), encryption);
            LOG.info("Initialized encrypted profile storage");
        } catch (Exception e) {
            LOG.error("Failed to initialize profile storage", e);
            throw new RuntimeException("Profile storage initialization failed", e);
        }

        // Initialize asynchronously
        Thread.startVirtualThread(this::initialize);
    }

    public DefaultAdaptiveSTTEngine(EngineConfig config, AdaptiveSTTEngineFactory.PipelineComponents components) {
        this.config = config;
        this.status = new AtomicReference<>(EngineStatus.initializing());
        this.activeSessions = new ConcurrentHashMap<>();
        this.loadedProfiles = new ConcurrentHashMap<>();
        
        // Try to initialize WhisperAdapter, but don't fail if native libs aren't available
        WhisperCppAdapter tempAdapter = null;
        try {
            tempAdapter = new WhisperCppAdapter(config);
        } catch (IllegalStateException e) {
            LOG.warn("WhisperCppAdapter not available: {}. Engine will have limited functionality.", e.getMessage());
        }
        this.whisperAdapter = tempAdapter;
        this.metricsCollector = new MetricsCollector();
        this.adaptationEngine = new AdaptationEngine(AdaptationEngine.AdaptationConfig.defaults());
        this.availableModels = new ConcurrentHashMap<>();
        this.activeModelId = config.defaultModel();

        try {
            ProfileEncryption encryption = new ProfileEncryption();
            this.profileStorage = new ProfileStorage(config.dataPath().resolve("profiles"), encryption);
            LOG.info("Initialized encrypted profile storage");
        } catch (Exception e) {
            LOG.error("Failed to initialize profile storage", e);
            throw new RuntimeException("Profile storage initialization failed", e);
        }

        // Initialize asynchronously
        Thread.startVirtualThread(this::initialize);
    }

    @Override
    public TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options) {
        ensureReady();
        
        long startTime = System.currentTimeMillis();

        try {
            // Convert API classes to model classes for Whisper.cpp
            com.ghatana.stt.core.model.AudioData modelAudio = convertToModelAudioData(audio);
            com.ghatana.stt.core.model.TranscriptionOptions modelOptions = convertToModelTranscriptionOptions(options);
            
            // Use Whisper.cpp for transcription (cost-optimized)
            com.ghatana.stt.core.model.TranscriptionResult modelResult = whisperAdapter.transcribe(modelAudio, modelOptions);
            
            // Convert model result back to API result
            TranscriptionResult result = convertToApiTranscriptionResult(modelResult);
            
            // Apply user profile adaptations if available
            if (profileStorage != null && options.profileId() != null) {
                result = applyUserProfileAdaptations(result, options.profileId());
            }
            
            // Collect metrics
            if (metricsCollector != null) {
                metricsCollector.recordTranscription(
                    result.text().length(),
                    System.currentTimeMillis() - startTime,
                    result.confidence()
                );
            }
            
            return result;
            
        } catch (Exception e) {
            LOG.error("Transcription failed", e);
            throw new RuntimeException("Transcription failed", e);
        }
    }

    @Override
    public Promise<TranscriptionResult> transcribeAsync(AudioData audio, TranscriptionOptions options) {
        return Promise.ofBlocking(Executors.newVirtualThreadPerTaskExecutor(), () -> transcribe(audio, options));
    }

    @Override
    public StreamingSession createStreamingSession(UserProfile profile) {
        ensureReady();
        
        if (metricsCollector != null) {
            metricsCollector.recordSessionStart();
        }

        DefaultStreamingSession session = new DefaultStreamingSession(this, profile);
        activeSessions.put(session.getSessionId(), session);
        return session;
    }

    @Override
    public AdaptationResult adaptFromInteraction(Interaction interaction) {
        ensureReady();
        
        try {
            if (interaction.hasCorrectedTranscript()) {
                AdaptationEngine.AdaptationResult engineResult = adaptationEngine.processCorrection(
                    interaction.contextId() != null ? interaction.contextId() : "default",
                    interaction.originalTranscript(),
                    interaction.correctedTranscript(),
                    interaction.audioFeatures()
                );
                LOG.debug("Adaptation from correction: {} vocab updates, estimated WER={}",
                    engineResult.vocabularyUpdates(), engineResult.estimatedWer());
                return AdaptationResult.success(1, engineResult.vocabularyUpdates());
            }
            // No correction — record the interaction for speaker embedding updates
            if (interaction.hasAudioFeatures()) {
                adaptationEngine.processCorrection(
                    interaction.contextId() != null ? interaction.contextId() : "default",
                    interaction.originalTranscript(),
                    interaction.originalTranscript(),
                    interaction.audioFeatures()
                );
            }
            return AdaptationResult.success(1, 0);
        } catch (Exception e) {
            LOG.error("Failed to adapt from interaction", e);
            return AdaptationResult.failure("Adaptation failed: " + e.getMessage());
        }
    }

    @Override
    public AdaptationResult adaptFromInteractions(List<Interaction> interactions) {
        ensureReady();
        
        try {
            int totalVocabUpdates = 0;
            for (Interaction interaction : interactions) {
                AdaptationResult result = adaptFromInteraction(interaction);
                totalVocabUpdates += result.newVocabularyTerms();
            }
            LOG.info("Batch adaptation completed: {} interactions, {} total vocab updates",
                interactions.size(), totalVocabUpdates);
            return AdaptationResult.success(interactions.size(), totalVocabUpdates);
        } catch (Exception e) {
            LOG.error("Failed to adapt from interactions", e);
            return AdaptationResult.failure("Batch adaptation failed: " + e.getMessage());
        }
    }

    @Override
    public UserProfile createUserProfile(List<AudioSample> enrollmentData) {
        ensureReady();
        
        UserProfile profile = UserProfile.create("New User");
        
        // Extract speaker embedding from enrollment audio samples
        if (enrollmentData != null && !enrollmentData.isEmpty()) {
            float[] aggregatedEmbedding = null;
            int sampleCount = 0;
            for (AudioSample sample : enrollmentData) {
                if (sample.audio() != null && sample.audio().data().length > 0) {
                    // Compute a simple energy-based feature vector from the audio data
                    float[] features = extractSimpleFeatures(sample.audio());
                    if (aggregatedEmbedding == null) {
                        aggregatedEmbedding = features.clone();
                    } else {
                        for (int i = 0; i < Math.min(aggregatedEmbedding.length, features.length); i++) {
                            aggregatedEmbedding[i] += features[i];
                        }
                    }
                    sampleCount++;
                }
            }
            if (aggregatedEmbedding != null && sampleCount > 0) {
                for (int i = 0; i < aggregatedEmbedding.length; i++) {
                    aggregatedEmbedding[i] /= sampleCount;
                }
                profile.setSpeakerEmbedding(aggregatedEmbedding);
                LOG.info("Created speaker embedding from {} enrollment samples", sampleCount);
            }
        }
        
        loadedProfiles.put(profile.getProfileId(), profile);
        LOG.info("Created new user profile: {}", profile.getProfileId());
        return profile;
    }

    @Override
    public UserProfile loadUserProfile(String profileId) {
        // Check cache first
        UserProfile cached = loadedProfiles.get(profileId);
        if (cached != null) {
            return cached;
        }
        
        // Load from encrypted storage
        try {
            UserProfile profile = profileStorage.load(profileId).orElse(null);
            if (profile != null) {
                loadedProfiles.put(profileId, profile);
                LOG.info("Loaded user profile: {}", profileId);
            }
            return profile;
        } catch (Exception e) {
            LOG.error("Failed to load profile: {}", profileId, e);
            return null;
        }
    }

    @Override
    public void updateUserProfile(UserProfile profile, AdaptationData data) {
        ensureReady();
        
        // Apply adaptation data to profile
        if (data.speakerEmbedding() != null) {
            profile.setSpeakerEmbedding(data.speakerEmbedding());
        }
        if (data.mllrTransform() != null) {
            profile.addMllrTransform(data.mllrTransform());
        }
        if (data.newVocabulary() != null) {
            profile.addVocabularyTerms(data.newVocabulary());
        }
        if (data.wordFrequencies() != null) {
            data.wordFrequencies().forEach(profile::updateWordFrequency);
        }
        if (data.namedEntities() != null) {
            data.namedEntities().forEach(profile::addNamedEntity);
        }
        if (data.acousticStats() != null) {
            profile.setAcousticStats(data.acousticStats());
        }
        if (data.loraUpdate() != null) {
            profile.setVoiceAdapter(data.loraUpdate());
        }
        LOG.debug("Applied adaptation data to user profile");
    }

    @Override
    public void saveUserProfile(UserProfile profile) {
        // Save to encrypted storage
        try {
            profileStorage.save(profile);
            loadedProfiles.put(profile.getProfileId(), profile);
            LOG.info("Saved user profile: {}", profile.getProfileId());
        } catch (Exception e) {
            LOG.error("Failed to save profile: {}", profile.getProfileId(), e);
            throw new RuntimeException("Failed to save user profile", e);
        }
    }

    @Override
    public List<ModelInfo> getAvailableModels() {
        if (availableModels.isEmpty()) {
            discoverModels();
        }
        return List.copyOf(availableModels.values());
    }

    @Override
    public void loadModel(String modelId, ModelOptions options) {
        ensureReady();
        ModelInfo model = availableModels.get(modelId);
        if (model == null) {
            throw new IllegalArgumentException("Unknown model: " + modelId);
        }
        this.activeModelId = modelId;
        // Update model info to reflect loaded state
        availableModels.put(modelId, new ModelInfo(
            model.modelId(), model.name(), model.version(), model.languages(),
            model.sizeBytes(), true, model.format(), model.expectedWer()));
        LOG.info("Loaded model: {} ({})", modelId, model.name());
    }

    @Override
    public ModelInfo getActiveModel() {
        if (activeModelId == null) {
            return null;
        }
        ModelInfo cached = availableModels.get(activeModelId);
        if (cached != null) {
            return cached;
        }
        // Return a default model info for the whisper-cpp adapter
        return new ModelInfo(
            activeModelId, "Whisper " + activeModelId, "1.0",
            List.of("en", "es", "fr", "de", "ja", "zh"),
            0L, true, ModelInfo.ModelFormat.GGML, null);
    }

    @Override
    public PersonalizedModel createPersonalizedModel(UserProfile profile) {
        ensureReady();
        ModelInfo baseModel = getActiveModel();
        
        // Build language adapter from profile vocabulary and n-gram data
        float[] ngramWeights = profile.getNgramProbabilities().isEmpty()
            ? new float[0]
            : profile.getNgramProbabilities().values().stream()
                .reduce(new float[0], (acc, v) -> acc, (a, b) -> a); // placeholder aggregation
        float[] vocabBoosts = new float[profile.getPersonalVocabulary().size()];
        int idx = 0;
        for (String term : profile.getPersonalVocabulary()) {
            vocabBoosts[idx++] = profile.getWordFrequencies().getOrDefault(term, 1.0f);
        }
        
        PersonalizedModel.LanguageAdapter langAdapter = new PersonalizedModel.LanguageAdapter(
            ngramWeights, vocabBoosts, new float[0]);
        
        PersonalizedModel personalizedModel = new PersonalizedModel(
            baseModel, profile.getProfileId(), profile.getVoiceAdapter(), langAdapter, true);
        
        LOG.info("Created personalized model for user: {} with {} vocab terms",
            profile.getProfileId(), profile.getPersonalVocabulary().size());
        return personalizedModel;
    }

    @Override
    public EngineStatus getStatus() {
        return status.get();
    }

    @Override
    public EngineMetrics getMetrics() {
        return metricsCollector.getMetrics();
    }

    @Override
    public boolean isReady() {
        return status.get().state() == EngineStatus.State.READY;
    }

    @Override
    public void close() throws Exception {
        status.set(new EngineStatus(EngineStatus.State.SHUTDOWN, null, 0, null));
        activeSessions.clear();
        loadedProfiles.clear();
        LOG.info("DefaultAdaptiveSTTEngine closed");
    }

    // Private helper methods

    private void initialize() {
        try {
            status.set(new EngineStatus(EngineStatus.State.BUSY, null, 0, null));
            
            // Discover available models from the model directory
            discoverModels();
            
            // Mark the default model as loaded if whisper adapter is available
            if (whisperAdapter != null && activeModelId != null) {
                ModelInfo active = availableModels.get(activeModelId);
                if (active != null) {
                    availableModels.put(activeModelId, new ModelInfo(
                        active.modelId(), active.name(), active.version(), active.languages(),
                        active.sizeBytes(), true, active.format(), active.expectedWer()));
                }
            }
            
            status.set(EngineStatus.ready(activeModelId != null ? activeModelId : "whisper-cpp"));
            LOG.info("DefaultAdaptiveSTTEngine initialized: {} models discovered, active={}",
                availableModels.size(), activeModelId);
            
        } catch (Exception e) {
            status.set(EngineStatus.error("Initialization failed: " + e.getMessage()));
            LOG.error("Failed to initialize DefaultAdaptiveSTTEngine", e);
        }
    }

    private TranscriptionResult applyUserProfileAdaptations(TranscriptionResult result, String profileId) {
        try {
            UserProfile profile = loadUserProfile(profileId);
            if (profile == null) {
                return result;
            }
            
            // Apply vocabulary corrections from the adaptation engine
            String adaptedText = adaptationEngine.applyAdaptation(profileId, result.text());
            
            // Apply named entity boosting — replace lowercase variants with proper casing
            for (String entity : profile.getNamedEntities()) {
                String lowerEntity = entity.toLowerCase();
                if (adaptedText.toLowerCase().contains(lowerEntity)) {
                    adaptedText = adaptedText.replaceAll(
                        "(?i)" + java.util.regex.Pattern.quote(lowerEntity), entity);
                }
            }
            
            if (!adaptedText.equals(result.text())) {
                LOG.debug("Applied user profile adaptations for user: {}", profileId);
                return TranscriptionResult.builder()
                    .text(adaptedText)
                    .confidence(result.confidence())
                    .processingTimeMs(result.processingTimeMs())
                    .modelUsed(result.modelUsed())
                    .language(result.language())
                    .isFinal(result.isFinal())
                    .build();
            }
            return result;
        } catch (Exception e) {
            LOG.warn("Failed to apply user profile adaptations for user: {}", profileId, e);
            return result;
        }
    }

    private void discoverModels() {
        // Register built-in Whisper model variants
        registerBuiltinModel("whisper-tiny", "Whisper Tiny", 75_000_000L, 0.076f);
        registerBuiltinModel("whisper-base", "Whisper Base", 142_000_000L, 0.057f);
        registerBuiltinModel("whisper-small", "Whisper Small", 466_000_000L, 0.043f);
        registerBuiltinModel("whisper-medium", "Whisper Medium", 1_500_000_000L, 0.030f);
        registerBuiltinModel("whisper-large", "Whisper Large", 2_900_000_000L, 0.022f);
        
        // Scan model directory for additional GGML files
        Path modelDir = config.modelPath();
        if (Files.isDirectory(modelDir)) {
            try (Stream<Path> files = Files.list(modelDir)) {
                files.filter(p -> p.toString().endsWith(".bin") || p.toString().endsWith(".ggml"))
                     .forEach(p -> {
                         String fileName = p.getFileName().toString();
                         String modelId = fileName.replaceFirst("\\.(bin|ggml)$", "");
                         if (!availableModels.containsKey(modelId)) {
                             try {
                                 long size = Files.size(p);
                                 availableModels.put(modelId, new ModelInfo(
                                     modelId, modelId, "1.0",
                                     List.of("en"), size, false,
                                     ModelInfo.ModelFormat.GGML, null));
                             } catch (Exception e) {
                                 LOG.warn("Failed to read model file: {}", p, e);
                             }
                         }
                     });
            } catch (Exception e) {
                LOG.debug("Could not scan model directory {}: {}", modelDir, e.getMessage());
            }
        }
    }

    private void registerBuiltinModel(String id, String name, long sizeBytes, float expectedWer) {
        availableModels.putIfAbsent(id, new ModelInfo(
            id, name, "1.0",
            List.of("en", "es", "fr", "de", "ja", "zh", "ko", "pt", "ru", "it"),
            sizeBytes, false, ModelInfo.ModelFormat.GGML, expectedWer));
    }

    private static float[] extractSimpleFeatures(AudioData audio) {
        // Compute a simple 128-dim energy-based feature vector as a stand-in for
        // a proper speaker embedding (e.g., x-vector / ECAPA-TDNN).
        int featureDim = 128;
        float[] features = new float[featureDim];
        byte[] data = audio.data();
        int samplesPerBin = Math.max(1, (data.length / 2) / featureDim); // 16-bit PCM
        for (int bin = 0; bin < featureDim && bin * samplesPerBin * 2 < data.length; bin++) {
            float energy = 0;
            for (int s = 0; s < samplesPerBin; s++) {
                int offset = (bin * samplesPerBin + s) * 2;
                if (offset + 1 < data.length) {
                    short sample = (short) ((data[offset] & 0xFF) | (data[offset + 1] << 8));
                    energy += (float) sample * sample;
                }
            }
            features[bin] = (float) Math.sqrt(energy / samplesPerBin);
        }
        return features;
    }

    private void ensureReady() {
        if (!isReady()) {
            throw new IllegalStateException("Engine is not ready: " + status.get().state());
        }
    }

    void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
        if (metricsCollector != null) {
            metricsCollector.recordSessionEnd();
        }
    }
    
    // Conversion methods between API and model classes
    private com.ghatana.stt.core.model.AudioData convertToModelAudioData(AudioData apiAudio) {
        return new com.ghatana.stt.core.model.AudioData(
            apiAudio.data(),
            apiAudio.sampleRate(),
            apiAudio.channels(),
            com.ghatana.stt.core.model.AudioData.AudioFormat.PCM_16BIT
        );
    }
    
    private com.ghatana.stt.core.model.TranscriptionOptions convertToModelTranscriptionOptions(TranscriptionOptions apiOptions) {
        return com.ghatana.stt.core.model.TranscriptionOptions.builder()
            .language(apiOptions.language())
            .enablePunctuation(apiOptions.enablePunctuation())
            .enableWordTiming(apiOptions.enableWordTimings())
            .maxAlternatives(apiOptions.maxAlternatives())
            .build();
    }
    
    private TranscriptionResult convertToApiTranscriptionResult(com.ghatana.stt.core.model.TranscriptionResult modelResult) {
        return TranscriptionResult.builder()
            .text(modelResult.text())
            .confidence((float) modelResult.confidence())
            .processingTimeMs(modelResult.processingTime().totalMs())
            .modelUsed("whisper-cpp")
            .language(modelResult.language())
            .isFinal(true)
            .build();
    }
    
    // Streaming session implementation with callback support and audio buffering
    private static class DefaultStreamingSession implements StreamingSession {
        private final String sessionId;
        private final DefaultAdaptiveSTTEngine engine;
        private final UserProfile profile;
        private volatile SessionState state = SessionState.CREATED;
        private final List<Consumer<TranscriptionResult>> transcriptionCallbacks = new CopyOnWriteArrayList<>();
        private final List<Consumer<Throwable>> errorCallbacks = new CopyOnWriteArrayList<>();
        private final List<Consumer<SessionState>> stateChangeCallbacks = new CopyOnWriteArrayList<>();
        private final AtomicLong totalAudioMs = new AtomicLong(0);
        private final AtomicLong chunksProcessed = new AtomicLong(0);
        private final AtomicLong transcriptionsEmitted = new AtomicLong(0);
        private float confidenceSum = 0.0f;
        private long processingStartMs = 0;
        
        public DefaultStreamingSession(DefaultAdaptiveSTTEngine engine, UserProfile profile) {
            this.engine = engine;
            this.profile = profile;
            this.sessionId = "session-" + System.nanoTime();
        }
        
        @Override
        public String getSessionId() {
            return sessionId;
        }
        
        @Override
        public void start() {
            SessionState prev = state;
            state = SessionState.ACTIVE;
            processingStartMs = System.currentTimeMillis();
            if (prev != state) {
                notifyStateChange(state);
            }
        }
        
        @Override
        public void stop() {
            SessionState prev = state;
            state = SessionState.STOPPED;
            engine.removeSession(sessionId);
            if (prev != state) {
                notifyStateChange(state);
            }
        }
        
        @Override
        public void feedAudio(AudioChunk chunk) {
            if (state != SessionState.ACTIVE) {
                notifyError(new IllegalStateException(
                    "Cannot feed audio in state: " + state));
                return;
            }
            chunksProcessed.incrementAndGet();
            long chunkDurationMs = (chunk.data().length * 1000L) / (chunk.sampleRate() * 2); // 16-bit PCM
            totalAudioMs.addAndGet(chunkDurationMs);
            
            // Process the audio chunk asynchronously via the engine's whisper adapter
            Thread.startVirtualThread(() -> {
                try {
                    if (engine.whisperAdapter == null) {
                        return;
                    }
                    AudioData audioData = AudioData.fromPcm(chunk.data(), chunk.sampleRate());
                    TranscriptionOptions options = TranscriptionOptions.builder()
                        .language(profile != null && profile.getSettings() != null
                            ? profile.getSettings().preferredLanguage() : "en")
                        .enablePunctuation(true)
                        .enableWordTimings(false)
                        .maxAlternatives(1)
                        .profileId(profile != null ? profile.getProfileId() : null)
                        .build();
                    
                    TranscriptionResult result = engine.transcribe(audioData, options);
                    confidenceSum += result.confidence();
                    transcriptionsEmitted.incrementAndGet();
                    notifyTranscription(result);
                } catch (Exception e) {
                    notifyError(e);
                }
            });
        }
        
        @Override
        public void onTranscription(Consumer<TranscriptionResult> callback) {
            transcriptionCallbacks.add(callback);
        }
        
        @Override
        public void onError(Consumer<Throwable> callback) {
            errorCallbacks.add(callback);
        }
        
        @Override
        public void onStateChange(Consumer<SessionState> callback) {
            stateChangeCallbacks.add(callback);
        }
        
        @Override
        public SessionState getState() {
            return state;
        }
        
        @Override
        public SessionStats getStats() {
            long emitted = transcriptionsEmitted.get();
            float avgConfidence = emitted > 0 ? confidenceSum / emitted : 0.0f;
            long elapsed = processingStartMs > 0 ? System.currentTimeMillis() - processingStartMs : 0;
            long audio = totalAudioMs.get();
            float rtf = audio > 0 ? (float) elapsed / audio : 0.0f;
            return new SessionStats(audio, (int) chunksProcessed.get(),
                (int) emitted, avgConfidence, rtf);
        }
        
        @Override
        public void close() throws Exception {
            stop();
        }
        
        private void notifyTranscription(TranscriptionResult result) {
            for (Consumer<TranscriptionResult> cb : transcriptionCallbacks) {
                try {
                    cb.accept(result);
                } catch (Exception e) {
                    LOG.warn("Transcription callback error in session {}", sessionId, e);
                }
            }
        }
        
        private void notifyError(Throwable error) {
            for (Consumer<Throwable> cb : errorCallbacks) {
                try {
                    cb.accept(error);
                } catch (Exception e) {
                    LOG.warn("Error callback error in session {}", sessionId, e);
                }
            }
        }
        
        private void notifyStateChange(SessionState newState) {
            for (Consumer<SessionState> cb : stateChangeCallbacks) {
                try {
                    cb.accept(newState);
                } catch (Exception e) {
                    LOG.warn("State change callback error in session {}", sessionId, e);
                }
            }
        }
    }
}
