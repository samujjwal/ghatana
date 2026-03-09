package com.ghatana.refactorer.refactoring.impl.typescript;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.refactoring.api.RenameRefactoring.Context;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for TypeScriptRenameRefactoring.
 *
 * <p>These tests require J2V8 to be available on the system and will be skipped if not available.
 * To run these tests, set the system property 'test.typescript.enabled' to 'true'.
 *
 * <p>Example: {@code ./gradlew test -Dtest.typescript.enabled=true}
 */
@EnabledIfSystemProperty(named = "test.typescript.enabled", matches = "true")
@Tag("integration")
/**
 * @doc.type class
 * @doc.purpose Handles type script rename refactoring test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class TypeScriptRenameRefactoringTest {
    @TempDir Path tempDir;
    private TypeScriptRenameRefactoring refactoring;
    private Path testFile;
    private PolyfixProjectContext projectContext;
    private ExecutorService executor;
    private boolean isTypeScriptAvailable;

    @BeforeEach
    void setUp() throws IOException {
        // Skip setup if TypeScript is not available
        isTypeScriptAvailable = TypeScriptRenameRefactoring.isSupported();
        if (!isTypeScriptAvailable) {
            return;
        }

        refactoring = new TypeScriptRenameRefactoring();
        testFile = tempDir.resolve("test_rename.ts");
        executor = Executors.newSingleThreadExecutor();
        projectContext = new PolyfixProjectContext(tempDir, null, List.of(), executor, null);

        // Create a simple TypeScript file for testing
        String testCode =
                """
                class MyClass {
                    private oldProperty: string;

                    constructor(value: string) {
                        this.oldProperty = value;
                    }

                    oldMethod(): string {
                        return this.oldProperty;
                    }

                    anotherMethod(): string {
                        return this.oldMethod();
                    }
                }

                function functionToRename(param: string): string {
                    return param.toUpperCase();
                }

                const obj = new MyClass("test");
                console.log(obj.oldMethod());
                console.log(functionToRename("hello"));
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
        if (!isTypeScriptAvailable) {
            return; // Skip test if TypeScript is not available
        }

        try {
            // Given
            var context = createContext(testFile, "method", "oldMethod", "newMethod");

            // When
            var result = refactoring.apply(context);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getModifiedFiles()).contains(testFile);

            // Verify the content was actually changed
            String content = readFileContent(testFile);
            assertThat(content).contains("newMethod(): string {");
            assertThat(content).contains("return this.newMethod()");
            assertThat(content).contains("console.log(obj.newMethod())");
        } catch (Exception e) {
            throw new AssertionError("Test failed: " + e.getMessage(), e);
        }
    }

    @Test
    void shouldRenameFunction() {
        if (!isTypeScriptAvailable) {
            return; // Skip test if TypeScript is not available
        }

        try {
            // Given
            var context =
                    createContext(testFile, "function", "functionToRename", "renamedFunction");

            // When
            var result = refactoring.apply(context);

            // Then
            assertThat(result.isSuccess()).isTrue();

            // Verify the content was actually changed
            String content = readFileContent(testFile);
            assertThat(content).contains("function renamedFunction(param: string): string");
            assertThat(content).contains("console.log(renamedFunction(\"hello\"));");
        } catch (Exception e) {
            throw new AssertionError("Test failed: " + e.getMessage(), e);
        }
    }

    @Test
    void shouldRenameProperty() {
        if (!isTypeScriptAvailable) {
            return; // Skip test if TypeScript is not available
        }

        try {
            // Given
            var context = createContext(testFile, "property", "oldProperty", "newProperty");

            // When
            var result = refactoring.apply(context);

            // Then
            assertThat(result.isSuccess()).isTrue();

            // Verify the content was actually changed
            String content = readFileContent(testFile);
            assertThat(content).contains("private newProperty: string;");
            assertThat(content).contains("this.newProperty = value;");
            assertThat(content).contains("return this.newProperty;");
        } catch (Exception e) {
            throw new AssertionError("Test failed: " + e.getMessage(), e);
        }
    }

    @Test
    void shouldNotRenameIfElementNotFound() {
        if (!isTypeScriptAvailable) {
            return; // Skip test if TypeScript is not available
        }

        try {
            // Given
            var context = createContext(testFile, "function", "nonExistentFunction", "newName");

            // When
            var result = refactoring.apply(context);

            // Then
            assertThat(result.isSuccess()).isTrue();
            // The real implementation might return success with no changes or fail with a message
            if (result.getChangeSummary() != null) {
                assertThat(result.getChangeSummary())
                        .containsAnyOf("No changes were made", "not found");
            }
        } catch (Exception e) {
            throw new AssertionError("Test failed: " + e.getMessage(), e);
        }
    }

    @Test
    void shouldValidateNewName() {
        if (!isTypeScriptAvailable) {
            return; // Skip test if TypeScript is not available
        }

        try {
            // Test valid names
            assertThat(refactoring.isNewNameValid("validName")).isTrue();
            assertThat(refactoring.isNewNameValid("validName123")).isTrue();
            assertThat(refactoring.isNewNameValid("_privateName")).isTrue();
            assertThat(refactoring.isNewNameValid("$name")).isTrue();

            // Test invalid names
            assertThat(refactoring.isNewNameValid(null)).isFalse();
            assertThat(refactoring.isNewNameValid("")).isFalse();
            assertThat(refactoring.isNewNameValid("123invalid")).isFalse();
            assertThat(refactoring.isNewNameValid("invalid-name")).isFalse();
            assertThat(refactoring.isNewNameValid("invalid.name")).isFalse();
        } catch (Exception e) {
            throw new AssertionError("Test failed: " + e.getMessage(), e);
        }
    }

    @Test
    void shouldCheckIfCanBeApplied() {
        if (!isTypeScriptAvailable) {
            return; // Skip test if TypeScript is not available
        }

        try {
            // Valid TypeScript file
            var validContext = createContext(testFile, "function", "someName", "newName");
            assertThat(refactoring.canApply(validContext)).isTrue();

            // Non-existent file
            var invalidFileContext =
                    createContext(
                            tempDir.resolve("non_existent.ts"), "function", "someName", "newName");
            assertThat(refactoring.canApply(invalidFileContext)).isFalse();

            // Non-TypeScript file
            var nonTsFile = tempDir.resolve("not_typescript.txt");
            try {
                Files.writeString(nonTsFile, "Not a TypeScript file");
                var nonTsContext = createContext(nonTsFile, "function", "someName", "newName");
                assertThat(refactoring.canApply(nonTsContext)).isFalse();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create test file", e);
            }
        } catch (Exception e) {
            throw new AssertionError("Test failed: " + e.getMessage(), e);
        }
    }

    private String readFileContent(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read test file", e);
        }
    }

    private Context createContext(
            Path sourceFile, String elementType, String oldName, String newName) {
        return new Context() {
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

    /**
     * Custom implementation of TypeScriptRenameRefactoring for testing purposes. This
     * implementation doesn't rely on external dependencies and directly modifies the file content.
     */
    // Custom implementation for testing purposes
    private static class TestRenameContext implements Context {
        private final String oldName;
        private final String newName;
        private final String elementType;
        private final String sourceFile;
        private final PolyfixProjectContext projectContext;

        public TestRenameContext(
                String oldName,
                String newName,
                String elementType,
                String sourceFile,
                PolyfixProjectContext projectContext) {
            this.oldName = oldName;
            this.newName = newName;
            this.elementType = elementType;
            this.sourceFile = sourceFile;
            this.projectContext = projectContext;
        }

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
            return sourceFile;
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
            return Set.of(Path.of(sourceFile));
        }

        @Override
        public boolean isDryRun() {
            return false;
        }

        @Override
        public boolean isInteractive() {
            return false;
        }
    }
}
