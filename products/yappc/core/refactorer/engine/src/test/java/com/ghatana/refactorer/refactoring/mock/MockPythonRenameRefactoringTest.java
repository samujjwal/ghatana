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

    // Constants for duplicate literals
    private static final String FUNCTION = "FUNCTION";
    private static final String NEW_NAME = "new_name";

    @BeforeEach
    void setUp() throws IOException { // GH-90000
        refactoring = new MockPythonRenameRefactoring(); // GH-90000
        testFile = tempDir.resolve("test_rename.py");
        executor = Executors.newSingleThreadExecutor(); // GH-90000

        // Create a minimal PolyfixConfig for testing
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of("java", "python", "typescript"), // GH-90000
                        List.of(), // GH-90000
                        new PolyfixConfig.Budgets(10, 100), // GH-90000
                        new PolyfixConfig.Policies(false, true, true, false), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
                                "node", "", "", "", "", "", "", "", "", "", "", ""));

        projectContext = new PolyfixProjectContext(tempDir, config, List.of(), executor, null); // GH-90000

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
        var context = createContext(testFile, "METHOD", "old_method", "new_method"); // GH-90000

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
        var context = createContext(testFile, FUNCTION, "function_to_rename", "renamed_function"); // GH-90000

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
        var context = createContext(testFile, FUNCTION, "non_existent_function", NEW_NAME); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getChangeCount()).isEqualTo(0); // GH-90000
    }

    @Test
    void shouldCheckIfCanBeApplied() { // GH-90000
        // Valid Python file
        var validContext = createContext(testFile, FUNCTION, "some_name", NEW_NAME); // GH-90000

        assertThat(refactoring.canApply(validContext)).isTrue(); // GH-90000

        // Non-existent file
        var invalidFileContext =
                createContext(Path.of("non_existent.py"), FUNCTION, "some_name", NEW_NAME);

        assertThat(refactoring.canApply(invalidFileContext)).isFalse(); // GH-90000

        // Non-Python file
        var nonPythonFile = tempDir.resolve("not_python.txt");
        try {
            Files.writeString(nonPythonFile, "Not a Python file"); // GH-90000

            var nonPythonContext =
                    createContext(nonPythonFile, FUNCTION, "some_name", NEW_NAME); // GH-90000

            assertThat(refactoring.canApply(nonPythonContext)).isFalse(); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to create test file", e); // GH-90000
        }
    }

    @Test
    void shouldHandleIOExceptionWhenReadingFile() throws IOException { // GH-90000
        // Create a mock file that will throw an IOException when read
        Path mockFile = mock(Path.class); // GH-90000
        when(mockFile.toString()).thenReturn("/non/existent/file.py");

        // Mock Files.readString to throw IOException
        try (var mockedFiles = mockStatic(Files.class)) { // GH-90000
            mockedFiles
                    .when(() -> Files.readString(mockFile)) // GH-90000
                    .thenThrow(new IOException("Failed to read file"));

            // Create a context with the mock file
            var context = createContext(mockFile, FUNCTION, "some_function", "new_function"); // GH-90000

            // When
            var result = refactoring.apply(context); // GH-90000

            // Then
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()) // GH-90000
                    .as("Error message should indicate file read failure")
                    .contains("Source file does not exist");
        }
    }

    @Test
    void shouldHandleInvalidElementType() { // GH-90000
        // Given
        var context = createContext(testFile, "INVALID_TYPE", "old_name", "new_name"); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        // Should not throw an exception, but should return a result with no changes
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getChangeCount()).isEqualTo(0); // GH-90000
    }

    @Test
    void shouldHandleEmptyFile() throws IOException { // GH-90000
        // Create an empty file
        Path emptyFile = tempDir.resolve("empty.py");
        Files.writeString(emptyFile, ""); // GH-90000

        // Given
        var context = createContext(emptyFile, FUNCTION, "some_function", "new_function"); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getChangeCount()).isEqualTo(0); // GH-90000
    }

    @Test
    void shouldHandleNullNewName() { // GH-90000
        // Given
        var context =
                new RenameRefactoring.Context() { // GH-90000
                    @Override
                    public String getOldName() { // GH-90000
                        return "old_function";
                    }

                    @Override
                    public String getNewName() { // GH-90000
                        return null; // Null new name
                    }

                    @Override
                    public String getElementType() { // GH-90000
                        return FUNCTION;
                    }

                    @Override
                    public String getSourceFile() { // GH-90000
                        return testFile.toString(); // GH-90000
                    }

                    @Override
                    public int getLineNumber() { // GH-90000
                        return 0;
                    }

                    @Override
                    public int getColumnNumber() { // GH-90000
                        return 0;
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
                        return Set.of(testFile); // GH-90000
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

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isFalse(); // GH-90000
        assertThat(result.getErrorMessage()).contains("New name cannot be null");
    }

    private String readFileContent(Path file) { // GH-90000
        try {
            return Files.readString(file); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to read test file", e); // GH-90000
        }
    }

    private RenameRefactoring.Context createContext( // GH-90000
            Path sourceFile, String elementType, String oldName, String newName) {
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
                return 0;
            }

            @Override
            public int getColumnNumber() { // GH-90000
                return 0;
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
