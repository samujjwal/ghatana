package com.ghatana.yappc.core.make;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of Makefile generation containing the generated files and metadata.
 * @doc.type class
 * @doc.purpose Result of Makefile generation containing the generated files and metadata.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class GeneratedMakeProject {

    private final String makefile;
    private final Map<String, String> sourceFiles;
    private final Map<String, String> additionalFiles;
    private final List<MakeOptimization> optimizations;
    private final List<String> warnings;
    private final MakeValidationResult validation;
    private final Instant generatedAt;
    private final String generatorVersion;

    @JsonCreator
    public GeneratedMakeProject(
            @JsonProperty("makefile") String makefile,
            @JsonProperty("sourceFiles") Map<String, String> sourceFiles,
            @JsonProperty("additionalFiles") Map<String, String> additionalFiles,
            @JsonProperty("optimizations") List<MakeOptimization> optimizations,
            @JsonProperty("warnings") List<String> warnings,
            @JsonProperty("validation") MakeValidationResult validation,
            @JsonProperty("generatedAt") Instant generatedAt,
            @JsonProperty("generatorVersion") String generatorVersion) {
        this.makefile = Objects.requireNonNull(makefile, "makefile cannot be null");
        this.sourceFiles = sourceFiles != null ? Map.copyOf(sourceFiles) : Map.of();
        this.additionalFiles = additionalFiles != null ? Map.copyOf(additionalFiles) : Map.of();
        this.optimizations = optimizations != null ? List.copyOf(optimizations) : List.of();
        this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
        this.validation = validation;
        this.generatedAt = generatedAt != null ? generatedAt : Instant.now();
        this.generatorVersion = generatorVersion != null ? generatorVersion : "1.0.0";
    }

    public String getMakefile() {
        return makefile;
    }

    public Map<String, String> getSourceFiles() {
        return sourceFiles;
    }

    public Map<String, String> getAdditionalFiles() {
        return additionalFiles;
    }

    public List<MakeOptimization> getOptimizations() {
        return optimizations;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public MakeValidationResult getValidation() {
        return validation;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getGeneratorVersion() {
        return generatorVersion;
    }

    /**
 * Make-specific optimization applied during generation */
    public static class MakeOptimization {
        private final String type;
        private final String description;
        private final String rationale;
        private final String impact;

        @JsonCreator
        public MakeOptimization(
                @JsonProperty("type") String type,
                @JsonProperty("description") String description,
                @JsonProperty("rationale") String rationale,
                @JsonProperty("impact") String impact) {
            this.type = Objects.requireNonNull(type, "optimization type cannot be null");
            this.description =
                    Objects.requireNonNull(description, "optimization description cannot be null");
            this.rationale = rationale;
            this.impact = impact;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public String getRationale() {
            return rationale;
        }

        public String getImpact() {
            return impact;
        }
    }
}
