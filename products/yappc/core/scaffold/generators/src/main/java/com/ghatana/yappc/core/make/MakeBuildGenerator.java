package com.ghatana.yappc.core.make;

import io.activej.promise.Promise;

/**
 * Service interface for AI-powered Makefile generation. Provides intelligent C/C++ project
 * configuration with best practices and cross-platform support.
 *
 * @doc.type interface
 * @doc.purpose Service interface for AI-powered Makefile generation. Provides intelligent C/C++ project
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface MakeBuildGenerator {

    /**
     * Generates a Makefile based on the provided specification.
     *
     * @param spec The Make build specification containing project details
     * @return Promise containing the generated Make project configuration
     */
    Promise<GeneratedMakeProject> generateMakefile(MakeBuildSpec spec);

    /**
     * Validates a Make build specification for completeness and best practices.
     *
     * @param spec The Make build specification to validate
     * @return Promise containing validation results and recommendations
     */
    Promise<MakeValidationResult> validateSpec(MakeBuildSpec spec);

    /**
     * Suggests improvements for an existing Make build specification. Uses AI to recommend compiler
     * optimizations, build targets, and best practices.
     *
     * @param spec The current Make build specification
     * @return Promise containing improvement suggestions
     */
    Promise<MakeImprovementSuggestions> suggestImprovements(MakeBuildSpec spec);

    /**
     * Generates C/C++ project scaffolding including source files and headers.
     *
     * @param spec The Make build specification
     * @return Promise containing generated C/C++ source files
     */
    Promise<CProjectScaffold> generateProjectScaffold(MakeBuildSpec spec);

    /**
     * Analyzes existing C/C++ code and suggests Makefile configuration optimizations.
     *
     * @param projectPath Path to the C/C++ project directory
     * @return Promise containing analysis results and suggestions
     */
    Promise<MakeAnalysisResult> analyzeProject(String projectPath);

    /**
     * Generates cross-platform Makefile with platform-specific configurations.
     *
     * @param spec The Make build specification with platform configurations
     * @return Promise containing cross-platform Makefile
     */
    Promise<GeneratedMakeProject> generateCrossPlatformMakefile(MakeBuildSpec spec);
}
