package com.ghatana.refactorer.diagnostics.bash;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles shellcheck runner test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class ShellcheckRunnerTest {

    @TempDir Path tempDir;
    private ShellcheckRunner shellcheckRunner;
    private Path testScript;

    @BeforeEach
    void setUp() throws Exception {
        PolyfixProjectContext context = mock(PolyfixProjectContext.class);
        shellcheckRunner = new ShellcheckRunner(context);

        // Create a test bash script
        testScript = tempDir.resolve("test.sh");
        String scriptContent =
                "#!/bin/bash\n"
                        + "echo $1\n"
                        + // SC2086: Double quote to prevent globbing and word splitting
                        "echo `ls`\n"; // SC2006: Use $(..) instead of legacy backticks

        Files.writeString(testScript, scriptContent);
    }

    @Test
    void testRun_WithValidScript_ReturnsDiagnostics() throws Exception {
        // Check if shellcheck is available
        if (!shellcheckRunner.isShellcheckAvailable()) {
            System.out.println("Skipping test: shellcheck not available");
            return;
        }

        // Recreate the test script to ensure it exists and is executable
        String scriptContent = "#!/bin/bash\n" + "echo $1\n" + "echo `ls`\n";
        Files.writeString(testScript, scriptContent);

        // Make the script executable
        if (!testScript.toFile().setExecutable(true)) {
            fail("Could not set execute permission on test script");
        }

        // Verify the script exists and is executable
        if (!Files.exists(testScript)) {
            fail("Test script was not created at: " + testScript);
        }
        if (!Files.isExecutable(testScript)) {
            fail("Test script is not executable: " + testScript);
        }

        // Debug output
        System.out.println("=== Running shellcheck on: " + testScript);
        System.out.println("File exists: " + Files.exists(testScript));
        System.out.println("File content:\n" + Files.readString(testScript));

        // Run shellcheck directly first to verify it works
        try {
            Process process =
                    new ProcessBuilder("shellcheck", testScript.toString())
                            .redirectErrorStream(true)
                            .start();
            process.waitFor(5, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes());
            System.out.println("Direct shellcheck output:\n" + output);
        } catch (Exception e) {
            System.err.println("Error running shellcheck directly: " + e.getMessage());
            e.printStackTrace();
        }

        // Run shellcheck
        List<UnifiedDiagnostic> diagnostics = shellcheckRunner.run(testScript);

        // Print diagnostics for debugging
        if (diagnostics.isEmpty()) {
            System.out.println(
                    "No diagnostics found. This might indicate an issue with shellcheck"
                            + " execution.");
            System.out.println("Trying to run shellcheck directly for more info...");
            try {
                Process process =
                        new ProcessBuilder("shellcheck", testScript.toString())
                                .redirectErrorStream(true)
                                .start();
                process.waitFor(5, TimeUnit.SECONDS);
                String output = new String(process.getInputStream().readAllBytes());
                System.out.println("Direct shellcheck output:\n" + output);
            } catch (Exception e) {
                System.err.println("Error running shellcheck directly: " + e.getMessage());
            }
        } else {
            System.out.println("Found " + diagnostics.size() + " diagnostics:");
            diagnostics.forEach(
                    d ->
                            System.out.printf(
                                    "- %s: %s (line %d, col %d)%n",
                                    d.getRuleId(), d.getMessage(), d.getLine(), d.getColumn()));
        }

        // Assertions with more detailed error messages
        assertFalse(
                diagnostics.isEmpty(),
                "Expected to find shellcheck issues. Check the logs above for details.");

        // Check for SC2086
        boolean hasSC2086 = diagnostics.stream().anyMatch(d -> "SC2086".equals(d.getRuleId()));
        assertTrue(
                hasSC2086,
                "Expected SC2086 diagnostic (Double quote to prevent globbing and word splitting). "
                        + "Found rules: "
                        + diagnostics.stream().map(UnifiedDiagnostic::getRuleId).toList());

        // Check for SC2006
        boolean hasSC2006 = diagnostics.stream().anyMatch(d -> "SC2006".equals(d.getRuleId()));
        assertTrue(
                hasSC2006,
                "Expected SC2006 diagnostic (Use $(..) instead of legacy backticks). "
                        + "Found rules: "
                        + diagnostics.stream().map(UnifiedDiagnostic::getRuleId).toList());
    }

    @Test
    void testRun_WithNonExistentFile_ReturnsEmptyList() {
        Path nonExistentFile = tempDir.resolve("nonexistent.sh");
        List<UnifiedDiagnostic> diagnostics = shellcheckRunner.run(nonExistentFile);

        assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for non-existent file");
    }

    @Test
    void testIsShellcheckAvailable() {
        // Just verify the method doesn't throw and returns a boolean
        assertDoesNotThrow(() -> shellcheckRunner.isShellcheckAvailable());
    }
}
