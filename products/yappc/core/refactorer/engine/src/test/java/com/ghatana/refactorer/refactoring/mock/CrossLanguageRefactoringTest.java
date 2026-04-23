package com.ghatana.refactorer.refactoring.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import com.ghatana.refactorer.refactoring.model.crosslanguage.CrossLanguageReference;
import com.ghatana.refactorer.refactoring.model.crosslanguage.CrossLanguageReference.ReferenceType;
import com.ghatana.refactorer.refactoring.orchestrator.RefactoringOrchestrator;
import com.ghatana.refactorer.refactoring.service.ReferenceResolver;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests cross-language refactoring using mock implementations. This test ensures that the
 * RefactoringOrchestrator can handle refactorings across different languages.
 */
@Disabled("Temporarily disabled until cross-language refactoring is fully implemented")
/**
 * @doc.type class
 * @doc.purpose Handles cross language refactoring test operations
 * @doc.layer core
 * @doc.pattern Test
 */
public class CrossLanguageRefactoringTest {
    @TempDir Path tempDir;

    private ReferenceResolver referenceResolver;
    private MockPythonRenameRefactoring pythonRefactoring;
    private MockTypeScriptRenameRefactoring typescriptRefactoring;
    private ExecutorService executor;
    private PolyfixProjectContext projectContext;
    private RefactoringOrchestrator orchestrator;

    private Path pythonFile;
    private Path typescriptFile;

    // Constants for duplicate literals
    private static final String FUNCTION = "FUNCTION";
    private static final String TYPESCRIPT = "typescript";

    @BeforeEach
    void setUp() throws IOException { // GH-90000
        // Create mock reference resolver
        referenceResolver = mock(ReferenceResolver.class); // GH-90000

        // Create real mock implementations
        pythonRefactoring = new MockPythonRenameRefactoring(); // GH-90000
        typescriptRefactoring = new MockTypeScriptRenameRefactoring(); // GH-90000

        // Create executor and project context
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

        // Create orchestrator with mock implementations
        orchestrator = new RefactoringOrchestrator(referenceResolver, projectContext, false); // GH-90000
        orchestrator.registerRefactoring(pythonRefactoring); // GH-90000
        orchestrator.registerRefactoring(typescriptRefactoring); // GH-90000

        // Create test files
        pythonFile = tempDir.resolve("module.py");
        typescriptFile = tempDir.resolve("component.ts");

        // Create Python file
        String pythonCode =
                "def old_function():\n" // GH-90000
                        + "    return \"Hello from Python\"\n"
                        + "    \n"
                        + "class PythonClass:\n"
                        + "    def use_typescript_function(self):\n" // GH-90000
                        + "        # This references a TypeScript function\n"
                        + "        print(\"Calling typescript_function\")\n"; // GH-90000
        Files.writeString(pythonFile, pythonCode); // GH-90000

        // Create TypeScript file
        String typescriptCode =
                "function typescript_function(): string {\n" // GH-90000
                        + "    return \"Hello from TypeScript\";\n"
                        + "}\n"
                        + "\n"
                        + "class TypeScriptClass {\n"
                        + "    callPythonFunction(): void {\n" // GH-90000
                        + "        // This references a Python function\n"
                        + "        console.log(\"Calling old_function\");\n" // GH-90000
                        + "    }\n"
                        + "}\n";
        Files.writeString(typescriptFile, typescriptCode); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (executor != null) { // GH-90000
            executor.shutdownNow(); // GH-90000
        }
    }

