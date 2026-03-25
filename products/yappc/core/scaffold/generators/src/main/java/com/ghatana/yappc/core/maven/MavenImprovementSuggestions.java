package com.ghatana.yappc.core.maven;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * AI-generated improvement suggestions for Maven builds.
 * @doc.type class
 * @doc.purpose AI-generated improvement suggestions for Maven builds.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class MavenImprovementSuggestions {

    private final List<DependencyUpgrade> dependencyUpgrades;
    private final List<PluginRecommendation> pluginRecommendations;
    private final List<PropertyOptimization> propertyOptimizations;
    private final List<ProfileSuggestion> profileSuggestions;
    private final List<String> generalRecommendations;
    private final double confidenceScore;

    @JsonCreator
    public MavenImprovementSuggestions(
            @JsonProperty("dependencyUpgrades") List<DependencyUpgrade> dependencyUpgrades,
            @JsonProperty("pluginRecommendations") List<PluginRecommendation> pluginRecommendations,
            @JsonProperty("propertyOptimizations") List<PropertyOptimization> propertyOptimizations,
            @JsonProperty("profileSuggestions") List<ProfileSuggestion> profileSuggestions,
            @JsonProperty("generalRecommendations") List<String> generalRecommendations,
            @JsonProperty("confidenceScore") double confidenceScore) {
        this.dependencyUpgrades =
                dependencyUpgrades != null ? List.copyOf(dependencyUpgrades) : List.of();
        this.pluginRecommendations =
                pluginRecommendations != null ? List.copyOf(pluginRecommendations) : List.of();
        this.propertyOptimizations =
                propertyOptimizations != null ? List.copyOf(propertyOptimizations) : List.of();
        this.profileSuggestions =
                profileSuggestions != null ? List.copyOf(profileSuggestions) : List.of();
        this.generalRecommendations =
                generalRecommendations != null ? List.copyOf(generalRecommendations) : List.of();
        this.confidenceScore = confidenceScore;
    }

    public List<DependencyUpgrade> getDependencyUpgrades() {
        return dependencyUpgrades;
    }

    public List<PluginRecommendation> getPluginRecommendations() {
        return pluginRecommendations;
    }

    public List<PropertyOptimization> getPropertyOptimizations() {
        return propertyOptimizations;
    }

    public List<ProfileSuggestion> getProfileSuggestions() {
        return profileSuggestions;
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
        private final String groupId;
        private final String artifactId;
        private final String currentVersion;
        private final String recommendedVersion;
        private final String reason;
        private final String riskLevel;

        @JsonCreator
        public DependencyUpgrade(
                @JsonProperty("groupId") String groupId,
                @JsonProperty("artifactId") String artifactId,
                @JsonProperty("currentVersion") String currentVersion,
                @JsonProperty("recommendedVersion") String recommendedVersion,
                @JsonProperty("reason") String reason,
                @JsonProperty("riskLevel") String riskLevel) {
            this.groupId = Objects.requireNonNull(groupId, "groupId cannot be null");
            this.artifactId = Objects.requireNonNull(artifactId, "artifactId cannot be null");
            this.currentVersion = currentVersion;
            this.recommendedVersion =
                    Objects.requireNonNull(recommendedVersion, "recommendedVersion cannot be null");
            this.reason = reason;
            this.riskLevel = riskLevel != null ? riskLevel : "MEDIUM";
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
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
 * Plugin recommendation */
    public static class PluginRecommendation {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String purpose;
        private final String configuration;
        private final String priority;

        @JsonCreator
        public PluginRecommendation(
                @JsonProperty("groupId") String groupId,
                @JsonProperty("artifactId") String artifactId,
                @JsonProperty("version") String version,
                @JsonProperty("purpose") String purpose,
                @JsonProperty("configuration") String configuration,
                @JsonProperty("priority") String priority) {
            this.groupId = groupId;
            this.artifactId =
                    Objects.requireNonNull(artifactId, "plugin artifactId cannot be null");
            this.version = version;
            this.purpose = purpose;
            this.configuration = configuration;
            this.priority = priority != null ? priority : "MEDIUM";
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public String getPurpose() {
            return purpose;
        }

        public String getConfiguration() {
            return configuration;
        }

        public String getPriority() {
            return priority;
        }
    }

    /**
 * Property optimization suggestion */
    public static class PropertyOptimization {
        private final String property;
        private final String currentValue;
        private final String recommendedValue;
        private final String reason;

        @JsonCreator
        public PropertyOptimization(
                @JsonProperty("property") String property,
                @JsonProperty("currentValue") String currentValue,
                @JsonProperty("recommendedValue") String recommendedValue,
                @JsonProperty("reason") String reason) {
            this.property = Objects.requireNonNull(property, "property name cannot be null");
            this.currentValue = currentValue;
            this.recommendedValue =
                    Objects.requireNonNull(recommendedValue, "recommendedValue cannot be null");
            this.reason = reason;
        }

        public String getProperty() {
            return property;
        }

        public String getCurrentValue() {
            return currentValue;
        }

        public String getRecommendedValue() {
            return recommendedValue;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
 * Profile configuration suggestion */
    public static class ProfileSuggestion {
        private final String profileId;
        private final String purpose;
        private final String activation;
        private final String description;

        @JsonCreator
        public ProfileSuggestion(
                @JsonProperty("profileId") String profileId,
                @JsonProperty("purpose") String purpose,
                @JsonProperty("activation") String activation,
                @JsonProperty("description") String description) {
            this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
            this.purpose = purpose;
            this.activation = activation;
            this.description = description;
        }

        public String getProfileId() {
            return profileId;
        }

        public String getPurpose() {
            return purpose;
        }

        public String getActivation() {
            return activation;
        }

        public String getDescription() {
            return description;
        }
    }
}
