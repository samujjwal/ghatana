package com.ghatana.yappc.core.cargo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * AI-generated improvement suggestions for Cargo builds.
 * @doc.type class
 * @doc.purpose AI-generated improvement suggestions for Cargo builds.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class CargoImprovementSuggestions {

    private final List<DependencyUpgrade> dependencyUpgrades;
    private final List<FeatureSuggestion> featureSuggestions;
    private final List<OptimizationSuggestion> optimizations;
    private final List<SecuritySuggestion> securitySuggestions;
    private final List<String> generalRecommendations;
    private final double confidenceScore;

    @JsonCreator
    public CargoImprovementSuggestions(
            @JsonProperty("dependencyUpgrades") List<DependencyUpgrade> dependencyUpgrades,
            @JsonProperty("featureSuggestions") List<FeatureSuggestion> featureSuggestions,
            @JsonProperty("optimizations") List<OptimizationSuggestion> optimizations,
            @JsonProperty("securitySuggestions") List<SecuritySuggestion> securitySuggestions,
            @JsonProperty("generalRecommendations") List<String> generalRecommendations,
            @JsonProperty("confidenceScore") double confidenceScore) {
        this.dependencyUpgrades =
                dependencyUpgrades != null ? List.copyOf(dependencyUpgrades) : List.of();
        this.featureSuggestions =
                featureSuggestions != null ? List.copyOf(featureSuggestions) : List.of();
        this.optimizations = optimizations != null ? List.copyOf(optimizations) : List.of();
        this.securitySuggestions =
                securitySuggestions != null ? List.copyOf(securitySuggestions) : List.of();
        this.generalRecommendations =
                generalRecommendations != null ? List.copyOf(generalRecommendations) : List.of();
        this.confidenceScore = confidenceScore;
    }

    public List<DependencyUpgrade> getDependencyUpgrades() {
        return dependencyUpgrades;
    }

    public List<FeatureSuggestion> getFeatureSuggestions() {
        return featureSuggestions;
    }

    public List<OptimizationSuggestion> getOptimizations() {
        return optimizations;
    }

    public List<SecuritySuggestion> getSecuritySuggestions() {
        return securitySuggestions;
    }

    public List<String> getGeneralRecommendations() {
        return generalRecommendations;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    /**
 * Dependency upgrade recommendation */
    public static class DependencyUpgrade {
        private final String name;
        private final String currentVersion;
        private final String recommendedVersion;
        private final String reason;
        private final String riskLevel;

        @JsonCreator
        public DependencyUpgrade(
                @JsonProperty("name") String name,
                @JsonProperty("currentVersion") String currentVersion,
                @JsonProperty("recommendedVersion") String recommendedVersion,
                @JsonProperty("reason") String reason,
                @JsonProperty("riskLevel") String riskLevel) {
            this.name = Objects.requireNonNull(name, "dependency name cannot be null");
            this.currentVersion = currentVersion;
            this.recommendedVersion =
                    Objects.requireNonNull(recommendedVersion, "recommendedVersion cannot be null");
            this.reason = reason;
            this.riskLevel = riskLevel != null ? riskLevel : "MEDIUM";
        }

        public String getName() {
            return name;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getRecommendedVersion() {
            return recommendedVersion;
        }

        public String getReason() {
            return reason;
        }

        public String getRiskLevel() {
            return riskLevel;
        }
    }

    /**
 * Feature configuration suggestion */
    public static class FeatureSuggestion {
        private final String featureName;
        private final String action; // "add", "remove", "modify"
        private final List<String> dependencies;
        private final String reason;

        @JsonCreator
        public FeatureSuggestion(
                @JsonProperty("featureName") String featureName,
                @JsonProperty("action") String action,
                @JsonProperty("dependencies") List<String> dependencies,
                @JsonProperty("reason") String reason) {
            this.featureName = Objects.requireNonNull(featureName, "featureName cannot be null");
            this.action = Objects.requireNonNull(action, "action cannot be null");
            this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
            this.reason = reason;
        }

        public String getFeatureName() {
            return featureName;
        }

        public String getAction() {
            return action;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
 * Performance/build optimization suggestion */
    public static class OptimizationSuggestion {
        private final String type;
        private final String description;
        private final String impact;
        private final String implementation;

        @JsonCreator
        public OptimizationSuggestion(
                @JsonProperty("type") String type,
                @JsonProperty("description") String description,
                @JsonProperty("impact") String impact,
                @JsonProperty("implementation") String implementation) {
            this.type = Objects.requireNonNull(type, "optimization type cannot be null");
            this.description = Objects.requireNonNull(description, "description cannot be null");
            this.impact = impact;
            this.implementation = implementation;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public String getImpact() {
            return impact;
        }

        public String getImplementation() {
            return implementation;
        }
    }

    /**
 * Security-related suggestion */
    public static class SecuritySuggestion {
        private final String category;
        private final String description;
        private final String severity;
        private final String mitigation;

        @JsonCreator
        public SecuritySuggestion(
                @JsonProperty("category") String category,
                @JsonProperty("description") String description,
                @JsonProperty("severity") String severity,
                @JsonProperty("mitigation") String mitigation) {
            this.category = Objects.requireNonNull(category, "category cannot be null");
            this.description = Objects.requireNonNull(description, "description cannot be null");
            this.severity = severity != null ? severity : "MEDIUM";
            this.mitigation = mitigation;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }

        public String getSeverity() {
            return severity;
        }

        public String getMitigation() {
            return mitigation;
        }
    }
}
