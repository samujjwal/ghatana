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

    @BeforeEach
    void setUp() throws IOException {
        refactoring = new MockTypeScriptRenameRefactoring();
        testFile = tempDir.resolve("test_rename.ts");
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

        // Create a simple TypeScript file for testing
        String testCode =
                """
                class OldClass {
                    oldMethod(): string {
                        return "Hello";
                    }

                    anotherMethod(): string {
                        return this.oldMethod();
                    }
                }

                function functionToRename(): number {
                    return 42;
                }

                interface OldInterface {
                    prop: string;
                    method(): void;
                }

                const oldVariable = "test";

                // Usage
                const instance = new OldClass();
                console.log(instance.oldMethod());
                console.log(functionToRename());
                console.log(oldVariable);
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
        String originalContent = readFileContent(testFile);
        System.out.println("=== Original file content ===");
        System.out.println(originalContent);
        System.out.println("=============================\n");

        var context = createContext(testFile, "METHOD", "oldMethod", "newMethod");

        // When
        var result = refactoring.apply(context);

        // Debug output
        System.out.println("=== Refactoring result ===");
        System.out.println("Success: " + result.isSuccess());
        System.out.println("Message: " + result.getErrorMessage());
        System.out.println("Modified files: " + result.getModifiedFiles());
        System.out.println("==========================\n");

        // Then
        assertThat(result.isSuccess()).as("Refactoring should be successful").isTrue();

        assertThat(result.getModifiedFiles())
                .as("Test file should be in modified files")
                .contains(testFile);

        // Verify the content was actually changed
        String content = readFileContent(testFile);
        System.out.println("=== File content after refactoring ===");
        System.out.println(content);
        System.out.println("=====================================\n");

        // Check method definition
        assertThat(content)
                .as("Method definition should be renamed")
                .contains("newMethod(): string");

        // Check method call in class
        assertThat(content)
                .as("Method call in class should be renamed")
                .contains("return this.newMethod()");

        // Check method call on instance
        assertThat(content)
                .as("Method call on instance should be renamed")
                .contains("console.log(instance.newMethod())");

        // Additional debug output
        System.out.println("=== Debug Info ===");
        System.out.println(
                "Contains 'newMethod(): string': " + content.contains("newMethod(): string"));
        System.out.println(
                "Contains 'return this.newMethod()': "
                        + content.contains("return this.newMethod()"));
        System.out.println(
                "Contains 'console.log(instance.newMethod())': "
                        + content.contains("console.log(instance.newMethod())"));
        System.out.println("=================\n");
    }

    @Test
    void shouldRenameClass() {
        // Given
        var context = createContext(testFile, "CLASS", "OldClass", "NewClass");

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getModifiedFiles()).contains(testFile);

        // Verify the content was actually changed
        String content = readFileContent(testFile);
        assertThat(content).contains("class NewClass");
        assertThat(content).contains("new NewClass()");
    }

    @Test
    void shouldRenameFunction() {
        // Given
        var context = createContext(testFile, "FUNCTION", "functionToRename", "renamedFunction");

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();

        // Verify the content was actually changed
        String content = readFileContent(testFile);
        assertThat(content).contains("function renamedFunction()");
        assertThat(content).contains("console.log(renamedFunction())");
    }

    @Test
    void shouldRenameInterface() {
        // Given
        var context = createContext(testFile, "INTERFACE", "OldInterface", "NewInterface");

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();

        // Verify the content was actually changed
        String content = readFileContent(testFile);
        assertThat(content).contains("interface NewInterface");
    }

    @Test
    void shouldRenameVariable() {
        // Given
        var context = createContext(testFile, "VARIABLE", "oldVariable", "newVariable");

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();

        // Verify the content was actually changed
        String content = readFileContent(testFile);
        assertThat(content).contains("const newVariable = \"test\"");
        assertThat(content).contains("console.log(newVariable)");
    }

    @Test
    void shouldNotRenameIfElementNotFound() {
        // Given
        var context = createContext(testFile, "FUNCTION", "nonExistentFunction", "newName");

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChangeCount()).isEqualTo(0);
    }

    @Test
    void shouldCheckIfCanBeApplied() {
        // Valid TypeScript file
        var validContext = createContext(testFile, "FUNCTION", "someName", "newName");
        assertThat(refactoring.canApply(validContext)).isTrue();

        // Valid TSX file
        var tsxFile = tempDir.resolve("component.tsx");
        try {
            Files.writeString(tsxFile, "const Component = () => <div>Hello</div>;");
            var tsxContext = createContext(tsxFile, "FUNCTION", "someName", "newName");
            assertThat(refactoring.canApply(tsxContext)).isTrue();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test file", e);
        }

        // Non-existent file
        var invalidFileContext =
                createContext(Path.of("non_existent.ts"), "FUNCTION", "someName", "newName");
        assertThat(refactoring.canApply(invalidFileContext)).isFalse();

        // Non-TypeScript file
        var nonTsFile = tempDir.resolve("not_typescript.txt");
        try {
            Files.writeString(nonTsFile, "Not a TypeScript file");
            var nonTsContext = createContext(nonTsFile, "FUNCTION", "someName", "newName");
            assertThat(refactoring.canApply(nonTsContext)).isFalse();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test file", e);
        }
    }

    @Test
    void shouldHandleIOExceptionWhenReadingFile() throws IOException {
        // Create a mock file that will throw an IOException when read
        Path mockFile = mock(Path.class);
        when(mockFile.toString()).thenReturn("/non/existent/file.ts");

        // Mock Files.readString to throw IOException
        try (var mockedFiles = mockStatic(Files.class)) {
            mockedFiles
                    .when(() -> Files.readString(mockFile))
                    .thenThrow(new IOException("Failed to read file"));

            // Create a context with the mock file
            var context = createContext(mockFile, "FUNCTION", "someFunction", "newFunction");

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
        var context = createContext(testFile, "INVALID_TYPE", "oldName", "newName");

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
        Path emptyFile = tempDir.resolve("empty.ts");
        Files.writeString(emptyFile, "");

        // Given
        var context = createContext(emptyFile, "FUNCTION", "someFunction", "newFunction");

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
                        return "oldFunction";
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

    @Test
    void shouldHandleSpecialCharactersInNames() throws IOException {
        // Create a file with special characters
        Path specialFile = tempDir.resolve("special.ts");
        String specialCode =
                """
            function special$Function() {
                return "special";
            }

            const obj = {
                special$Function: function() {
                    return "special";
                }
            };

            console.log(special$Function());
            console.log(obj.special$Function());
            """;
        Files.writeString(specialFile, specialCode);

        // Given
        var context = createContext(specialFile, "FUNCTION", "special$Function", "normalFunction");

        // When
        var result = refactoring.apply(context);

        // Then
        assertThat(result.isSuccess()).isTrue();

        // Verify the content was actually changed
        String content = readFileContent(specialFile);
        assertThat(content).contains("function normalFunction()");
        assertThat(content).contains("normalFunction: function()");
        assertThat(content).contains("console.log(normalFunction())");
        assertThat(content).contains("console.log(obj.normalFunction())");
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
