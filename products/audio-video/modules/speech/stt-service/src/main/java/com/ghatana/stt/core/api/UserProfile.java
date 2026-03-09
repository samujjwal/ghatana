package com.ghatana.stt.core.api;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * User profile containing personalization data for adaptive STT.
 * 
 * <p>Stores acoustic adaptation parameters, language model customizations,
 * vocabulary expansions, and context-specific settings.
 * 
 * @doc.type class
 * @doc.purpose User profile for STT personalization
 * @doc.layer api
 */
public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    // Identification
    private final UUID userId;
    private String displayName;

    // Acoustic adaptation
    private float[] speakerEmbedding;
    private AcousticStats acousticStats;
    private List<float[]> mllrTransforms;
    private LoRAParameters voiceAdapter;

    // Language adaptation
    private Map<String, Float> ngramProbabilities;
    private Set<String> personalVocabulary;
    private Map<String, String> pronunciationOverrides;

    // Context adaptation
    private Map<String, ContextProfile> contexts;
    private Set<String> namedEntities;
    private Map<String, Float> wordFrequencies;

    // Settings
    private ProfileSettings settings;

    // Metadata
    private AdaptationStatistics stats;
    private Instant createdAt;
    private Instant lastUpdatedAt;

    public UserProfile(UUID userId, String displayName) {
        this.userId = Objects.requireNonNull(userId);
        this.displayName = displayName;
        this.mllrTransforms = new ArrayList<>();
        this.ngramProbabilities = new HashMap<>();
        this.personalVocabulary = new HashSet<>();
        this.pronunciationOverrides = new HashMap<>();
        this.contexts = new HashMap<>();
        this.namedEntities = new HashSet<>();
        this.wordFrequencies = new HashMap<>();
        this.settings = ProfileSettings.defaults();
        this.stats = new AdaptationStatistics();
        this.createdAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
    }

    public static UserProfile create(String displayName) {
        return new UserProfile(UUID.randomUUID(), displayName);
    }

    // Getters and setters
    public UUID getUserId() { return userId; }
    public String getProfileId() { return userId.toString(); }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public float[] getSpeakerEmbedding() { return speakerEmbedding; }
    public void setSpeakerEmbedding(float[] embedding) { 
        this.speakerEmbedding = embedding;
        this.lastUpdatedAt = Instant.now();
    }

    public AcousticStats getAcousticStats() { return acousticStats; }
    public void setAcousticStats(AcousticStats stats) { 
        this.acousticStats = stats;
        this.lastUpdatedAt = Instant.now();
    }

    public List<float[]> getMllrTransforms() { return Collections.unmodifiableList(mllrTransforms); }
    public void addMllrTransform(float[] transform) { 
        this.mllrTransforms.add(transform);
        this.lastUpdatedAt = Instant.now();
    }

    public LoRAParameters getVoiceAdapter() { return voiceAdapter; }
    public void setVoiceAdapter(LoRAParameters adapter) { 
        this.voiceAdapter = adapter;
        this.lastUpdatedAt = Instant.now();
    }

    public Set<String> getPersonalVocabulary() { return Collections.unmodifiableSet(personalVocabulary); }
    public void addVocabularyTerm(String term) { 
        this.personalVocabulary.add(term);
        this.lastUpdatedAt = Instant.now();
    }
    public void addVocabularyTerms(Collection<String> terms) { 
        this.personalVocabulary.addAll(terms);
        this.lastUpdatedAt = Instant.now();
    }

    public Map<String, Float> getWordFrequencies() { return Collections.unmodifiableMap(wordFrequencies); }
    public void updateWordFrequency(String word, float frequency) {
        this.wordFrequencies.put(word, frequency);
        this.lastUpdatedAt = Instant.now();
    }

    public Set<String> getNamedEntities() { return Collections.unmodifiableSet(namedEntities); }
    public void addNamedEntity(String entity) { 
        this.namedEntities.add(entity);
        this.lastUpdatedAt = Instant.now();
    }

    public Map<String, Float> getNgramProbabilities() { return Collections.unmodifiableMap(ngramProbabilities); }
    public void updateNgramProbability(String ngram, float probability) {
        this.ngramProbabilities.put(ngram, probability);
        this.lastUpdatedAt = Instant.now();
    }

    public Map<String, String> getPronunciationOverrides() { return Collections.unmodifiableMap(pronunciationOverrides); }
    public void addPronunciationOverride(String word, String pronunciation) {
        this.pronunciationOverrides.put(word, pronunciation);
        this.lastUpdatedAt = Instant.now();
    }

    public Map<String, ContextProfile> getContexts() { return Collections.unmodifiableMap(contexts); }
    public void addContext(String contextId, ContextProfile context) {
        this.contexts.put(contextId, context);
        this.lastUpdatedAt = Instant.now();
    }

    public ProfileSettings getSettings() { return settings; }
    public void setSettings(ProfileSettings settings) { 
        this.settings = settings;
        this.lastUpdatedAt = Instant.now();
    }

    public AdaptationStatistics getStats() { return stats; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }

    // Nested types
    public record AcousticStats(
        float[] meanVector,
        float[] varianceVector,
        int sampleCount
    ) {}

    public record LoRAParameters(
        float[][] weights,
        int rank,
        float alpha
    ) {}

    public record ContextProfile(
        String contextId,
        String name,
        Set<String> vocabulary,
        Map<String, Float> termWeights
    ) {}

    public record ProfileSettings(
        String preferredLanguage,
        AdaptationMode adaptationMode,
        PrivacyLevel privacyLevel,
        boolean storeAudio,
        boolean storeTranscripts
    ) {
        public static ProfileSettings defaults() {
            return new ProfileSettings(
                "en-US",
                AdaptationMode.BALANCED,
                PrivacyLevel.HIGH,
                false,
                true
            );
        }
    }

    public enum AdaptationMode {
        CONSERVATIVE,
        BALANCED,
        AGGRESSIVE
    }

    public enum PrivacyLevel {
        HIGH,
        MEDIUM,
        LOW
    }

    public static class AdaptationStatistics implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long totalTranscriptionTimeMs;
        private int totalInteractions;
        private float estimatedWer;
        private int vocabularyTermsLearned;

        public long getTotalTranscriptionTimeMs() { return totalTranscriptionTimeMs; }
        public void addTranscriptionTime(long ms) { this.totalTranscriptionTimeMs += ms; }
        
        public int getTotalInteractions() { return totalInteractions; }
        public void incrementInteractions() { this.totalInteractions++; }
        
        public float getEstimatedWer() { return estimatedWer; }
        public void setEstimatedWer(float wer) { this.estimatedWer = wer; }
        
        public int getVocabularyTermsLearned() { return vocabularyTermsLearned; }
        public void addVocabularyTermsLearned(int count) { this.vocabularyTermsLearned += count; }
    }
}
