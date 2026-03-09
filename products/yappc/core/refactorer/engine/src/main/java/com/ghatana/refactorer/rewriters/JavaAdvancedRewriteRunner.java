package com.ghatana.refactorer.rewriters;

import com.ghatana.refactorer.codemods.OpenRewriteRunner;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Logger;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.internal.lang.NonNull;

/**
 * Adapter around existing OpenRewriteRunner for advanced migrations. This class is a placeholder to
 * wire recipe packs defined under config/rewriters.
 
 * @doc.type class
 * @doc.purpose Handles java advanced rewrite runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class JavaAdvancedRewriteRunner {
    private final OpenRewriteRunner delegate;
    private final Logger log;

    public JavaAdvancedRewriteRunner(@NonNull OpenRewriteRunner delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        PolyfixProjectContext ctx = getContext(delegate);
        this.log =
                ctx != null ? ctx.log() : org.apache.logging.log4j.LogManager.getLogger(getClass());
    }

    /**
     * Load an OpenRewrite YAML recipe pack and apply the given recipes to all Java sources under
     * repoRoot.
     *
     * @param repoRoot repository root
     * @param recipePack path to YAML containing recipe definitions
     * @param recipeNames list of recipe names to activate
     * @return diagnostics collected from OpenRewrite applications
     */
    public List<UnifiedDiagnostic> applyRecipes(
            Path repoRoot, Path recipePack, List<String> recipeNames) {
        Objects.requireNonNull(repoRoot, "repoRoot");
        Objects.requireNonNull(recipePack, "recipePack");
        Objects.requireNonNull(recipeNames, "recipeNames");

        List<UnifiedDiagnostic> all = new ArrayList<>();

        if (!Files.exists(recipePack)) {
            log.warn("Recipe pack not found: {}", recipePack);
            return all;
        }

        try (InputStream in = Files.newInputStream(recipePack)) {
            Environment env =
                    Environment.builder()
                            .load(new YamlResourceLoader(in, recipePack.toUri(), new Properties()))
                            .build();

            // Discover Java files
            List<Path> javaFiles = listJavaFiles(repoRoot);
            if (javaFiles.isEmpty()) {
                log.info("No Java source files found under {}", repoRoot);
                return all;
            }

            for (String name : recipeNames) {
                try {
                    Recipe recipe = env.activateRecipes(name);
                    if (recipe == null) {
                        log.warn("Recipe not found in pack: {}", name);
                        continue;
                    }
                    List<UnifiedDiagnostic> diags = delegate.run(recipe, javaFiles);
                    all.addAll(diags);
                } catch (Exception e) {
                    log.error("Failed to apply recipe {}: {}", name, e.getMessage(), e);
                }
            }
        } catch (IOException ioe) {
            log.error("Unable to read recipe pack {}: {}", recipePack, ioe.getMessage());
        }

        return all;
    }

    private static List<Path> listJavaFiles(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    // Best-effort: reflectively access PolyfixProjectContext from OpenRewriteRunner for logging
    private static PolyfixProjectContext getContext(OpenRewriteRunner runner) {
        try {
            var f = OpenRewriteRunner.class.getDeclaredField("context");
            f.setAccessible(true);
            Object ctx = f.get(runner);
            return (ctx instanceof PolyfixProjectContext) ? (PolyfixProjectContext) ctx : null;
        } catch (Exception ignore) {
            return null;
        }
    }
}
