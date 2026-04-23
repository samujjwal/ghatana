package com.ghatana.refactorer.diagnostics.tsjs;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.Severity;
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
            org.apache.logging.log4j.LogManager.getLogger(TscRunnerTest.class); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        // Enable debug logging for tests
        try (LoggerContext ctx = (LoggerContext) LogManager.getContext(false)) { // GH-90000
            Configuration config = ctx.getConfiguration(); // GH-90000
            config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.DEBUG); // GH-90000
            ctx.updateLoggers(config); // GH-90000
        }

        this.context =
                new PolyfixProjectContext( // GH-90000
                        tempDir, null, List.of(), null, LogManager.getLogger(TscRunnerTest.class)); // GH-90000
        this.tscRunner = new TscRunner(context); // GH-90000
    }

    @Test
    void testIsAvailable() { // GH-90000
        // This test will pass if TypeScript is available, but won't fail if it's not
        // since we can't assume TypeScript is installed in the test environment
        boolean isAvailable = tscRunner.isAvailable(); // GH-90000
        logger.info("TypeScript is {}available in test environment", isAvailable ? "" : "not "); // GH-90000

        // If TypeScript is not available, we'll skip the other tests
        if (!isAvailable) { // GH-90000
            logger.warn( // GH-90000
                    "Skipping TypeScript tests because TypeScript is not available in the test"
                            + " environment");
        }
    }

    @Test
    void testRunWithValidCode() throws IOException { // GH-90000
        if (!tscRunner.isAvailable()) { // GH-90000
            logger.warn("Skipping testRunWithValidCode: TypeScript is not available");
            return;
        }

        try {
            // Copy test project to temp directory
            Path testProjectDir = tempDir.resolve("test-project");
            copyTestProject(testProjectDir); // GH-90000

            // Run the linter on the valid file
            Path validFile = testProjectDir.resolve("src/valid.ts");
            assertTrue(Files.exists(validFile), "Test file should exist: " + validFile); // GH-90000

            List<UnifiedDiagnostic> diagnostics = runPromise(() -> tscRunner.run(testProjectDir)); // GH-90000

            // Should have no errors in valid code
            List<UnifiedDiagnostic> errors =
                    diagnostics.stream() // GH-90000
                            .filter(d -> d.severity() == Severity.ERROR) // GH-90000
                            .collect(Collectors.toList()); // GH-90000

            assertEquals( // GH-90000
                    0, errors.size(), "Expected no errors in valid code, but found: " + errors); // GH-90000
        } catch (Exception e) { // GH-90000
            logger.error("Error in testRunWithValidCode", e); // GH-90000
            throw e;
        }
    }

    @Test
    void testRunWithInvalidCode() throws IOException { // GH-90000
        if (!tscRunner.isAvailable()) { // GH-90000
            logger.warn("Skipping testRunWithInvalidCode: TypeScript is not available");
            return;
        }

        try {
            // Copy test project to temp directory
            Path testProjectDir = tempDir.resolve("test-project");
            copyTestProject(testProjectDir); // GH-90000

            // Run the linter on the invalid file
            Path invalidFile = testProjectDir.resolve("src/invalid.ts");
            assertTrue(Files.exists(invalidFile), "Test file should exist: " + invalidFile); // GH-90000

            List<UnifiedDiagnostic> diagnostics = runPromise(() -> tscRunner.run(testProjectDir)); // GH-90000

            // Should find errors in the invalid code
            assertFalse(diagnostics.isEmpty(), "Expected to find errors in invalid code"); // GH-90000

            // Verify we found the expected errors
            List<String> errorMessages =
                    diagnostics.stream() // GH-90000
                            .map(UnifiedDiagnostic::message) // GH-90000
                            .collect(Collectors.toList()); // GH-90000

            logger.info("Found {} diagnostics: {}", diagnostics.size(), errorMessages); // GH-90000

            // Check for common error patterns (may vary based on TypeScript version) // GH-90000
            boolean hasTypeError =
                    errorMessages.stream() // GH-90000
                            .anyMatch( // GH-90000
                                    msg ->
                                            msg.contains("is not assignable")
                                                    || msg.contains("Type '")
                                                    || msg.contains("not assignable"));

            boolean hasUndefinedError =
                    errorMessages.stream() // GH-90000
                            .anyMatch( // GH-90000
                                    msg ->
                                            msg.contains("Cannot find name")
                                                    || msg.contains("is not defined"));

            assertTrue(hasTypeError, "Expected type error not found in: " + errorMessages); // GH-90000

            assertTrue( // GH-90000
                    hasUndefinedError,
                    "Expected undefined variable error not found in: " + errorMessages);
        } catch (Exception e) { // GH-90000
            logger.error("Error in testRunWithInvalidCode", e); // GH-90000
            throw e;
        }
    }

    private void copyTestProject(Path targetDir) throws IOException { // GH-90000
        // Copy the test project from resources to the temp directory
        Path sourceDir = Path.of("src/test/resources/ts-test-project");

        // Verify source directory exists
        if (!Files.exists(sourceDir)) { // GH-90000
            throw new IOException("Source directory not found: " + sourceDir.toAbsolutePath()); // GH-90000
        }

        logger.debug("Copying test project from {} to {}", sourceDir, targetDir); // GH-90000

        // Create all directories first
        try (var paths = Files.walk(sourceDir)) { // GH-90000
            paths.filter(Files::isDirectory) // GH-90000
                    .map(sourceDir::relativize) // GH-90000
                    .forEach( // GH-90000
                            relPath -> {
                                try {
                                    Path dir = targetDir.resolve(relPath); // GH-90000
                                    if (!Files.exists(dir)) { // GH-90000
                                        logger.trace("Creating directory: {}", dir); // GH-90000
                                        Files.createDirectories(dir); // GH-90000
                                    }
                                } catch (IOException e) { // GH-90000
                                    throw new RuntimeException( // GH-90000
                                            "Failed to create directory: " + relPath, e);
                                }
                            });
        }

        // Copy all files with overwrite
        try (var paths = Files.walk(sourceDir)) { // GH-90000
            paths.filter(Files::isRegularFile) // GH-90000
                    .forEach( // GH-90000
                            source -> {
                                try {
                                    Path relative = sourceDir.relativize(source); // GH-90000
                                    Path target = targetDir.resolve(relative); // GH-90000

                                    logger.trace("Copying file: {} -> {}", source, target); // GH-90000

                                    // Create parent directories if they don't exist
                                    if (target.getParent() != null // GH-90000
                                            && !Files.exists(target.getParent())) { // GH-90000
                                        Files.createDirectories(target.getParent()); // GH-90000
                                    }

                                    // Copy with overwrite
                                    Files.copy( // GH-90000
                                            source,
                                            target,
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                } catch (IOException e) { // GH-90000
                                    throw new RuntimeException("Failed to copy file: " + source, e); // GH-90000
                                }
                            });
        }

        logger.debug("Successfully copied test project to: {}", targetDir); // GH-90000
    }
}
