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
@EnabledIfSystemProperty(named = "test.python.enabled", matches = "true")
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

    @BeforeEach
    void setUp() throws IOException {
        refactoring = new PythonRenameRefactoring();
        testFile = tempDir.resolve("test_rename.py");
        executor = Executors.newSingleThreadExecutor();
        logger = LogManager.getLogger(PythonRenameRefactoringTest.class);
        projectContext = new PolyfixProjectContext(tempDir, null, List.of(), executor, logger);

        // Create a simple Python file for testing
        String testCode =
                """
                class MyClass:
                    def old_method(self):
                        return "Hello"

                    def another_method(self):
                        return self.old_method()

                def function_to_rename():
                    return 42

                if __name__ == "__main__":
                    obj = MyClass()
                    print(obj.old_method())
                    print(function_to_rename())
                """;

        Files.writeString(testFile, testCode);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRenameMethod() {
        // Given
        var context =
                createContext(
                        testFile, PythonElementType.METHOD.name(), "old_method", "new_method", 3);

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getModifiedFiles()).contains(testFile);

        // Verify the content was actually changed
        String content = readFileContent(testFile);
        assertThat(content).contains("def new_method");
        assertThat(content).contains("return self.new_method()");
        assertThat(content).contains("print(obj.new_method())");
    }

    @Test
    void shouldRenameFunction() {
        // Given
        var context =
                createContext(
                        testFile,
                        PythonElementType.FUNCTION.name(),
                        "function_to_rename",
                        "renamed_function",
                        9);

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();

        // Verify the content was actually changed
        String content = readFileContent(testFile);
        assertThat(content).contains("def renamed_function():");
        assertThat(content).contains("print(renamed_function())");
    }

    @Test
    void shouldNotRenameIfElementNotFound() {
        // Given
        var context =
                createContext(
                        testFile,
                        PythonElementType.FUNCTION.name(),
                        "non_existent_function",
                        "new_name",
                        1);

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChangeSummary()).contains("No changes were made");
    }

    @Test
    void shouldValidateNewName() {
        assertThat(refactoring.isNewNameValid("valid_name")).isTrue();
        assertThat(refactoring.isNewNameValid("validName123")).isTrue();
        assertThat(refactoring.isNewNameValid("_private_name")).isTrue();

        assertThat(refactoring.isNewNameValid(null)).isFalse();
        assertThat(refactoring.isNewNameValid("")).isFalse();
        assertThat(refactoring.isNewNameValid("123invalid")).isFalse();
        assertThat(refactoring.isNewNameValid("invalid-name")).isFalse();
        assertThat(refactoring.isNewNameValid("invalid.name")).isFalse();
    }

    @Test
    void shouldCheckIfCanBeApplied() {
        // Valid Python file
        var validContext =
                createContext(
                        testFile, PythonElementType.FUNCTION.name(), "some_name", "new_name", 1);

        assertThat(refactoring.canApply(validContext)).isTrue();

        // Non-existent file
        var invalidFileContext =
                createContext(
                        Path.of("non_existent.py"),
                        PythonElementType.FUNCTION.name(),
                        "some_name",
                        "new_name",
                        1);

        assertThat(refactoring.canApply(invalidFileContext)).isFalse();

        // Non-Python file
        var nonPythonFile = tempDir.resolve("not_python.txt");
        try {
            Files.writeString(nonPythonFile, "Not a Python file");

            var nonPythonContext =
                    createContext(
                            nonPythonFile,
                            PythonElementType.FUNCTION.name(),
                            "some_name",
                            "new_name",
                            1);

            assertThat(refactoring.canApply(nonPythonContext)).isFalse();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test file", e);
        }
    }

    private String readFileContent(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read test file", e);
        }
    }

    private RenameRefactoring.Context createContext(
            Path sourceFile, String elementType, String oldName, String newName, int lineNumber) {
        return new RenameRefactoring.Context() {
            @Override
            public String getOldName() {
                return oldName;
            }

            @Override
            public String getNewName() {
                return newName;
            }

            @Override
            public String getElementType() {
                return elementType;
            }

            @Override
            public String getSourceFile() {
                return sourceFile.toString();
            }

            @Override
            public int getLineNumber() {
                return lineNumber;
            }

            @Override
            public int getColumnNumber() {
                return 0; // Column number is not used in Python refactoring
            }

            @Override
            public PolyfixProjectContext getPolyfixProjectContext() {
                return projectContext;
            }

            @Override
            public Path getProjectRoot() {
                return projectContext.getProjectRoot();
            }

            @Override
            public Set<Path> getAffectedFiles() {
                return Set.of(sourceFile);
            }

            @Override
            public boolean isDryRun() {
                return false;
            }

            @Override
            public boolean isInteractive() {
                return false;
            }
        };
    }
}
