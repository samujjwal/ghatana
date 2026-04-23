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

  TestCodeGeneratorTest() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
  }

  @TempDir Path tempDir;

  @Test
  @DisplayName("generateTestCode builds compilable junit fallback tests")
  void generateTestCodeBuildsCompilableJunitFallbackTests() throws IOException { // GH-90000
    TestCodeGenerator generator = new TestCodeGenerator(); // GH-90000

    TestCodeGenerator.GeneratedTestArtifact artifact =
        runPromise( // GH-90000
            () -> // GH-90000
                generator.generateTestCode( // GH-90000
                    "CalculatorService",
                    "package com.example; public class CalculatorService { public CalculatorService() {} }", // GH-90000
                    List.of( // GH-90000
                        new TestScenario( // GH-90000
                            "handles valid requests",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "a valid input exists",
                            "the subject is invoked",
                            "a result is produced",
                            List.of("main-flow"))),
                    TestCodeGenerator.TestFramework.JUNIT5));

    assertThat(artifact.fileName()).isEqualTo("CalculatorServiceTest.java");
    assertThat(artifact.sourceCode()).contains("@DisplayName(\"handles valid requests\")"); // GH-90000

    compileJavaSource(tempDir, artifact.sourceCode(), "CalculatorServiceTest.java", // GH-90000
        "package com.example; public class CalculatorService { public CalculatorService() {} }"); // GH-90000
  }

  @Test
  @DisplayName("generateTestCode builds vitest fallback tests")
  void generateTestCodeBuildsVitestFallbackTests() { // GH-90000
    TestCodeGenerator generator = new TestCodeGenerator(); // GH-90000

    TestCodeGenerator.GeneratedTestArtifact artifact =
        runPromise( // GH-90000
            () -> // GH-90000
                generator.generateTestCode( // GH-90000
                    "WidgetStore",
                    "export class WidgetStore {}",
                    List.of( // GH-90000
                        new TestScenario( // GH-90000
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
  void generateTestCodePrefersAiOutputAndStripsMarkdownFences() { // GH-90000
    when(aiService.generateTests(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("```java\nclass GeneratedTest {}\n```"));

    TestCodeGenerator generator = new TestCodeGenerator(aiService); // GH-90000

    TestCodeGenerator.GeneratedTestArtifact artifact =
        runPromise( // GH-90000
            () -> // GH-90000
                generator.generateTestCode( // GH-90000
                    "Generated",
                    "public class Generated {}",
                    List.of( // GH-90000
                        new TestScenario( // GH-90000
                            "uses ai",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "ai is available",
                            "generation runs",
                            "ai output is returned",
                            List.of("ai"))),
                    TestCodeGenerator.TestFramework.JUNIT5));

    assertThat(artifact.sourceCode()).isEqualTo("class GeneratedTest {}");
    verify(aiService).generateTests(anyString(), anyMap()); // GH-90000
  }

  @Test
  @DisplayName("generateTestCode falls back when AI returns blank source")
  void generateTestCodeFallsBackWhenAiReturnsBlankSource() { // GH-90000
    when(aiService.generateTests(anyString(), anyMap())).thenReturn(Promise.of("   "));

    TestCodeGenerator generator = new TestCodeGenerator(aiService); // GH-90000

    TestCodeGenerator.GeneratedTestArtifact artifact =
        runPromise( // GH-90000
            () -> // GH-90000
                generator.generateTestCode( // GH-90000
                    "FallbackSubject",
                    "public class FallbackSubject { public FallbackSubject() {} }", // GH-90000
                    List.of( // GH-90000
                        new TestScenario( // GH-90000
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
    void generateTestCodeHandlesNullAiOutputAndGeneratedSubjectFallback() { // GH-90000
        when(aiService.generateTests(anyString(), anyMap())).thenReturn(Promise.of(null)); // GH-90000

        TestCodeGenerator generator = new TestCodeGenerator(aiService); // GH-90000

        TestCodeGenerator.GeneratedTestArtifact artifact =
                runPromise( // GH-90000
                        () -> // GH-90000
                                generator.generateTestCode( // GH-90000
                                        "",
                                        null,
                                        List.of( // GH-90000
                                                new TestScenario( // GH-90000
                                                        "!!!",
                                                        TestScenario.ScenarioCategory.HAPPY_PATH,
                                                        "state exists",
                                                        "generation runs",
                                                        "fallback naming is used",
                                                        List.of())), // GH-90000
                                        TestCodeGenerator.TestFramework.JUNIT5));

        assertThat(artifact.fileName()).isEqualTo("GeneratedSubjectTest.java");
        assertThat(artifact.sourceCode()).contains("void scenario_0()");
    }

    @Test
    @DisplayName("generateTestCode preserves fenced output without newline delimiter")
    void generateTestCodePreservesFencedOutputWithoutNewlineDelimiter() { // GH-90000
        when(aiService.generateTests(anyString(), anyMap())).thenReturn(Promise.of("```java```"));

        TestCodeGenerator generator = new TestCodeGenerator(aiService); // GH-90000

        TestCodeGenerator.GeneratedTestArtifact artifact =
                runPromise( // GH-90000
                        () -> // GH-90000
                                generator.generateTestCode( // GH-90000
                                        "InlineFence",
                                        "public class InlineFence {}",
                                        List.of( // GH-90000
                                                new TestScenario( // GH-90000
                                                        "returns fenced text",
                                                        TestScenario.ScenarioCategory.HAPPY_PATH,
                                                        "ai responds",
                                                        "sanitization runs",
                                                        "text is preserved",
                                                        List.of())), // GH-90000
                                        TestCodeGenerator.TestFramework.JUNIT5));

        assertThat(artifact.sourceCode()).isEqualTo("```java```");
    }

  @Test
  @DisplayName("generateTestCode handles partial fences extracted types and null vitest source")
  void generateTestCodeHandlesPartialFencesExtractedTypesAndNullVitestSource() { // GH-90000
    when(aiService.generateTests(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("```java\nclass PartialFence {}"))
        .thenReturn(Promise.of(""))
        .thenReturn(Promise.of(""));

    TestCodeGenerator generator = new TestCodeGenerator(aiService); // GH-90000

    TestCodeGenerator.GeneratedTestArtifact partialFenceArtifact =
        runPromise( // GH-90000
            () -> // GH-90000
                generator.generateTestCode( // GH-90000
                    "PartialFence",
                    "public class PartialFence {}",
                    List.of( // GH-90000
                        new TestScenario( // GH-90000
                            "partial fence",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "ai returns a partial fence",
                            "sanitization runs",
                            "text stays intact",
                            List.of())), // GH-90000
                    TestCodeGenerator.TestFramework.JUNIT5));
    TestCodeGenerator.GeneratedTestArtifact extractedTypeArtifact =
        runPromise( // GH-90000
            () -> // GH-90000
                generator.generateTestCode( // GH-90000
                    "",
                    "package demo.sample; public class ExtractedType {}",
                    List.of( // GH-90000
                        new TestScenario( // GH-90000
                            "extract type",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "class source exists",
                            "fallback generation runs",
                            "type name is extracted",
                            List.of())), // GH-90000
                    TestCodeGenerator.TestFramework.JUNIT5));
    TestCodeGenerator.GeneratedTestArtifact vitestArtifact =
        runPromise( // GH-90000
            () -> // GH-90000
                generator.generateTestCode( // GH-90000
                    "VitestFallback",
                    null,
                    List.of( // GH-90000
                        new TestScenario( // GH-90000
                            "vitest fallback",
                            TestScenario.ScenarioCategory.HAPPY_PATH,
                            "source is absent",
                            "vitest fallback runs",
                            "package extraction handles null source",
                            List.of())), // GH-90000
                    TestCodeGenerator.TestFramework.VITEST));

    assertThat(partialFenceArtifact.sourceCode()).startsWith("```java");
    assertThat(extractedTypeArtifact.fileName()).isEqualTo("ExtractedTypeTest.java");
    assertThat(extractedTypeArtifact.sourceCode()).contains("package demo.sample;");
    assertThat(vitestArtifact.fileName()).isEqualTo("VitestFallback.test.ts");
  }

  @Test
  @DisplayName("private extraction helpers handle null and non-null source")
  void privateExtractionHelpersHandleNullAndNonNullSource() throws Exception { // GH-90000
    TestCodeGenerator generator = new TestCodeGenerator(); // GH-90000
    Method extractPackageLine =
        TestCodeGenerator.class.getDeclaredMethod("extractPackageLine", String.class); // GH-90000
    Method extractPackageName =
        TestCodeGenerator.class.getDeclaredMethod("extractPackageName", String.class); // GH-90000
    Method extractClassName =
        TestCodeGenerator.class.getDeclaredMethod("extractClassName", String.class); // GH-90000
    extractPackageLine.setAccessible(true); // GH-90000
    extractPackageName.setAccessible(true); // GH-90000
    extractClassName.setAccessible(true); // GH-90000

    assertThat((String) extractPackageLine.invoke(generator, new Object[] {null})).isEmpty(); // GH-90000
    assertThat((String) extractPackageLine.invoke(generator, "package demo.reflect; class Box {}")) // GH-90000
        .isEqualTo("package demo.reflect;");
    assertThat((String) extractPackageName.invoke(generator, new Object[] {null})).isEmpty(); // GH-90000
    assertThat((String) extractPackageName.invoke(generator, "package demo.reflect; class Box {}")) // GH-90000
        .isEqualTo("demo.reflect");
    assertThat((String) extractClassName.invoke(generator, new Object[] {null})) // GH-90000
        .isEqualTo("GeneratedSubject");
    assertThat((String) extractClassName.invoke(generator, "public class ReflectedType {}")) // GH-90000
        .isEqualTo("ReflectedType");
  }

  private void compileJavaSource(Path tempDir, String testSource, String testFileName, String subjectSource) // GH-90000
      throws IOException {
    Path sourceDir = tempDir.resolve("src");
    Files.createDirectories(sourceDir.resolve("com/example"));
    Files.writeString(sourceDir.resolve("com/example/CalculatorService.java"), subjectSource);
    Files.writeString(sourceDir.resolve("com/example/" + testFileName), testSource); // GH-90000

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler(); // GH-90000
    int result =
        compiler.run( // GH-90000
            null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            tempDir.resolve("classes").toString(),
            sourceDir.resolve("com/example/CalculatorService.java").toString(),
            sourceDir.resolve("com/example/" + testFileName).toString()); // GH-90000

    assertThat(result).isZero(); // GH-90000
  }
}
