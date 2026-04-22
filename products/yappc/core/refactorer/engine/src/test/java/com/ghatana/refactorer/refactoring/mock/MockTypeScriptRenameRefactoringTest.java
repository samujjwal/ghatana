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

 * @doc.purpose Handles mock type script rename refactoring test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class MockTypeScriptRenameRefactoringTest {
    @TempDir Path tempDir;
    private MockTypeScriptRenameRefactoring refactoring;
    private Path testFile;
    private PolyfixProjectContext projectContext;
    private ExecutorService executor;

    // Constants for duplicate literals
    private static final String FUNCTION = "FUNCTION";
    private static final String NEW_NAME = "newName";
    private static final String SOME_NAME = "someName";

    @BeforeEach
    void setUp() throws IOException { // GH-90000
        refactoring = new MockTypeScriptRenameRefactoring(); // GH-90000
        testFile = tempDir.resolve("test_rename.ts [GH-90000]");
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

        // Create a simple TypeScript file for testing
        String testCode =
                """
                class OldClass {
                    oldMethod(): string { // GH-90000
                        return "Hello";
                    }

                    anotherMethod(): string { // GH-90000
                        return this.oldMethod(); // GH-90000
                    }
                }

                function functionToRename(): number { // GH-90000
                    return 42;
                }

                interface OldInterface {
                    prop: string;
                    method(): void; // GH-90000
                }

                const oldVariable = "test";

                // Usage
                const instance = new OldClass(); // GH-90000
                console.log(instance.oldMethod()); // GH-90000
                console.log(functionToRename()); // GH-90000
                console.log(oldVariable); // GH-90000
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
        String originalContent = readFileContent(testFile); // GH-90000
        System.out.println("=== Original file content === [GH-90000]");
        System.out.println(originalContent); // GH-90000
        System.out.println("=============================\n [GH-90000]");

        var context = createContext(testFile, "METHOD", "oldMethod", "newMethod"); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Debug output
        System.out.println("=== Refactoring result === [GH-90000]");
        System.out.println("Success: " + result.isSuccess()); // GH-90000
        System.out.println("Message: " + result.getErrorMessage()); // GH-90000
        System.out.println("Modified files: " + result.getModifiedFiles()); // GH-90000
        System.out.println("==========================\n [GH-90000]");

        // Then
        assertThat(result.isSuccess()).as("Refactoring should be successful [GH-90000]").isTrue();

        assertThat(result.getModifiedFiles()) // GH-90000
                .as("Test file should be in modified files [GH-90000]")
                .contains(testFile); // GH-90000

        // Verify the content was actually changed
        String content = readFileContent(testFile); // GH-90000
        System.out.println("=== File content after refactoring === [GH-90000]");
        System.out.println(content); // GH-90000
        System.out.println("=====================================\n [GH-90000]");

        // Check method definition
        assertThat(content) // GH-90000
                .as("Method definition should be renamed [GH-90000]")
                .contains("newMethod(): string [GH-90000]");

        // Check method call in class
        assertThat(content) // GH-90000
                .as("Method call in class should be renamed [GH-90000]")
                .contains("return this.newMethod() [GH-90000]");

        // Check method call on instance
        assertThat(content) // GH-90000
                .as("Method call on instance should be renamed [GH-90000]")
                .contains("console.log(instance.newMethod()) [GH-90000]");

        // Additional debug output
        System.out.println("=== Debug Info === [GH-90000]");
        System.out.println( // GH-90000
                "Contains 'newMethod(): string': " + content.contains("newMethod(): string [GH-90000]"));
        System.out.println( // GH-90000
                "Contains 'return this.newMethod()': " // GH-90000
                        + content.contains("return this.newMethod() [GH-90000]"));
        System.out.println( // GH-90000
                "Contains 'console.log(instance.newMethod())': " // GH-90000
                        + content.contains("console.log(instance.newMethod()) [GH-90000]"));
        System.out.println("=================\n [GH-90000]");
    }

    @Test
    void shouldRenameClass() { // GH-90000
        // Given
        var context = createContext(testFile, "CLASS", "OldClass", "NewClass"); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getModifiedFiles()).contains(testFile); // GH-90000

        // Verify the content was actually changed
        String content = readFileContent(testFile); // GH-90000
        assertThat(content).contains("class NewClass [GH-90000]");
        assertThat(content).contains("new NewClass() [GH-90000]");
    }

    @Test
    void shouldRenameFunction() { // GH-90000
        // Given
        var context = createContext(testFile, FUNCTION, "functionToRename", "renamedFunction"); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000

        // Verify the content was actually changed
        String content = readFileContent(testFile); // GH-90000
        assertThat(content).contains("function renamedFunction() [GH-90000]");
        assertThat(content).contains("console.log(renamedFunction()) [GH-90000]");
    }

    @Test
    void shouldRenameInterface() { // GH-90000
        // Given
        var context = createContext(testFile, "INTERFACE", "OldInterface", "NewInterface"); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000

        // Verify the content was actually changed
        String content = readFileContent(testFile); // GH-90000
        assertThat(content).contains("interface NewInterface [GH-90000]");
    }

    @Test
    void shouldRenameVariable() { // GH-90000
        // Given
        var context = createContext(testFile, "VARIABLE", "oldVariable", "newVariable"); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000

        // Verify the content was actually changed
        String content = readFileContent(testFile); // GH-90000
        assertThat(content).contains("const newVariable = \"test\""); // GH-90000
        assertThat(content).contains("console.log(newVariable) [GH-90000]");
    }

    @Test
    void shouldNotRenameIfElementNotFound() { // GH-90000
        // Given
        var context = createContext(testFile, FUNCTION, "nonExistentFunction", NEW_NAME); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getChangeCount()).isEqualTo(0); // GH-90000
    }

    @Test
    void shouldCheckIfCanBeApplied() { // GH-90000
        // Valid TypeScript file
        var validContext = createContext(testFile, FUNCTION, SOME_NAME, NEW_NAME); // GH-90000
        assertThat(refactoring.canApply(validContext)).isTrue(); // GH-90000

        // Valid TSX file
        var tsxFile = tempDir.resolve("component.tsx [GH-90000]");
        try {
            Files.writeString(tsxFile, "const Component = () => <div>Hello</div>;"); // GH-90000
            var tsxContext = createContext(tsxFile, FUNCTION, SOME_NAME, NEW_NAME); // GH-90000
            assertThat(refactoring.canApply(tsxContext)).isTrue(); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to create test file", e); // GH-90000
        }

        // Non-existent file
        var invalidFileContext =
                createContext(Path.of("non_existent.ts [GH-90000]"), FUNCTION, SOME_NAME, NEW_NAME);
        assertThat(refactoring.canApply(invalidFileContext)).isFalse(); // GH-90000

        // Non-TypeScript file
        var nonTsFile = tempDir.resolve("not_typescript.txt [GH-90000]");
        try {
            Files.writeString(nonTsFile, "Not a TypeScript file"); // GH-90000
            var nonTsContext = createContext(nonTsFile, FUNCTION, SOME_NAME, NEW_NAME); // GH-90000
            assertThat(refactoring.canApply(nonTsContext)).isFalse(); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to create test file", e); // GH-90000
        }
    }

    @Test
    void shouldHandleIOExceptionWhenReadingFile() throws IOException { // GH-90000
        // Create a mock file that will throw an IOException when read
        Path mockFile = mock(Path.class); // GH-90000
        when(mockFile.toString()).thenReturn("/non/existent/file.ts [GH-90000]");

        // Mock Files.readString to throw IOException
        try (var mockedFiles = mockStatic(Files.class)) { // GH-90000
            mockedFiles
                    .when(() -> Files.readString(mockFile)) // GH-90000
                    .thenThrow(new IOException("Failed to read file [GH-90000]"));

            // Create a context with the mock file
            var context = createContext(mockFile, FUNCTION, "someFunction", "newFunction"); // GH-90000

            // When
            var result = refactoring.apply(context); // GH-90000

            // Then
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()) // GH-90000
                    .as("Error message should indicate file read failure [GH-90000]")
                    .contains("Source file does not exist [GH-90000]");
        }
    }

    @Test
    void shouldHandleInvalidElementType() { // GH-90000
        // Given
        var context = createContext(testFile, "INVALID_TYPE", "oldName", "newName"); // GH-90000

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
        Path emptyFile = tempDir.resolve("empty.ts [GH-90000]");
        Files.writeString(emptyFile, ""); // GH-90000

        // Given
        var context = createContext(emptyFile, FUNCTION, "someFunction", "newFunction"); // GH-90000

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
                        return "oldFunction";
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
        assertThat(result.getErrorMessage()).contains("New name cannot be null [GH-90000]");
    }

    @Test
    void shouldHandleSpecialCharactersInNames() throws IOException { // GH-90000
        // Create a file with special characters
        Path specialFile = tempDir.resolve("special.ts [GH-90000]");
        String specialCode =
                """
            function special$Function() { // GH-90000
                return "special";
            }

            const obj = {
                special$Function: function() { // GH-90000
                    return "special";
                }
            };

            console.log(special$Function()); // GH-90000
            console.log(obj.special$Function()); // GH-90000
            """;
        Files.writeString(specialFile, specialCode); // GH-90000

        // Given
        var context = createContext(specialFile, FUNCTION, "special$Function", "normalFunction"); // GH-90000

        // When
        var result = refactoring.apply(context); // GH-90000

        // Then
        assertThat(result.isSuccess()).isTrue(); // GH-90000

        // Verify the content was actually changed
        String content = readFileContent(specialFile); // GH-90000
        assertThat(content).contains("function normalFunction() [GH-90000]");
        assertThat(content).contains("normalFunction: function() [GH-90000]");
        assertThat(content).contains("console.log(normalFunction()) [GH-90000]");
        assertThat(content).contains("console.log(obj.normalFunction()) [GH-90000]");
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
