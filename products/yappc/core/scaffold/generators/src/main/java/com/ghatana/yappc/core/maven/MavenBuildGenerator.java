package com.ghatana.yappc.core.maven;

import io.activej.promise.Promise;

/**
 * Service interface for AI-powered Maven POM generation. Provides intelligent Maven project
 * configuration with best practices and optimizations.
 *
 * @doc.type interface
 * @doc.purpose Service interface for AI-powered Maven POM generation. Provides intelligent Maven project
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface MavenBuildGenerator {

    /**
     * Generates a Maven POM.xml file based on the provided specification.
     *
     * @param spec The Maven build specification containing project details
     * @return Promise containing the generated Maven build configuration
     */
    Promise<GeneratedMavenProject> generatePom(MavenBuildSpec spec);

    /**
     * Validates a Maven build specification for completeness and best practices.
     *
     * @param spec The Maven build specification to validate
     * @return Promise containing validation results and recommendations
     */
    Promise<MavenValidationResult> validateSpec(MavenBuildSpec spec);

    /**
     * Suggests improvements for an existing Maven build specification. Uses AI to recommend
     * dependency updates, plugin optimizations, and best practices.
     *
     * @param spec The current Maven build specification
     * @return Promise containing improvement suggestions
     */
    Promise<MavenImprovementSuggestions> suggestImprovements(MavenBuildSpec spec);

    /**
     * Converts a Gradle build specification to Maven equivalent. Useful for migrating projects from
     * Gradle to Maven.
     *
     * @param gradleSpec The Gradle build specification to convert
     * @return Promise containing the equivalent Maven specification
     */
    Promise<MavenBuildSpec> convertFromGradle(Object gradleSpec);
}
