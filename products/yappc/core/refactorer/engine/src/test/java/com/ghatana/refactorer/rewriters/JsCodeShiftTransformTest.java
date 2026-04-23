package com.ghatana.refactorer.rewriters;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.util.ProcessExec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration-style tests that exercise {@link JsCodeShiftBridge} end-to-end with sample
 * transforms. Tests assume jscodeshift is either installed locally or can be installed into the
 * workspace on demand.
 */
@ExtendWith(JsCodeShiftTransformTest.JsCodeShiftExtension.class) // GH-90000
@Timeout(value = 45, unit = TimeUnit.SECONDS) // GH-90000
@Tag("integration")
/**
 * @doc.type class
 * @doc.purpose Handles js code shift transform test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class JsCodeShiftTransformTest {
    private static final Logger log = LoggerFactory.getLogger(JsCodeShiftTransformTest.class); // GH-90000
    private static final long TIMEOUT_MS = 60_000;

    private JsCodeShiftBridge bridge;
    private Path tempDir;
    private Path srcFile;
    private Path transformFile;
    private Path srcDir;
    private Path transformsDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException { // GH-90000
        this.tempDir = tempDir;
        this.bridge =
                JsCodeShiftBridge.builder() // GH-90000
                        .withJscodeshiftCommand(JsCodeShiftExtension.getJsCodeShiftCommand()) // GH-90000
                        .withTimeoutMs(TIMEOUT_MS) // GH-90000
                        .withDependencyInstallation(false) // GH-90000
                        .withTypeScript(true) // GH-90000
                        .build(); // GH-90000

        this.srcDir = tempDir.resolve("src");
        this.transformsDir = tempDir.resolve("transforms");
        Files.createDirectories(srcDir); // GH-90000
        Files.createDirectories(transformsDir); // GH-90000

        this.srcFile = srcDir.resolve("test.js");
        Files.writeString( // GH-90000
                srcFile,
                "// Test file\n"
                        + "function test() {\n" // GH-90000
                        + "  // This is a test\n"
                        + "  console.log('Hello, world!');\n" // GH-90000
                        + "  return 42;\n"
                        + "}\n");

        this.transformFile = transformsDir.resolve("test-transform.js");
        Files.writeString( // GH-90000
                transformFile,
                "// Simple transform that adds a comment\n"
                        + "module.exports = function(file, api) {\n" // GH-90000
                        + "  const j = api.jscodeshift;\n"
                        + "  const root = j(file.source);\n" // GH-90000
                        + "  const firstNode = root.find(j.Program).get('body', 0).node;\n" // GH-90000
                        + "  firstNode.comments = [\n"
                        + "    j.commentLine(' Added by test transform '),\n" // GH-90000
                        + "    ...(firstNode.comments || [])\n" // GH-90000
                        + "  ];\n"
                        + "  return root.toSource();\n" // GH-90000
                        + "};\n");
    }

    @Test
    @DisplayName("Applies transform to a single file")
    void appliesTransformToSingleFile() throws IOException { // GH-90000
        String before = Files.readString(srcFile); // GH-90000

        ProcessExec.Result result = bridge.transform(tempDir, transformFile, List.of(srcFile)); // GH-90000

        assertSuccessfulResult(result); // GH-90000

        String after = Files.readString(srcFile); // GH-90000
        assertNotEquals(before, after, "File content should change after transform"); // GH-90000
        assertTrue(after.contains("Added by test transform"), "Transform should add a comment");
    }

    @Test
    @DisplayName("Honours --dry option without mutating files")
    void respectsDryRunOption() throws IOException { // GH-90000
        String before = Files.readString(srcFile); // GH-90000

        ProcessExec.Result dryRunResult =
                bridge.transform( // GH-90000
                        tempDir,
                        transformFile,
                        List.of(srcFile), // GH-90000
                        Map.of( // GH-90000
                                "dry", "",
                                "print", ""));

        assertSuccessfulResult(dryRunResult); // GH-90000
        assertEquals(before, Files.readString(srcFile), "Dry run should not mutate file"); // GH-90000

        ProcessExec.Result realRunResult =
                bridge.transform(tempDir, transformFile, List.of(srcFile)); // GH-90000
        assertSuccessfulResult(realRunResult); // GH-90000
        assertNotEquals(before, Files.readString(srcFile), "Real run should mutate file"); // GH-90000
    }

    @Test
    @DisplayName("Surfacing transform failures with invalid code")
    void surfacesTransformErrors() throws IOException { // GH-90000
        Path invalidTransform = transformsDir.resolve("invalid-transform.js");
        Files.writeString(invalidTransform, "module.exports = () => { invalid }"); // GH-90000

        ProcessExec.Result result = bridge.transform(tempDir, invalidTransform, List.of(srcFile)); // GH-90000

        assertNotEquals(0, result.exitCode(), "Invalid transform should exit non-zero"); // GH-90000
        String combinedOutput = result.out() + "\n" + result.err(); // GH-90000
        assertTrue( // GH-90000
                combinedOutput.contains("SyntaxError")
                        || combinedOutput.contains("Error")
                        || combinedOutput.contains("error"),
                "Expected syntax error details in output");
    }

    @Test
    @DisplayName("Gracefully handles missing input files")
    void handlesMissingFiles() { // GH-90000
        Path missing = srcDir.resolve("missing.js");

        ProcessExec.Result result = bridge.transform(tempDir, transformFile, List.of(missing)); // GH-90000

        String combinedOutput = result.out() + "\n" + result.err(); // GH-90000
        if (result.exitCode() == 0) { // GH-90000
            log.warn("jscodeshift succeeded despite missing file");
            boolean warned =
                    combinedOutput.contains("no such file")
                            || combinedOutput.contains("not found")
                            || combinedOutput.contains("does not exist")
                            || combinedOutput.contains("ENOENT")
                            || combinedOutput.contains("No files processed");
            assertTrue(warned, "Expected warning about missing file in output"); // GH-90000
        } else {
            assertTrue( // GH-90000
                    combinedOutput.contains("no such file")
                            || combinedOutput.contains("not found")
                            || combinedOutput.contains("does not exist")
                            || combinedOutput.contains("ENOENT"),
                    "Error output should mention missing file");
        }
    }

    private void assertSuccessfulResult(ProcessExec.Result result) { // GH-90000
        if (result.exitCode() != 0) { // GH-90000
            fail( // GH-90000
                    String.format( // GH-90000
                            "Transform failed with exit code %d\nSTDOUT:\n%s\nSTDERR:\n%s",
                            result.exitCode(), result.out(), result.err())); // GH-90000
        }
    }

    /** JUnit 5 extension that installs jscodeshift locally when required. */
    public static class JsCodeShiftExtension implements BeforeAllCallback {
        private static final Logger log = LoggerFactory.getLogger(JsCodeShiftExtension.class); // GH-90000
        private static final long INSTALL_TIMEOUT_SECONDS = 300;
        private static boolean isInstalled = false;
        private static Path installedBinary;

        @Override
        public void beforeAll(ExtensionContext context) throws Exception { // GH-90000
            if (isInstalled) { // GH-90000
                return;
            }

            if (!isJsCodeShiftAvailable()) { // GH-90000
                log.info("jscodeshift not found, attempting local install...");
                boolean installed = installJsCodeShiftLocally(); // GH-90000
                if (!installed || !isJsCodeShiftAvailable()) { // GH-90000
                    log.warn( // GH-90000
                            "jscodeshift remains unavailable after attempted install; skipping"
                                    + " tests.");
                    Assumptions.assumeTrue(false, "jscodeshift could not be provisioned locally"); // GH-90000
                }
            }

            installedBinary = findLocalJsCodeShiftBinary(); // GH-90000
            isInstalled = true;
        }

        private static boolean isJsCodeShiftAvailable() { // GH-90000
            try {
                Process process =
                        new ProcessBuilder() // GH-90000
                                .command(getJsCodeShiftCommand("--version"))
                                .redirectErrorStream(true) // GH-90000
                                .start(); // GH-90000

                boolean finished = process.waitFor(10, TimeUnit.SECONDS); // GH-90000
                if (!finished) { // GH-90000
                    process.destroyForcibly(); // GH-90000
                    return false;
                }
                return process.exitValue() == 0; // GH-90000
            } catch (Exception e) { // GH-90000
                log.debug("jscodeshift availability check failed", e); // GH-90000
                return false;
            }
        }

        private static boolean installJsCodeShiftLocally() { // GH-90000
            Path projectRoot = Paths.get("").toAbsolutePath();

            try {
                log.info("Installing jscodeshift locally in {}", projectRoot); // GH-90000

                List<String> command =
                        List.of( // GH-90000
                                "npm",
                                "install",
                                "--no-package-lock",
                                "--no-save",
                                "--prefer-offline",
                                "--progress=false",
                                "jscodeshift@latest");

                Map<String, String> envOverrides =
                        Map.ofEntries( // GH-90000
                                Map.entry("npm_config_audit", "false"), // GH-90000
                                Map.entry("npm_config_fund", "false"), // GH-90000
                                Map.entry("npm_config_progress", "false"), // GH-90000
                                Map.entry("npm_config_registry", "https://registry.npmjs.org"), // GH-90000
                                Map.entry("npm_config_proxy", ""), // GH-90000
                                Map.entry("npm_config_https_proxy", "")); // GH-90000

                ProcessExec.Result result =
                        ProcessExec.run( // GH-90000
                                projectRoot,
                                Duration.ofSeconds(INSTALL_TIMEOUT_SECONDS), // GH-90000
                                command,
                                envOverrides);

                if (result.exitCode() != 0) { // GH-90000
                    log.error( // GH-90000
                            "npm install jscodeshift failed with exit code {}\n"
                                    + "stdout:\n"
                                    + "{}\n"
                                    + "stderr:\n"
                                    + "{}",
                            result.exitCode(), // GH-90000
                            result.out(), // GH-90000
                            result.err()); // GH-90000
                    return false;
                }

                log.info("Successfully installed jscodeshift locally");
                installedBinary = findLocalJsCodeShiftBinary(); // GH-90000
                return installedBinary != null;
            } catch (Exception e) { // GH-90000
                log.error("Error installing jscodeshift locally", e); // GH-90000
                return false;
            }
        }

        public static List<String> getJsCodeShiftCommand(String... args) { // GH-90000
            Path binary = installedBinary != null ? installedBinary : findLocalJsCodeShiftBinary(); // GH-90000
            List<String> command = new ArrayList<>(); // GH-90000
            if (binary != null) { // GH-90000
                command.add(binary.toAbsolutePath().toString()); // GH-90000
            } else {
                command.add("npx");
                command.add("--no-install");
                command.add("--quiet");
                command.add("jscodeshift");
            }
            command.addAll(Arrays.asList(args)); // GH-90000
            return command;
        }

        private static Path findLocalJsCodeShiftBinary() { // GH-90000
            Path candidate =
                    Paths.get("")
                            .toAbsolutePath() // GH-90000
                            .resolve("node_modules")
                            .resolve(".bin")
                            .resolve("jscodeshift");
            if (Files.isRegularFile(candidate) && candidate.toFile().canExecute()) { // GH-90000
                return candidate;
            }
            return null;
        }
    }
}
