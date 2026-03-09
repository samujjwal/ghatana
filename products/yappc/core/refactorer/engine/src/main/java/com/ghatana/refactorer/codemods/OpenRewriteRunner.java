package com.ghatana.refactorer.codemods;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

/**
 * Executes OpenRewrite recipes on source files with proper error handling and file management.
 *
 * <p>This class is thread-safe and handles atomic file writes to prevent partial updates.
 
 * @doc.type class
 * @doc.purpose Handles open rewrite runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class OpenRewriteRunner {
    private final PolyfixProjectContext context;
    private final Logger logger;
    private final JavaParser javaParser;

    public OpenRewriteRunner(PolyfixProjectContext context) {
        this(context, JavaParser.fromJavaVersion().classpath("*").build());
    }

    OpenRewriteRunner(PolyfixProjectContext context, JavaParser javaParser) {
        this.context = Objects.requireNonNull(context, "context");
        this.logger = context.log();
        this.javaParser = Objects.requireNonNull(javaParser, "javaParser");
    }

    /**
     * Applies the specified OpenRewrite recipe to the given Java source files.
     *
     * @param recipe The OpenRewrite recipe to apply
     * @param files The source files to process
     * @return List of diagnostics for any issues found or created
     */
    public List<UnifiedDiagnostic> run(Recipe recipe, List<Path> files) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(files, "files");

        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();
        if (files.isEmpty()) {
            return diagnostics;
        }

        final String recipeName = recipe.getName();

        logger.info("Running OpenRewrite recipe {} on {} files", recipeName, files.size());

        try {
            // Parse files into SourceFile objects
            List<SourceFile> sourceFiles =
                    javaParser
                            .parse(files, context.root(), createExecutionContext())
                            .collect(Collectors.toList());

            // Run recipe using InMemoryLargeSourceSet and return results
            List<Result> results =
                    recipe.run(new InMemoryLargeSourceSet(sourceFiles), createExecutionContext())
                            .getChangeset()
                            .getAllResults();

            // Apply changes
            for (Result result : results) {
                if (result.diff() != null && !result.diff().isEmpty()) {
                    writeResult(result);
                    diagnostics.add(createDiagnostic(result, recipeName));
                }
            }

            logger.info("Applied {} changes from recipe {}", results.size(), recipeName);

        } catch (Exception e) {
            logger.error("Error executing OpenRewrite recipe: {}", e.getMessage(), e);
            diagnostics.add(
                    UnifiedDiagnostic.error(
                            "openrewrite",
                            "Failed to apply OpenRewrite recipe: " + e.getMessage(),
                            null,
                            -1,
                            -1,
                            e));
        }

        return diagnostics;
    }

    private ExecutionContext createExecutionContext() {
        return new ExecutionContext() {
            @Override
            public void putMessage(String key, Object value) {}

            @Override
            public <T> T getMessage(String key) {
                return null;
            }

            @Override
            public <T> T getMessage(String key, T defaultValue) {
                return defaultValue;
            }

            @Override
            public <T> T pollMessage(String key) {
                return null;
            }

            @Override
            public Consumer<Throwable> getOnError() {
                return e -> logger.error("Error occurred", e);
            }

            @Override
            public BiConsumer<Throwable, ExecutionContext> getOnTimeout() {
                return (e, ctx) -> logger.error("Timeout occurred", e);
            }
        };
    }

    private void writeResult(Result result) throws IOException {
        Path path = context.root().resolve(result.getBefore().getSourcePath());

        // Create parent directories if they don't exist
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Write atomically using a temporary file
        Path tempFile =
                Files.createTempFile(path.getParent(), "." + path.getFileName() + ".tmp", "");
        try {
            Files.writeString(tempFile, result.getAfter().printAll());
            Files.move(tempFile, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private UnifiedDiagnostic createDiagnostic(Result result, String recipeName) {
        return UnifiedDiagnostic.builder()
                .tool("openrewrite")
                .code("") // code not available
                .message("Applied " + recipeName)
                .file(Path.of(result.getBefore().getSourcePath().toString()))
                .startLine(1) // Line number not available
                .startColumn(1) // Column number not available
                .endLine(1)
                .endColumn(1)
                .build();
    }
}
