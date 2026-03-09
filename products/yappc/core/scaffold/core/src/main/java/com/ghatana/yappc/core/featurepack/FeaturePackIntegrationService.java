/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.featurepack;

import com.ghatana.yappc.core.featurepack.api.APIFeaturePacks;
import com.ghatana.yappc.core.featurepack.database.DatabaseFeaturePacks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cross-build-system feature pack integration service. Provides unified access to database and API
 * feature packs across Gradle, Maven, Cargo, and Make.
 *
 * <p>Week 7 Day 34: Feature pack integration with multi-build-system support.
 *
 * @doc.type class
 * @doc.purpose Cross-build-system feature pack integration service. Provides unified access to database and API
 * @doc.layer platform
 * @doc.pattern Service
 */
public class FeaturePackIntegrationService implements FeaturePackGenerator {

    private final Map<String, FeaturePackSpec> featurePacks;

    public FeaturePackIntegrationService() {
        this.featurePacks = new HashMap<>();
        loadBuiltinFeaturePacks();
    }

    private void loadBuiltinFeaturePacks() {
        // Load database feature packs
        DatabaseFeaturePacks.allDatabasePacks()
                .forEach(pack -> featurePacks.put(pack.name(), pack));

        // Load API feature packs
        APIFeaturePacks.allAPIPacks().forEach(pack -> featurePacks.put(pack.name(), pack));
    }

    @Override
    public GeneratedFeaturePack generateFeaturePack(FeaturePackSpec spec) {
        // Generate cross-build-system artifacts
        Map<String, String> buildSystemConfigurations = generateBuildSystemConfigurations(spec);
        Map<String, String> sourceFiles = generateSourceFiles(spec);
        Map<String, String> testFiles = generateTestFiles(spec);
        Map<String, String> configurationFiles = generateConfigurationFiles(spec);

        return GeneratedFeaturePack.builder()
                .spec(spec)
                .generatedFiles(List.of())
                .buildSystemConfigurations(buildSystemConfigurations)
                .sourceFiles(sourceFiles)
                .testFiles(testFiles)
                .configurationFiles(configurationFiles)
                .requiredDependencies(extractDependencies(spec, false))
                .recommendedDependencies(extractDependencies(spec, true))
                .metadata(
                        Map.of(
                                "crossLanguageSupport", spec.supportedLanguages(),
                                "buildSystemSupport", spec.supportedBuildSystems(),
                                "featureCount",
                                        spec.requiredFeatures().size()
                                                + spec.optionalFeatures().size()))
                .build();
    }

    private Map<String, String> generateBuildSystemConfigurations(FeaturePackSpec spec) {
        Map<String, String> configs = new HashMap<>();

        if (spec.supportedBuildSystems().contains("gradle")) {
            configs.put("build.gradle", generateGradleConfiguration(spec));
        }
        if (spec.supportedBuildSystems().contains("maven")) {
            configs.put("pom.xml", generateMavenConfiguration(spec));
        }
        if (spec.supportedBuildSystems().contains("cargo")) {
            configs.put("Cargo.toml", generateCargoConfiguration(spec));
        }
        if (spec.supportedBuildSystems().contains("make")) {
            configs.put("Makefile", generateMakeConfiguration(spec));
        }

        return configs;
    }

    private String generateGradleConfiguration(FeaturePackSpec spec) {
        StringBuilder gradle = new StringBuilder();
        gradle.append("// ").append(spec.name()).append(" feature pack dependencies\\n");
        gradle.append("dependencies {\\n");

        spec.dependencies().entrySet().stream()
                .filter(entry -> isGradleDependency(entry.getKey()))
                .forEach(
                        entry -> {
                            String[] parts = entry.getKey().split(":");
                            if (parts.length >= 2) {
                                gradle.append("    implementation '")
                                        .append(entry.getKey())
                                        .append(":")
                                        .append(entry.getValue())
                                        .append("'\\n");
                            }
                        });

        gradle.append("}\\n");
        return gradle.toString();
    }

    private String generateMavenConfiguration(FeaturePackSpec spec) {
        StringBuilder maven = new StringBuilder();
        maven.append("<!-- ").append(spec.name()).append(" feature pack dependencies -->\\n");
        maven.append("<dependencies>\\n");

        spec.dependencies().entrySet().stream()
                .filter(
                        entry ->
                                isGradleDependency(entry.getKey())) // Maven and Gradle share format
                .forEach(
                        entry -> {
                            String[] parts = entry.getKey().split(":");
                            if (parts.length >= 2) {
                                maven.append("    <dependency>\\n")
                                        .append("        <groupId>")
                                        .append(parts[0])
                                        .append("</groupId>\\n")
                                        .append("        <artifactId>")
                                        .append(parts[1])
                                        .append("</artifactId>\\n")
                                        .append("        <version>")
                                        .append(entry.getValue())
                                        .append("</version>\\n")
                                        .append("    </dependency>\\n");
                            }
                        });

        maven.append("</dependencies>\\n");
        return maven.toString();
    }

