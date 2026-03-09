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
    @TempDir Path tempDir;
    private Path srcDir;
    private CodemodOrchestrator orchestrator;
    private OpenRewriteRunner mockOpenRewriteRunner;
    private JsonYamlCodemods mockJsonYamlCodemods;
    private PolyfixProjectContext mockContext;
    private Recipe mockRecipe;
    private Supplier<List<Recipe>> recipeSupplier;

    @BeforeEach
    void setUp() {
        srcDir = tempDir.resolve("src/main/java");

        mockContext = mock(PolyfixProjectContext.class);
        Logger mockLogger = mock(Logger.class);
        when(mockContext.log()).thenReturn(mockLogger);
        when(mockContext.root()).thenReturn(tempDir);

        mockOpenRewriteRunner = mock(OpenRewriteRunner.class);
        mockJsonYamlCodemods = mock(JsonYamlCodemods.class);
        mockRecipe = mock(Recipe.class);
        lenient().when(mockRecipe.getName()).thenReturn("mock-recipe");

        recipeSupplier = () -> List.of(mockRecipe);

        orchestrator =
                new CodemodOrchestrator(
                        mockContext, mockOpenRewriteRunner, mockJsonYamlCodemods, recipeSupplier);
    }

    @Test
    void testEmptyFilesList() {
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of());
        assertThat(results).isEmpty();
        verifyNoInteractions(mockOpenRewriteRunner, mockJsonYamlCodemods);
    }

    @Test
    void testJavaFilesOnly() throws Exception {
        // Setup test files
        Path javaFile = srcDir.resolve("Test.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class Test {}");

        // Mock OpenRewrite response
        when(mockOpenRewriteRunner.run(eq(mockRecipe), anyList()))
                .thenReturn(List.of(createMockDiagnostic("java")));

        // Execute
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of(javaFile));

        // Verify
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage()).contains("java");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Path>> javaFilesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockOpenRewriteRunner).run(eq(mockRecipe), javaFilesCaptor.capture());
        assertThat(javaFilesCaptor.getValue()).containsExactly(javaFile);
        verifyNoInteractions(mockJsonYamlCodemods);
    }

    @Test
    void testJsonYamlFilesOnly() throws Exception {
        // Setup test files
        Path jsonFile = srcDir.resolve("config.json");
        Files.createDirectories(jsonFile.getParent());
        Files.writeString(jsonFile, "{}");

        // Create schema directory
        Path schemaDir = tempDir.resolve("config/schemas");
        Files.createDirectories(schemaDir);

        // Mock JSON/YAML codemods response
        when(mockJsonYamlCodemods.normalizeAndValidate(anyList(), eq(schemaDir)))
                .thenReturn(List.of(createMockDiagnostic("json")));

        // Execute
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of(jsonFile));

        // Verify
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage()).contains("json");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Path>> jsonFilesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockJsonYamlCodemods).normalizeAndValidate(jsonFilesCaptor.capture(), eq(schemaDir));
        assertThat(jsonFilesCaptor.getValue()).containsExactly(jsonFile);
        verifyNoInteractions(mockOpenRewriteRunner);
    }

    @Test
    void testMixedFileTypes() throws Exception {
        // Setup test files
        Path javaFile = srcDir.resolve("Test.java");
        Path jsonFile = srcDir.resolve("config.json");

        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class Test {}");
        Files.writeString(jsonFile, "{}");

        // Create schema directory
        Path schemaDir = tempDir.resolve("config/schemas");
        Files.createDirectories(schemaDir);

        // Mock responses
        when(mockOpenRewriteRunner.run(eq(mockRecipe), anyList()))
                .thenReturn(List.of(createMockDiagnostic("java")));
        when(mockJsonYamlCodemods.normalizeAndValidate(anyList(), eq(schemaDir)))
                .thenReturn(List.of(createMockDiagnostic("json")));

        // Execute
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of(javaFile, jsonFile));

        // Verify
        assertThat(results).hasSize(2);
        assertThat(results.stream().map(UnifiedDiagnostic::getMessage))
                .containsExactlyInAnyOrder("java", "json");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Path>> javaCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockOpenRewriteRunner).run(eq(mockRecipe), javaCaptor.capture());
        assertThat(javaCaptor.getValue()).containsExactly(javaFile);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Path>> jsonCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockJsonYamlCodemods).normalizeAndValidate(jsonCaptor.capture(), eq(schemaDir));
        assertThat(jsonCaptor.getValue()).containsExactly(jsonFile);
    }

    @Test
    void testErrorHandling() throws Exception {
        // Setup test file
        Path javaFile = srcDir.resolve("Test.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class Test {}");

        // Mock error
        when(mockOpenRewriteRunner.run(eq(mockRecipe), anyList()))
                .thenThrow(new RuntimeException("Test error"));

        // Execute
        List<UnifiedDiagnostic> results = orchestrator.applyCodemods(List.of(javaFile));

        // Verify
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage())
                .contains("Error applying Java codemods: Test error");
    }

    private UnifiedDiagnostic createMockDiagnostic(String type) {
        return UnifiedDiagnostic.builder()
                .tool("test")
                .code(type)
                .message(type)
                .startLine(1)
                .startColumn(1)
                .endLine(1)
                .endColumn(1)
                .build();
    }
}
