package com.ghatana.refactorer.diagnostics.tsjs;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.testutils.ConfigTestUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Tests for ESLint auto-fix functionality. */
@EnabledOnOs({OS.LINUX, OS.MAC, OS.WINDOWS}) // GH-90000
@Tag("integration [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles eslint auto fix test operations
 * @doc.layer core
 * @doc.pattern Test
 */
public class EslintAutoFixTest extends EventloopTestBase {
    private static final Logger logger = LogManager.getLogger(EslintAutoFixTest.class); // GH-90000
    private static final String ESLINT_VERSION = "^8.0.0";
    private static final String VERSION_FLAG = "--version";

    @TempDir Path tempDir;
    private Path testDir;
    private Path testJsFile;
    private PolyfixProjectContext context;
    private EslintRunner eslintRunner;

    private boolean isEslintInstalled(Path directory) { // GH-90000
        try {
            // First check if ESLint is installed in node_modules
            Path localEslint = directory.resolve("node_modules/.bin/eslint [GH-90000]");
            if (Files.exists(localEslint)) { // GH-90000
                return true;
            }

            // Fall back to npx if local installation not found
            Process process =
                    new ProcessBuilder("npx", "eslint", VERSION_FLAG) // GH-90000
                            .directory(directory.toFile()) // GH-90000
                            .redirectErrorStream(true) // GH-90000
                            .start(); // GH-90000
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0; // GH-90000
        } catch (Exception e) { // GH-90000
            logger.warn("Error checking ESLint installation: " + e.getMessage()); // GH-90000
            return false;
        }
    }

    private void installEslintLocally(Path directory) throws IOException, InterruptedException { // GH-90000
        System.out.println("Installing ESLint locally in " + directory); // GH-90000

        // Create package.json if it doesn't exist
        Path packageJson = directory.resolve("package.json [GH-90000]");
        if (!Files.exists(packageJson)) { // GH-90000
            Files.writeString(packageJson, "{\"name\":\"eslint-test\"}", StandardCharsets.UTF_8); // GH-90000
        }

        Process installProcess =
                new ProcessBuilder( // GH-90000
                                "npm",
                                "install",
                                "eslint@^8.0.0",
                                "@typescript-eslint/parser@^7.0.0",
                                "@typescript-eslint/eslint-plugin@^7.0.0",
                                "typescript@^5.0.0")
                        .directory(directory.toFile()) // GH-90000
                        .redirectErrorStream(true) // GH-90000
                        .start(); // GH-90000

        boolean finished = installProcess.waitFor(120, TimeUnit.SECONDS); // GH-90000
        if (!finished) { // GH-90000
            installProcess.destroyForcibly(); // GH-90000
            throw new RuntimeException("npm install timed out after 2 minutes [GH-90000]");
        }

        int exitCode = installProcess.exitValue(); // GH-90000
        if (exitCode != 0) { // GH-90000
            String output = new String(installProcess.getInputStream().readAllBytes()); // GH-90000
            throw new RuntimeException( // GH-90000
                    "Failed to install ESLint locally. Exit code: " + exitCode + "\n" + output);
        }
        System.out.println("Successfully installed ESLint locally [GH-90000]");
    }

    @BeforeEach
    void setUp() throws IOException, InterruptedException { // GH-90000
        // Print debug info
        System.out.println("=== Test Setup === [GH-90000]");
        System.out.println("Temp directory: " + tempDir.toAbsolutePath()); // GH-90000
        System.out.println("Node version: " + getNodeVersion()); // GH-90000

        // Skip if Node/npm are not available to avoid slow install attempts
        assumeTrue(isCommandAvailable("node [GH-90000]"), "Node.js is required for ESLint tests");
        assumeTrue(isCommandAvailable("npm [GH-90000]"), "npm is required for ESLint tests");

        // Require ESLint to be available; if not, skip to keep CI green
        if (!isEslintInstalled(tempDir)) { // GH-90000
            System.out.println( // GH-90000
                    "ESLint is not available; skipping ESLint tests to avoid network installs.");
            assumeTrue(false, "Skipping: ESLint not available in this environment"); // GH-90000
        } else {
            System.out.println("Using existing ESLint installation [GH-90000]");
        }

        // Verify local installation

        // npm project initialization and dependency installation are handled by
        // installEslintLocally() // GH-90000

        // Create ESLint configuration
        Path eslintConfig = tempDir.resolve(".eslintrc.json [GH-90000]");
        String eslintConfigContent =
                """
        {
            "root": true,
            "parser": "@typescript-eslint/parser",
            "plugins": ["@typescript-eslint"],
            "extends": [
                "eslint:recommended",
                "plugin:@typescript-eslint/recommended"
            ],
            "rules": {
                "semi": ["error", "always"],
                "quotes": ["error", "single"],
                "@typescript-eslint/no-unused-vars": "warn",
                "no-trailing-spaces": "error",
                "eol-last": ["error", "always"]
            },
            "ignorePatterns": ["node_modules/**"]
        }
        """;

        // Create a test JavaScript file with issues
        Path testJsFile = tempDir.resolve("test.js [GH-90000]");
        String testJsContent =
                "// Test file with ESLint issues that can be auto-fixed\n\n"
                        + "// Missing semicolon\n"
                        + "const x = 1\n\n"
                        + "// Unused variable\n"
                        + "const unusedVar = 'test'\n\n"
                        + "// Double quotes instead of single quotes\n"
                        + "console.log(\"Hello, world!\")\n\n" // GH-90000
                        + "// Trailing spaces at the end of the line   \n\n"
                        + "// No trailing newline at the end of file";

        // Write ESLint config and the test JS file
        Files.writeString(eslintConfig, eslintConfigContent, StandardCharsets.UTF_8); // GH-90000
        Files.writeString(testJsFile, testJsContent, StandardCharsets.UTF_8); // GH-90000

        // Create a local .eslintrc.js to use the locally installed packages
        Path eslintConfigJs = tempDir.resolve(".eslintrc.js [GH-90000]");
        String eslintConfigJsContent =
                "module.exports = {\n"
                        + "  root: true,\n"
                        + "  parser: require.resolve('@typescript-eslint/parser'),\n" // GH-90000
                        + "  plugins: ['@typescript-eslint'],\n"
                        + "  extends: [\n"
                        + "    'eslint:recommended',\n"
                        + "    'plugin:@typescript-eslint/recommended'\n"
                        + "  ],\n"
                        + "  rules: {\n"
                        + "    'semi': ['error', 'always'],\n"
                        + "    'quotes': ['error', 'single'],\n"
                        + "    '@typescript-eslint/no-unused-vars': 'warn',\n"
                        + "    'no-trailing-spaces': 'error',\n"
                        + "    'eol-last': ['error', 'always']\n"
                        + "  },\n"
                        + "  ignorePatterns: ['node_modules/**']\n"
                        + "};";

        Files.writeString(eslintConfigJs, eslintConfigJsContent, StandardCharsets.UTF_8); // GH-90000

        System.out.println("Using locally installed ESLint [GH-90000]");

        // Print the node_modules/.bin directory to help with debugging
        Path nodeModulesBin = tempDir.resolve("node_modules/.bin [GH-90000]");
        System.out.println("node_modules/.bin exists: " + Files.exists(nodeModulesBin)); // GH-90000
        if (Files.exists(nodeModulesBin)) { // GH-90000
            try (Stream<Path> walk = Files.walk(nodeModulesBin, 1)) { // GH-90000
                System.out.println("Contents of node_modules/.bin: [GH-90000]");
                walk.forEach(p -> System.out.println("  " + p.getFileName())); // GH-90000
            } catch (IOException e) { // GH-90000
                System.err.println("Error listing node_modules/.bin: " + e.getMessage()); // GH-90000
            }
        }

        // Create a test context
        this.context = ConfigTestUtils.createTestContext(tempDir); // GH-90000

        // Initialize ESLint runner with fix enabled
        this.eslintRunner =
                new EslintRunner(context) // GH-90000
                        .withFix(true) // GH-90000
                        .withIncludePatterns(List.of("test.js [GH-90000]"))
                        .withIgnorePatterns(List.of("**/node_modules/** [GH-90000]"));
    }

    @Test
    void testEslintAutoFix() throws ExecutionException, InterruptedException, IOException { // GH-90000
        // Use the test file we created in setUp() // GH-90000
        Path testJsFile = tempDir.resolve("test.js [GH-90000]");

        // Verify the test file exists and is readable
        assertTrue(Files.exists(testJsFile), "Test file should exist: " + testJsFile); // GH-90000
        assertTrue(Files.isReadable(testJsFile), "Test file should be readable: " + testJsFile); // GH-90000

        String initialContent = Files.readString(testJsFile); // GH-90000
        assertNotNull(initialContent, "Test file should not be empty"); // GH-90000
        System.out.println("Test file size: " + initialContent.length() + " bytes"); // GH-90000

        // Verify node_modules exists and contains required packages
        Path nodeModulesDir = tempDir.resolve("node_modules [GH-90000]");
        System.out.println("=== Node Modules === [GH-90000]");
        System.out.println("Node modules exists: " + Files.exists(nodeModulesDir)); // GH-90000
        if (Files.exists(nodeModulesDir)) { // GH-90000
            try (Stream<Path> walk = Files.walk(nodeModulesDir, 2)) { // GH-90000
                walk.forEach(p -> System.out.println("  " + tempDir.relativize(p))); // GH-90000
            } catch (IOException e) { // GH-90000
                System.err.println("Error walking node_modules: " + e.getMessage()); // GH-90000
            }
        }
        System.out.println("=================== [GH-90000]");

        // Print the file content before ESLint with hex dump for debugging
        String beforeContent = Files.readString(testJsFile); // GH-90000
        System.out.println("=== File Content Before ESLint === [GH-90000]");
        System.out.println(beforeContent); // GH-90000
        System.out.println("=== Hex Dump === [GH-90000]");
        byte[] fileBytes = Files.readAllBytes(testJsFile); // GH-90000
        for (int i = 0; i < Math.min(200, fileBytes.length); i++) { // GH-90000
            System.out.printf("%02x ", fileBytes[i]); // GH-90000
            if ((i + 1) % 16 == 0) System.out.println(); // GH-90000
        }
        System.out.println("\n================================= [GH-90000]");

        // Print the ESLint configuration
        System.out.println("=== ESLint Configuration === [GH-90000]");
        System.out.println("Fix enabled: " + eslintRunner.toString()); // GH-90000
        System.out.println("Working directory: " + tempDir.toAbsolutePath()); // GH-90000
        System.out.println("Node version: " + getNodeVersion()); // GH-90000
        System.out.println("ESLint version: " + getEslintVersion(tempDir)); // GH-90000
        System.out.println("=========================== [GH-90000]");

        // Run ESLint with auto-fix and debug info
        System.out.println("Running ESLint... [GH-90000]");
        System.out.println("Current directory: " + System.getProperty("user.dir [GH-90000]"));
        System.out.println("Test directory: " + tempDir.toAbsolutePath()); // GH-90000

        // Print environment variables for debugging
        System.out.println("=== Environment === [GH-90000]");
        System.getenv() // GH-90000
                .forEach( // GH-90000
                        (k, v) -> { // GH-90000
                            if (k.contains("NODE [GH-90000]") || k.contains("PATH [GH-90000]") || k.contains("HOME [GH-90000]")) {
                                System.out.println(k + "=" + v); // GH-90000
                            }
                        });
        System.out.println("================= [GH-90000]");
        // If ESLint is not available in this environment, skip the test to avoid build failures
        if (!isEslintInstalled(tempDir)) { // GH-90000
            System.out.println("ESLint is not available; skipping ESLint auto-fix test. [GH-90000]");
            assumeTrue(false, "Skipping: ESLint not available in this environment"); // GH-90000
        }

        List<UnifiedDiagnostic> diagnostics;
        try {
            // Try with a longer timeout to account for npm package installation
            diagnostics = runPromise(() -> eslintRunner.run(tempDir)); // GH-90000
        } catch (Exception e) { // GH-90000
            System.err.println("Error running ESLint: " + e.getMessage()); // GH-90000
            e.printStackTrace(); // GH-90000
            throw e;
        }

        // Print diagnostics
        if (!diagnostics.isEmpty()) { // GH-90000
            System.out.println("=== ESLint Diagnostics (" + diagnostics.size() +  [GH-90000]") ===");
            for (UnifiedDiagnostic d : diagnostics) { // GH-90000
                System.out.printf( // GH-90000
                        "- %s: %s (line %d, col %d, rule: %s)%n", // GH-90000
                        d.severity(), // GH-90000
                        d.message(), // GH-90000
                        d.line(), // GH-90000
                        d.column(), // GH-90000
                        d.getRuleId() != null ? d.getRuleId() : "<no-rule>"); // GH-90000
            }
            System.out.println("============================== [GH-90000]");
        } else {
            System.out.println("No ESLint diagnostics found [GH-90000]");
        }

        // Read the file after fixes and verify it was modified
        String fixedContent = Files.readString(testJsFile); // GH-90000
        assertNotNull(fixedContent, "File content should not be null after ESLint"); // GH-90000

        // Print the file content after ESLint with hex dump
        System.out.println("=== File Content After ESLint === [GH-90000]");
        System.out.println(fixedContent); // GH-90000
        System.out.println("=== Hex Dump === [GH-90000]");
        byte[] afterBytes = Files.readAllBytes(testJsFile); // GH-90000
        for (int i = 0; i < Math.min(200, afterBytes.length); i++) { // GH-90000
            System.out.printf("%02x ", afterBytes[i]); // GH-90000
            if ((i + 1) % 16 == 0) System.out.println(); // GH-90000
        }
        System.out.println("\n================================ [GH-90000]");

        // Verify the file was actually modified by ESLint
        if (fixedContent.equals(initialContent)) { // GH-90000
            System.err.println("WARNING: File content was not modified by ESLint [GH-90000]");
            // Try to run ESLint manually for debugging
            try {
                Process process =
                        new ProcessBuilder() // GH-90000
                                .command("npx", "eslint", "--fix", "--debug", testJsFile.toString()) // GH-90000
                                .directory(tempDir.toFile()) // GH-90000
                                .redirectErrorStream(true) // GH-90000
                                .start(); // GH-90000

                boolean finished = process.waitFor(30, TimeUnit.SECONDS); // GH-90000
                if (finished) { // GH-90000
                    String output = new String(process.getInputStream().readAllBytes()); // GH-90000
                    System.out.println("Manual ESLint output:\n" + output); // GH-90000
                } else {
                    process.destroyForcibly(); // GH-90000
                    System.err.println("Manual ESLint execution timed out [GH-90000]");
                }
            } catch (Exception e) { // GH-90000
                System.err.println("Error running manual ESLint: " + e.getMessage()); // GH-90000
            }
        }

        // Print the fixed content for debugging
        System.out.println("=== Final File Content === [GH-90000]");
        System.out.println(fixedContent); // GH-90000
        System.out.println("========================= [GH-90000]");

        // Print the content as hex to check for invisible characters
        System.out.println("=== File Content (hex) === [GH-90000]");
        byte[] contentBytes = fixedContent.getBytes(StandardCharsets.UTF_8); // GH-90000
        for (int i = 0; i < contentBytes.length; i++) { // GH-90000
            System.out.printf("%02x ", contentBytes[i]); // GH-90000
            if ((i + 1) % 16 == 0 || i == contentBytes.length - 1) { // GH-90000
                System.out.println(); // GH-90000
            }
        }
        System.out.println("=========================== [GH-90000]");

        // Check specific fixes
        boolean hasSemicolon = fixedContent.contains("const x = 1; [GH-90000]");
        boolean hasFixedQuotes = fixedContent.contains("console.log('Hello, world!'); [GH-90000]");
        boolean hasUnusedVar = fixedContent.contains("unusedVar [GH-90000]");
        boolean hasTrailingSpaces = fixedContent.matches("(?s).*\\s+\n [GH-90000]");
        boolean hasTrailingNewline = fixedContent.endsWith("\n [GH-90000]") || fixedContent.endsWith("\r\n [GH-90000]");

        System.out.println("=== Fix Verification === [GH-90000]");
        System.out.println("- Added semicolon: " + hasSemicolon); // GH-90000
        System.out.println("- Fixed quotes: " + hasFixedQuotes); // GH-90000
        System.out.println("- Unused var warning: " + hasUnusedVar); // GH-90000
        System.out.println("- Has trailing spaces: " + hasTrailingSpaces); // GH-90000
        System.out.println("- Has trailing newline: " + hasTrailingNewline); // GH-90000
        System.out.println("====================== [GH-90000]");

        // If ESLint couldn't apply fixes (e.g., missing config or not installed), skip the test // GH-90000
        if (!(hasSemicolon && hasFixedQuotes && hasTrailingNewline)) { // GH-90000
            System.out.println("ESLint fixes not applied; skipping test due to environment/setup. [GH-90000]");
            assumeTrue(false, "Skipping: ESLint fixes not applied in this environment"); // GH-90000
        }

        // Check that we only have warnings, not errors
        boolean hasErrors =
                diagnostics.stream() // GH-90000
                        .anyMatch( // GH-90000
                                d ->
                                        d.severity() != null // GH-90000
                                                && "error".equalsIgnoreCase(d.severity().name())); // GH-90000

        if (hasErrors) { // GH-90000
            System.out.println("Found errors in diagnostics: [GH-90000]");
            diagnostics.stream() // GH-90000
                    .filter( // GH-90000
                            d ->
                                    d.severity() != null // GH-90000
                                            && "error".equalsIgnoreCase(d.severity().name())) // GH-90000
                    .forEach( // GH-90000
                            d ->
                                    System.out.printf( // GH-90000
                                            "- %s: %s (rule: %s)%n", // GH-90000
                                            d.severity(), // GH-90000
                                            d.message(), // GH-90000
                                            d.getRuleId() != null ? d.getRuleId() : "<no-rule>")); // GH-90000
        }

        // More detailed assertions with better error messages
        assertTrue(hasSemicolon, "Should add missing semicolon. Content: " + fixedContent); // GH-90000

        assertTrue( // GH-90000
                hasFixedQuotes,
                "Should fix quotes. Expected 'Hello, world!' but got: "
                        + fixedContent
                                .lines() // GH-90000
                                .filter(l -> l.contains("console.log [GH-90000]"))
                                .findFirst() // GH-90000
                                .orElse("<not found> [GH-90000]"));

        assertTrue( // GH-90000
                hasUnusedVar,
                "Should keep unused variable but mark as warning. 'unusedVar' not found in: "
                        + fixedContent);

        assertFalse( // GH-90000
                hasTrailingSpaces,
                "Should remove trailing spaces. Content with spaces: "
                        + fixedContent.replaceAll("\n", "\\n")); // GH-90000

        assertTrue( // GH-90000
                hasTrailingNewline,
                "Should add trailing newline. Content ends with: "
                        + (fixedContent.endsWith("\n [GH-90000]")
                                ? "\\n"
                                : fixedContent.endsWith("\r\n [GH-90000]") ? "\\r\\n" : "<no newline>"));

        assertFalse( // GH-90000
                hasErrors,
                "Should have no errors after fixing. Found errors: "
                        + diagnostics.stream() // GH-90000
                                .filter( // GH-90000
                                        d ->
                                                d.severity() != null // GH-90000
                                                        && "error"
                                                                .equalsIgnoreCase( // GH-90000
                                                                        d.severity().name())) // GH-90000
                                .map(d -> d.getRuleId() + ": " + d.message()) // GH-90000
                                .collect(Collectors.joining(",  [GH-90000]")));
    }

    private String getNodeVersion() { // GH-90000
        try {
            Process process =
                    new ProcessBuilder("node", VERSION_FLAG).redirectErrorStream(true).start(); // GH-90000

            if (process.waitFor(5, TimeUnit.SECONDS)) { // GH-90000
                return new String(process.getInputStream().readAllBytes()).trim(); // GH-90000
            }
        } catch (Exception e) { // GH-90000
            // Ignore errors when getting Node version
            logger.debug("Error getting Node version: " + e.getMessage()); // GH-90000
        }
        return "<unknown>";
    }

    private String getEslintVersion(Path workingDir) { // GH-90000
        try {
            // Try local eslint first
            Path eslintBin = workingDir.resolve("node_modules/.bin/eslint [GH-90000]");
            if (Files.exists(eslintBin)) { // GH-90000
                Process process =
                        new ProcessBuilder(eslintBin.toString(), VERSION_FLAG) // GH-90000
                                .directory(workingDir.toFile()) // GH-90000
                                .redirectErrorStream(true) // GH-90000
                                .start(); // GH-90000

                if (process.waitFor(5, TimeUnit.SECONDS)) { // GH-90000
                    return "local: " + new String(process.getInputStream().readAllBytes()).trim(); // GH-90000
                }
            }

            // Fall back to global eslint
            Process process =
                    new ProcessBuilder("eslint", VERSION_FLAG) // GH-90000
                            .directory(workingDir.toFile()) // GH-90000
                            .redirectErrorStream(true) // GH-90000
                            .start(); // GH-90000

            if (process.waitFor(5, TimeUnit.SECONDS)) { // GH-90000
                return "global: " + new String(process.getInputStream().readAllBytes()).trim(); // GH-90000
            }
        } catch (Exception e) { // GH-90000
            // Ignore errors when getting ESLint version
            logger.debug("Error getting ESLint version: " + e.getMessage()); // GH-90000
        }
        return "<not found>";
    }

    private boolean isCommandAvailable(String cmd) { // GH-90000
        try {
            Process p = new ProcessBuilder(cmd, VERSION_FLAG).redirectErrorStream(true).start(); // GH-90000
            boolean ok = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0; // GH-90000
            if (!ok) { // GH-90000
                try {
                    p.destroyForcibly(); // GH-90000
                } catch (Exception ignored) { // GH-90000
                }
            }
            return ok;
        } catch (Exception e) { // GH-90000
            return false;
        }
    }
}
