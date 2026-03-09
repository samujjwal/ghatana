package com.ghatana.refactorer.codemods;

import com.ghatana.refactorer.diagnostics.jsonyaml.NodeBridge;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.openrewrite.Recipe;

/**
 * Orchestrates the execution of different types of codemods.
 *
 * <p>This class coordinates between Java, JSON, and YAML codemods, ensuring proper error handling
 * and reporting.
 
 * @doc.type class
 * @doc.purpose Handles codemod orchestrator operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class CodemodOrchestrator {
    private final PolyfixProjectContext context;
    private final Logger logger;
    private final OpenRewriteRunner openRewriteRunner;
    private final JsonYamlCodemods jsonYamlCodemods;
    private final Supplier<List<Recipe>> recipeSupplier;

    public CodemodOrchestrator(PolyfixProjectContext context) {
        this(
                context,
                new OpenRewriteRunner(context),
                new JsonYamlCodemods(context, new NodeBridge(context)),
                null);
    }

    CodemodOrchestrator(
            PolyfixProjectContext context,
            OpenRewriteRunner openRewriteRunner,
            JsonYamlCodemods jsonYamlCodemods) {
        this(context, openRewriteRunner, jsonYamlCodemods, null);
    }

    CodemodOrchestrator(
            PolyfixProjectContext context,
            OpenRewriteRunner openRewriteRunner,
            JsonYamlCodemods jsonYamlCodemods,
            Supplier<List<Recipe>> recipeSupplier) {
        this.context = Objects.requireNonNull(context, "context");
        this.logger = context.log();

        // Validate required configuration
        if (context.root() == null) {
            throw new IllegalArgumentException("Project root directory must be specified");
        }

        this.openRewriteRunner = Objects.requireNonNull(openRewriteRunner, "openRewriteRunner");
        this.jsonYamlCodemods = Objects.requireNonNull(jsonYamlCodemods, "jsonYamlCodemods");
        this.recipeSupplier = recipeSupplier != null ? recipeSupplier : this::loadRecipes;

        logger.debug("Initialized CodemodOrchestrator with root: {}", context.root());
    }

    /**
     * Applies all configured codemods to the specified files.
     *
     * @param files The files to process. Must not be null.
     * @return List of diagnostics from all codemod operations. Never null, may be empty.
     * @throws NullPointerException if files is null or contains null elements
     */
    public List<UnifiedDiagnostic> applyCodemods(List<Path> files) {
        Objects.requireNonNull(files, "files cannot be null");
        if (files.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("files list cannot contain null elements");
        }

        logger.info("Applying codemods to {} files", files.size());
        List<UnifiedDiagnostic> allDiagnostics = new ArrayList<>();

        // Group files by type
        List<Path> javaFiles = files.stream().filter(this::isJavaFile).collect(Collectors.toList());

        List<Path> jsonYamlFiles =
                files.stream().filter(this::isJsonOrYamlFile).collect(Collectors.toList());

        // Log file distribution
        logger.debug(
                "Processing {} Java files and {} JSON/YAML files",
                javaFiles.size(),
                jsonYamlFiles.size());

        // Apply Java codemods
        if (!javaFiles.isEmpty()) {
            try {
                List<Recipe> recipes =
                        Objects.requireNonNullElseGet(recipeSupplier.get(), List::of);
                if (recipes.isEmpty()) {
                    logger.warn("No recipes configured for Java files. Skipping Java codemods.");
                } else {
                    logger.info(
                            "Applying {} recipes to {} Java files",
                            recipes.size(),
                            javaFiles.size());
                    for (Recipe recipe : recipes) {
                        logger.debug("Applying recipe: {}", recipe.getName());
                        List<UnifiedDiagnostic> results = openRewriteRunner.run(recipe, javaFiles);
                        allDiagnostics.addAll(results);
                        logger.debug(
                                "Recipe {} generated {} diagnostics",
                                recipe.getName(),
                                results.size());
                    }
                }
            } catch (Exception e) {
                String errorMsg = String.format("Error applying Java codemods: %s", e.getMessage());
                logger.error(errorMsg, e);
                allDiagnostics.add(
                        UnifiedDiagnostic.error("java-codemods", errorMsg, null, -1, -1, e));
            }
        }

        // Apply JSON/YAML codemods
        if (!jsonYamlFiles.isEmpty()) {
            try {
                Path schemaDir = context.root().resolve("config/schemas");
                if (Files.exists(schemaDir)) {
                    logger.debug("Using schema directory: {}", schemaDir);
                    List<UnifiedDiagnostic> results =
                            jsonYamlCodemods.normalizeAndValidate(jsonYamlFiles, schemaDir);
                    allDiagnostics.addAll(results);
                    logger.debug("JSON/YAML processing generated {} diagnostics", results.size());
                } else {
                    logger.warn(
                            "Schema directory not found: {}. Skipping JSON/YAML validation.",
                            schemaDir);
                }
            } catch (Exception e) {
                String errorMsg =
                        String.format("Error applying JSON/YAML codemods: %s", e.getMessage());
                logger.error(errorMsg, e);
                allDiagnostics.add(
                        UnifiedDiagnostic.error("jsonyaml-codemods", errorMsg, null, -1, -1, e));
            }
        }

        logger.info("Completed codemods. Generated {} diagnostics", allDiagnostics.size());
        return allDiagnostics;
    }

    /**
     * Loads OpenRewrite recipes from configuration.
     *
     * @return List of configured recipes, or empty list if none configured
     */
    private List<Recipe> loadRecipes() {
        try {
            Path recipeConfig = context.root().resolve("config/rewrite/rewrite.yml");
            if (Files.exists(recipeConfig)) {
                logger.debug("Loading recipes from: {}", recipeConfig);
                // NOTE: Implement YAML recipe loading
                // For now, return a default set of recipes
                return List.of(
                        // Add default recipes here
                        );
            } else {
                logger.debug("No recipe configuration found at: {}", recipeConfig);
            }
        } catch (Exception e) {
            logger.error("Error loading recipes: {}", e.getMessage(), e);
        }
        return List.of();
    }

    /**
     * Checks if the given file is a Java source file.
     *
     * @param file The file to check
     * @return true if the file has a .java extension (case-insensitive), false otherwise
     */
    private boolean isJavaFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".java");
    }

    /**
     * Checks if the given file is a JSON or YAML file.
     *
     * @param file The file to check
     * @return true if the file has a .json, .yaml, or .yml extension (case-insensitive), false
     *     otherwise
     */
    private boolean isJsonOrYamlFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml");
    }
}
