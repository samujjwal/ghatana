package com.ghatana.yappc.core.cargo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template-based Cargo.toml generator with intelligent defaults and Rust best practices. Generates
 * production-ready Cargo.toml files with comprehensive dependency management.
 *
 * @doc.type class
 * @doc.purpose Template-based Cargo.toml generator with intelligent defaults and Rust best practices. Generates
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class CargoTomlGenerator implements CargoBuildGenerator {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private static final Logger logger = LoggerFactory.getLogger(CargoTomlGenerator.class);

    private static final String DEFAULT_EDITION = "2021";
    private static final String DEFAULT_LICENSE = "MIT OR Apache-2.0";

    @Override
    public Promise<GeneratedCargoProject> generateCargoToml(CargoBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    try {
                        logger.info("Generating Cargo.toml for project: {}", spec.getName());

                        String cargoToml = generateCargoTomlContent(spec);
                        Map<String, String> sourceFiles = generateSourceFiles(spec);
                        Map<String, String> additionalFiles = generateAdditionalFiles(spec);
                        List<GeneratedCargoProject.CargoOptimization> optimizations =
                                generateOptimizations(spec);
                        List<String> warnings = generateWarnings(spec);
                        CargoValidationResult validation = performValidation(spec);

                        return new GeneratedCargoProject(
                                cargoToml,
                                sourceFiles,
                                additionalFiles,
                                optimizations,
                                warnings,
                                validation,
                                Instant.now(),
                                "1.0.0");
                    } catch (Exception e) {
                        logger.error("Error generating Cargo.toml", e);
                        throw new RuntimeException("Failed to generate Cargo.toml", e);
                    }
                });
    }

    @Override
    public Promise<CargoValidationResult> validateSpec(CargoBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> performValidation(spec));
    }

    @Override
    public Promise<CargoImprovementSuggestions> suggestImprovements(CargoBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    List<CargoImprovementSuggestions.DependencyUpgrade> upgrades =
                            new ArrayList<>();
                    List<CargoImprovementSuggestions.FeatureSuggestion> features =
                            new ArrayList<>();
                    List<CargoImprovementSuggestions.OptimizationSuggestion> optimizations =
                            new ArrayList<>();
                    List<CargoImprovementSuggestions.SecuritySuggestion> security =
                            new ArrayList<>();
                    List<String> general = new ArrayList<>();

                    // Suggest common improvements
                    if (!hasCommonDependencies(spec)) {
                        upgrades.add(
                                new CargoImprovementSuggestions.DependencyUpgrade(
                                        "serde",
                                        null,
                                        "1.0",
                                        "Consider adding serde for serialization",
                                        "LOW"));
                    }

                    if (!hasTestingFramework(spec)) {
                        general.add(
                                "Consider adding testing dependencies like tokio-test or"
                                        + " assert_matches");
                    }

                    // Security suggestions
                    security.add(
                            new CargoImprovementSuggestions.SecuritySuggestion(
                                    "dependency-audit",
                                            "Run cargo audit to check for security vulnerabilities",
                                    "HIGH", "cargo install cargo-audit && cargo audit"));

                    // Performance optimizations
                    optimizations.add(
                            new CargoImprovementSuggestions.OptimizationSuggestion(
                                    "lto",
                                    "Enable Link Time Optimization for release builds",
                                    "Smaller binary size and better performance",
                                    "Add lto = true to [profile.release]"));

                    general.add("Consider adding a README.md with usage examples");
                    general.add("Set up GitHub Actions for CI/CD");
                    general.add("Consider adding documentation with cargo doc");

                    return new CargoImprovementSuggestions(
                            upgrades, features, optimizations, security, general, 0.85);
                });
    }

    @Override
    public Promise<RustProjectScaffold> generateProjectScaffold(CargoBuildSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    Map<String, String> sourceFiles = new HashMap<>();
                    Map<String, String> testFiles = new HashMap<>();
                    Map<String, String> exampleFiles = new HashMap<>();
                    List<String> directories = new ArrayList<>();

                    // Generate main source files
                    if (hasLibrary(spec)) {
                        sourceFiles.put("src/lib.rs", generateLibRs(spec));
                        directories.add("src");
                    }

                    if (hasBinaries(spec)) {
                        sourceFiles.put("src/main.rs", generateMainRs(spec));
                        directories.add("src");
                    }

                    // Generate test files
                    testFiles.put("tests/integration_tests.rs", generateIntegrationTest(spec));
                    directories.add("tests");

                    // Generate example files
                    if (!spec.getExamples().isEmpty()) {
                        exampleFiles.put("examples/basic_usage.rs", generateBasicExample(spec));
                        directories.add("examples");
                    }

                    return new RustProjectScaffold(
                            sourceFiles, testFiles, exampleFiles, Map.of(), directories);
                });
    }

    @Override
    public Promise<CargoAnalysisResult> analyzeProject(String projectPath) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    // Mock analysis - in real implementation would analyze actual Rust project
                    CargoAnalysisResult.ProjectMetrics metrics =
                            new CargoAnalysisResult.ProjectMetrics(1000, 5, 10, 1, 85.0);

                    List<String> features = List.of("async", "serialization", "cli");
                    Map<String, String> depAnalysis =
                            Map.of(
                                    "serde", "Serialization library - up to date",
                                    "tokio", "Async runtime - consider upgrading to latest");

                    List<String> suggestions =
                            List.of(
                                    "Add more unit tests to improve coverage",
                                    "Consider using cargo-clippy for linting",
                                    "Add documentation examples");

                    return new CargoAnalysisResult(
                            metrics, features, depAnalysis, suggestions, 8.5);
                });
    }

    private String generateCargoTomlContent(CargoBuildSpec spec) {
        StringBuilder toml = new StringBuilder();

        // [package] section
        toml.append("[package]\n");
        toml.append("name = \"").append(spec.getName()).append("\"\n");
        toml.append("version = \"").append(spec.getVersion()).append("\"\n");
        toml.append("edition = \"").append(spec.getEdition()).append("\"\n");

        if (spec.getDescription() != null) {
            toml.append("description = \"").append(spec.getDescription()).append("\"\n");
        }

        if (!spec.getAuthors().isEmpty()) {
            toml.append("authors = [");
            for (int i = 0; i < spec.getAuthors().size(); i++) {
                if (i > 0) toml.append(", ");
                toml.append("\"").append(spec.getAuthors().get(i)).append("\"");
            }
            toml.append("]\n");
        }

        if (spec.getLicense() != null) {
            toml.append("license = \"").append(spec.getLicense()).append("\"\n");
        }

        if (spec.getRepository() != null) {
            toml.append("repository = \"").append(spec.getRepository()).append("\"\n");
        }

        if (spec.getHomepage() != null) {
            toml.append("homepage = \"").append(spec.getHomepage()).append("\"\n");
        }

        if (spec.getDocumentation() != null) {
            toml.append("documentation = \"").append(spec.getDocumentation()).append("\"\n");
        }

        if (!spec.getKeywords().isEmpty()) {
            toml.append("keywords = [");
            for (int i = 0; i < spec.getKeywords().size(); i++) {
                if (i > 0) toml.append(", ");
                toml.append("\"").append(spec.getKeywords().get(i)).append("\"");
            }
            toml.append("]\n");
        }

        if (!spec.getCategories().isEmpty()) {
            toml.append("categories = [");
            for (int i = 0; i < spec.getCategories().size(); i++) {
                if (i > 0) toml.append(", ");
                toml.append("\"").append(spec.getCategories().get(i)).append("\"");
            }
            toml.append("]\n");
        }

        toml.append("publish = ").append(spec.isPublish()).append("\n\n");

        // [lib] section
        if (!spec.getLibraries().isEmpty()) {
            CargoBuildSpec.CargoLibrary lib = spec.getLibraries().get(0);
            toml.append("[[lib]]\n");
            if (lib.getName() != null) {
                toml.append("name = \"").append(lib.getName()).append("\"\n");
            }
            if (lib.getPath() != null) {
                toml.append("path = \"").append(lib.getPath()).append("\"\n");
            }
            if (!lib.getCrateType().isEmpty()) {
                toml.append("crate-type = [");
                for (int i = 0; i < lib.getCrateType().size(); i++) {
                    if (i > 0) toml.append(", ");
                    toml.append("\"").append(lib.getCrateType().get(i)).append("\"");
                }
                toml.append("]\n");
            }
            toml.append("\n");
        }

        // [[bin]] sections
        for (CargoBuildSpec.CargoBinary bin : spec.getBinaries()) {
            toml.append("[[bin]]\n");
            toml.append("name = \"").append(bin.getName()).append("\"\n");
            if (bin.getPath() != null) {
                toml.append("path = \"").append(bin.getPath()).append("\"\n");
            }
            toml.append("\n");
        }

        // [dependencies] section
        if (!spec.getDependencies().isEmpty()) {
            toml.append("[dependencies]\n");
            for (Map.Entry<String, CargoBuildSpec.CargoDependency> entry :
                    spec.getDependencies().entrySet()) {
                appendDependency(toml, entry.getKey(), entry.getValue());
            }
            toml.append("\n");
        }

        // [dev-dependencies] section
        if (!spec.getDevDependencies().isEmpty()) {
            toml.append("[dev-dependencies]\n");
            for (Map.Entry<String, CargoBuildSpec.CargoDependency> entry :
                    spec.getDevDependencies().entrySet()) {
                appendDependency(toml, entry.getKey(), entry.getValue());
            }
            toml.append("\n");
        }

        // [build-dependencies] section
        if (!spec.getBuildDependencies().isEmpty()) {
            toml.append("[build-dependencies]\n");
            for (Map.Entry<String, CargoBuildSpec.CargoDependency> entry :
                    spec.getBuildDependencies().entrySet()) {
                appendDependency(toml, entry.getKey(), entry.getValue());
            }
            toml.append("\n");
        }

        // [features] section
        if (spec.getFeatures() != null && !spec.getFeatures().getFeatures().isEmpty()) {
            toml.append("[features]\n");
            if (!spec.getFeatures().getDefaultFeatures().isEmpty()) {
                toml.append("default = [");
                for (int i = 0; i < spec.getFeatures().getDefaultFeatures().size(); i++) {
                    if (i > 0) toml.append(", ");
                    toml.append("\"")
                            .append(spec.getFeatures().getDefaultFeatures().get(i))
                            .append("\"");
                }
                toml.append("]\n");
            }

            for (Map.Entry<String, List<String>> feature :
                    spec.getFeatures().getFeatures().entrySet()) {
                toml.append(feature.getKey()).append(" = [");
                for (int i = 0; i < feature.getValue().size(); i++) {
                    if (i > 0) toml.append(", ");
                    toml.append("\"").append(feature.getValue().get(i)).append("\"");
                }
                toml.append("]\n");
            }
            toml.append("\n");
        }

        // [profile.release] section for optimizations
        toml.append("[profile.release]\n");
        toml.append("lto = true\n");
        toml.append("codegen-units = 1\n");
        toml.append("panic = \"abort\"\n\n");

        // [workspace] section
        if (spec.getWorkspace() != null
                && (!spec.getWorkspace().getMembers().isEmpty()
                        || !spec.getWorkspace().getExclude().isEmpty())) {
            toml.append("[workspace]\n");
            if (!spec.getWorkspace().getMembers().isEmpty()) {
                toml.append("members = [");
                for (int i = 0; i < spec.getWorkspace().getMembers().size(); i++) {
                    if (i > 0) toml.append(", ");
                    toml.append("\"").append(spec.getWorkspace().getMembers().get(i)).append("\"");
                }
                toml.append("]\n");
            }
            if (!spec.getWorkspace().getExclude().isEmpty()) {
                toml.append("exclude = [");
                for (int i = 0; i < spec.getWorkspace().getExclude().size(); i++) {
                    if (i > 0) toml.append(", ");
                    toml.append("\"").append(spec.getWorkspace().getExclude().get(i)).append("\"");
                }
                toml.append("]\n");
            }
            toml.append("\n");
        }

        return toml.toString();
    }

    private void appendDependency(
            StringBuilder toml, String name, CargoBuildSpec.CargoDependency dep) {
        toml.append(name).append(" = ");

        if (dep.getVersion() != null && dep.getPath() == null && dep.getGit() == null) {
            // Simple version dependency
            toml.append("\"").append(dep.getVersion()).append("\"");
        } else {
            // Complex dependency
            toml.append("{ ");
            boolean first = true;

            if (dep.getVersion() != null) {
                toml.append("version = \"").append(dep.getVersion()).append("\"");
                first = false;
            }

            if (dep.getPath() != null) {
                if (!first) toml.append(", ");
                toml.append("path = \"").append(dep.getPath()).append("\"");
                first = false;
            }

            if (dep.getGit() != null) {
                if (!first) toml.append(", ");
                toml.append("git = \"").append(dep.getGit()).append("\"");
                first = false;
            }

            if (dep.getBranch() != null) {
                if (!first) toml.append(", ");
                toml.append("branch = \"").append(dep.getBranch()).append("\"");
                first = false;
            }

            if (dep.getTag() != null) {
                if (!first) toml.append(", ");
                toml.append("tag = \"").append(dep.getTag()).append("\"");
                first = false;
            }

            if (dep.getRev() != null) {
                if (!first) toml.append(", ");
                toml.append("rev = \"").append(dep.getRev()).append("\"");
                first = false;
            }

            if (!dep.getFeatures().isEmpty()) {
                if (!first) toml.append(", ");
                toml.append("features = [");
                for (int i = 0; i < dep.getFeatures().size(); i++) {
                    if (i > 0) toml.append(", ");
                    toml.append("\"").append(dep.getFeatures().get(i)).append("\"");
                }
                toml.append("]");
                first = false;
            }

            if (!dep.isDefaultFeatures()) {
                if (!first) toml.append(", ");
                toml.append("default-features = false");
                first = false;
            }

            if (dep.isOptional()) {
                if (!first) toml.append(", ");
                toml.append("optional = true");
            }

            toml.append(" }");
        }

        toml.append("\n");
    }

    private Map<String, String> generateSourceFiles(CargoBuildSpec spec) {
        Map<String, String> files = new HashMap<>();

        if (hasLibrary(spec)) {
            files.put("src/lib.rs", generateLibRs(spec));
        }

        if (hasBinaries(spec)) {
            files.put("src/main.rs", generateMainRs(spec));
        }

        return files;
    }

    private Map<String, String> generateAdditionalFiles(CargoBuildSpec spec) {
        Map<String, String> files = new HashMap<>();

        // Generate .gitignore
        files.put(".gitignore", generateGitIgnore());

        // Generate README.md
        files.put("README.md", generateReadme(spec));

        return files;
    }

    private String generateLibRs(CargoBuildSpec spec) {
        return String.format(
                """
            //! %s
            //!
            //! %s

            #![warn(missing_docs)]
            #![deny(unsafe_code)]

            /// Main library functionality
            pub fn hello() -> &'static str {
                "Hello, %s!"
            }

            #[cfg(test)]
            mod tests {
                use super::*;

                #[test]
                fn test_hello() {
                    assert_eq!(hello(), "Hello, %s!");
                }
            }
            """,
                spec.getName(),
                spec.getDescription() != null ? spec.getDescription() : "A Rust library",
                spec.getName(),
                spec.getName());
    }

    private String generateMainRs(CargoBuildSpec spec) {
        return String.format(
                """
            //! %s
            //!
            //! %s

            fn main() {
                println!("Hello from {}!", "%s");
            }
            """,
                spec.getName(),
                spec.getDescription() != null ? spec.getDescription() : "A Rust application",
                spec.getName());
    }

    private String generateIntegrationTest(CargoBuildSpec spec) {
        return String.format(
                """
            //! Integration tests for %s

            #[cfg(test)]
            mod integration_tests {
                #[test]
                fn test_basic_functionality() {
                    // Add integration tests here
                    assert!(true);
                }
            }
            """,
                spec.getName());
    }

    private String generateBasicExample(CargoBuildSpec spec) {
        return String.format(
                """
            //! Basic usage example for %s

            fn main() {
                println!("This is a basic example of using %s");
                // Add example code here
            }
            """,
                spec.getName(), spec.getName());
    }

    private String generateGitIgnore() {
        return """
            # Rust
            /target/
            **/*.rs.bk
            Cargo.lock

            # IDE
            .vscode/
            .idea/
            *.swp
            *.swo
            *~

            # OS
            .DS_Store
            Thumbs.db
            """;
    }

    private String generateReadme(CargoBuildSpec spec) {
        return String.format(
                """
            # %s

            %s

            ## Installation

            Add this to your `Cargo.toml`:

            ```toml
            [dependencies]
            %s = "%s"
            ```

            ## Usage

            ```rust
            use %s;

            fn main() {
                // Add usage examples here
            }
            ```

            ## License

            Licensed under %s
            """,
                spec.getName(),
                spec.getDescription() != null
                        ? spec.getDescription()
                        : "A Rust library/application",
                spec.getName(),
                spec.getVersion(),
                spec.getName().replace("-", "_"),
                spec.getLicense() != null ? spec.getLicense() : DEFAULT_LICENSE);
    }

    private List<GeneratedCargoProject.CargoOptimization> generateOptimizations(
            CargoBuildSpec spec) {
        List<GeneratedCargoProject.CargoOptimization> optimizations = new ArrayList<>();

        optimizations.add(
                new GeneratedCargoProject.CargoOptimization(
                        "release-profile",
                        "Configured optimized release profile with LTO and panic=abort",
                        "Reduces binary size and improves performance",
                        "Smaller, faster binaries"));

        optimizations.add(
                new GeneratedCargoProject.CargoOptimization(
                        "edition-2021",
                        "Uses Rust 2021 edition for latest language features",
                        "Access to newest Rust language improvements",
                        "Better ergonomics and performance"));

        return optimizations;
    }

    private List<String> generateWarnings(CargoBuildSpec spec) {
        List<String> warnings = new ArrayList<>();

        if (spec.getDependencies().isEmpty() && spec.getDevDependencies().isEmpty()) {
            warnings.add("No dependencies specified - project may be incomplete");
        }

        if (spec.getDescription() == null || spec.getDescription().trim().isEmpty()) {
            warnings.add("No project description provided");
        }

        if (spec.getLicense() == null) {
            warnings.add("No license specified - consider adding MIT or Apache-2.0");
        }

        return warnings;
    }

    private CargoValidationResult performValidation(CargoBuildSpec spec) {
        List<CargoValidationResult.ValidationIssue> errors = new ArrayList<>();
        List<CargoValidationResult.ValidationIssue> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Validate required fields
        if (spec.getName().isEmpty()) {
            errors.add(
                    new CargoValidationResult.ValidationIssue(
                            "MISSING_NAME", "Package name is required", "name", "ERROR"));
        }

        if (spec.getVersion().isEmpty()) {
            errors.add(
                    new CargoValidationResult.ValidationIssue(
                            "MISSING_VERSION", "Package version is required", "version", "ERROR"));
        }

        // Validate package name format
        if (!isValidPackageName(spec.getName())) {
            errors.add(
                    new CargoValidationResult.ValidationIssue(
                            "INVALID_NAME",
                            "Package name must contain only lowercase letters, numbers, and"
                                    + " hyphens",
                            "name",
                            "ERROR"));
        }

        // Warnings
        if (spec.getDescription() == null) {
            warnings.add(
                    new CargoValidationResult.ValidationIssue(
                            "MISSING_DESCRIPTION",
                            "Package description is recommended",
                            "description",
                            "WARNING"));
        }

        if (spec.getLicense() == null) {
            warnings.add(
                    new CargoValidationResult.ValidationIssue(
                            "MISSING_LICENSE",
                            "License specification is recommended",
                            "license",
                            "WARNING"));
        }

        recommendations.add("Consider adding unit tests");
        recommendations.add("Add documentation with cargo doc");
        recommendations.add("Set up continuous integration");

        boolean isValid = errors.isEmpty();
        double score = isValid ? (warnings.isEmpty() ? 1.0 : 0.8) : 0.0;

        return new CargoValidationResult(isValid, errors, warnings, recommendations, score);
    }

    private boolean isValidPackageName(String name) {
        return name.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$");
    }

    private boolean hasCommonDependencies(CargoBuildSpec spec) {
        return spec.getDependencies().containsKey("serde")
                || spec.getDependencies().containsKey("tokio")
                || spec.getDependencies().containsKey("clap");
    }

    private boolean hasTestingFramework(CargoBuildSpec spec) {
        return spec.getDevDependencies().containsKey("tokio-test")
                || spec.getDevDependencies().containsKey("assert_matches");
    }

    private boolean hasLibrary(CargoBuildSpec spec) {
        return !spec.getLibraries().isEmpty();
    }

    private boolean hasBinaries(CargoBuildSpec spec) {
        return !spec.getBinaries().isEmpty();
    }
}
