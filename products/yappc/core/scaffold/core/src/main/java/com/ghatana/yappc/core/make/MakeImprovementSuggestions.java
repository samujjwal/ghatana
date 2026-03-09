package com.ghatana.yappc.core.make;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * AI-generated improvement suggestions for Make builds.
 * @doc.type class
 * @doc.purpose AI-generated improvement suggestions for Make builds.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class MakeImprovementSuggestions {

    private final List<CompilerOptimization> compilerOptimizations;
    private final List<TargetSuggestion> targetSuggestions;
    private final List<DependencySuggestion> dependencySuggestions;
    private final List<CrossPlatformSuggestion> crossPlatformSuggestions;
    private final List<String> generalRecommendations;
    private final double confidenceScore;

    @JsonCreator
    public MakeImprovementSuggestions(
            @JsonProperty("compilerOptimizations") List<CompilerOptimization> compilerOptimizations,
            @JsonProperty("targetSuggestions") List<TargetSuggestion> targetSuggestions,
            @JsonProperty("dependencySuggestions") List<DependencySuggestion> dependencySuggestions,
            @JsonProperty("crossPlatformSuggestions")
                    List<CrossPlatformSuggestion> crossPlatformSuggestions,
            @JsonProperty("generalRecommendations") List<String> generalRecommendations,
            @JsonProperty("confidenceScore") double confidenceScore) {
        this.compilerOptimizations =
                compilerOptimizations != null ? List.copyOf(compilerOptimizations) : List.of();
        this.targetSuggestions =
                targetSuggestions != null ? List.copyOf(targetSuggestions) : List.of();
        this.dependencySuggestions =
                dependencySuggestions != null ? List.copyOf(dependencySuggestions) : List.of();
        this.crossPlatformSuggestions =
                crossPlatformSuggestions != null
                        ? List.copyOf(crossPlatformSuggestions)
                        : List.of();
        this.generalRecommendations =
                generalRecommendations != null ? List.copyOf(generalRecommendations) : List.of();
        this.confidenceScore = confidenceScore;
    }

    public List<CompilerOptimization> getCompilerOptimizations() {
        return compilerOptimizations;
    }

    public List<TargetSuggestion> getTargetSuggestions() {
        return targetSuggestions;
    }

    public List<DependencySuggestion> getDependencySuggestions() {
        return dependencySuggestions;
    }

    public List<CrossPlatformSuggestion> getCrossPlatformSuggestions() {
        return crossPlatformSuggestions;
    }

    public List<String> getGeneralRecommendations() {
        return generalRecommendations;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    /**
 * Compiler optimization suggestion */
    public static class CompilerOptimization {
        private final String flag;
        private final String description;
        private final String impact;
        private final String compatibility;

        @JsonCreator
        public CompilerOptimization(
                @JsonProperty("flag") String flag,
                @JsonProperty("description") String description,
                @JsonProperty("impact") String impact,
                @JsonProperty("compatibility") String compatibility) {
            this.flag = Objects.requireNonNull(flag, "compiler flag cannot be null");
            this.description = Objects.requireNonNull(description, "description cannot be null");
            this.impact = impact;
            this.compatibility = compatibility;
        }

        public String getFlag() {
            return flag;
        }

        public String getDescription() {
            return description;
        }

        public String getImpact() {
            return impact;
        }

        public String getCompatibility() {
            return compatibility;
        }
    }

    /**
 * Build target suggestion */
    public static class TargetSuggestion {
        private final String name;
        private final String purpose;
        private final List<String> commands;
        private final String priority;

        @JsonCreator
        public TargetSuggestion(
                @JsonProperty("name") String name,
                @JsonProperty("purpose") String purpose,
                @JsonProperty("commands") List<String> commands,
                @JsonProperty("priority") String priority) {
            this.name = Objects.requireNonNull(name, "target name cannot be null");
            this.purpose = Objects.requireNonNull(purpose, "purpose cannot be null");
            this.commands = commands != null ? List.copyOf(commands) : List.of();
            this.priority = priority != null ? priority : "MEDIUM";
        }

        public String getName() {
            return name;
        }

        public String getPurpose() {
            return purpose;
        }

        public List<String> getCommands() {
            return commands;
        }

        public String getPriority() {
            return priority;
        }
    }

    /**
 * Dependency management suggestion */
    public static class DependencySuggestion {
        private final String library;
        private final String reason;
        private final String installMethod;
        private final String linkFlags;

        @JsonCreator
        public DependencySuggestion(
                @JsonProperty("library") String library,
                @JsonProperty("reason") String reason,
                @JsonProperty("installMethod") String installMethod,
                @JsonProperty("linkFlags") String linkFlags) {
            this.library = Objects.requireNonNull(library, "library name cannot be null");
            this.reason = reason;
            this.installMethod = installMethod;
            this.linkFlags = linkFlags;
        }

        public String getLibrary() {
            return library;
        }

        public String getReason() {
            return reason;
        }

        public String getInstallMethod() {
            return installMethod;
        }

        public String getLinkFlags() {
            return linkFlags;
        }
    }

    /**
 * Cross-platform compatibility suggestion */
    public static class CrossPlatformSuggestion {
        private final String platform;
        private final String modification;
        private final String reason;
        private final String implementation;

        @JsonCreator
        public CrossPlatformSuggestion(
                @JsonProperty("platform") String platform,
                @JsonProperty("modification") String modification,
                @JsonProperty("reason") String reason,
                @JsonProperty("implementation") String implementation) {
            this.platform = Objects.requireNonNull(platform, "platform cannot be null");
            this.modification = Objects.requireNonNull(modification, "modification cannot be null");
            this.reason = reason;
            this.implementation = implementation;
        }

        public String getPlatform() {
            return platform;
        }

        public String getModification() {
            return modification;
        }

        public String getReason() {
            return reason;
        }

        public String getImplementation() {
            return implementation;
        }
    }
}
