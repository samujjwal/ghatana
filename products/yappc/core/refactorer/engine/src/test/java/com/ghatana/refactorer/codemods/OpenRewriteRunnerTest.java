package com.ghatana.refactorer.codemods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

/**

 * @doc.type class

 * @doc.purpose Handles open rewrite runner test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class OpenRewriteRunnerTest {
    private static final String SRC_MAIN_JAVA_TEST_JAVA = "src/main/java/Test.java";
    private static final String CLASS_TEST = "class Test {}";

    @TempDir Path tempDir;
    private Path srcDir;
    private PolyfixProjectContext mockContext;
    private JavaParser mockParser;
    private OpenRewriteRunner runner;

    @BeforeEach
    void setUp() { // GH-90000
        srcDir = tempDir.resolve("src/main/java [GH-90000]");

        mockContext = mock(PolyfixProjectContext.class); // GH-90000
        when(mockContext.log()).thenReturn(mock(Logger.class)); // GH-90000
        when(mockContext.exec()).thenReturn(mock(ExecutorService.class)); // GH-90000
        when(mockContext.root()).thenReturn(tempDir); // GH-90000

        mockParser = mock(JavaParser.class); // GH-90000
        runner = new OpenRewriteRunner(mockContext, mockParser); // GH-90000

        when(mockParser.parse(any(), any(), any(ExecutionContext.class))) // GH-90000
                .thenReturn(java.util.stream.Stream.of(mock(SourceFile.class))); // GH-90000
    }

    @Test
    void testEmptyFilesList() { // GH-90000
        Recipe recipe = mock(Recipe.class); // GH-90000
        List<UnifiedDiagnostic> results = runner.run(recipe, List.of()); // GH-90000
        assertThat(results).isEmpty(); // GH-90000
        verifyNoInteractions(recipe); // GH-90000
    }

    @Test
    void testRecipeExecution() throws Exception { // GH-90000
        // Setup test file
        Path testFile = srcDir.resolve("Test.java [GH-90000]");
        Files.createDirectories(testFile.getParent()); // GH-90000
        Files.writeString(testFile, CLASS_TEST); // GH-90000

        // Mock OpenRewrite components
        SourceFile sourceFile = mock(SourceFile.class); // GH-90000
        when(sourceFile.getSourcePath()).thenReturn(Path.of(SRC_MAIN_JAVA_TEST_JAVA)); // GH-90000

        when(mockParser.parse(any(), any(), any(ExecutionContext.class))) // GH-90000
                .thenReturn(java.util.stream.Stream.of(sourceFile)); // GH-90000

        Recipe recipe = mock(Recipe.class); // GH-90000
        when(recipe.getName()).thenReturn("test-recipe [GH-90000]");

        RecipeRun recipeRun = mock(RecipeRun.class, RETURNS_DEEP_STUBS); // GH-90000
        Result result = mock(Result.class); // GH-90000
        SourceFile before = mock(SourceFile.class); // GH-90000
        SourceFile after = mock(SourceFile.class); // GH-90000
        when(before.getSourcePath()).thenReturn(Path.of(SRC_MAIN_JAVA_TEST_JAVA)); // GH-90000
        when(after.printAll()).thenReturn(CLASS_TEST); // GH-90000

        when(result.getBefore()).thenReturn(before); // GH-90000
        when(result.getAfter()).thenReturn(after); // GH-90000
        when(result.diff()).thenReturn("diff [GH-90000]");

        when(recipeRun.getChangeset().getAllResults()).thenReturn(List.of(result)); // GH-90000
        when(recipe.run(any(InMemoryLargeSourceSet.class), any(ExecutionContext.class))) // GH-90000
                .thenReturn(recipeRun); // GH-90000

        // Execute
        List<UnifiedDiagnostic> diagnostics = runner.run(recipe, List.of(testFile)); // GH-90000

        // Verify
        assertThat(diagnostics).hasSize(1); // GH-90000
        assertThat(diagnostics.get(0).getMessage()).contains("Applied test-recipe [GH-90000]");

        // Verify file was written
        assertThat(testFile).exists(); // GH-90000
        assertThat(testFile).content().isEqualTo(CLASS_TEST); // GH-90000
    }

    @Test
    void testErrorHandling() { // GH-90000
        Recipe recipe = mock(Recipe.class); // GH-90000
        when(recipe.getName()).thenReturn("failing-recipe [GH-90000]");
        when(recipe.run(any(InMemoryLargeSourceSet.class), any(ExecutionContext.class))) // GH-90000
                .thenThrow(new RuntimeException("Test error [GH-90000]"));

        Path testFile = tempDir.resolve("Test.java [GH-90000]");
        List<UnifiedDiagnostic> results = runner.run(recipe, List.of(testFile)); // GH-90000

        assertThat(results).hasSize(1); // GH-90000
        assertThat(results.get(0).getMessage()) // GH-90000
                .contains("Failed to apply OpenRewrite recipe: Test error [GH-90000]");
    }

    @Test
    void testFileWriting() throws IOException { // GH-90000
        // Setup test file with content that will be modified
        Path testFile = srcDir.resolve("Test.java [GH-90000]");
        Files.createDirectories(testFile.getParent()); // GH-90000
        Files.writeString(testFile, CLASS_TEST); // GH-90000

        // Mock OpenRewrite components
        SourceFile sourceFile = mock(SourceFile.class); // GH-90000
        when(sourceFile.getSourcePath()).thenReturn(Path.of(SRC_MAIN_JAVA_TEST_JAVA)); // GH-90000

        when(mockParser.parse(any(), any(), any(ExecutionContext.class))) // GH-90000
                .thenReturn(java.util.stream.Stream.of(sourceFile)); // GH-90000

        Recipe recipe = mock(Recipe.class); // GH-90000
        when(recipe.getName()).thenReturn("modify-recipe [GH-90000]");

        RecipeRun recipeRun = mock(RecipeRun.class, RETURNS_DEEP_STUBS); // GH-90000
        Result result = mock(Result.class); // GH-90000
        SourceFile before = mock(SourceFile.class); // GH-90000
        SourceFile after = mock(SourceFile.class); // GH-90000
        when(before.getSourcePath()).thenReturn(Path.of(SRC_MAIN_JAVA_TEST_JAVA)); // GH-90000
        when(after.printAll()).thenReturn("class Test { /* modified */ } [GH-90000]");

        when(result.getBefore()).thenReturn(before); // GH-90000
        when(result.getAfter()).thenReturn(after); // GH-90000
        when(result.diff()).thenReturn("diff [GH-90000]");

        when(recipeRun.getChangeset().getAllResults()).thenReturn(List.of(result)); // GH-90000
        when(recipe.run(any(InMemoryLargeSourceSet.class), any(ExecutionContext.class))) // GH-90000
                .thenReturn(recipeRun); // GH-90000

        // Execute
        runner.run(recipe, List.of(testFile)); // GH-90000

        // Verify file was modified
        assertThat(testFile).content().isEqualTo("class Test { /* modified */ } [GH-90000]");
    }
}
