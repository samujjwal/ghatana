package com.ghatana.yappc.core.cargo;

import io.activej.promise.Promise;

/**
 * Service interface for AI-powered Cargo.toml generation. Provides intelligent Rust project
 * configuration with best practices and optimizations.
 *
 * @doc.type interface
 * @doc.purpose Service interface for AI-powered Cargo.toml generation. Provides intelligent Rust project
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface CargoBuildGenerator {

    /**
     * Generates a Cargo.toml file based on the provided specification.
     *
     * @param spec The Cargo build specification containing project details
     * @return Promise containing the generated Cargo project configuration
     */
    Promise<GeneratedCargoProject> generateCargoToml(CargoBuildSpec spec);

    /**
     * Validates a Cargo build specification for completeness and best practices.
     *
     * @param spec The Cargo build specification to validate
     * @return Promise containing validation results and recommendations
     */
    Promise<CargoValidationResult> validateSpec(CargoBuildSpec spec);

    /**
     * Suggests improvements for an existing Cargo build specification. Uses AI to recommend
     * dependency updates, feature optimizations, and best practices.
     *
     * @param spec The current Cargo build specification
     * @return Promise containing improvement suggestions
     */
    Promise<CargoImprovementSuggestions> suggestImprovements(CargoBuildSpec spec);

    /**
     * Generates Rust project scaffolding including src/main.rs, src/lib.rs, and tests.
     *
     * @param spec The Cargo build specification
     * @return Promise containing generated Rust source files
     */
    Promise<RustProjectScaffold> generateProjectScaffold(CargoBuildSpec spec);

    /**
     * Analyzes existing Rust code and suggests Cargo configuration optimizations.
     *
     * @param projectPath Path to the Rust project directory
     * @return Promise containing analysis results and suggestions
     */
    Promise<CargoAnalysisResult> analyzeProject(String projectPath);
}
