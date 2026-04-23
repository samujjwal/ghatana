package com.ghatana.refactorer.codemods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import io.activej.promise.Promise;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoSettings;
import org.openrewrite.Recipe;

@MockitoSettings
/**
 * @doc.type class
 * @doc.purpose Handles codemod orchestrator test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class CodemodOrchestratorTest {
    private static final String JSON = "json";
    private static final String UNCHECKED = "unchecked";
    private static final String JAVA = "java";

    @TempDir Path tempDir;
    private Path srcDir;
    private CodemodOrchestrator orchestrator;
    private OpenRewriteRunner mockOpenRewriteRunner;
    private JsonYamlCodemods mockJsonYamlCodemods;
    private PolyfixProjectContext mockContext;
    private Recipe mockRecipe;
    private Supplier<List<Recipe>> recipeSupplier;

    @BeforeEach
    void setUp() { // GH-90000
        srcDir = tempDir.resolve("src/main/java");

        mockContext = mock(PolyfixProjectContext.class); // GH-90000
        Logger mockLogger = mock(Logger.class); // GH-90000
        when(mockContext.log()).thenReturn(mockLogger); // GH-90000
        when(mockContext.root()).thenReturn(tempDir); // GH-90000

        mockOpenRewriteRunner = mock(OpenRewriteRunner.class); // GH-90000
        mockJsonYamlCodemods = mock(JsonYamlCodemods.class); // GH-90000
        mockRecipe = mock(Recipe.class); // GH-90000
        lenient().when(mockRecipe.getName()).thenReturn("mock-recipe");

        recipeSupplier = () -> List.of(mockRecipe); // GH-90000

        orchestrator =
                new CodemodOrchestrator( // GH-90000
                        mockContext, mockOpenRewriteRunner, mockJsonYamlCodemods, recipeSupplier);
    }

    @Test
    void testEmptyFilesList() { // GH-90000
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of()); // GH-90000
        assertThat(results).isEmpty(); // GH-90000
        verifyNoInteractions(mockOpenRewriteRunner, mockJsonYamlCodemods); // GH-90000
    }

    @Test
    void testJavaFilesOnly() throws Exception { // GH-90000
        // Setup test files
        Path javaFile = srcDir.resolve("Test.java");
        Files.createDirectories(javaFile.getParent()); // GH-90000
        Files.writeString(javaFile, "class Test {}"); // GH-90000

        // Mock OpenRewrite response
        when(mockOpenRewriteRunner.run(eq(mockRecipe), anyList())) // GH-90000
                .thenReturn(List.of(createMockDiagnostic(JAVA))); // GH-90000

        // Execute
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of(javaFile)); // GH-90000

        // Verify
        assertThat(results).hasSize(1); // GH-90000
        assertThat(results.get(0).getMessage()).contains(JAVA); // GH-90000
        @SuppressWarnings(UNCHECKED) // GH-90000
        ArgumentCaptor<List<Path>> JAVAFilesCaptor = ArgumentCaptor.forClass(List.class); // GH-90000
        verify(mockOpenRewriteRunner).run(eq(mockRecipe), JAVAFilesCaptor.capture()); // GH-90000
        assertThat(JAVAFilesCaptor.getValue()).containsExactly(javaFile); // GH-90000
        verifyNoInteractions(mockJsonYamlCodemods); // GH-90000
    }

    @Test
    void testJsonYamlFilesOnly() throws Exception { // GH-90000
        // Setup test files
        Path JSONFile = srcDir.resolve("config." + JSON); // GH-90000
        Files.createDirectories(JSONFile.getParent()); // GH-90000
        Files.writeString(JSONFile, "{}"); // GH-90000

        // Create schema directory
        Path schemaDir = tempDir.resolve("config/schemas");
        Files.createDirectories(schemaDir); // GH-90000

        // Mock JSON/YAML codemods response
        when(mockJsonYamlCodemods.normalizeAndValidate(anyList(), eq(schemaDir))) // GH-90000
                .thenReturn(Promise.of(List.of(createMockDiagnostic(JSON)))); // GH-90000

        // Execute
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of(JSONFile)); // GH-90000

        // Verify
        assertThat(results).hasSize(1); // GH-90000
        assertThat(results.get(0).getMessage()).contains(JSON); // GH-90000
        @SuppressWarnings(UNCHECKED) // GH-90000
        ArgumentCaptor<List<Path>> JSONFilesCaptor = ArgumentCaptor.forClass(List.class); // GH-90000
        verify(mockJsonYamlCodemods).normalizeAndValidate(JSONFilesCaptor.capture(), eq(schemaDir)); // GH-90000
        assertThat(JSONFilesCaptor.getValue()).containsExactly(JSONFile); // GH-90000
        verifyNoInteractions(mockOpenRewriteRunner); // GH-90000
    }

    @Test
    void testMixedFileTypes() throws Exception { // GH-90000
        // Setup test files
        Path javaFile = srcDir.resolve("Test.java");
        Path JSONFile = srcDir.resolve("config." + JSON); // GH-90000

        Files.createDirectories(javaFile.getParent()); // GH-90000
        Files.writeString(javaFile, "class Test {}"); // GH-90000
        Files.writeString(JSONFile, "{}"); // GH-90000

        // Create schema directory
        Path schemaDir = tempDir.resolve("config/schemas");
        Files.createDirectories(schemaDir); // GH-90000

        // Mock responses
        when(mockOpenRewriteRunner.run(eq(mockRecipe), anyList())) // GH-90000
                .thenReturn(List.of(createMockDiagnostic(JAVA))); // GH-90000
        when(mockJsonYamlCodemods.normalizeAndValidate(anyList(), eq(schemaDir))) // GH-90000
                .thenReturn(Promise.of(List.of(createMockDiagnostic(JSON)))); // GH-90000

        // Execute
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of(javaFile, JSONFile)); // GH-90000

        // Verify
        assertThat(results).hasSize(2); // GH-90000
        assertThat(results.stream().map(UnifiedDiagnostic::getMessage)) // GH-90000
                .containsExactlyInAnyOrder(JAVA, JSON); // GH-90000
        @SuppressWarnings(UNCHECKED) // GH-90000
        ArgumentCaptor<List<Path>> JAVACaptor = ArgumentCaptor.forClass(List.class); // GH-90000
        verify(mockOpenRewriteRunner).run(eq(mockRecipe), JAVACaptor.capture()); // GH-90000
        assertThat(JAVACaptor.getValue()).containsExactly(javaFile); // GH-90000

        @SuppressWarnings(UNCHECKED) // GH-90000
        ArgumentCaptor<List<Path>> JSONCaptor = ArgumentCaptor.forClass(List.class); // GH-90000
        verify(mockJsonYamlCodemods).normalizeAndValidate(JSONCaptor.capture(), eq(schemaDir)); // GH-90000
        assertThat(JSONCaptor.getValue()).containsExactly(JSONFile); // GH-90000
    }

    @Test
    void testErrorHandling() throws Exception { // GH-90000
        // Setup test file
        Path javaFile = srcDir.resolve("Test.java");
        Files.createDirectories(javaFile.getParent()); // GH-90000
        Files.writeString(javaFile, "class Test {}"); // GH-90000

        // Mock error
        when(mockOpenRewriteRunner.run(eq(mockRecipe), anyList())) // GH-90000
                .thenThrow(new RuntimeException("Test error"));

        // Execute
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of(javaFile)); // GH-90000

        // Verify
        assertThat(results).hasSize(1); // GH-90000
        assertThat(results.get(0).getMessage()) // GH-90000
                .contains("Error applying Java codemods: Test error");
    }

    private UnifiedDiagnostic createMockDiagnostic(String type) { // GH-90000
        return UnifiedDiagnostic.builder() // GH-90000
                .tool("test")
                .code(type) // GH-90000
                .message(type) // GH-90000
                .startLine(1) // GH-90000
                .startColumn(1) // GH-90000
                .endLine(1) // GH-90000
                .endColumn(1) // GH-90000
                .build(); // GH-90000
    }
}
