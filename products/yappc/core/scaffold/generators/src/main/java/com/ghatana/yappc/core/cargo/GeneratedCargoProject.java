package com.ghatana.yappc.core.cargo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of Cargo.toml generation containing the generated files and metadata.
 * @doc.type class
 * @doc.purpose Result of Cargo.toml generation containing the generated files and metadata.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class GeneratedCargoProject {

    private final String cargoToml;
    private final Map<String, String> sourceFiles;
    private final Map<String, String> additionalFiles;
    private final List<CargoOptimization> optimizations;
    private final List<String> warnings;
    private final CargoValidationResult validation;
    private final Instant generatedAt;
    private final String generatorVersion;

    @JsonCreator
    public GeneratedCargoProject(
            @JsonProperty("cargoToml") String cargoToml,
            @JsonProperty("sourceFiles") Map<String, String> sourceFiles,
            @JsonProperty("additionalFiles") Map<String, String> additionalFiles,
            @JsonProperty("optimizations") List<CargoOptimization> optimizations,
            @JsonProperty("warnings") List<String> warnings,
            @JsonProperty("validation") CargoValidationResult validation,
            @JsonProperty("generatedAt") Instant generatedAt,
            @JsonProperty("generatorVersion") String generatorVersion) {
        this.cargoToml = Objects.requireNonNull(cargoToml, "cargoToml cannot be null");
        this.sourceFiles = sourceFiles != null ? Map.copyOf(sourceFiles) : Map.of();
        this.additionalFiles = additionalFiles != null ? Map.copyOf(additionalFiles) : Map.of();
        this.optimizations = optimizations != null ? List.copyOf(optimizations) : List.of();
        this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
        this.validation = validation;
        this.generatedAt = generatedAt != null ? generatedAt : Instant.now();
        this.generatorVersion = generatorVersion != null ? generatorVersion : "1.0.0";
    }

    public String getCargoToml() {
        return cargoToml;
    }

    public Map<String, String> getSourceFiles() {
        return sourceFiles;
    }

    public Map<String, String> getAdditionalFiles() {
        return additionalFiles;
    }

    public List<CargoOptimization> getOptimizations() {
        return optimizations;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public CargoValidationResult getValidation() {
        return validation;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getGeneratorVersion() {
        return generatorVersion;
    }

    /**
 * Cargo-specific optimization applied during generation */
    public static class CargoOptimization {
        private final String type;
        private final String description;
        private final String rationale;
        private final String impact;

        @JsonCreator
        public CargoOptimization(
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
