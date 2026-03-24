package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Input for the Prediction Agent.
 *
 * @doc.type record
 * @doc.purpose Prediction agent input
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PredictionInput(
        @NotNull String itemId,
        @Nullable String currentPhase,
        @NotNull List<HistoricalDataPoint> historicalData,
        @Nullable TeamMetrics teamMetrics,
        @Nullable List<SimilarItem> similarItems,
        int horizonDays
) {

    /**
     * Historical data point for time series analysis.
     */
    public record HistoricalDataPoint(
            @NotNull String timestamp,
            @NotNull String metric,
            double value,
            @Nullable Map<String, Object> attributes
    ) {}

    /**
     * Team metrics for capacity planning.
     */
    public record TeamMetrics(
            int teamSize,
            double averageVelocity,
            double capacityUtilization,
            int activeItems,
            int blockedItems
    ) {}

    /**
     * A similar historical item for comparison.
     */
    public record SimilarItem(
            @NotNull String itemId,
            double similarity,
            @NotNull String outcome,
            int actualDuration,
            @Nullable List<String> phases
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String itemId;
        private String currentPhase;
        private List<HistoricalDataPoint> historicalData = List.of();
        private TeamMetrics teamMetrics;
        private List<SimilarItem> similarItems;
        private int horizonDays = 30;

        public Builder itemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder currentPhase(String currentPhase) {
            this.currentPhase = currentPhase;
            return this;
        }

        public Builder historicalData(List<HistoricalDataPoint> historicalData) {
            this.historicalData = historicalData;
            return this;
        }

        public Builder teamMetrics(TeamMetrics teamMetrics) {
            this.teamMetrics = teamMetrics;
            return this;
        }

        public Builder similarItems(List<SimilarItem> similarItems) {
            this.similarItems = similarItems;
            return this;
        }

        public Builder horizonDays(int horizonDays) {
            this.horizonDays = horizonDays;
            return this;
        }

        public PredictionInput build() {
            return new PredictionInput(
                    itemId,
                    currentPhase,
                    historicalData,
                    teamMetrics,
                    similarItems,
                    horizonDays
            );
        }
    }
}
