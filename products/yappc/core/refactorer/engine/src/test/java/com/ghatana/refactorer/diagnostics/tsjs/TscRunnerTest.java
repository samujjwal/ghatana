package com.ghatana.refactorer.diagnostics.tsjs;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link TscRunner}. 
 * @doc.type class
 * @doc.purpose Handles tsc runner test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class TscRunnerTest extends EventloopTestBase {

    @TempDir static Path tempDir;
    private TscRunner tscRunner;
    private PolyfixProjectContext context;
    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(TscRunnerTest.class);

    @BeforeEach
    void setUp() {
        // Enable debug logging for tests
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
        ctx.updateLoggers(config);

        this.context =
                new PolyfixProjectContext(
                        tempDir, null, List.of(), null, LogManager.getLogger(TscRunnerTest.class));
        this.tscRunner = new TscRunner(context);
    }

    @Test
    void testIsAvailable() {
        // This test will pass if TypeScript is available, but won't fail if it's not
        // since we can't assume TypeScript is installed in the test environment
        boolean isAvailable = tscRunner.isAvailable();
        logger.info("TypeScript is {}available in test environment", isAvailable ? "" : "not ");

        // If TypeScript is not available, we'll skip the other tests
        if (!isAvailable) {
            logger.warn(
                    "Skipping TypeScript tests because TypeScript is not available in the test"
                            + " environment");
        }
    }

    @Test
    void testRunWithValidCode() throws IOException {
        if (!tscRunner.isAvailable()) {
            logger.warn("Skipping testRunWithValidCode: TypeScript is not available");
            return;
        }

        try {
            // Copy test project to temp directory
            Path testProjectDir = tempDir.resolve("test-project");
            copyTestProject(testProjectDir);

            // Run the linter on the valid file
            Path validFile = testProjectDir.resolve("src/valid.ts");
            assertTrue(Files.exists(validFile), "Test file should exist: " + validFile);

            List<UnifiedDiagnostic> diagnostics = runPromise(() -> tscRunner.run(testProjectDir));

            // Should have no errors in valid code
            List<UnifiedDiagnostic> errors =
                    diagnostics.stream()
                            .filter(d -> d.severity() == Severity.ERROR)
                            .collect(Collectors.toList());

            assertEquals(
                    0, errors.size(), "Expected no errors in valid code, but found: " + errors);
        } catch (Exception e) {
            logger.error("Error in testRunWithValidCode", e);
            throw e;
        }
    }

    @Test
    void testRunWithInvalidCode() throws IOException {
        if (!tscRunner.isAvailable()) {
            logger.warn("Skipping testRunWithInvalidCode: TypeScript is not available");
            return;
        }

        try {
            // Copy test project to temp directory
            Path testProjectDir = tempDir.resolve("test-project");
            copyTestProject(testProjectDir);

            // Run the linter on the invalid file
            Path invalidFile = testProjectDir.resolve("src/invalid.ts");
            assertTrue(Files.exists(invalidFile), "Test file should exist: " + invalidFile);

            List<UnifiedDiagnostic> diagnostics = runPromise(() -> tscRunner.run(testProjectDir));

            // Should find errors in the invalid code
            assertFalse(diagnostics.isEmpty(), "Expected to find errors in invalid code");

            // Verify we found the expected errors
            List<String> errorMessages =
                    diagnostics.stream()
                            .map(UnifiedDiagnostic::message)
                            .collect(Collectors.toList());

            logger.info("Found {} diagnostics: {}", diagnostics.size(), errorMessages);

            // Check for common error patterns (may vary based on TypeScript version)
            boolean hasTypeError =
                    errorMessages.stream()
                            .anyMatch(
                                    msg ->
                                            msg.contains("is not assignable")
                                                    || msg.contains("Type '")
                                                    || msg.contains("not assignable"));

            boolean hasUndefinedError =
                    errorMessages.stream()
                            .anyMatch(
                                    msg ->
                                            msg.contains("Cannot find name")
                                                    || msg.contains("is not defined"));

            assertTrue(hasTypeError, "Expected type error not found in: " + errorMessages);

            assertTrue(
                    hasUndefinedError,
                    "Expected undefined variable error not found in: " + errorMessages);
        } catch (Exception e) {
            logger.error("Error in testRunWithInvalidCode", e);
            throw e;
        }
    }

    private void copyTestProject(Path targetDir) throws IOException {
        // Copy the test project from resources to the temp directory
        Path sourceDir = Path.of("src/test/resources/ts-test-project");

        // Verify source directory exists
        if (!Files.exists(sourceDir)) {
            throw new IOException("Source directory not found: " + sourceDir.toAbsolutePath());
        }

        logger.debug("Copying test project from {} to {}", sourceDir, targetDir);

        // Create all directories first
        try (var paths = Files.walk(sourceDir)) {
            paths.filter(Files::isDirectory)
                    .map(sourceDir::relativize)
                    .forEach(
                            relPath -> {
                                try {
                                    Path dir = targetDir.resolve(relPath);
                                    if (!Files.exists(dir)) {
                                        logger.trace("Creating directory: {}", dir);
                                        Files.createDirectories(dir);
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(
                                            "Failed to create directory: " + relPath, e);
                                }
                            });
        }

        // Copy all files with overwrite
        try (var paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(
                            source -> {
                                try {
                                    Path relative = sourceDir.relativize(source);
                                    Path target = targetDir.resolve(relative);

                                    logger.trace("Copying file: {} -> {}", source, target);

                                    // Create parent directories if they don't exist
                                    if (target.getParent() != null
                                            && !Files.exists(target.getParent())) {
                                        Files.createDirectories(target.getParent());
                                    }

                                    // Copy with overwrite
                                    Files.copy(
                                            source,
                                            target,
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to copy file: " + source, e);
                                }
                            });
        }

        logger.debug("Successfully copied test project to: {}", targetDir);
    }
}
