package com.ghatana.refactorer.rewriters;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles error prone runner test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class ErrorProneRunnerTest {

    private ErrorProneRunner runner;
    private String javacPath;

    @BeforeEach
    void setUp() {
        runner = new ErrorProneRunner();
        javacPath = System.getProperty("java.home") + "/bin/javac";
    }

    @Test
    void testRunWithNoSources() {
        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder().javacCmd(javacPath).build();
        List<UnifiedDiagnostic> diagnostics = runner.run(Path.of("."), opts, List.of(), 5000);
        assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for empty sources");
    }

    @Test
    void testRunWithDefaultOptions() throws IOException {
        // Create a simple Java file
        Path tempDir = Files.createTempDirectory("errorprone-test");
        Path srcFile = tempDir.resolve("Test.java");
        Files.writeString(
                srcFile,
                "public class Test { \n"
                        + "    public static void main(String[] args) { \n"
                        + "        System.out.println(\"Hello\"); \n"
                        + "    } \n"
                        + "}");

        List<UnifiedDiagnostic> diagnostics = runner.run(tempDir, List.of(srcFile));

        // Should compile without errors
        assertTrue(diagnostics.isEmpty(), "Expected no compilation errors: " + diagnostics);
    }

    @Test
    void testRunWithValidJavaFile(@TempDir Path tempDir) throws Exception {
        // Create a simple Java file
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Path javaFile = srcDir.resolve("Test.java");

        // Create a very simple Java file that's unlikely to trigger any warnings
        Files.writeString(
                javaFile,
                "// Simple test file with no warnings\n"
                        + "public class Test { \n"
                        + "    @SuppressWarnings(\"all\") // Suppress all warnings\n"
                        + "    public static void main(String[] args) { \n"
                        + "        System.out.println(\"Hello\"); \n"
                        + "    } \n"
                        + "}");

        // Use minimal arguments to avoid unnecessary warnings
        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder()
                        .javacCmd(javacPath)
                        .extraArgs(List.of("-Xlint:none")) // Disable all warnings
                        .build();

        List<UnifiedDiagnostic> diagnostics = runner.run(tempDir, opts, List.of(javaFile), 10000);

        // For a valid Java file with warnings disabled, we should get no diagnostics at all
        assertTrue(
                diagnostics.isEmpty(),
                "Expected no diagnostics for valid Java file, but got: " + diagnostics);
    }

    @Test
    void testRunWithInvalidJavaFile(@TempDir Path tempDir) throws Exception {
        // Create a Java file with an error
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Path javaFile = srcDir.resolve("Test.java");
        Files.writeString(
                javaFile,
                "public class Test { \n"
                        + "    public static void main(String[] args) { \n"
                        + "        UndefinedType x; \n"
                        + "    } \n"
                        + "}");

        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder()
                        .javacCmd(javacPath)
                        .extraArgs(List.of("-Xlint:all"))
                        .build();

        List<UnifiedDiagnostic> diagnostics = runner.run(tempDir, opts, List.of(javaFile), 10000);

        // Should have at least one error
        assertFalse(diagnostics.isEmpty(), "Expected compilation errors but found none");

        // Check that the error message contains relevant information
        String errorMessage = diagnostics.get(0).message();
        assertTrue(
                errorMessage.contains("cannot find symbol")
                        || errorMessage.contains("UndefinedType"),
                "Unexpected error message: " + errorMessage);
    }

    @Test
    void testRunWithWarning(@TempDir Path tempDir) throws Exception {
        // Create a Java file with a warning
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Path javaFile = srcDir.resolve("Test.java");
        Files.writeString(
                javaFile,
                "import java.util.*;\n"
                        + "public class Test { \n"
                        + "    public static void main(String[] args) { \n"
                        + "        List<String> list = new ArrayList(); // Raw type warning\n"
                        + "        System.out.println(list); \n"
                        + "    } \n"
                        + "}");

        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder()
                        .javacCmd(javacPath)
                        .extraArgs(List.of("-Xlint:all"))
                        .build();

        List<UnifiedDiagnostic> diagnostics = runner.run(tempDir, opts, List.of(javaFile), 10000);

        // Should have at least one warning
        assertFalse(diagnostics.isEmpty(), "Expected warnings but found none");

        // Check that the warning message contains relevant information
        String warningMessage = diagnostics.get(0).message();
        assertTrue(
                warningMessage.toLowerCase().contains("raw type")
                        || warningMessage.toLowerCase().contains("arraylist"),
                "Unexpected warning message: " + warningMessage);
    }

    @Test
    void testRunWithInvalidJavacCommand() {
        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder().javacCmd("nonexistent-javac").build();

        List<UnifiedDiagnostic> diagnostics =
                runner.run(
                        Path.of("."),
                        opts,
                        List.of(Path.of("Test.java").toAbsolutePath()), // Use absolute path
                        5000);

        // Should have at least one error
        assertFalse(diagnostics.isEmpty(), "Expected error for invalid javac command");

        // Check that the error message indicates the command wasn't found
        String errorMessage = diagnostics.get(0).message().toLowerCase();
        assertTrue(
                errorMessage.contains("cannot run program")
                        || errorMessage.contains("not found")
                        || errorMessage.contains("no such file")
                        || errorMessage.contains("source file not readable"),
                "Unexpected error message: " + errorMessage);
    }

    @Test
    void testRunWithNonexistentSourceFile() {
        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder().javacCmd(javacPath).build();

        Path nonExistentFile = Path.of("nonexistent/Test.java").toAbsolutePath();

        // Now we expect a diagnostic instead of an exception
        List<UnifiedDiagnostic> diagnostics =
                runner.run(Path.of("."), opts, List.of(nonExistentFile), 5000);

        // Should have at least one error
        assertFalse(diagnostics.isEmpty(), "Expected error for non-existent source file");

        // Check that the error message indicates the file wasn't found
        String errorMessage = diagnostics.get(0).message().toLowerCase();
        assertTrue(
                errorMessage.contains("source file not readable")
                        || errorMessage.contains("file not found"),
                "Unexpected error message: " + errorMessage);
    }
}