    private String generateCargoConfiguration(FeaturePackSpec spec) {
        StringBuilder cargo = new StringBuilder();
        cargo.append("# ").append(spec.name()).append(" feature pack dependencies\\n");
        cargo.append("[dependencies]\\n");

        spec.dependencies().entrySet().stream()
                .filter(entry -> isRustDependency(entry.getKey()))
                .forEach(
                        entry -> {
                            cargo.append(entry.getKey())
                                    .append(" = \"")
                                    .append(entry.getValue())
                                    .append("\"\\n");
                        });

        return cargo.toString();
    }

    private String generateMakeConfiguration(FeaturePackSpec spec) {
        StringBuilder make = new StringBuilder();
        make.append("# ").append(spec.name()).append(" feature pack system dependencies\\n");
        make.append("SYSTEM_DEPS = ");

        spec.dependencies().entrySet().stream()
                .filter(entry -> isCppDependency(entry.getKey()))
                .forEach(
                        entry -> {
                            make.append(entry.getKey()).append(" ");
                        });

        make.append("\\n");
        return make.toString();
    }

    private boolean isGradleDependency(String dependency) {
        return dependency.contains(":") && !dependency.endsWith("-dev");
    }

    private boolean isRustDependency(String dependency) {
        return !dependency.contains(":") && !dependency.endsWith("-dev");
    }

    private boolean isCppDependency(String dependency) {
        return dependency.endsWith("-dev") || dependency.contains("lib");
    }

    private Map<String, String> generateSourceFiles(FeaturePackSpec spec) {
        Map<String, String> sourceFiles = new HashMap<>();

        if (spec.supportedLanguages().contains("java")) {
            sourceFiles.put("java/FeaturePackConfig.java", generateJavaConfig(spec));
        }
        if (spec.supportedLanguages().contains("rust")) {
            sourceFiles.put("rust/config.rs", generateRustConfig(spec));
        }
        if (spec.supportedLanguages().contains("cpp")) {
            sourceFiles.put("cpp/config.hpp", generateCppConfig(spec));
        }

        return sourceFiles;
    }

    private String generateJavaConfig(FeaturePackSpec spec) {
        return """
            // Generated configuration for %s feature pack
            @Configuration
            public class %sConfig {
                // Feature pack configuration
            }
            """
                .formatted(spec.name(), capitalize(spec.name()));
    }

    private String generateRustConfig(FeaturePackSpec spec) {
        return """
            // Generated configuration for %s feature pack
            pub struct %sConfig {
                // Feature pack configuration
            }
            """
                .formatted(spec.name(), capitalize(spec.name()));
    }

    private String generateCppConfig(FeaturePackSpec spec) {
        return """
            // Generated configuration for %s feature pack
            class %sConfig {
                // Feature pack configuration
            };
            """
                .formatted(spec.name(), capitalize(spec.name()));
    }

    private Map<String, String> generateTestFiles(FeaturePackSpec spec) {
        return Map.of(
                "test/" + spec.name() + "_test.java", "// Generated tests for " + spec.name(),
                "test/" + spec.name() + "_test.rs", "// Generated tests for " + spec.name(),
                "test/" + spec.name() + "_test.cpp", "// Generated tests for " + spec.name());
    }

    private Map<String, String> generateConfigurationFiles(FeaturePackSpec spec) {
        Map<String, String> configs = new HashMap<>();

        // Generate property files for different formats
        configs.put("application.properties", generatePropertiesConfig(spec));
        configs.put("config.toml", generateTomlConfig(spec));
        configs.put("config.yaml", generateYamlConfig(spec));

        return configs;
    }

    private String generatePropertiesConfig(FeaturePackSpec spec) {
        StringBuilder props = new StringBuilder();
        props.append("# ").append(spec.name()).append(" feature pack configuration\\n");

        spec.configuration()
                .forEach(
                        (key, value) -> {
                            props.append(spec.name())
                                    .append(".")
                                    .append(key)
                                    .append("=")
                                    .append(value)
                                    .append("\\n");
                        });

        return props.toString();
    }

