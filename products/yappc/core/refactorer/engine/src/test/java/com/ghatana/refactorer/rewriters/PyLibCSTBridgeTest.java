package com.ghatana.refactorer.rewriters;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ghatana.refactorer.shared.util.ProcessExec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
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
    private static final Logger log = LoggerFactory.getLogger(PyLibCSTBridgeTest.class); // GH-90000
    private static final long TEST_TIMEOUT_MS = 120_000;

    private static PyLibCSTBridge bridge;
    private static Path workspaceRoot;
    private static Path toolHome;
    private static Path virtualEnvPath;
    private Path testTempDir;

    @BeforeAll
    static void setUpAll(@TempDir Path tempDir) throws Exception { // GH-90000
        workspaceRoot = Paths.get("").toAbsolutePath();
        toolHome = workspaceRoot.resolve(".tools").resolve("python");
        Files.createDirectories(toolHome); // GH-90000
        virtualEnvPath = toolHome.resolve("venv-libcst");

        try {
            bridge = provisionBridge(); // GH-90000
        } catch (Exception e) { // GH-90000
            log.warn("Unable to provision LibCST bridge: {}", e.getMessage(), e); // GH-90000
            assumeTrue(false, "Unable to provision LibCST bridge: " + e.getMessage()); // GH-90000
            return;
        }

        if (!bridge.isLibCSTAvailable()) { // GH-90000
            assumeTrue(false, "LibCST is still unavailable after provisioning"); // GH-90000
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) { // GH-90000
        this.testTempDir = tempDir;
    }

    @AfterEach
    void tearDown() { // GH-90000
        // Clean up any resources if needed
    }

    @AfterAll
    static void tearDownAll() { // GH-90000
        // Clean up handled by garbage collection
    }

    @Test
    void testTransformWithSimplePythonFile() throws IOException { // GH-90000
        // Create a simple Python file
        Path srcFile = testTempDir.resolve("test.py");
        String originalContent = "def old_func():\n    return 'old'\n"; // GH-90000
        Files.writeString(srcFile, originalContent); // GH-90000

        // Create a LibCST transform script that renames old_func -> new_func
        Path transformScript = createTransformScript("RenameFunction");

        // Run the transform
        ProcessExec.Result result =
                bridge.transform( // GH-90000
                        testTempDir,
                        transformScript,
                        List.of(srcFile), // GH-90000
                        Map.of("old-name", "old_func", "new-name", "new_func")); // GH-90000

        // Verify the command was successful
        assertEquals(0, result.exitCode(), "Transform failed: " + result.err()); // GH-90000

        // Verify the file was modified
        String transformedContent = Files.readString(srcFile); // GH-90000
        assertNotEquals(originalContent, transformedContent, "File content should be modified"); // GH-90000
        assertTrue(transformedContent.contains("def new_func"), "Function should be renamed");
    }

    @Test
    void testTransformWithMultipleFiles() throws IOException { // GH-90000
        // Create multiple Python files
        Path file1 = testTempDir.resolve("file1.py");
        Path file2 = testTempDir.resolve("file2.py");

        String content = "def old_name():\n    return 'old'\n"; // GH-90000
        Files.writeString(file1, content); // GH-90000
        Files.writeString(file2, content); // GH-90000

        // Create a transform script that renames the function
        Path transformScript = createTransformScript("RenameFunction");

        // Transform both files
        ProcessExec.Result result =
                bridge.transform( // GH-90000
                        testTempDir,
                        transformScript,
                        List.of(file1, file2), // GH-90000
                        Map.of("old-name", "old_name", "new-name", "new_name")); // GH-90000

        // Verify success
        assertEquals(0, result.exitCode(), "Transform failed: " + result.err()); // GH-90000

        // Verify both files were modified
        assertTrue(Files.readString(file1).contains("def new_name"));
        assertTrue(Files.readString(file2).contains("def new_name"));
    }

    @Test
    void testTransformWithVirtualEnv() throws IOException { // GH-90000
        // Test a simple transformation
        Path srcFile = testTempDir.resolve("test_venv.py");
        Files.writeString(srcFile, "def test_func():\n    pass\n"); // GH-90000

        // Just verify the transform runs without errors
        Path transformScript = createDummyTransformScript(); // GH-90000
        ProcessExec.Result result =
                bridge.transform(testTempDir, transformScript, List.of(srcFile), Map.of()); // GH-90000

        assertEquals(0, result.exitCode(), "Transform failed: " + result.err()); // GH-90000
    }

    @Test
    void testInstallPackages() throws Exception { // GH-90000
        // Test installing a package (we'll use 'six' as it's small and widely compatible) // GH-90000
        boolean success = bridge.installPackages(testTempDir, List.of("six"));
        assertTrue(success, "Failed to install package"); // GH-90000

        // Verify the package is importable
        String pythonExecutable = getPythonExecutable(); // GH-90000
        Process process =
                new ProcessBuilder(pythonExecutable, "-c", "import six; print('ok')").start(); // GH-90000

        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Process timed out"); // GH-90000
        assertEquals(0, process.exitValue(), "Failed to import installed package"); // GH-90000
    }

    // Helper method to create a transform script for testing
    private Path createTransformScript(String transformType) throws IOException { // GH-90000
        Path script = testTempDir.resolve("transform_" + transformType.toLowerCase(Locale.ROOT) + ".py"); // GH-90000
        String code =
                String.join( // GH-90000
                        "\n",
                        "import sys",
                        "import argparse",
                        "import libcst as cst",
                        "from pathlib import Path",
                        "",
                        "class " + transformType + "(cst.CSTTransformer):", // GH-90000
                        "    def __init__(self, old_name, new_name):", // GH-90000
                        "        self.old_name = old_name",
                        "        self.new_name = new_name",
                        "        super().__init__()", // GH-90000
                        "    ",
                        "    def leave_FunctionDef(self, original_node, updated_node):", // GH-90000
                        "        if original_node.name.value == self.old_name:",
                        "            return"
                                + " updated_node.with_changes(name=cst.Name(self.new_name))", // GH-90000
                        "        return updated_node",
                        "",
                        "def main():", // GH-90000
                        "    parser = argparse.ArgumentParser()", // GH-90000
                        "    parser.add_argument('files', nargs='+', help='Files to transform')", // GH-90000
                        "    parser.add_argument('--old-name', required=True, help='Old function" // GH-90000
                                + " name')",
                        "    parser.add_argument('--new-name', required=True, help='New function" // GH-90000
                                + " name')",
                        "    args = parser.parse_args()", // GH-90000
                        "    ",
                        "    for file_path in args.files:",
                        "        path = Path(file_path)", // GH-90000
                        "        try:",
                        "            # Read and parse the source file",
                        "            source = path.read_text()", // GH-90000
                        "            tree = cst.parse_module(source)", // GH-90000
                        "            ",
                        "            # Apply the transformation",
                        "            transformer = "
                                + transformType
                                + "(args.old_name, args.new_name)", // GH-90000
                        "            modified_tree = tree.visit(transformer)", // GH-90000
                        "            ",
                        "            # Write the modified source back",
                        "            path.write_text(modified_tree.code)", // GH-90000
                        "            print(f'Successfully transformed {path}')", // GH-90000
                        "        except Exception as e:",
                        "            print(f'Error processing {path}: {e}', file=sys.stderr)", // GH-90000
                        "            sys.exit(1)", // GH-90000
                        "",
                        "if __name__ == '__main__':",
                        "    main()"); // GH-90000

        Files.writeString(script, code); // GH-90000
        return script;
    }

    // Helper method to create a simple dummy transform script
    private Path createDummyTransformScript() throws IOException { // GH-90000
        Path script = testTempDir.resolve("dummy_transform.py");
        String code =
                String.join( // GH-90000
                        "\n",
                        "import sys",
                        "import libcst as cst",
                        "from pathlib import Path",
                        "",
                        "class DummyTransformer(cst.CSTTransformer):", // GH-90000
                        "    pass",
                        "",
                        "def main():", // GH-90000
                        "    if len(sys.argv) < 2:", // GH-90000
                        "        print('Usage: python dummy_transform.py <file> [file ...]')", // GH-90000
                        "        sys.exit(1)", // GH-90000
                        "    ",
                        "    for file_path in sys.argv[1:]:",
                        "        path = Path(file_path)", // GH-90000
                        "        try:",
                        "            source = path.read_text()", // GH-90000
                        "            tree = cst.parse_module(source)", // GH-90000
                        "            # Just parse and rewrite without changes",
                        "            modified_tree = tree.visit(DummyTransformer())", // GH-90000
                        "            path.write_text(modified_tree.code)", // GH-90000
                        "        except Exception as e:",
                        "            print(f'Error processing {path}: {e}', file=sys.stderr)", // GH-90000
                        "            sys.exit(1)", // GH-90000
                        "",
                        "if __name__ == '__main__':",
                        "    main()"); // GH-90000

        Files.writeString(script, code); // GH-90000
        return script;
    }

    // Helper method to get the Python executable path (for testing) // GH-90000
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private String getPythonExecutable() { // GH-90000
        try {
            // Use reflection to access the private field for testing
            java.lang.reflect.Field field =
                    PyLibCSTBridge.class.getDeclaredField("pythonExecutable");
            // PMD: setAccessible required for testing private field
            field.setAccessible(true); // GH-90000
            return (String) field.get(bridge); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to get Python executable path", e); // GH-90000
        }
    }

    /**
     * Checks if Python with LibCST is available in the system. This is used to conditionally
     * enable/disable tests.
     *
     * @return true if Python with LibCST is available, false otherwise
     */
    static boolean isPythonWithLibCSTAvailable() { // GH-90000
        return bridge != null && bridge.isLibCSTAvailable(); // GH-90000
    }

    private static PyLibCSTBridge provisionBridge() throws Exception { // GH-90000
        PyLibCSTBridge bootstrap = new PyLibCSTBridge(null, null, TEST_TIMEOUT_MS); // GH-90000

        if (Files.notExists(virtualEnvPath) // GH-90000
                || Files.notExists(virtualEnvPath.resolve("pyvenv.cfg"))) {
            Files.createDirectories(virtualEnvPath.getParent()); // GH-90000
            log.info("Creating LibCST virtual environment at {}", virtualEnvPath); // GH-90000
            if (!bootstrap.createVirtualEnv(virtualEnvPath)) { // GH-90000
                throw new IllegalStateException( // GH-90000
                        "Failed to create virtual environment at " + virtualEnvPath);
            }
        }

        Path pythonInVenv = virtualEnvPath.resolve("bin").resolve("python");
        if (!Files.exists(pythonInVenv)) { // GH-90000
            pythonInVenv = virtualEnvPath.resolve("Scripts").resolve("python.exe");
        }

        PyLibCSTBridge venvBridge =
                new PyLibCSTBridge(pythonInVenv.toString(), virtualEnvPath, TEST_TIMEOUT_MS); // GH-90000

        if (!venvBridge.isLibCSTAvailable()) { // GH-90000
            log.info("Installing LibCST into virtual environment at {}", virtualEnvPath); // GH-90000
            boolean installed = venvBridge.installPackages(workspaceRoot, List.of("libcst"));
            if (!installed || !venvBridge.isLibCSTAvailable()) { // GH-90000
                throw new IllegalStateException( // GH-90000
                        "Failed to provision LibCST in virtual environment");
            }
        }

        return venvBridge;
    }
}
