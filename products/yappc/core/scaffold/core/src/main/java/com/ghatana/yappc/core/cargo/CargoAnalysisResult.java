package com.ghatana.yappc.core.cargo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Result of Cargo project analysis with insights and recommendations.
 * @doc.type class
 * @doc.purpose Result of Cargo project analysis with insights and recommendations.
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class CargoAnalysisResult {

    private final ProjectMetrics metrics;
    private final List<String> detectedFeatures;
    private final Map<String, String> dependencyAnalysis;
    private final List<String> suggestions;
    private final double healthScore;

    @JsonCreator
    public CargoAnalysisResult(
            @JsonProperty("metrics") ProjectMetrics metrics,
            @JsonProperty("detectedFeatures") List<String> detectedFeatures,
            @JsonProperty("dependencyAnalysis") Map<String, String> dependencyAnalysis,
            @JsonProperty("suggestions") List<String> suggestions,
            @JsonProperty("healthScore") double healthScore) {
        this.metrics = metrics;
        this.detectedFeatures =
                detectedFeatures != null ? List.copyOf(detectedFeatures) : List.of();
        this.dependencyAnalysis =
                dependencyAnalysis != null ? Map.copyOf(dependencyAnalysis) : Map.of();
        this.suggestions = suggestions != null ? List.copyOf(suggestions) : List.of();
        this.healthScore = healthScore;
    }

    public ProjectMetrics getMetrics() {
        return metrics;
    }

    public List<String> getDetectedFeatures() {
        return detectedFeatures;
    }

    public Map<String, String> getDependencyAnalysis() {
        return dependencyAnalysis;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public double getHealthScore() {
        return healthScore;
    }

    /**
 * Project metrics and statistics */
    public static class ProjectMetrics {
        private final int linesOfCode;
        private final int numberOfDependencies;
        private final int numberOfTests;
        private final int numberOfBinaries;
        private final double testCoverage;

        @JsonCreator
        public ProjectMetrics(
                @JsonProperty("linesOfCode") int linesOfCode,
                @JsonProperty("numberOfDependencies") int numberOfDependencies,
                @JsonProperty("numberOfTests") int numberOfTests,
                @JsonProperty("numberOfBinaries") int numberOfBinaries,
                @JsonProperty("testCoverage") double testCoverage) {
            this.linesOfCode = linesOfCode;
            this.numberOfDependencies = numberOfDependencies;
            this.numberOfTests = numberOfTests;
            this.numberOfBinaries = numberOfBinaries;
            this.testCoverage = testCoverage;
        }

        public int getLinesOfCode() {
            return linesOfCode;
        }

        public int getNumberOfDependencies() {
            return numberOfDependencies;
        }

        public int getNumberOfTests() {
            return numberOfTests;
        }

        public int getNumberOfBinaries() {
            return numberOfBinaries;
        }

        public double getTestCoverage() {
            return testCoverage;
        }
    }
}