    private String generateTomlConfig(FeaturePackSpec spec) {
        StringBuilder toml = new StringBuilder();
        toml.append("# ").append(spec.name()).append(" feature pack configuration\\n");
        toml.append("[").append(spec.name()).append("]\\n");

        spec.configuration()
                .forEach(
                        (key, value) -> {
                            if (value instanceof String) {
                                toml.append(key).append(" = \"").append(value).append("\"\\n");
                            } else {
                                toml.append(key).append(" = ").append(value).append("\\n");
                            }
                        });

        return toml.toString();
    }

    private String generateYamlConfig(FeaturePackSpec spec) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# ").append(spec.name()).append(" feature pack configuration\\n");
        yaml.append(spec.name()).append(":\\n");

        spec.configuration()
                .forEach(
                        (key, value) -> {
                            yaml.append("  ").append(key).append(": ").append(value).append("\\n");
                        });

        return yaml.toString();
    }

    private List<String> extractDependencies(FeaturePackSpec spec, boolean optional) {
        return optional
                ? spec.devDependencies().keySet().stream().toList()
                : spec.dependencies().keySet().stream().toList();
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public FeaturePackValidationResult validateFeaturePack(FeaturePackSpec spec) {
        var errors = new java.util.ArrayList<String>();
        var warnings = new java.util.ArrayList<String>();
        var compatibilityIssues = new java.util.ArrayList<String>();

        if (spec.name() == null || spec.name().isBlank()) {
            errors.add("Feature pack name is required");
        }

        if (spec.supportedBuildSystems().isEmpty()) {
            errors.add("At least one build system must be supported");
        }

        if (spec.supportedLanguages().isEmpty()) {
            warnings.add("No supported languages specified");
        }

        double score = errors.isEmpty() ? 1.0 : 0.0;

        return FeaturePackValidationResult.builder()
                .isValid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .compatibilityIssues(compatibilityIssues)
                .compatibilityScore(score)
                .build();
    }

    @Override
    public FeaturePackImprovementSuggestions suggestImprovements(FeaturePackSpec spec) {
        var dependencyOptimizations = new java.util.ArrayList<String>();
        var securityEnhancements = new java.util.ArrayList<String>();
        var performanceOptimizations = new java.util.ArrayList<String>();

        if (spec.dependencies().isEmpty()) {
            dependencyOptimizations.add(
                    "Add required dependencies for " + spec.type().getDescription());
        }

        if (!spec.optionalFeatures().contains("security")) {
            securityEnhancements.add("Consider adding security features");
        }

        if (!spec.optionalFeatures().contains("monitoring")) {
            performanceOptimizations.add("Add observability and monitoring");
        }

        return FeaturePackImprovementSuggestions.builder()
                .dependencyOptimizations(dependencyOptimizations)
                .securityEnhancements(securityEnhancements)
                .performanceOptimizations(performanceOptimizations)
                .improvementScore(0.8)
                .build();
    }

    @Override
    public FeaturePackRecommendationResult recommendFeaturePacks(String projectPath) {
        // Simple analysis - in practice this would analyze the project structure
        return FeaturePackRecommendationResult.builder()
                .projectType("web-service")
                .detectedLanguages(List.of("java"))
                .detectedBuildSystems(List.of("gradle"))
                .recommendations(
                        List.of(
                                new FeaturePackRecommendationResult.FeaturePackRecommendation(
                                        "rest-api",
                                        FeaturePackType.API,
                                        "1.0.0",
                                        "Project structure suggests a REST API service",
                                        0.9,
                                        List.of("OpenAPI documentation", "Spring Boot integration"),
                                        List.of("Additional configuration required"))))
                .confidenceScore(0.8)
                .build();
    }

    /**
 * Get available feature pack by name. */
    public Optional<FeaturePackSpec> getFeaturePack(String name) {
        return Optional.ofNullable(featurePacks.get(name));
    }

    /**
 * Get all available feature packs. */
    public List<FeaturePackSpec> getAllFeaturePacks() {
        return featurePacks.values().stream().toList();
    }

    /**
 * Get feature packs by type. */
    public List<FeaturePackSpec> getFeaturePacksByType(FeaturePackType type) {
        return featurePacks.values().stream().filter(pack -> pack.type() == type).toList();
    }

    /**
 * Get feature packs compatible with build system. */
    public List<FeaturePackSpec> getFeaturePacksForBuildSystem(String buildSystem) {
        return featurePacks.values().stream()
                .filter(pack -> pack.supportedBuildSystems().contains(buildSystem))
                .toList();
    }
}
