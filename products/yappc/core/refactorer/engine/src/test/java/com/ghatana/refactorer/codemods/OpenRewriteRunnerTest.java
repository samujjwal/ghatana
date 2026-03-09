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
    @TempDir Path tempDir;
    private Path srcDir;
    private PolyfixProjectContext mockContext;
    private JavaParser mockParser;
    private OpenRewriteRunner runner;

    @BeforeEach
    void setUp() {
        srcDir = tempDir.resolve("src/main/java");

        mockContext = mock(PolyfixProjectContext.class);
        when(mockContext.log()).thenReturn(mock(Logger.class));
        when(mockContext.exec()).thenReturn(mock(ExecutorService.class));
        when(mockContext.root()).thenReturn(tempDir);

        mockParser = mock(JavaParser.class);
        runner = new OpenRewriteRunner(mockContext, mockParser);

        when(mockParser.parse(any(), any(), any(ExecutionContext.class)))
                .thenReturn(java.util.stream.Stream.of(mock(SourceFile.class)));
    }

    @Test
    void testEmptyFilesList() {
        Recipe recipe = mock(Recipe.class);
        List<UnifiedDiagnostic> results = runner.run(recipe, List.of());
        assertThat(results).isEmpty();
        verifyNoInteractions(recipe);
    }

    @Test
    void testRecipeExecution() throws Exception {
        // Setup test file
        Path testFile = srcDir.resolve("Test.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "class Test {}");

        // Mock OpenRewrite components
        SourceFile sourceFile = mock(SourceFile.class);
        when(sourceFile.getSourcePath()).thenReturn(Path.of("src/main/java/Test.java"));

        when(mockParser.parse(any(), any(), any(ExecutionContext.class)))
                .thenReturn(java.util.stream.Stream.of(sourceFile));

        Recipe recipe = mock(Recipe.class);
        when(recipe.getName()).thenReturn("test-recipe");

        RecipeRun recipeRun = mock(RecipeRun.class, RETURNS_DEEP_STUBS);
        Result result = mock(Result.class);
        SourceFile before = mock(SourceFile.class);
        SourceFile after = mock(SourceFile.class);
        when(before.getSourcePath()).thenReturn(Path.of("src/main/java/Test.java"));
        when(after.printAll()).thenReturn("class Test {}");

        when(result.getBefore()).thenReturn(before);
        when(result.getAfter()).thenReturn(after);
        when(result.diff()).thenReturn("diff");

        when(recipeRun.getChangeset().getAllResults()).thenReturn(List.of(result));
        when(recipe.run(any(InMemoryLargeSourceSet.class), any(ExecutionContext.class)))
                .thenReturn(recipeRun);

        // Execute
        List<UnifiedDiagnostic> diagnostics = runner.run(recipe, List.of(testFile));

        // Verify
        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).getMessage()).contains("Applied test-recipe");

        // Verify file was written
        assertThat(testFile).exists();
        assertThat(testFile).content().isEqualTo("class Test {}");
    }

    @Test
    void testErrorHandling() {
        Recipe recipe = mock(Recipe.class);
        when(recipe.getName()).thenReturn("failing-recipe");
        when(recipe.run(any(InMemoryLargeSourceSet.class), any(ExecutionContext.class)))
                .thenThrow(new RuntimeException("Test error"));

        Path testFile = tempDir.resolve("Test.java");
        List<UnifiedDiagnostic> results = runner.run(recipe, List.of(testFile));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage())
                .contains("Failed to apply OpenRewrite recipe: Test error");
    }

    @Test
    void testFileWriting() throws IOException {
        // Setup test file with content that will be modified
        Path testFile = srcDir.resolve("Test.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "class Test {}");

        // Mock OpenRewrite components
        SourceFile sourceFile = mock(SourceFile.class);
        when(sourceFile.getSourcePath()).thenReturn(Path.of("src/main/java/Test.java"));

        when(mockParser.parse(any(), any(), any(ExecutionContext.class)))
                .thenReturn(java.util.stream.Stream.of(sourceFile));

        Recipe recipe = mock(Recipe.class);
        when(recipe.getName()).thenReturn("modify-recipe");

        RecipeRun recipeRun = mock(RecipeRun.class, RETURNS_DEEP_STUBS);
        Result result = mock(Result.class);
        SourceFile before = mock(SourceFile.class);
        SourceFile after = mock(SourceFile.class);
        when(before.getSourcePath()).thenReturn(Path.of("src/main/java/Test.java"));
        when(after.printAll()).thenReturn("class Test { /* modified */ }");

        when(result.getBefore()).thenReturn(before);
        when(result.getAfter()).thenReturn(after);
        when(result.diff()).thenReturn("diff");

        when(recipeRun.getChangeset().getAllResults()).thenReturn(List.of(result));
        when(recipe.run(any(InMemoryLargeSourceSet.class), any(ExecutionContext.class)))
                .thenReturn(recipeRun);

        // Execute
        runner.run(recipe, List.of(testFile));

        // Verify file was modified
        assertThat(testFile).content().isEqualTo("class Test { /* modified */ }");
    }
}
