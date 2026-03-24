package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Output from the Recommendation Agent.
 *
 * @doc.type record
 * @doc.purpose Recommendation output
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record RecommendationOutput(
        @NotNull List<Recommendation> recommendations,
        @NotNull RecommendationMetadata metadata
) {
    /**
     * A single recommendation.
     */
    public record Recommendation(
            @NotNull String id,
            @NotNull String value,
            @NotNull String label,
            @Nullable String description,
            double confidence,
            double relevanceScore,
            @NotNull RecommendationReason reason,
            @Nullable String iconUrl,
            @Nullable Object metadata
    ) {}

    /**
     * Explanation for why this was recommended.
     */
    public record RecommendationReason(
            @NotNull String type,
            @NotNull String explanation,
            @Nullable List<String> factors
    ) {}

    /**
     * Metadata about the recommendation generation.
     */
    public record RecommendationMetadata(
            @NotNull String algorithm,
            long processingTimeMs,
            int candidatesEvaluated,
            @Nullable String modelVersion
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<Recommendation> recommendations = List.of();
        private RecommendationMetadata metadata;

        public Builder recommendations(List<Recommendation> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public Builder metadata(RecommendationMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public RecommendationOutput build() {
            if (metadata == null) {
                throw new IllegalStateException("metadata is required");
            }
            return new RecommendationOutput(recommendations, metadata);
        }
    }
}
