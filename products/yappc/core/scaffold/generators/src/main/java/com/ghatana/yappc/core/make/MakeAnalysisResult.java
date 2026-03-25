package com.ghatana.yappc.core.make;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Result of Make project analysis with insights and recommendations.
 * @doc.type class
 * @doc.purpose Result of Make project analysis with insights and recommendations.
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class MakeAnalysisResult {

    private final ProjectMetrics metrics;
    private final List<String> detectedFeatures;
    private final Map<String, String> dependencyAnalysis;
    private final List<String> suggestions;
    private final double healthScore;

    @JsonCreator
    public MakeAnalysisResult(
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
        private final int numberOfSourceFiles;
        private final int numberOfHeaderFiles;
        private final int numberOfTests;
        private final double testCoverage;

        @JsonCreator
        public ProjectMetrics(
                @JsonProperty("linesOfCode") int linesOfCode,
                @JsonProperty("numberOfSourceFiles") int numberOfSourceFiles,
                @JsonProperty("numberOfHeaderFiles") int numberOfHeaderFiles,
                @JsonProperty("numberOfTests") int numberOfTests,
                @JsonProperty("testCoverage") double testCoverage) {
            this.linesOfCode = linesOfCode;
            this.numberOfSourceFiles = numberOfSourceFiles;
            this.numberOfHeaderFiles = numberOfHeaderFiles;
            this.numberOfTests = numberOfTests;
            this.testCoverage = testCoverage;
        }

        public int getLinesOfCode() {
            return linesOfCode;
        }

        public int getNumberOfSourceFiles() {
            return numberOfSourceFiles;
        }

        public int getNumberOfHeaderFiles() {
            return numberOfHeaderFiles;
        }

        public int getNumberOfTests() {
            return numberOfTests;
        }

        public double getTestCoverage() {
            return testCoverage;
        }
    }
}
