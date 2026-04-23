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
    void setUp() throws Exception { // GH-90000
        PolyfixProjectContext context = mock(PolyfixProjectContext.class); // GH-90000
        shellcheckRunner = new ShellcheckRunner(context); // GH-90000

        // Create a test bash script
        testScript = tempDir.resolve("test.sh");
        String scriptContent =
                "#!/bin/bash\n"
                        + "echo $1\n"
                        + // SC2086: Double quote to prevent globbing and word splitting
                        "echo `ls`\n"; // SC2006: Use $(..) instead of legacy backticks // GH-90000

        Files.writeString(testScript, scriptContent); // GH-90000
    }

    @Test
    void testRun_WithValidScript_ReturnsDiagnostics() throws Exception { // GH-90000
        // Check if shellcheck is available
        if (!shellcheckRunner.isShellcheckAvailable()) { // GH-90000
            System.out.println("Skipping test: shellcheck not available");
            return;
        }

        // Recreate the test script to ensure it exists and is executable
        String scriptContent = "#!/bin/bash\n" + "echo $1\n" + "echo `ls`\n";
        Files.writeString(testScript, scriptContent); // GH-90000

        // Make the script executable
        if (!testScript.toFile().setExecutable(true)) { // GH-90000
            fail("Could not set execute permission on test script");
        }

        // Verify the script exists and is executable
        if (!Files.exists(testScript)) { // GH-90000
            fail("Test script was not created at: " + testScript); // GH-90000
        }
        if (!Files.isExecutable(testScript)) { // GH-90000
            fail("Test script is not executable: " + testScript); // GH-90000
        }

        // Debug output
        System.out.println("=== Running shellcheck on: " + testScript); // GH-90000
        System.out.println("File exists: " + Files.exists(testScript)); // GH-90000
        System.out.println("File content:\n" + Files.readString(testScript)); // GH-90000

        // Run shellcheck directly first to verify it works
        try {
            Process process =
                    new ProcessBuilder("shellcheck", testScript.toString()) // GH-90000
                            .redirectErrorStream(true) // GH-90000
                            .start(); // GH-90000
            process.waitFor(5, TimeUnit.SECONDS); // GH-90000
            String output = new String(process.getInputStream().readAllBytes()); // GH-90000
            System.out.println("Direct shellcheck output:\n" + output); // GH-90000
        } catch (Exception e) { // GH-90000
            System.err.println("Error running shellcheck directly: " + e.getMessage()); // GH-90000
            e.printStackTrace(); // GH-90000
        }

        // Run shellcheck
        List<UnifiedDiagnostic> diagnostics = shellcheckRunner.run(testScript); // GH-90000

        // Print diagnostics for debugging
        if (diagnostics.isEmpty()) { // GH-90000
            System.out.println( // GH-90000
                    "No diagnostics found. This might indicate an issue with shellcheck"
                            + " execution.");
            System.out.println("Trying to run shellcheck directly for more info...");
            try {
                Process process =
                        new ProcessBuilder("shellcheck", testScript.toString()) // GH-90000
                                .redirectErrorStream(true) // GH-90000
                                .start(); // GH-90000
                process.waitFor(5, TimeUnit.SECONDS); // GH-90000
                String output = new String(process.getInputStream().readAllBytes()); // GH-90000
                System.out.println("Direct shellcheck output:\n" + output); // GH-90000
            } catch (Exception e) { // GH-90000
                System.err.println("Error running shellcheck directly: " + e.getMessage()); // GH-90000
            }
        } else {
            System.out.println("Found " + diagnostics.size() + " diagnostics:"); // GH-90000
            diagnostics.forEach( // GH-90000
                    d ->
                            System.out.printf( // GH-90000
                                    "- %s: %s (line %d, col %d)%n", // GH-90000
                                    d.getRuleId(), d.getMessage(), d.getLine(), d.getColumn())); // GH-90000
        }

        // Assertions with more detailed error messages
        assertFalse( // GH-90000
                diagnostics.isEmpty(), // GH-90000
                "Expected to find shellcheck issues. Check the logs above for details.");

        // Check for SC2086
        boolean hasSC2086 = diagnostics.stream().anyMatch(d -> "SC2086".equals(d.getRuleId())); // GH-90000
        assertTrue( // GH-90000
                hasSC2086,
                "Expected SC2086 diagnostic (Double quote to prevent globbing and word splitting). " // GH-90000
                        + "Found rules: "
                        + diagnostics.stream().map(UnifiedDiagnostic::getRuleId).toList()); // GH-90000

        // Check for SC2006
        boolean hasSC2006 = diagnostics.stream().anyMatch(d -> "SC2006".equals(d.getRuleId())); // GH-90000
        assertTrue( // GH-90000
                hasSC2006,
                "Expected SC2006 diagnostic (Use $(..) instead of legacy backticks). " // GH-90000
                        + "Found rules: "
                        + diagnostics.stream().map(UnifiedDiagnostic::getRuleId).toList()); // GH-90000
    }

    @Test
    void testRun_WithNonExistentFile_ReturnsEmptyList() { // GH-90000
        Path nonExistentFile = tempDir.resolve("nonexistent.sh");
        List<UnifiedDiagnostic> diagnostics = shellcheckRunner.run(nonExistentFile); // GH-90000

        assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for non-existent file"); // GH-90000
    }

    @Test
    void testIsShellcheckAvailable() { // GH-90000
        // Just verify the method doesn't throw and returns a boolean
        assertDoesNotThrow(() -> shellcheckRunner.isShellcheckAvailable()); // GH-90000
    }
}
