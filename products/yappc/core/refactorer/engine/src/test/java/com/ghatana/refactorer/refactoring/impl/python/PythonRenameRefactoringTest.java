package com.ghatana.refactorer.refactoring.impl.python;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import com.ghatana.refactorer.refactoring.model.PythonElementType;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for PythonRenameRefactoring using the real implementation. Note: These tests require a
 * working Python environment with Jython installed.
 */
@EnabledIfSystemProperty(named = "test.python.enabled", matches = "true") // GH-90000
@Tag("integration")
/**
 * @doc.type class
 * @doc.purpose Handles python rename refactoring test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PythonRenameRefactoringTest {

    @TempDir Path tempDir;

    private PythonRenameRefactoring refactoring;
    private Path testFile;
    private PolyfixProjectContext projectContext;
    private ExecutorService executor;
    private Logger logger;

    // Constants for duplicate literals
    private static final String NEW_NAME = "new_name";

    @BeforeEach
    void setUp() throws IOException { // GH-90000
        refactoring = new PythonRenameRefactoring(); // GH-90000
        testFile = tempDir.resolve("test_rename.py");
        executor = Executors.newSingleThreadExecutor(); // GH-90000
        logger = LogManager.getLogger(PythonRenameRefactoringTest.class); // GH-90000
        projectContext = new PolyfixProjectContext(tempDir, null, List.of(), executor, logger); // GH-90000

        // Create a simple Python file for testing
        String testCode =
                """
                class MyClass:
                    def old_method(self): // GH-90000
                        return "Hello"

                    def another_method(self): // GH-90000
                        return self.old_method() // GH-90000

                def function_to_rename(): // GH-90000
                    return 42

                if __name__ == "__main__":
                    obj = MyClass() // GH-90000
                    print(obj.old_method()) // GH-90000
                    print(function_to_rename()) // GH-90000
                """;

        Files.writeString(testFile, testCode); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (executor != null) { // GH-90000
            executor.shutdownNow(); // GH-90000
        }
    }

    @Test
    void shouldRenameMethod() { // GH-90000
        // Given
        var context =
                createContext( // GH-90000
                        testFile, PythonElementType.METHOD.name(), "old_method", "new_method", 3); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getModifiedFiles()).contains(testFile); // GH-90000

        // Verify the content was actually changed
        String content = readFileContent(testFile); // GH-90000
        assertThat(content).contains("def new_method");
        assertThat(content).contains("return self.new_method()");
        assertThat(content).contains("print(obj.new_method())");
    }

    @Test
    void shouldRenameFunction() { // GH-90000
        // Given
        var context =
                createContext( // GH-90000
                        testFile,
                        PythonElementType.FUNCTION.name(), // GH-90000
                        "function_to_rename",
                        "renamed_function",
                        9);

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000

        // Verify the content was actually changed
        String content = readFileContent(testFile); // GH-90000
        assertThat(content).contains("def renamed_function():");
        assertThat(content).contains("print(renamed_function())");
    }

    @Test
    void shouldNotRenameIfElementNotFound() { // GH-90000
        // Given
        var context =
                createContext( // GH-90000
                        testFile,
                        PythonElementType.FUNCTION.name(), // GH-90000
                        "non_existent_function",
                        NEW_NAME,
                        1);

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getChangeSummary()).contains("No changes were made");
    }

    @Test
    void shouldValidateNewName() { // GH-90000
        assertThat(refactoring.isNewNameValid("valid_name")).isTrue();
        assertThat(refactoring.isNewNameValid("validName123")).isTrue();
        assertThat(refactoring.isNewNameValid("_private_name")).isTrue();

        assertThat(refactoring.isNewNameValid(null)).isFalse(); // GH-90000
        assertThat(refactoring.isNewNameValid("")).isFalse();
        assertThat(refactoring.isNewNameValid("123invalid")).isFalse();
        assertThat(refactoring.isNewNameValid("invalid-name")).isFalse();
        assertThat(refactoring.isNewNameValid("invalid.name")).isFalse();
    }

    @Test
    void shouldCheckIfCanBeApplied() { // GH-90000
        // Valid Python file
        var validContext =
                createContext( // GH-90000
                        testFile, PythonElementType.FUNCTION.name(), "some_name", NEW_NAME, 1); // GH-90000

        assertThat(refactoring.canApply(validContext)).isTrue(); // GH-90000

        // Non-existent file
        var invalidFileContext =
                createContext( // GH-90000
                        Path.of("non_existent.py"),
                        PythonElementType.FUNCTION.name(), // GH-90000
                        "some_name",
                        NEW_NAME,
                        1);

        assertThat(refactoring.canApply(invalidFileContext)).isFalse(); // GH-90000

        // Non-Python file
        var nonPythonFile = tempDir.resolve("not_python.txt");
        try {
            Files.writeString(nonPythonFile, "Not a Python file"); // GH-90000

            var nonPythonContext =
                    createContext( // GH-90000
                            nonPythonFile,
                            PythonElementType.FUNCTION.name(), // GH-90000
                            "some_name",
                            NEW_NAME,
                            1);

            assertThat(refactoring.canApply(nonPythonContext)).isFalse(); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to create test file", e); // GH-90000
        }
    }

    private String readFileContent(Path file) { // GH-90000
        try {
            return Files.readString(file); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to read test file", e); // GH-90000
        }
    }

    private RenameRefactoring.Context createContext( // GH-90000
            Path sourceFile, String elementType, String oldName, String newName, int lineNumber) {
        return new RenameRefactoring.Context() { // GH-90000
            @Override
            public String getOldName() { // GH-90000
                return oldName;
            }

            @Override
            public String getNewName() { // GH-90000
                return newName;
            }

            @Override
            public String getElementType() { // GH-90000
                return elementType;
            }

            @Override
            public String getSourceFile() { // GH-90000
                return sourceFile.toString(); // GH-90000
            }

            @Override
            public int getLineNumber() { // GH-90000
                return lineNumber;
            }

            @Override
            public int getColumnNumber() { // GH-90000
                return 0; // Column number is not used in Python refactoring
            }

            @Override
            public PolyfixProjectContext getPolyfixProjectContext() { // GH-90000
                return projectContext;
            }

            @Override
            public Path getProjectRoot() { // GH-90000
                return projectContext.getProjectRoot(); // GH-90000
            }

            @Override
            public Set<Path> getAffectedFiles() { // GH-90000
                return Set.of(sourceFile); // GH-90000
            }

            @Override
            public boolean isDryRun() { // GH-90000
                return false;
            }

            @Override
            public boolean isInteractive() { // GH-90000
                return false;
            }
        };
    }
}
