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

    @BeforeEach
    void setUp() throws IOException {
        // Create mock reference resolver
        referenceResolver = mock(ReferenceResolver.class);

        // Create real mock implementations
        pythonRefactoring = new MockPythonRenameRefactoring();
        typescriptRefactoring = new MockTypeScriptRenameRefactoring();

        // Create executor and project context
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

        // Create orchestrator with mock implementations
        orchestrator = new RefactoringOrchestrator(referenceResolver, projectContext, false);
        orchestrator.registerRefactoring(pythonRefactoring);
        orchestrator.registerRefactoring(typescriptRefactoring);

        // Create test files
        pythonFile = tempDir.resolve("module.py");
        typescriptFile = tempDir.resolve("component.ts");

        // Create Python file
        String pythonCode =
                "def old_function():\n"
                        + "    return \"Hello from Python\"\n"
                        + "    \n"
                        + "class PythonClass:\n"
                        + "    def use_typescript_function(self):\n"
                        + "        # This references a TypeScript function\n"
                        + "        print(\"Calling typescript_function\")\n";
        Files.writeString(pythonFile, pythonCode);

        // Create TypeScript file
        String typescriptCode =
                "function typescript_function(): string {\n"
                        + "    return \"Hello from TypeScript\";\n"
                        + "}\n"
                        + "\n"
                        + "class TypeScriptClass {\n"
                        + "    callPythonFunction(): void {\n"
                        + "        // This references a Python function\n"
                        + "        console.log(\"Calling old_function\");\n"
                        + "    }\n"
                        + "}\n";
        Files.writeString(typescriptFile, typescriptCode);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldHandleCrossLanguageRefactoring() {
        // Setup cross-language references
        CrossLanguageReference pyToTsRef =
                CrossLanguageReference.builder()
                        .sourceFile(pythonFile.toString())
                        .sourceLanguage("python")
                        .sourceElement("use_typescript_function")
                        .sourceElementType("METHOD")
                        .sourcePosition(4, 4)
                        .targetFile(typescriptFile.toString())
                        .targetLanguage("typescript")
                        .targetElement("typescript_function")
                        .targetElementType("FUNCTION")
                        .referenceType(ReferenceType.METHOD_CALL)
                        .build();

        CrossLanguageReference tsToPyRef =
                CrossLanguageReference.builder()
                        .sourceFile(typescriptFile.toString())
                        .sourceLanguage("typescript")
                        .sourceElement("callPythonFunction")
                        .sourceElementType("METHOD")
                        .sourcePosition(6, 4)
                        .targetFile(pythonFile.toString())
                        .targetLanguage("python")
                        .targetElement("old_function")
                        .targetElementType("FUNCTION")
                        .referenceType(ReferenceType.METHOD_CALL)
                        .build();

        // Configure reference resolver to return cross-language references
        when(referenceResolver.findReferences(any(), any(), any(), any()))
                .thenReturn(List.of(pyToTsRef));
        when(referenceResolver.findIncomingReferences(any())).thenReturn(List.of(tsToPyRef));

        // Create context for renaming Python function
        RenameRefactoring.Context context =
                createContext(pythonFile, "FUNCTION", "old_function", "new_function");

        // Perform the refactoring
        RefactoringResult result = orchestrator.performRename(context);

        // Verify the result
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChangeCount()).isGreaterThan(0);

        // Verify Python file was updated
        String pythonContent = readFileContent(pythonFile);
        assertThat(pythonContent).contains("def new_function()");
        assertThat(pythonContent).doesNotContain("def old_function()");

        // Verify TypeScript file was updated
        String tsContent = readFileContent(typescriptFile);
        assertThat(tsContent).contains("Calling new_function");
        assertThat(tsContent).doesNotContain("Calling old_function");
    }

    @Test
    void shouldHandleTypeScriptToJavaScriptRefactoring() {
        // Create JavaScript file
        Path jsFile = tempDir.resolve("script.js");
        try {
            String jsCode =
                    "// JavaScript code that uses TypeScript function\n"
                            + "function jsFunction() {\n"
                            + "    return typescript_function() + \" from JS\";\n"
                            + "}\n";
            Files.writeString(jsFile, jsCode);

            // Setup cross-language references
            CrossLanguageReference jsToTsRef =
                    CrossLanguageReference.builder()
                            .sourceFile(jsFile.toString())
                            .sourceLanguage("javascript")
                            .sourceElement("jsFunction")
                            .sourceElementType("FUNCTION")
                            .sourcePosition(3, 4)
                            .targetFile(typescriptFile.toString())
                            .targetLanguage("typescript")
                            .targetElement("typescript_function")
                            .targetElementType("FUNCTION")
                            .referenceType(ReferenceType.METHOD_CALL)
                            .build();

            // Configure reference resolver
            when(referenceResolver.findReferences(any(), any(), any(), any()))
                    .thenReturn(List.of(jsToTsRef));
            when(referenceResolver.findIncomingReferences(any())).thenReturn(List.of());

            // Create context for renaming TypeScript function
            RenameRefactoring.Context context =
                    createContext(
                            typescriptFile,
                            "FUNCTION",
                            "typescript_function",
                            "renamed_typescript_function");

            // Perform the refactoring
            RefactoringResult result = orchestrator.performRename(context);

            // Verify the result
            assertThat(result.isSuccess()).isTrue();

            // Verify TypeScript file was updated
            String tsContent = readFileContent(typescriptFile);
            assertThat(tsContent).contains("function renamed_typescript_function()");
            assertThat(tsContent).doesNotContain("function typescript_function()");

            // Verify JavaScript file was updated
            String jsContent = readFileContent(jsFile);
            assertThat(jsContent).contains("return renamed_typescript_function()");
            assertThat(jsContent).doesNotContain("return typescript_function()");
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
