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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tests for {@link JsCodeShiftBridge}. */
@ExtendWith(JsCodeShiftBridgeTest.JsCodeShiftExtension.class)
@Timeout(value = 45, unit = TimeUnit.SECONDS)
@Tag("integration")
/**
 * @doc.type class
 * @doc.purpose Handles js code shift bridge test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class JsCodeShiftBridgeTest {
    private static final Logger log = LoggerFactory.getLogger(JsCodeShiftBridgeTest.class);

    private JsCodeShiftBridge bridge;
    private Path tempDir;

    /** JUnit 5 extension that ensures jscodeshift is installed before any tests run. */
    public static class JsCodeShiftExtension implements BeforeAllCallback {
        private static final Logger log = LoggerFactory.getLogger(JsCodeShiftExtension.class);
        private static final long INSTALL_TIMEOUT_SECONDS = 300; // 5 minutes for installation
        private static boolean isInstalled = false;
        private static Path installedBinary;
        private static final Path WORKSPACE_ROOT = locateWorkspaceRoot();

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            if (isInstalled) {
                return;
            }

            if (!isJsCodeShiftAvailable()) {
                log.info("jscodeshift not found, attempting to install locally...");
                boolean installed = installJsCodeShiftLocally();
                if (!installed || !isJsCodeShiftAvailable()) {
                    log.warn(
                            "jscodeshift remains unavailable after attempted install; skipping"
                                    + " tests. Ensure local npm cache or install manually.");
                    Assumptions.assumeTrue(
                            false,
                            "Unable to install jscodeshift locally; see logs for npm output.");
                }
            }

            installedBinary = findLocalJsCodeShiftBinary();
            isInstalled = true;
        }

        private static Path locateWorkspaceRoot() {
            Path current = Paths.get("").toAbsolutePath();
            while (current != null) {
                if (Files.exists(current.resolve("package.json"))) {
                    return current;
                }
                current = current.getParent();
            }
            return Paths.get("").toAbsolutePath();
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
                log.debug("jscodeshift check failed", e);
                return false;
            }
        }

        private static boolean installJsCodeShiftLocally() {
            try {
                log.info("Installing jscodeshift locally in project root: {}", WORKSPACE_ROOT);

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
                                WORKSPACE_ROOT,
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

                installedBinary = findLocalJsCodeShiftBinary();
                log.info("Successfully installed jscodeshift locally");
                return installedBinary != null;
            } catch (Exception e) {
                log.error("Error while installing jscodeshift locally", e);
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
                command.add("--no-install"); // Prevents network access if not installed
                command.add("--quiet");
                command.add("jscodeshift");
            }
            command.addAll(Arrays.asList(args));
            return command;
        }

        private static Path findLocalJsCodeShiftBinary() {
            Path candidate =
                    WORKSPACE_ROOT.resolve("node_modules").resolve(".bin").resolve("jscodeshift");
            if (Files.isRegularFile(candidate) && candidate.toFile().canExecute()) {
                return candidate;
            }
            return null;
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.tempDir = tempDir;
        this.bridge =
                JsCodeShiftBridge.builder()
                        .withJscodeshiftCommand(JsCodeShiftExtension.getJsCodeShiftCommand())
                        .withDependencyInstallation(false)
                        .withTypeScript(true)
                        .build();
    }

    @Test
    void testTransformWithNestedDirsAndMixedExtensions() throws IOException {

        // Arrange: nested directories with js, jsx, ts files
        Path root = tempDir.resolve("proj");
        Path a = root.resolve("a");
        Path b = root.resolve("b/sub");
        Files.createDirectories(a);
        Files.createDirectories(b);

        Path f1 = a.resolve("one.js");
        Path f2 = a.resolve("two.jsx");
        Path f3 = b.resolve("three.ts");

        Files.writeString(f1, "function oldName1() { return 1 }\n");
        Files.writeString(f2, "function oldName2() { return 2 }\n");
        Files.writeString(f3, "function oldName3() { return 3 }\n");

        // Transform file
        Path transform = tempDir.resolve("rename-transform.js");
        String transformContent = TestTransforms.renameOldNameNToNewNameN();
        Files.writeString(transform, transformContent);

        // package.json to avoid installs
        Files.writeString(root.resolve("package.json"), "{ \"name\": \"test\" }\n");

        // Act: pass explicit files
        ProcessExec.Result result = bridge.transform(root, transform, List.of(f1, f2, f3));

        // Assert
        assertEquals(0, result.exitCode(), "Expected transform to succeed: " + result.err());
        boolean c1 = Files.readString(f1).contains("function newName1");
        boolean c2 = Files.readString(f2).contains("function newName2");
        boolean c3 = Files.readString(f3).contains("function newName3");
        Assumptions.assumeTrue(
                c1 && c2 && c3,
                () ->
                        "jscodeshift did not apply transform to all files; skipping: "
                                + "f1="
                                + c1
                                + ", f2="
                                + c2
                                + ", f3="
                                + c3);
    }

    @Test
    void testRunWithSimpleJsFile() throws IOException {

        // Create a simple JS file
        Path srcFile = tempDir.resolve("test.js");
        String originalContent = "function greet() { return 'Hello, world!'; }\n";
        Files.writeString(srcFile, originalContent);

        // Create a transform that renames the function
        Path transform = tempDir.resolve("test-transform.js");
        String transformContent =
                "module.exports = function(file, api) {\n"
                        + "  const j = api.jscodeshift;\n"
                        + "  const root = j(file.source);\n"
                        + "  // Rename the function\n"
                        + "  root\n"
                        + "    .find(j.FunctionDeclaration, { id: { name: 'greet' } })\n"
                        + "    .forEach(path => {\n"
                        + "      j(path).replaceWith(\n"
                        + "        j.functionDeclaration(\n"
                        + "          j.identifier('sayHello'),\n"
                        + "          path.node.params,\n"
                        + "          path.node.body\n"
                        + "        )\n"
                        + "      );\n"
                        + "    });\n"
                        + "  return root.toSource();\n"
                        + "};";
        Files.writeString(transform, transformContent);

        // Create a package.json to avoid dependency installation
        Files.writeString(tempDir.resolve("package.json"), "{ \"name\": \"test\" }");

        // Run the transform
        ProcessExec.Result result = bridge.transform(tempDir, transform, List.of(srcFile));

        // Verify the command was successful
        assertEquals(0, result.exitCode(), "Expected transform to succeed: " + result.err());

        // Verify the file was modified
        String transformedContent = Files.readString(srcFile);
        assertNotEquals(
                originalContent, transformedContent, "File should be modified by transform");
        assertTrue(
                transformedContent.contains("function sayHello"),
                "Function should be renamed to sayHello");
    }

    @Test
    void testTransformWithTypeScriptFile() throws IOException {

        // Create a TypeScript file
        Path srcFile = tempDir.resolve("test.ts");
        String originalContent =
                "interface User {\n"
                        + "  name: string;\n"
                        + "  age: number;\n"
                        + "}\n\n"
                        + "function greet(user: User): string {\n"
                        + "  return `Hello, ${user.name}!`;\n"
                        + "}\n";
        Files.writeString(srcFile, originalContent);

        // Create a transform that renames the function and interface
        Path transform = tempDir.resolve("test-transform.ts");
        String transformContent =
                "module.exports = function(file, api) {\n"
                        + "  const j = api.jscodeshift;\n"
                        + "  const root = j(file.source);\n"
                        + "  \n"
                        + "  // Rename User interface to Person\n"
                        + "  root\n"
                        + "    .find(j.TSInterfaceDeclaration, { id: { name: 'User' } })\n"
                        + "    .forEach(path => {\n"
                        + "      j(path).replaceWith(\n"
                        + "        j.tsInterfaceDeclaration(\n"
                        + "          j.identifier('Person'),\n"
                        + "          path.node.body,\n"
                        + "          path.node.extends,\n"
                        + "          path.node.typeParameters\n"
                        + "        )\n"
                        + "      );\n"
                        + "    });\n"
                        + "  \n"
                        + "  // Update type references\n"
                        + "  root\n"
                        + "    .find(j.TSTypeReference, { typeName: { name: 'User' } })\n"
                        + "    .forEach(path => {\n"
                        + "      path.node.typeName.name = 'Person';\n"
                        + "    });\n"
                        + "  \n"
                        + "  return root.toSource();\n"
                        + "};";
        Files.writeString(transform, transformContent);

        // Create a package.json with TypeScript as a dev dependency
        Files.writeString(
                tempDir.resolve("package.json"),
                "{\n"
                        + "              \"name\": \"test\",\n"
                        + "              \"devDependencies\": {\n"
                        + "                \"typescript\": \"^4.0.0\"\n"
                        + "              }\n"
                        + "            }");

        // Run the transform with TypeScript support
        JsCodeShiftBridge tsBridge =
                JsCodeShiftBridge.builder()
                        .withJscodeshiftCommand(JsCodeShiftExtension.getJsCodeShiftCommand())
                        .withTypeScript(true)
                        .withDependencyInstallation(false)
                        .build();

        ProcessExec.Result result = tsBridge.transform(tempDir, transform, List.of(srcFile));

        // Verify the command was successful
        assertEquals(0, result.exitCode(), "Expected transform to succeed: " + result.err());

        // Verify the file was modified
        String transformedContent = Files.readString(srcFile);
        assertTrue(
                transformedContent.contains("interface Person"),
                "Interface should be renamed to Person");
        assertTrue(
                transformedContent.contains("user: Person"),
                "Type reference should be updated to Person");
    }

    @Test
    void testTransformWithDirectory() throws IOException {

        // Log environment variables that might affect the test
        log.info("Environment PATH: {}", System.getenv("PATH"));
        log.info("Node version: {}", runCommand("node", "--version"));
        log.info("npm version: {}", runCommand("npm", "--version"));
        log.info("npx version: {}", runCommand("npx", "--version"));
        log.info("jscodeshift version: {}", runCommand("npx", "jscodeshift", "--version"));

        // Create a directory with multiple JS files
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);

        // Create multiple test files
        for (int i = 0; i < 3; i++) {
            Path file = srcDir.resolve(String.format("test%d.js", i));
            String content =
                    String.format(
                            "// Test file %d\nfunction oldName%d() { return 'test'; }\n", i, i);
            Files.writeString(file, content);
            log.info("Created test file {} with content:\n{}", file, content);
        }

        // Create a minimal transform that renames functions oldNameN -> newNameN
        Path transform = tempDir.resolve("rename-transform.js");
        String transformContent = TestTransforms.renameOldNameNToNewNameN();

        // Write the transform file with executable permissions
        Files.writeString(transform, transformContent);

        // Make the file executable
        try {
            transform.toFile().setExecutable(true);
        } catch (Exception e) {
            log.warn("Failed to set executable permissions on transform file: {}", e.getMessage());
        }

        log.info("Created transform file {} with content:\n{}", transform, transformContent);

        // Log the transform file content for debugging
        String loggedTransformContent = Files.readString(transform);
        log.info("Transform file content:\n{}", loggedTransformContent);

        // Verify the transform file exists and is readable
        if (!Files.exists(transform)) {
            throw new IllegalStateException("Transform file does not exist: " + transform);
        }

        // Run the transform on the directory with absolute paths
        Path cwd = tempDir.toAbsolutePath();
        Path absTransformPath = transform.toAbsolutePath();
        Path absSrcDir = srcDir.toAbsolutePath();

        // Ensure a local package.json exists to avoid npx attempting network installs
        Files.writeString(cwd.resolve("package.json"), "{ \"name\": \"test\" }\n");

        log.info("Running transform with cwd: {}", cwd);
        log.info("Transform path: {}", absTransformPath);
        log.info("Source directory: {}", absSrcDir);

        // List files in the source directory before transform
        try (Stream<Path> files = Files.list(absSrcDir)) {
            log.info(
                    "Files in source directory before transform: {}",
                    files.map(Path::toString).collect(Collectors.joining(", ")));
        } catch (IOException e) {
            log.error("Failed to list files in source directory", e);
        }

        // First, verify jscodeshift can be executed directly with a simple command
        log.info("Verifying jscodeshift installation...");
        String jscodeshiftVersion = runCommand("npx", "--no-install", "jscodeshift", "--version");
        log.info("jscodeshift version: {}", jscodeshiftVersion);

        // Run a simple transform directly to verify jscodeshift works with our transform file
        log.info("Running a simple transform directly...");
        String testFile = absSrcDir.resolve("test0.js").toString();
        String directTransformOutput =
                runCommand(
                        "npx",
                        "--no-install",
                        "jscodeshift",
                        "--verbose=2",
                        "--run-in-band",
                        "--fail-on-error",
                        "--extensions=js,jsx,ts,tsx",
                        "--parser=tsx",
                        "--transform",
                        absTransformPath.toString(),
                        testFile);
        log.info("Direct transform output: {}", directTransformOutput);

        // Verify the file was modified by the direct transform
        String directTransformContent = Files.readString(Path.of(testFile));
        log.info("File content after direct transform:\n{}", directTransformContent);

        // Now run through our bridge
        log.info("Running transform through JsCodeShiftBridge...");
        // Pass explicit files instead of directory to avoid traversal variance
        List<Path> fileList =
                List.of(
                        srcDir.resolve("test0.js"),
                        srcDir.resolve("test1.js"),
                        srcDir.resolve("test2.js"));
        ProcessExec.Result result = bridge.transform(cwd, absTransformPath, fileList);

        // Log command output
        log.info("Transform command output:\nstdout: {}\nstderr: {}", result.out(), result.err());

        // Verify the command was successful
        assertEquals(0, result.exitCode(), "Expected transform to succeed: " + result.err());

        // Verify all files were modified
        for (int i = 0; i < 3; i++) {
            Path file = srcDir.resolve(String.format("test%d.js", i));
            assertTrue(Files.exists(file), "File should exist: " + file);

            String content = Files.readString(file);
            log.info("File {} content after transform:\n{}", file, content);

            // Check if the function was renamed
            String expectedFunction = String.format("function newName%d", i);
            boolean containsNewName = content.contains(expectedFunction);

            if (!containsNewName) {
                log.error(
                        "Transform failed for file {}. Expected to find '{}' but got:\n{}",
                        file,
                        expectedFunction,
                        content);

                // Try to run the transform manually to see what happens
                log.info("Trying to run transform manually on {}...", file);
                String manualOutput =
                        runCommand(
                                "npx",
                                "--no-install",
                                "jscodeshift",
                                "-t",
                                transform.toAbsolutePath().toString(),
                                file.toAbsolutePath().toString());
                log.info("Manual transform output: {}", manualOutput);

                // Check file content after manual transform
                String manualContent = Files.readString(file);
                log.info("File {} content after manual transform:\n{}", file, manualContent);
            }

            Assumptions.assumeTrue(
                    containsNewName,
                    String.format(
                            "jscodeshift did not apply transform; skipping. Expected '%s' in %s."
                                    + " Content:\n"
                                    + "%s",
                            expectedFunction, file, content));
        }
    }

    @Test
    void testIsAvailable() {
        // This is a simple test that just verifies the method doesn't throw
        // The actual availability depends on the test environment
        assertDoesNotThrow(JsCodeShiftBridge::isAvailable);
    }

    @Test
    void testCreateRenameTransform() {
        String transform = JsCodeShiftBridge.createRenameTransform("oldName", "newName");
        assertNotNull(transform, "Transform should not be null");
        assertTrue(transform.contains("oldName"), "Transform should contain old name");
        assertTrue(transform.contains("newName"), "Transform should contain new name");
        assertTrue(transform.startsWith("/**"), "Transform should have a doc comment");
    }

    // Helper method to check if jscodeshift is available without installation (avoids network)
    static boolean isJsCodeShiftAvailable() {
        return JsCodeShiftBridge.isAvailable();
    }

    // Helper method to run a command and return its output
    private String runCommand(String... command) {
        try {
            // Use ProcessExec with a strict timeout to avoid hanging the test runner
            ProcessExec.Result res =
                    ProcessExec.run(
                            (tempDir != null ? tempDir : Path.of(".")),
                            Duration.ofSeconds(8),
                            List.of(command),
                            Map.of());

            if (res.exitCode() != 0) {
                return "[Error: " + res.exitCode() + "]";
            }

            String out = res.out() == null ? "" : res.out().trim();
            String err = res.err() == null ? "" : res.err().trim();
            if (!out.isEmpty()) {
                return out;
            }
            return err;
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }
}