    @Test
    void shouldHandleCrossLanguageRefactoring() { // GH-90000
        // Setup cross-language references
        CrossLanguageReference pyToTsRef =
                CrossLanguageReference.builder() // GH-90000
                        .sourceFile(pythonFile.toString()) // GH-90000
                        .sourceLanguage("python")
                        .sourceElement("use_typescript_function")
                        .sourceElementType("METHOD")
                        .sourcePosition(4, 4) // GH-90000
                        .targetFile(typescriptFile.toString()) // GH-90000
                        .targetLanguage(TYPESCRIPT) // GH-90000
                        .targetElement("typescript_function")
                        .targetElementType(FUNCTION) // GH-90000
                        .referenceType(ReferenceType.METHOD_CALL) // GH-90000
                        .build(); // GH-90000

        CrossLanguageReference tsToPyRef =
                CrossLanguageReference.builder() // GH-90000
                        .sourceFile(typescriptFile.toString()) // GH-90000
                        .sourceLanguage(TYPESCRIPT) // GH-90000
                        .sourceElement("callPythonFunction")
                        .sourceElementType("METHOD")
                        .sourcePosition(6, 4) // GH-90000
                        .targetFile(pythonFile.toString()) // GH-90000
                        .targetLanguage("python")
                        .targetElement("old_function")
                        .targetElementType(FUNCTION) // GH-90000
                        .referenceType(ReferenceType.METHOD_CALL) // GH-90000
                        .build(); // GH-90000

        // Configure reference resolver to return cross-language references
        when(referenceResolver.findReferences(any(), any(), any(), any())) // GH-90000
                .thenReturn(List.of(pyToTsRef)); // GH-90000
        when(referenceResolver.findIncomingReferences(any())).thenReturn(List.of(tsToPyRef)); // GH-90000

        // Create context for renaming Python function
        RenameRefactoring.Context context =
                createContext(pythonFile, FUNCTION, "old_function", "new_function"); // GH-90000

        // Perform the refactoring
        RefactoringResult result = orchestrator.performRename(context); // GH-90000

        // Verify the result
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getChangeCount()).isGreaterThan(0); // GH-90000

        // Verify Python file was updated
        String pythonContent = readFileContent(pythonFile); // GH-90000
        assertThat(pythonContent).contains("def new_function()");
        assertThat(pythonContent).doesNotContain("def old_function()");

        // Verify TypeScript file was updated
        String tsContent = readFileContent(typescriptFile); // GH-90000
        assertThat(tsContent).contains("Calling new_function");
        assertThat(tsContent).doesNotContain("Calling old_function");
    }

    @Test
    void shouldHandleTypeScriptToJavaScriptRefactoring() { // GH-90000
        // Create JavaScript file
        Path jsFile = tempDir.resolve("script.js");
        try {
            String jsCode =
                    "// JavaScript code that uses TypeScript function\n"
                            + "function jsFunction() {\n" // GH-90000
                            + "    return typescript_function() + \" from JS\";\n" // GH-90000
                            + "}\n";
            Files.writeString(jsFile, jsCode); // GH-90000

            // Setup cross-language references
            CrossLanguageReference jsToTsRef =
                    CrossLanguageReference.builder() // GH-90000
                            .sourceFile(jsFile.toString()) // GH-90000
                            .sourceLanguage("javascript")
                            .sourceElement("jsFunction")
                            .sourceElementType("FUNCTION")
                            .sourcePosition(3, 4) // GH-90000
                            .targetFile(typescriptFile.toString()) // GH-90000
                            .targetLanguage(TYPESCRIPT) // GH-90000
                            .targetElement("typescript_function")
                            .targetElementType(FUNCTION) // GH-90000
                            .referenceType(ReferenceType.METHOD_CALL) // GH-90000
                            .build(); // GH-90000

            // Configure reference resolver
            when(referenceResolver.findReferences(any(), any(), any(), any())) // GH-90000
                    .thenReturn(List.of(jsToTsRef)); // GH-90000
            when(referenceResolver.findIncomingReferences(any())).thenReturn(List.of()); // GH-90000

            // Create context for renaming TypeScript function
            RenameRefactoring.Context context =
                    createContext( // GH-90000
                            typescriptFile,
                            FUNCTION,
                            "typescript_function",
                            "renamed_typescript_function");

            // Perform the refactoring
            RefactoringResult result = orchestrator.performRename(context); // GH-90000

            // Verify the result
            assertThat(result.isSuccess()).isTrue(); // GH-90000

            // Verify TypeScript file was updated
            String tsContent = readFileContent(typescriptFile); // GH-90000
            assertThat(tsContent).contains("function renamed_typescript_function()");
            assertThat(tsContent).doesNotContain("function typescript_function()");

            // Verify JavaScript file was updated
            String jsContent = readFileContent(jsFile); // GH-90000
            assertThat(jsContent).contains("return renamed_typescript_function()");
            assertThat(jsContent).doesNotContain("return typescript_function()");
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
