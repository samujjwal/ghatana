package com.ghatana.refactorer.rewriters;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
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

    // Constants for duplicate literals
    private static final String BRACE_CLOSE = "    } \n";
    private static final String TEST_JAVA = "Test.java";
    private static final String CLASS_DECLARATION = "public class Test { \n";
    private static final String MAIN_METHOD = "    public static void main(String[] args) { \n"; // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        runner = new ErrorProneRunner(); // GH-90000
        javacPath = Path.of(System.getProperty("java.home"), "bin", "javac").toString();
    }

    @Test
    void testRunWithNoSources() { // GH-90000
        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder().javacCmd(javacPath).build(); // GH-90000
        List<UnifiedDiagnostic> diagnostics = runner.run(Path.of("."), opts, List.of(), 5000);
        assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for empty sources"); // GH-90000
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testRunWithDefaultOptions() throws IOException { // GH-90000
        // Create a simple Java file
        Path tempDir = Files.createTempDirectory("errorprone-test");
        Path srcFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString( // GH-90000
                srcFile,
                CLASS_DECLARATION
                        + MAIN_METHOD
                        + "        System.out.println(\"Hello\"); \n" // GH-90000
                        + BRACE_CLOSE
                        + "}");

        List<UnifiedDiagnostic> diagnostics = runner.run(tempDir, List.of(srcFile)); // GH-90000

        // Should compile without errors
        assertTrue(diagnostics.isEmpty(), "Expected no compilation errors: " + diagnostics); // GH-90000
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testRunWithValidJavaFile(@TempDir Path tempDir) throws Exception { // GH-90000
        // Create a simple Java file
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir); // GH-90000
        Path javaFile = srcDir.resolve(TEST_JAVA); // GH-90000

        // Create a very simple Java file that's unlikely to trigger any warnings
        Files.writeString( // GH-90000
                javaFile,
                "// Simple test file with no warnings\n"
                        + CLASS_DECLARATION
                        + "    @SuppressWarnings(\"all\") // Suppress all warnings\n" // GH-90000
                        + MAIN_METHOD
                        + "        System.out.println(\"Hello\"); \n" // GH-90000
                        + BRACE_CLOSE
                        + "}");

        // Use minimal arguments to avoid unnecessary warnings
        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder() // GH-90000
                        .javacCmd(javacPath) // GH-90000
                        .extraArgs(List.of("-Xlint:none")) // Disable all warnings
                        .build(); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runner.run(tempDir, opts, List.of(javaFile), 10000); // GH-90000

        // For a valid Java file with warnings disabled, we should get no diagnostics at all
        assertTrue( // GH-90000
                diagnostics.isEmpty(), // GH-90000
                "Expected no diagnostics for valid Java file, but got: " + diagnostics);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testRunWithInvalidJavaFile(@TempDir Path tempDir) throws Exception { // GH-90000
        // Create a Java file with an error
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir); // GH-90000
        Path javaFile = srcDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString( // GH-90000
                javaFile,
                CLASS_DECLARATION
                        + MAIN_METHOD
                        + "        UndefinedType x; \n"
                        + BRACE_CLOSE
                        + "}");

        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder() // GH-90000
                        .javacCmd(javacPath) // GH-90000
                        .extraArgs(List.of("-Xlint:all"))
                        .build(); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runner.run(tempDir, opts, List.of(javaFile), 10000); // GH-90000

        // Should have at least one error
        assertFalse(diagnostics.isEmpty(), "Expected compilation errors but found none"); // GH-90000

        // Check that the error message contains relevant information
        String errorMessage = diagnostics.get(0).message(); // GH-90000
        assertTrue( // GH-90000
                errorMessage.contains("cannot find symbol")
                        || errorMessage.contains("UndefinedType"),
                "Unexpected error message: " + errorMessage);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testRunWithWarning(@TempDir Path tempDir) throws Exception { // GH-90000
        // Create a Java file with a warning
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir); // GH-90000
        Path javaFile = srcDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString( // GH-90000
                javaFile,
                "import java.util.*;\n"
                        + CLASS_DECLARATION
                        + MAIN_METHOD
                        + "        List<String> list = new ArrayList(); // Raw type warning\n" // GH-90000
                        + "        System.out.println(list); \n" // GH-90000
                        + BRACE_CLOSE
                        + "}");

        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder() // GH-90000
                        .javacCmd(javacPath) // GH-90000
                        .extraArgs(List.of("-Xlint:all"))
                        .build(); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runner.run(tempDir, opts, List.of(javaFile), 10000); // GH-90000

        // Should have at least one warning
        assertFalse(diagnostics.isEmpty(), "Expected warnings but found none"); // GH-90000

        // Check that the warning message contains relevant information
        String warningMessage = diagnostics.get(0).message(); // GH-90000
        assertTrue( // GH-90000
                warningMessage.toLowerCase(Locale.ROOT).contains("raw type")
                        || warningMessage.toLowerCase(Locale.ROOT).contains("arraylist"),
                "Unexpected warning message: " + warningMessage);
    }

    @Test
    void testRunWithInvalidJavacCommand() { // GH-90000
        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder().javacCmd("nonexistent-javac").build();

        List<UnifiedDiagnostic> diagnostics =
                runner.run( // GH-90000
                        Path.of("."),
                        opts,
                        List.of(Path.of("Test.java").toAbsolutePath()), // Use absolute path
                        5000);

        // Should have at least one error
        assertFalse(diagnostics.isEmpty(), "Expected error for invalid javac command"); // GH-90000

        // Check that the error message indicates the command wasn't found
        String errorMessage = diagnostics.get(0).message().toLowerCase(Locale.ROOT); // GH-90000
        assertTrue( // GH-90000
                errorMessage.contains("cannot run program")
                        || errorMessage.contains("not found")
                        || errorMessage.contains("no such file")
                        || errorMessage.contains("source file not readable"),
                "Unexpected error message: " + errorMessage);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testRunWithNonexistentSourceFile() { // GH-90000
        ErrorProneRunner.Options opts =
                ErrorProneRunner.Options.builder().javacCmd(javacPath).build(); // GH-90000

        Path nonExistentFile = Path.of("nonexistent/" + TEST_JAVA).toAbsolutePath(); // GH-90000

        // Now we expect a diagnostic instead of an exception
        List<UnifiedDiagnostic> diagnostics =
                runner.run(Path.of("."), opts, List.of(nonExistentFile), 5000);

        // Should have at least one error
        assertFalse(diagnostics.isEmpty(), "Expected error for non-existent source file"); // GH-90000

        // Check that the error message indicates the file wasn't found
        String errorMessage = diagnostics.get(0).message().toLowerCase(Locale.ROOT); // GH-90000
        assertTrue( // GH-90000
                errorMessage.contains("source file not readable")
                        || errorMessage.contains("file not found"),
                "Unexpected error message: " + errorMessage);
    }
}
