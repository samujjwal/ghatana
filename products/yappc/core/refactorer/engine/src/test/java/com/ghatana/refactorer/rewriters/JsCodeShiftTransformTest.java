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
@ExtendWith(JsCodeShiftTransformTest.JsCodeShiftExtension.class)
@Timeout(value = 45, unit = TimeUnit.SECONDS)
@Tag("integration")
/**
 * @doc.type class
 * @doc.purpose Handles js code shift transform test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class JsCodeShiftTransformTest {
    private static final Logger log = LoggerFactory.getLogger(JsCodeShiftTransformTest.class);
    private static final long TIMEOUT_MS = 60_000;

    private JsCodeShiftBridge bridge;
    private Path tempDir;
    private Path srcFile;
    private Path transformFile;
    private Path srcDir;
    private Path transformsDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        this.tempDir = tempDir;
        this.bridge =
                JsCodeShiftBridge.builder()
                        .withJscodeshiftCommand(JsCodeShiftExtension.getJsCodeShiftCommand())
                        .withTimeoutMs(TIMEOUT_MS)
                        .withDependencyInstallation(false)
                        .withTypeScript(true)
                        .build();

        this.srcDir = tempDir.resolve("src");
        this.transformsDir = tempDir.resolve("transforms");
        Files.createDirectories(srcDir);
        Files.createDirectories(transformsDir);

        this.srcFile = srcDir.resolve("test.js");
        Files.writeString(
                srcFile,
                "// Test file\n"
                        + "function test() {\n"
                        + "  // This is a test\n"
                        + "  console.log('Hello, world!');\n"
                        + "  return 42;\n"
                        + "}\n");

        this.transformFile = transformsDir.resolve("test-transform.js");
        Files.writeString(
                transformFile,
                "// Simple transform that adds a comment\n"
                        + "module.exports = function(file, api) {\n"
                        + "  const j = api.jscodeshift;\n"
                        + "  const root = j(file.source);\n"
                        + "  const firstNode = root.find(j.Program).get('body', 0).node;\n"
                        + "  firstNode.comments = [\n"
                        + "    j.commentLine(' Added by test transform '),\n"
                        + "    ...(firstNode.comments || [])\n"
                        + "  ];\n"
                        + "  return root.toSource();\n"
                        + "};\n");
    }

    @Test
    @DisplayName("Applies transform to a single file")
    void appliesTransformToSingleFile() throws IOException {
        String before = Files.readString(srcFile);

        ProcessExec.Result result = bridge.transform(tempDir, transformFile, List.of(srcFile));

        assertSuccessfulResult(result);

        String after = Files.readString(srcFile);
        assertNotEquals(before, after, "File content should change after transform");
        assertTrue(after.contains("Added by test transform"), "Transform should add a comment");
    }

    @Test
    @DisplayName("Honours --dry option without mutating files")
    void respectsDryRunOption() throws IOException {
        String before = Files.readString(srcFile);

        ProcessExec.Result dryRunResult =
                bridge.transform(
                        tempDir,
                        transformFile,
                        List.of(srcFile),
                        Map.of(
                                "dry", "",
                                "print", ""));

        assertSuccessfulResult(dryRunResult);
        assertEquals(before, Files.readString(srcFile), "Dry run should not mutate file");

        ProcessExec.Result realRunResult =
                bridge.transform(tempDir, transformFile, List.of(srcFile));
        assertSuccessfulResult(realRunResult);
        assertNotEquals(before, Files.readString(srcFile), "Real run should mutate file");
    }

    @Test
    @DisplayName("Surfacing transform failures with invalid code")
    void surfacesTransformErrors() throws IOException {
        Path invalidTransform = transformsDir.resolve("invalid-transform.js");
        Files.writeString(invalidTransform, "module.exports = () => { invalid }");

        ProcessExec.Result result = bridge.transform(tempDir, invalidTransform, List.of(srcFile));

        assertNotEquals(0, result.exitCode(), "Invalid transform should exit non-zero");
        String combinedOutput = result.out() + "\n" + result.err();
        assertTrue(
                combinedOutput.contains("SyntaxError")
                        || combinedOutput.contains("Error")
                        || combinedOutput.contains("error"),
                "Expected syntax error details in output");
    }

    @Test
    @DisplayName("Gracefully handles missing input files")
    void handlesMissingFiles() {
        Path missing = srcDir.resolve("missing.js");

        ProcessExec.Result result = bridge.transform(tempDir, transformFile, List.of(missing));

        String combinedOutput = result.out() + "\n" + result.err();
        if (result.exitCode() == 0) {
            log.warn("jscodeshift succeeded despite missing file");
            boolean warned =
                    combinedOutput.contains("no such file")
                            || combinedOutput.contains("not found")
                            || combinedOutput.contains("does not exist")
                            || combinedOutput.contains("ENOENT")
                            || combinedOutput.contains("No files processed");
            assertTrue(warned, "Expected warning about missing file in output");
        } else {
            assertTrue(
                    combinedOutput.contains("no such file")
                            || combinedOutput.contains("not found")
                            || combinedOutput.contains("does not exist")
                            || combinedOutput.contains("ENOENT"),
                    "Error output should mention missing file");
        }
    }

    private void assertSuccessfulResult(ProcessExec.Result result) {
        if (result.exitCode() != 0) {
            fail(
                    String.format(
                            "Transform failed with exit code %d\nSTDOUT:\n%s\nSTDERR:\n%s",
                            result.exitCode(), result.out(), result.err()));
        }
    }

    /** JUnit 5 extension that installs jscodeshift locally when required. */
    public static class JsCodeShiftExtension implements BeforeAllCallback {
        private static final Logger log = LoggerFactory.getLogger(JsCodeShiftExtension.class);
        private static final long INSTALL_TIMEOUT_SECONDS = 300;
        private static boolean isInstalled = false;
        private static Path installedBinary;

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            if (isInstalled) {
                return;
            }

            if (!isJsCodeShiftAvailable()) {
                log.info("jscodeshift not found, attempting local install...");
                boolean installed = installJsCodeShiftLocally();
                if (!installed || !isJsCodeShiftAvailable()) {
                    log.warn(
                            "jscodeshift remains unavailable after attempted install; skipping"
                                    + " tests.");
                    Assumptions.assumeTrue(false, "jscodeshift could not be provisioned locally");
                }
            }

            installedBinary = findLocalJsCodeShiftBinary();
            isInstalled = true;
        }

        private static boolean isJsCodeShiftAvailable() {
            try {
                Process process =
                        new ProcessBuilder()
                                .command(getJsCodeShiftCommand("--version"))
                                .redirectErrorStream(true)
                                .start();

                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return false;
                }
                return process.exitValue() == 0;
            } catch (Exception e) {
                log.debug("jscodeshift availability check failed", e);
                return false;
            }
        }

        private static boolean installJsCodeShiftLocally() {
            Path projectRoot = Paths.get("").toAbsolutePath();

            try {
                log.info("Installing jscodeshift locally in {}", projectRoot);

                List<String> command =
                        List.of(
                                "npm",
                                "install",
                                "--no-package-lock",
                                "--no-save",
                                "--prefer-offline",
                                "--progress=false",
                                "jscodeshift@latest");

                Map<String, String> envOverrides =
                        Map.ofEntries(
                                Map.entry("npm_config_audit", "false"),
                                Map.entry("npm_config_fund", "false"),
                                Map.entry("npm_config_progress", "false"),
                                Map.entry("npm_config_registry", "https://registry.npmjs.org"),
                                Map.entry("npm_config_proxy", ""),
                                Map.entry("npm_config_https_proxy", ""));

                ProcessExec.Result result =
                        ProcessExec.run(
                                projectRoot,
                                Duration.ofSeconds(INSTALL_TIMEOUT_SECONDS),
                                command,
                                envOverrides);

                if (result.exitCode() != 0) {
                    log.error(
                            "npm install jscodeshift failed with exit code {}\n"
                                    + "stdout:\n"
                                    + "{}\n"
                                    + "stderr:\n"
                                    + "{}",
                            result.exitCode(),
                            result.out(),
                            result.err());
                    return false;
                }

                log.info("Successfully installed jscodeshift locally");
                installedBinary = findLocalJsCodeShiftBinary();
                return installedBinary != null;
            } catch (Exception e) {
                log.error("Error installing jscodeshift locally", e);
                return false;
            }
        }

        public static List<String> getJsCodeShiftCommand(String... args) {
            Path binary = installedBinary != null ? installedBinary : findLocalJsCodeShiftBinary();
            List<String> command = new ArrayList<>();
            if (binary != null) {
                command.add(binary.toAbsolutePath().toString());
            } else {
                command.add("npx");
                command.add("--no-install");
                command.add("--quiet");
                command.add("jscodeshift");
            }
            command.addAll(Arrays.asList(args));
            return command;
        }

        private static Path findLocalJsCodeShiftBinary() {
            Path candidate =
                    Paths.get("")
                            .toAbsolutePath()
                            .resolve("node_modules")
                            .resolve(".bin")
                            .resolve("jscodeshift");
            if (Files.isRegularFile(candidate) && candidate.toFile().canExecute()) {
                return candidate;
            }
            return null;
        }
    }
}
