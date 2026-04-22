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
@EnabledIfSystemProperty(named = "test.typescript.enabled", matches = "true") // GH-90000
@Tag("integration [GH-90000]")
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

    // Constants for duplicate literals
    private static final String NEW_NAME = "newName";
    private static final String FUNCTION = "function";
    private static final String TEST_FAILED = "Test failed: ";

    @BeforeEach
    void setUp() throws IOException { // GH-90000
        // Skip setup if TypeScript is not available
        isTypeScriptAvailable = TypeScriptRenameRefactoring.isSupported(); // GH-90000
        if (!isTypeScriptAvailable) { // GH-90000
            return;
        }

        refactoring = new TypeScriptRenameRefactoring(); // GH-90000
        testFile = tempDir.resolve("test_rename.ts [GH-90000]");
        executor = Executors.newSingleThreadExecutor(); // GH-90000
        projectContext = new PolyfixProjectContext(tempDir, null, List.of(), executor, null); // GH-90000

        // Create a simple TypeScript file for testing
        String testCode =
                """
                class MyClass {
                    private oldProperty: string;

                    constructor(value: string) { // GH-90000
                        this.oldProperty = value;
                    }

                    oldMethod(): string { // GH-90000
                        return this.oldProperty;
                    }

                    anotherMethod(): string { // GH-90000
                        return this.oldMethod(); // GH-90000
                    }
                }

                function functionToRename(param: string): string { // GH-90000
                    return param.toUpperCase(); // GH-90000
                }

                const obj = new MyClass("test [GH-90000]");
                console.log(obj.oldMethod()); // GH-90000
                console.log(functionToRename("hello [GH-90000]"));
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
        if (!isTypeScriptAvailable) { // GH-90000
            return; // Skip test if TypeScript is not available
        }

        try {
            // Given
            var context = createContext(testFile, "method", "oldMethod", "newMethod"); // GH-90000

            // When
            var result = refactoring.apply(context); // GH-90000

            // Then
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getModifiedFiles()).contains(testFile); // GH-90000

            // Verify the content was actually changed
            String content = readFileContent(testFile); // GH-90000
            assertThat(content).contains("newMethod(): string { [GH-90000]");
            assertThat(content).contains("return this.newMethod() [GH-90000]");
            assertThat(content).contains("console.log(obj.newMethod()) [GH-90000]");
        } catch (Exception e) { // GH-90000
            throw new AssertionError(TEST_FAILED + e.getMessage(), e); // GH-90000
        }
    }

    @Test
    void shouldRenameFunction() { // GH-90000
        if (!isTypeScriptAvailable) { // GH-90000
            return; // Skip test if TypeScript is not available
        }

        try {
            // Given
            var context =
                    createContext(testFile, "function", "functionToRename", "renamedFunction"); // GH-90000

            // When
            var result = refactoring.apply(context); // GH-90000

            // Then
            assertThat(result.isSuccess()).isTrue(); // GH-90000

            // Verify the content was actually changed
            String content = readFileContent(testFile); // GH-90000
            assertThat(content).contains("function renamedFunction(param: string): string [GH-90000]");
            assertThat(content).contains("console.log(renamedFunction(\"hello\"));"); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new AssertionError(TEST_FAILED + e.getMessage(), e); // GH-90000
        }
    }

    @Test
    void shouldRenameProperty() { // GH-90000
        if (!isTypeScriptAvailable) { // GH-90000
            return; // Skip test if TypeScript is not available
        }

        try {
            // Given
            var context = createContext(testFile, "property", "oldProperty", "newProperty"); // GH-90000

            // When
            var result = refactoring.apply(context); // GH-90000

            // Then
            assertThat(result.isSuccess()).isTrue(); // GH-90000

            // Verify the content was actually changed
            String content = readFileContent(testFile); // GH-90000
            assertThat(content).contains("private newProperty: string; [GH-90000]");
            assertThat(content).contains("this.newProperty = value; [GH-90000]");
            assertThat(content).contains("return this.newProperty; [GH-90000]");
        } catch (Exception e) { // GH-90000
            throw new AssertionError(TEST_FAILED + e.getMessage(), e); // GH-90000
        }
    }

    @Test
    void shouldNotRenameIfElementNotFound() { // GH-90000
        if (!isTypeScriptAvailable) { // GH-90000
            return; // Skip test if TypeScript is not available
        }

        try {
            // Given
            var context = createContext(testFile, FUNCTION, "nonExistentFunction", NEW_NAME); // GH-90000

            // When
            var result = refactoring.apply(context); // GH-90000

            // Then
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // The real implementation might return success with no changes or fail with a message
            if (result.getChangeSummary() != null) { // GH-90000
                assertThat(result.getChangeSummary()) // GH-90000
                        .containsAnyOf("No changes were made", "not found"); // GH-90000
            }
        } catch (Exception e) { // GH-90000
            throw new AssertionError(TEST_FAILED + e.getMessage(), e); // GH-90000
        }
    }

    @Test
    void shouldValidateNewName() { // GH-90000
        if (!isTypeScriptAvailable) { // GH-90000
            return; // Skip test if TypeScript is not available
        }

        try {
            // Test valid names
            assertThat(refactoring.isNewNameValid("validName [GH-90000]")).isTrue();
            assertThat(refactoring.isNewNameValid("validName123 [GH-90000]")).isTrue();
            assertThat(refactoring.isNewNameValid("_privateName [GH-90000]")).isTrue();
            assertThat(refactoring.isNewNameValid("$name [GH-90000]")).isTrue();

            // Test invalid names
            assertThat(refactoring.isNewNameValid(null)).isFalse(); // GH-90000
            assertThat(refactoring.isNewNameValid(" [GH-90000]")).isFalse();
            assertThat(refactoring.isNewNameValid("123invalid [GH-90000]")).isFalse();
            assertThat(refactoring.isNewNameValid("invalid-name [GH-90000]")).isFalse();
            assertThat(refactoring.isNewNameValid("invalid.name [GH-90000]")).isFalse();
        } catch (Exception e) { // GH-90000
            throw new AssertionError(TEST_FAILED + e.getMessage(), e); // GH-90000
        }
    }

    @Test
    void shouldCheckIfCanBeApplied() { // GH-90000
        if (!isTypeScriptAvailable) { // GH-90000
            return; // Skip test if TypeScript is not available
        }

        try {
            // Valid TypeScript file
            var validContext = createContext(testFile, FUNCTION, "someName", NEW_NAME); // GH-90000
            assertThat(refactoring.canApply(validContext)).isTrue(); // GH-90000

            // Non-existent file
            var invalidFileContext =
                    createContext( // GH-90000
                            tempDir.resolve("non_existent.ts [GH-90000]"), FUNCTION, "someName", NEW_NAME);
            assertThat(refactoring.canApply(invalidFileContext)).isFalse(); // GH-90000

            // Non-TypeScript file
            var nonTsFile = tempDir.resolve("not_typescript.txt [GH-90000]");
            try {
                Files.writeString(nonTsFile, "Not a TypeScript file"); // GH-90000
                var nonTsContext = createContext(nonTsFile, FUNCTION, "someName", NEW_NAME); // GH-90000
                assertThat(refactoring.canApply(nonTsContext)).isFalse(); // GH-90000
            } catch (IOException e) { // GH-90000
                throw new RuntimeException("Failed to create test file", e); // GH-90000
            }
        } catch (Exception e) { // GH-90000
            throw new AssertionError(TEST_FAILED + e.getMessage(), e); // GH-90000
        }
    }

    private String readFileContent(Path file) { // GH-90000
        try {
            return Files.readString(file); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to read test file", e); // GH-90000
        }
    }

    private Context createContext( // GH-90000
            Path sourceFile, String elementType, String oldName, String newName) {
        return new Context() { // GH-90000
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

    /**
     * Custom implementation of TypeScriptRenameRefactoring for testing purposes. This
     * implementation doesn't rely on external dependencies and directly modifies the file content.
     */
    // Custom implementation for testing purposes
    private static class RenameContextTestImpl implements Context {
        private final String oldName;
        private final String newName;
        private final String elementType;
        private final String sourceFile;
        private final PolyfixProjectContext projectContext;

        public RenameContextTestImpl( // GH-90000
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
            return sourceFile;
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
            return Set.of(Path.of(sourceFile)); // GH-90000
        }

        @Override
        public boolean isDryRun() { // GH-90000
            return false;
        }

        @Override
        public boolean isInteractive() { // GH-90000
            return false;
        }
    }
}
