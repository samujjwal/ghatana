package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayName("TestCodeGenerator Tests")
class TestCodeGeneratorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  TestCodeGeneratorTest() {
    MockitoAnnotations.openMocks(this);
  }

  @TempDir Path tempDir;

  @Test
  @DisplayName("generateTestCode builds compilable junit fallback tests")
  void generateTestCodeBuildsCompilableJunitFallbackTests() throws IOException {
    TestCodeGenerator generator = new TestCodeGenerator();

    TestCodeGenerator.GeneratedTestArtifact artifact =
        runPromise(
            () ->
                generator.generateTestCode(
                    "CalculatorService",
                    "package com.example; public class CalculatorService { public CalculatorService() {} }",
                    List.of(
                        new TestScenario(
                            "handles valid requests",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "a valid input exists",
                            "the subject is invoked",
                            "a result is produced",
                            List.of("main-flow"))),
                    TestCodeGenerator.TestFramework.JUNIT5));

    assertThat(artifact.fileName()).isEqualTo("CalculatorServiceTest.java");
    assertThat(artifact.sourceCode()).contains("@DisplayName(\"handles valid requests\")");

    compileJavaSource(tempDir, artifact.sourceCode(), "CalculatorServiceTest.java",
        "package com.example; public class CalculatorService { public CalculatorService() {} }");
  }

  @Test
  @DisplayName("generateTestCode builds vitest fallback tests")
  void generateTestCodeBuildsVitestFallbackTests() {
    TestCodeGenerator generator = new TestCodeGenerator();

    TestCodeGenerator.GeneratedTestArtifact artifact =
        runPromise(
            () ->
                generator.generateTestCode(
                    "WidgetStore",
                    "export class WidgetStore {}",
                    List.of(
                        new TestScenario(
                            "creates the store",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "a fixture exists",
                            "the store is created",
                            "the store is defined",
                            List.of("main-flow"))),
                    TestCodeGenerator.TestFramework.VITEST));

    assertThat(artifact.fileName()).isEqualTo("WidgetStore.test.ts");
    assertThat(artifact.sourceCode()).contains("from 'vitest'");
    assertThat(artifact.sourceCode()).contains("it('creates the store'");
  }

  @Test
  @DisplayName("generateTestCode prefers AI output and strips markdown fences")
  void generateTestCodePrefersAiOutputAndStripsMarkdownFences() {
    when(aiService.generateTests(anyString(), anyMap()))
        .thenReturn(Promise.of("```java\nclass GeneratedTest {}\n```"));

    TestCodeGenerator generator = new TestCodeGenerator(aiService);

    TestCodeGenerator.GeneratedTestArtifact artifact =
        runPromise(
            () ->
                generator.generateTestCode(
                    "Generated",
                    "public class Generated {}",
                    List.of(
                        new TestScenario(
                            "uses ai",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "ai is available",
                            "generation runs",
                            "ai output is returned",
                            List.of("ai"))),
                    TestCodeGenerator.TestFramework.JUNIT5));

    assertThat(artifact.sourceCode()).isEqualTo("class GeneratedTest {}");
    verify(aiService).generateTests(anyString(), anyMap());
  }

  @Test
  @DisplayName("generateTestCode falls back when AI returns blank source")
  void generateTestCodeFallsBackWhenAiReturnsBlankSource() {
    when(aiService.generateTests(anyString(), anyMap())).thenReturn(Promise.of("   "));

    TestCodeGenerator generator = new TestCodeGenerator(aiService);

    TestCodeGenerator.GeneratedTestArtifact artifact =
        runPromise(
            () ->
                generator.generateTestCode(
                    "FallbackSubject",
                    "public class FallbackSubject { public FallbackSubject() {} }",
                    List.of(
                        new TestScenario(
                            "fallback scenario",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "state is ready",
                            "generation runs",
                            "fallback is used",
                            List.of("fallback"))),
                    TestCodeGenerator.TestFramework.JUNIT5));

    assertThat(artifact.sourceCode()).contains("class FallbackSubjectTest");
  }

    @Test
    @DisplayName("generateTestCode handles null AI output and generated subject fallback")
    void generateTestCodeHandlesNullAiOutputAndGeneratedSubjectFallback() {
        when(aiService.generateTests(anyString(), anyMap())).thenReturn(Promise.of(null));

        TestCodeGenerator generator = new TestCodeGenerator(aiService);

        TestCodeGenerator.GeneratedTestArtifact artifact =
                runPromise(
                        () ->
                                generator.generateTestCode(
                                        "",
                                        null,
                                        List.of(
                                                new TestScenario(
                                                        "!!!",
                                                        TestScenario.ScenarioCategory.HAPPY_PATH,
                                                        "state exists",
                                                        "generation runs",
                                                        "fallback naming is used",
                                                        List.of())),
                                        TestCodeGenerator.TestFramework.JUNIT5));

        assertThat(artifact.fileName()).isEqualTo("GeneratedSubjectTest.java");
        assertThat(artifact.sourceCode()).contains("void scenario_0()");
    }

    @Test
    @DisplayName("generateTestCode preserves fenced output without newline delimiter")
    void generateTestCodePreservesFencedOutputWithoutNewlineDelimiter() {
        when(aiService.generateTests(anyString(), anyMap())).thenReturn(Promise.of("```java```"));

        TestCodeGenerator generator = new TestCodeGenerator(aiService);

        TestCodeGenerator.GeneratedTestArtifact artifact =
                runPromise(
                        () ->
                                generator.generateTestCode(
                                        "InlineFence",
                                        "public class InlineFence {}",
                                        List.of(
                                                new TestScenario(
                                                        "returns fenced text",
                                                        TestScenario.ScenarioCategory.HAPPY_PATH,
                                                        "ai responds",
                                                        "sanitization runs",
                                                        "text is preserved",
                                                        List.of())),
                                        TestCodeGenerator.TestFramework.JUNIT5));

        assertThat(artifact.sourceCode()).isEqualTo("```java```");
    }

  @Test
  @DisplayName("generateTestCode handles partial fences extracted types and null vitest source")
  void generateTestCodeHandlesPartialFencesExtractedTypesAndNullVitestSource() {
    when(aiService.generateTests(anyString(), anyMap()))
        .thenReturn(Promise.of("```java\nclass PartialFence {}"))
        .thenReturn(Promise.of(""))
        .thenReturn(Promise.of(""));

    TestCodeGenerator generator = new TestCodeGenerator(aiService);

    TestCodeGenerator.GeneratedTestArtifact partialFenceArtifact =
        runPromise(
            () ->
                generator.generateTestCode(
                    "PartialFence",
                    "public class PartialFence {}",
                    List.of(
                        new TestScenario(
                            "partial fence",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "ai returns a partial fence",
                            "sanitization runs",
                            "text stays intact",
                            List.of())),
                    TestCodeGenerator.TestFramework.JUNIT5));
    TestCodeGenerator.GeneratedTestArtifact extractedTypeArtifact =
        runPromise(
            () ->
                generator.generateTestCode(
                    "",
                    "package demo.sample; public class ExtractedType {}",
                    List.of(
                        new TestScenario(
                            "extract type",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "class source exists",
                            "fallback generation runs",
                            "type name is extracted",
                            List.of())),
                    TestCodeGenerator.TestFramework.JUNIT5));
    TestCodeGenerator.GeneratedTestArtifact vitestArtifact =
        runPromise(
            () ->
                generator.generateTestCode(
                    "VitestFallback",
                    null,
                    List.of(
                        new TestScenario(
                            "vitest fallback",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "source is absent",
                            "vitest fallback runs",
                            "package extraction handles null source",
                            List.of())),
                    TestCodeGenerator.TestFramework.VITEST));

    assertThat(partialFenceArtifact.sourceCode()).startsWith("```java");
    assertThat(extractedTypeArtifact.fileName()).isEqualTo("ExtractedTypeTest.java");
    assertThat(extractedTypeArtifact.sourceCode()).contains("package demo.sample;");
    assertThat(vitestArtifact.fileName()).isEqualTo("VitestFallback.test.ts");
  }

  @Test
  @DisplayName("private extraction helpers handle null and non-null source")
  void privateExtractionHelpersHandleNullAndNonNullSource() throws Exception {
    TestCodeGenerator generator = new TestCodeGenerator();
    Method extractPackageLine =
        TestCodeGenerator.class.getDeclaredMethod("extractPackageLine", String.class);
    Method extractPackageName =
        TestCodeGenerator.class.getDeclaredMethod("extractPackageName", String.class);
    Method extractClassName =
        TestCodeGenerator.class.getDeclaredMethod("extractClassName", String.class);
    extractPackageLine.setAccessible(true);
    extractPackageName.setAccessible(true);
    extractClassName.setAccessible(true);

    assertThat((String) extractPackageLine.invoke(generator, new Object[] {null})).isEmpty();
    assertThat((String) extractPackageLine.invoke(generator, "package demo.reflect; class Box {}"))
        .isEqualTo("package demo.reflect;");
    assertThat((String) extractPackageName.invoke(generator, new Object[] {null})).isEmpty();
    assertThat((String) extractPackageName.invoke(generator, "package demo.reflect; class Box {}"))
        .isEqualTo("demo.reflect");
    assertThat((String) extractClassName.invoke(generator, new Object[] {null}))
        .isEqualTo("GeneratedSubject");
    assertThat((String) extractClassName.invoke(generator, "public class ReflectedType {}"))
        .isEqualTo("ReflectedType");
  }

  private void compileJavaSource(Path tempDir, String testSource, String testFileName, String subjectSource)
      throws IOException {
    Path sourceDir = tempDir.resolve("src");
    Files.createDirectories(sourceDir.resolve("com/example"));
    Files.writeString(sourceDir.resolve("com/example/CalculatorService.java"), subjectSource);
    Files.writeString(sourceDir.resolve("com/example/" + testFileName), testSource);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    int result =
        compiler.run(
            null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            tempDir.resolve("classes").toString(),
            sourceDir.resolve("com/example/CalculatorService.java").toString(),
            sourceDir.resolve("com/example/" + testFileName).toString());

    assertThat(result).isZero();
  }
}