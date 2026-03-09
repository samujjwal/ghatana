package com.ghatana.refactorer.rewriters;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ghatana.refactorer.shared.util.ProcessExec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tests for {@link PyLibCSTBridge}. */
@Tag("integration")
/**
 * @doc.type class
 * @doc.purpose Handles py lib cst bridge test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PyLibCSTBridgeTest {
    private static final Logger log = LoggerFactory.getLogger(PyLibCSTBridgeTest.class);
    private static final long TEST_TIMEOUT_MS = 120_000;

    private static PyLibCSTBridge bridge;
    private static Path workspaceRoot;
    private static Path toolHome;
    private static Path virtualEnvPath;
    private Path testTempDir;

    @BeforeAll
    static void setUpAll(@TempDir Path tempDir) throws Exception {
        workspaceRoot = Paths.get("").toAbsolutePath();
        toolHome = workspaceRoot.resolve(".tools").resolve("python");
        Files.createDirectories(toolHome);
        virtualEnvPath = toolHome.resolve("venv-libcst");

        try {
            bridge = provisionBridge();
        } catch (Exception e) {
            log.warn("Unable to provision LibCST bridge: {}", e.getMessage(), e);
            assumeTrue(false, "Unable to provision LibCST bridge: " + e.getMessage());
            return;
        }

        if (!bridge.isLibCSTAvailable()) {
            assumeTrue(false, "LibCST is still unavailable after provisioning");
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.testTempDir = tempDir;
    }

    @AfterEach
    void tearDown() {
        // Clean up any resources if needed
    }

    @AfterAll
    static void tearDownAll() {
        bridge = null;
        virtualEnvPath = null;
    }

    @Test
    void testTransformWithSimplePythonFile() throws IOException {
        // Create a simple Python file
        Path srcFile = testTempDir.resolve("test.py");
        String originalContent = "def old_func():\n    return 'old'\n";
        Files.writeString(srcFile, originalContent);

        // Create a LibCST transform script that renames old_func -> new_func
        Path transformScript = createTransformScript("RenameFunction");

        // Run the transform
        ProcessExec.Result result =
                bridge.transform(
                        testTempDir,
                        transformScript,
                        List.of(srcFile),
                        Map.of("old-name", "old_func", "new-name", "new_func"));

        // Verify the command was successful
        assertEquals(0, result.exitCode(), "Transform failed: " + result.err());

        // Verify the file was modified
        String transformedContent = Files.readString(srcFile);
        assertNotEquals(originalContent, transformedContent, "File content should be modified");
        assertTrue(transformedContent.contains("def new_func"), "Function should be renamed");
    }

    @Test
    void testTransformWithMultipleFiles() throws IOException {
        // Create multiple Python files
        Path file1 = testTempDir.resolve("file1.py");
        Path file2 = testTempDir.resolve("file2.py");

        String content = "def old_name():\n    return 'old'\n";
        Files.writeString(file1, content);
        Files.writeString(file2, content);

        // Create a transform script that renames the function
        Path transformScript = createTransformScript("RenameFunction");

        // Transform both files
        ProcessExec.Result result =
                bridge.transform(
                        testTempDir,
                        transformScript,
                        List.of(file1, file2),
                        Map.of("old-name", "old_name", "new-name", "new_name"));

        // Verify success
        assertEquals(0, result.exitCode(), "Transform failed: " + result.err());

        // Verify both files were modified
        assertTrue(Files.readString(file1).contains("def new_name"));
        assertTrue(Files.readString(file2).contains("def new_name"));
    }

    @Test
    void testTransformWithVirtualEnv() throws IOException {
        // Test a simple transformation
        Path srcFile = testTempDir.resolve("test_venv.py");
        Files.writeString(srcFile, "def test_func():\n    pass\n");

        // Just verify the transform runs without errors
        Path transformScript = createDummyTransformScript();
        ProcessExec.Result result =
                bridge.transform(testTempDir, transformScript, List.of(srcFile), Map.of());

        assertEquals(0, result.exitCode(), "Transform failed: " + result.err());
    }

    @Test
    void testInstallPackages() throws Exception {
        // Test installing a package (we'll use 'six' as it's small and widely compatible)
        boolean success = bridge.installPackages(testTempDir, List.of("six"));
        assertTrue(success, "Failed to install package");

        // Verify the package is importable
        String pythonExecutable = getPythonExecutable();
        Process process =
                new ProcessBuilder(pythonExecutable, "-c", "import six; print('ok')").start();

        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Process timed out");
        assertEquals(0, process.exitValue(), "Failed to import installed package");
    }

    // Helper method to create a transform script for testing
    private Path createTransformScript(String transformType) throws IOException {
        Path script = testTempDir.resolve("transform_" + transformType.toLowerCase() + ".py");
        String code =
                String.join(
                        "\n",
                        "import sys",
                        "import argparse",
                        "import libcst as cst",
                        "from pathlib import Path",
                        "",
                        "class " + transformType + "(cst.CSTTransformer):",
                        "    def __init__(self, old_name, new_name):",
                        "        self.old_name = old_name",
                        "        self.new_name = new_name",
                        "        super().__init__()",
                        "    ",
                        "    def leave_FunctionDef(self, original_node, updated_node):",
                        "        if original_node.name.value == self.old_name:",
                        "            return"
                                + " updated_node.with_changes(name=cst.Name(self.new_name))",
                        "        return updated_node",
                        "",
                        "def main():",
                        "    parser = argparse.ArgumentParser()",
                        "    parser.add_argument('files', nargs='+', help='Files to transform')",
                        "    parser.add_argument('--old-name', required=True, help='Old function"
                                + " name')",
                        "    parser.add_argument('--new-name', required=True, help='New function"
                                + " name')",
                        "    args = parser.parse_args()",
                        "    ",
                        "    for file_path in args.files:",
                        "        path = Path(file_path)",
                        "        try:",
                        "            # Read and parse the source file",
                        "            source = path.read_text()",
                        "            tree = cst.parse_module(source)",
                        "            ",
                        "            # Apply the transformation",
                        "            transformer = "
                                + transformType
                                + "(args.old_name, args.new_name)",
                        "            modified_tree = tree.visit(transformer)",
                        "            ",
                        "            # Write the modified source back",
                        "            path.write_text(modified_tree.code)",
                        "            print(f'Successfully transformed {path}')",
                        "        except Exception as e:",
                        "            print(f'Error processing {path}: {e}', file=sys.stderr)",
                        "            sys.exit(1)",
                        "",
                        "if __name__ == '__main__':",
                        "    main()");

        Files.writeString(script, code);
        return script;
    }

    // Helper method to create a simple dummy transform script
    private Path createDummyTransformScript() throws IOException {
        Path script = testTempDir.resolve("dummy_transform.py");
        String code =
                String.join(
                        "\n",
                        "import sys",
                        "import libcst as cst",
                        "from pathlib import Path",
                        "",
                        "class DummyTransformer(cst.CSTTransformer):",
                        "    pass",
                        "",
                        "def main():",
                        "    if len(sys.argv) < 2:",
                        "        print('Usage: python dummy_transform.py <file> [file ...]')",
                        "        sys.exit(1)",
                        "    ",
                        "    for file_path in sys.argv[1:]:",
                        "        path = Path(file_path)",
                        "        try:",
                        "            source = path.read_text()",
                        "            tree = cst.parse_module(source)",
                        "            # Just parse and rewrite without changes",
                        "            modified_tree = tree.visit(DummyTransformer())",
                        "            path.write_text(modified_tree.code)",
                        "        except Exception as e:",
                        "            print(f'Error processing {path}: {e}', file=sys.stderr)",
                        "            sys.exit(1)",
                        "",
                        "if __name__ == '__main__':",
                        "    main()");

        Files.writeString(script, code);
        return script;
    }

    // Helper method to get the Python executable path (for testing)
    private String getPythonExecutable() {
        try {
            // Use reflection to access the private field for testing
            java.lang.reflect.Field field =
                    PyLibCSTBridge.class.getDeclaredField("pythonExecutable");
            field.setAccessible(true);
            return (String) field.get(bridge);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Python executable path", e);
        }
    }

    /**
     * Checks if Python with LibCST is available in the system. This is used to conditionally
     * enable/disable tests.
     *
     * @return true if Python with LibCST is available, false otherwise
     */
    static boolean isPythonWithLibCSTAvailable() {
        return bridge != null && bridge.isLibCSTAvailable();
    }

    private static PyLibCSTBridge provisionBridge() throws Exception {
        PyLibCSTBridge bootstrap = new PyLibCSTBridge(null, null, TEST_TIMEOUT_MS);

        if (Files.notExists(virtualEnvPath)
                || Files.notExists(virtualEnvPath.resolve("pyvenv.cfg"))) {
            Files.createDirectories(virtualEnvPath.getParent());
            log.info("Creating LibCST virtual environment at {}", virtualEnvPath);
            if (!bootstrap.createVirtualEnv(virtualEnvPath)) {
                throw new IllegalStateException(
                        "Failed to create virtual environment at " + virtualEnvPath);
            }
        }

        Path pythonInVenv = virtualEnvPath.resolve("bin").resolve("python");
        if (!Files.exists(pythonInVenv)) {
            pythonInVenv = virtualEnvPath.resolve("Scripts").resolve("python.exe");
        }

        PyLibCSTBridge venvBridge =
                new PyLibCSTBridge(pythonInVenv.toString(), virtualEnvPath, TEST_TIMEOUT_MS);

        if (!venvBridge.isLibCSTAvailable()) {
            log.info("Installing LibCST into virtual environment at {}", virtualEnvPath);
            boolean installed = venvBridge.installPackages(workspaceRoot, List.of("libcst"));
            if (!installed || !venvBridge.isLibCSTAvailable()) {
                throw new IllegalStateException(
                        "Failed to provision LibCST in virtual environment");
            }
        }

        return venvBridge;
    }
}
