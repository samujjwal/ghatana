package com.ghatana.refactorer.refactoring.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles mock python rename refactoring test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class MockPythonRenameRefactoringTest {
    @TempDir Path tempDir;
    private MockPythonRenameRefactoring refactoring;
    private Path testFile;
    private PolyfixProjectContext projectContext;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws IOException {
        refactoring = new MockPythonRenameRefactoring();
        testFile = tempDir.resolve("test_rename.py");
        executor = Executors.newSingleThreadExecutor();

        // Create a minimal PolyfixConfig for testing
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java", "python", "typescript"),
                        List.of(),
                        new PolyfixConfig.Budgets(10, 100),
                        new PolyfixConfig.Policies(false, true, true, false),
                        new PolyfixConfig.Tools(
                                "node", "", "", "", "", "", "", "", "", "", "", ""));

        projectContext = new PolyfixProjectContext(tempDir, config, List.of(), executor, null);

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
        var context = createContext(testFile, "METHOD", "old_method", "new_method");

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
        var context = createContext(testFile, "FUNCTION", "function_to_rename", "renamed_function");

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
        var context = createContext(testFile, "FUNCTION", "non_existent_function", "new_name");

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChangeCount()).isEqualTo(0);
    }

    @Test
    void shouldCheckIfCanBeApplied() {
        // Valid Python file
        var validContext = createContext(testFile, "FUNCTION", "some_name", "new_name");

        assertThat(refactoring.canApply(validContext)).isTrue();

        // Non-existent file
        var invalidFileContext =
                createContext(Path.of("non_existent.py"), "FUNCTION", "some_name", "new_name");

        assertThat(refactoring.canApply(invalidFileContext)).isFalse();

        // Non-Python file
        var nonPythonFile = tempDir.resolve("not_python.txt");
        try {
            Files.writeString(nonPythonFile, "Not a Python file");

            var nonPythonContext =
                    createContext(nonPythonFile, "FUNCTION", "some_name", "new_name");

            assertThat(refactoring.canApply(nonPythonContext)).isFalse();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test file", e);
        }
    }

    @Test
    void shouldHandleIOExceptionWhenReadingFile() throws IOException {
        // Create a mock file that will throw an IOException when read
        Path mockFile = mock(Path.class);
        when(mockFile.toString()).thenReturn("/non/existent/file.py");

        // Mock Files.readString to throw IOException
        try (var mockedFiles = mockStatic(Files.class)) {
            mockedFiles
                    .when(() -> Files.readString(mockFile))
                    .thenThrow(new IOException("Failed to read file"));

            // Create a context with the mock file
            var context = createContext(mockFile, "FUNCTION", "some_function", "new_function");

            // When
            var result = refactoring.apply(context);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage())
                    .as("Error message should indicate file read failure")
                    .contains("Source file does not exist");
        }
    }

    @Test
    void shouldHandleInvalidElementType() {
        // Given
        var context = createContext(testFile, "INVALID_TYPE", "old_name", "new_name");

        // When
        var result = refactoring.apply(context);

        // Then
        // Should not throw an exception, but should return a result with no changes
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChangeCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleEmptyFile() throws IOException {
        // Create an empty file
        Path emptyFile = tempDir.resolve("empty.py");
        Files.writeString(emptyFile, "");

        // Given
        var context = createContext(emptyFile, "FUNCTION", "some_function", "new_function");

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChangeCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleNullNewName() {
        // Given
        var context =
                new RenameRefactoring.Context() {
                    @Override
                    public String getOldName() {
                        return "old_function";
                    }

                    @Override
                    public String getNewName() {
                        return null; // Null new name
                    }

                    @Override
                    public String getElementType() {
                        return "FUNCTION";
                    }

                    @Override
                    public String getSourceFile() {
                        return testFile.toString();
                    }

                    @Override
                    public int getLineNumber() {
                        return 0;
                    }

                    @Override
                    public int getColumnNumber() {
                        return 0;
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
                        return Set.of(testFile);
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

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("New name cannot be null");
    }

    private String readFileContent(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read test file", e);
        }
    }

    private RenameRefactoring.Context createContext(
            Path sourceFile, String elementType, String oldName, String newName) {
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
                return 0;
            }

            @Override
            public int getColumnNumber() {
                return 0;
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
