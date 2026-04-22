package com.ghatana.aep.learning.mining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PatternRecommender [GH-90000]")
class PatternRecommenderTest {

    @Test
    void shouldRankMoreRelevantPatternsHigher() { // GH-90000
        PatternRecommender recommender = new PatternRecommender(); // GH-90000
        recommender.registerPattern("pattern-exact", Set.of("login", "purchase"), 0.9); // GH-90000
        recommender.registerPattern("pattern-partial", Set.of("login", "search"), 0.9); // GH-90000

        List<PatternRecommender.Recommendation> recommendations = recommender
            .getRecommendations(Set.of("login", "purchase"), 5) // GH-90000
            .toCompletableFuture() // GH-90000
            .join(); // GH-90000

        assertThat(recommendations).hasSize(2); // GH-90000
        assertThat(recommendations.get(0).getPatternId()).isEqualTo("pattern-exact [GH-90000]");
        assertThat(recommendations.get(0).getScore()).isGreaterThan(recommendations.get(1).getScore()); // GH-90000
    }

    @Test
    void shouldBoostRecommendationsWithPositiveFeedback() { // GH-90000
        PatternRecommender recommender = new PatternRecommender(); // GH-90000
        recommender.registerPattern("pattern-a", Set.of("checkout", "payment"), 0.8); // GH-90000
        recommender.registerPattern("pattern-b", Set.of("checkout", "payment"), 0.8); // GH-90000

        recommender.recordFeedback("pattern-b", true, 0.95); // GH-90000
        recommender.recordFeedback("pattern-b", true, 0.90); // GH-90000
        recommender.recordFeedback("pattern-a", false, 0.0); // GH-90000
        recommender.recordFeedback("pattern-a", false, 0.0); // GH-90000

        List<PatternRecommender.Recommendation> recommendations = recommender
            .getRecommendations(Set.of("checkout", "payment"), 5) // GH-90000
            .toCompletableFuture() // GH-90000
            .join(); // GH-90000

        assertThat(recommendations).hasSize(2); // GH-90000
        assertThat(recommendations.get(0).getPatternId()).isEqualTo("pattern-b [GH-90000]");
        assertThat(recommendations.get(0).getScore()).isGreaterThan(recommendations.get(1).getScore()); // GH-90000
    }

    @Test
    void shouldReturnNoRecommendationsWhenNoPatternsMatch() { // GH-90000
        PatternRecommender recommender = new PatternRecommender(); // GH-90000
        recommender.registerPattern("pattern-a", Set.of("fraud", "risk"), 0.7); // GH-90000

        List<PatternRecommender.Recommendation> recommendations = recommender
            .getRecommendations(Set.of("marketing", "engagement"), 5) // GH-90000
            .toCompletableFuture() // GH-90000
            .join(); // GH-90000

        assertThat(recommendations).isEmpty(); // GH-90000
    }
}