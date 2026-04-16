package com.ghatana.aep.learning.mining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PatternRecommender")
class PatternRecommenderTest {

    @Test
    void shouldRankMoreRelevantPatternsHigher() {
        PatternRecommender recommender = new PatternRecommender();
        recommender.registerPattern("pattern-exact", Set.of("login", "purchase"), 0.9);
        recommender.registerPattern("pattern-partial", Set.of("login", "search"), 0.9);

        List<PatternRecommender.Recommendation> recommendations = recommender
            .getRecommendations(Set.of("login", "purchase"), 5)
            .toCompletableFuture()
            .join();

        assertThat(recommendations).hasSize(2);
        assertThat(recommendations.get(0).getPatternId()).isEqualTo("pattern-exact");
        assertThat(recommendations.get(0).getScore()).isGreaterThan(recommendations.get(1).getScore());
    }

    @Test
    void shouldBoostRecommendationsWithPositiveFeedback() {
        PatternRecommender recommender = new PatternRecommender();
        recommender.registerPattern("pattern-a", Set.of("checkout", "payment"), 0.8);
        recommender.registerPattern("pattern-b", Set.of("checkout", "payment"), 0.8);

        recommender.recordFeedback("pattern-b", true, 0.95);
        recommender.recordFeedback("pattern-b", true, 0.90);
        recommender.recordFeedback("pattern-a", false, 0.0);
        recommender.recordFeedback("pattern-a", false, 0.0);

        List<PatternRecommender.Recommendation> recommendations = recommender
            .getRecommendations(Set.of("checkout", "payment"), 5)
            .toCompletableFuture()
            .join();

        assertThat(recommendations).hasSize(2);
        assertThat(recommendations.get(0).getPatternId()).isEqualTo("pattern-b");
        assertThat(recommendations.get(0).getScore()).isGreaterThan(recommendations.get(1).getScore());
    }

    @Test
    void shouldReturnNoRecommendationsWhenNoPatternsMatch() {
        PatternRecommender recommender = new PatternRecommender();
        recommender.registerPattern("pattern-a", Set.of("fraud", "risk"), 0.7);

        List<PatternRecommender.Recommendation> recommendations = recommender
            .getRecommendations(Set.of("marketing", "engagement"), 5)
            .toCompletableFuture()
            .join();

        assertThat(recommendations).isEmpty();
    }
}