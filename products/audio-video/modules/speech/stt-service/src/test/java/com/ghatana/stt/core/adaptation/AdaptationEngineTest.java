package com.ghatana.stt.core.adaptation;

import com.ghatana.stt.core.adaptation.AdaptationEngine.AdaptationConfig;
import com.ghatana.stt.core.adaptation.AdaptationEngine.AdaptationContext;
import com.ghatana.stt.core.adaptation.AdaptationEngine.AdaptationResult;
import com.ghatana.stt.core.adaptation.AdaptationEngine.AdaptationStats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AdaptationEngine.
 */
@DisplayName("AdaptationEngine")
class AdaptationEngineTest {

    private AdaptationEngine engine;
    private AdaptationConfig config;

    @BeforeEach
    void setUp() {
        config = AdaptationConfig.defaults();
        engine = new AdaptationEngine(config);
    }

    @Nested
    @DisplayName("Correction Processing")
    class CorrectionProcessing {

        @Test
        @DisplayName("should process correction and extract new vocabulary")
        void shouldProcessCorrectionAndExtractNewVocabulary() {
            // GIVEN a correction with new terms
            String profileId = "user-123";
            String original = "I went to the store";
            String corrected = "I went to the supermarket";

            // WHEN processing correction
            AdaptationResult result = engine.processCorrection(profileId, original, corrected, null);

            // THEN should succeed with vocabulary updates
            assertTrue(result.success());
            assertTrue(result.vocabularyUpdates() > 0);
        }

        @Test
        @DisplayName("should accumulate vocabulary across corrections")
        void shouldAccumulateVocabularyAcrossCorrections() {
            // GIVEN multiple corrections
            String profileId = "user-123";

            // WHEN processing multiple corrections
            engine.processCorrection(profileId, "hello world", "hello universe", null);
            engine.processCorrection(profileId, "good morning", "good afternoon", null);
            AdaptationResult result = engine.processCorrection(profileId, "nice day", "beautiful day", null);

            // THEN vocabulary should accumulate
            assertTrue(result.totalVocabularySize() >= 3);
        }

        @Test
        @DisplayName("should update speaker embedding with audio features")
        void shouldUpdateSpeakerEmbeddingWithAudioFeatures() {
            // GIVEN correction with audio features
            String profileId = "user-123";
            float[] audioFeatures = new float[256];
            for (int i = 0; i < audioFeatures.length; i++) {
                audioFeatures[i] = (float) Math.random();
            }

            // WHEN processing correction with features
            engine.processCorrection(profileId, "test", "testing", audioFeatures);

            // THEN should have speaker embedding
            AdaptationStats stats = engine.getStats(profileId);
            assertTrue(stats.hasSpeakerEmbedding());
        }

        @Test
        @DisplayName("should estimate WER from corrections")
        void shouldEstimateWerFromCorrections() {
            // GIVEN multiple corrections
            String profileId = "user-123";

            // WHEN processing corrections
            engine.processCorrection(profileId, "hello", "hello", null); // No error
            engine.processCorrection(profileId, "wrld", "world", null); // Small error
            engine.processCorrection(profileId, "tset", "test", null); // Small error

            // THEN should have WER estimate
            AdaptationStats stats = engine.getStats(profileId);
            assertTrue(stats.estimatedWer() >= 0 && stats.estimatedWer() <= 1);
        }
    }

    @Nested
    @DisplayName("Context Retrieval")
    class ContextRetrieval {

        @Test
        @DisplayName("should return empty context for unknown profile")
        void shouldReturnEmptyContextForUnknownProfile() {
            // GIVEN unknown profile
            String profileId = "unknown-user";

            // WHEN getting context
            AdaptationContext context = engine.getContext(profileId);

            // THEN should return empty context
            assertNotNull(context);
            assertTrue(context.vocabularyTerms().isEmpty());
            assertNull(context.speakerEmbedding());
        }

        @Test
        @DisplayName("should return vocabulary terms in context")
        void shouldReturnVocabularyTermsInContext() {
            // GIVEN profile with vocabulary
            String profileId = "user-123";
            engine.processCorrection(profileId, "hello world", "hello universe", null);

            // WHEN getting context
            AdaptationContext context = engine.getContext(profileId);

            // THEN should include vocabulary terms
            assertFalse(context.vocabularyTerms().isEmpty());
        }
    }

    @Nested
    @DisplayName("Adaptation Application")
    class AdaptationApplication {

        @Test
        @DisplayName("should apply vocabulary corrections to text")
        void shouldApplyVocabularyCorrectionToText() {
            // GIVEN profile with correction pattern
            String profileId = "user-123";
            engine.processCorrection(profileId, "teh", "the", null);

            // WHEN applying adaptation
            String result = engine.applyAdaptation(profileId, "teh quick brown fox");

            // THEN should apply correction
            assertEquals("the quick brown fox", result);
        }

        @Test
        @DisplayName("should return unchanged text for unknown profile")
        void shouldReturnUnchangedTextForUnknownProfile() {
            // GIVEN unknown profile
            String profileId = "unknown-user";
            String text = "hello world";

            // WHEN applying adaptation
            String result = engine.applyAdaptation(profileId, text);

            // THEN should return unchanged
            assertEquals(text, result);
        }
    }

    @Nested
    @DisplayName("Serialization")
    class Serialization {

        @Test
        @DisplayName("should serialize and deserialize adaptation data")
        void shouldSerializeAndDeserializeAdaptationData() {
            // GIVEN profile with adaptation data
            String profileId = "user-123";
            engine.processCorrection(profileId, "hello", "hi", null);
            engine.processCorrection(profileId, "world", "universe", null);

            // WHEN serializing and deserializing
            byte[] data = engine.saveAdaptation(profileId);
            
            AdaptationEngine newEngine = new AdaptationEngine(config);
            newEngine.loadAdaptation(profileId, data);

            // THEN should restore vocabulary
            AdaptationStats stats = newEngine.getStats(profileId);
            assertTrue(stats.vocabularySize() > 0);
        }

        @Test
        @DisplayName("should return empty bytes for unknown profile")
        void shouldReturnEmptyBytesForUnknownProfile() {
            // GIVEN unknown profile
            String profileId = "unknown-user";

            // WHEN saving
            byte[] data = engine.saveAdaptation(profileId);

            // THEN should return empty
            assertEquals(0, data.length);
        }
    }

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        @DisplayName("should return empty stats for unknown profile")
        void shouldReturnEmptyStatsForUnknownProfile() {
            // GIVEN unknown profile
            String profileId = "unknown-user";

            // WHEN getting stats
            AdaptationStats stats = engine.getStats(profileId);

            // THEN should return empty stats
            assertEquals(0, stats.vocabularySize());
            assertEquals(0, stats.totalCorrections());
            assertFalse(stats.hasSpeakerEmbedding());
        }

        @Test
        @DisplayName("should track correction count")
        void shouldTrackCorrectionCount() {
            // GIVEN profile with corrections
            String profileId = "user-123";

            // WHEN processing corrections
            engine.processCorrection(profileId, "a", "b", null);
            engine.processCorrection(profileId, "c", "d", null);
            engine.processCorrection(profileId, "e", "f", null);

            // THEN should track count
            AdaptationStats stats = engine.getStats(profileId);
            assertEquals(3, stats.totalCorrections());
        }
    }
}
