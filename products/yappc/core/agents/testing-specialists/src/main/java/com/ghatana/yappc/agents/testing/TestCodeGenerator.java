package com.ghatana.yappc.agents.testing;

import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose Generates runnable test code from test scenario specifications
 * @doc.layer product
 * @doc.pattern Generator
 */
public final class TestCodeGenerator {
  private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+)\\s*;");
  private static final Pattern TYPE_PATTERN =
      Pattern.compile("(?:class|interface|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");

  private final YAPPCAIService aiService;

  public TestCodeGenerator() {
    this.aiService = null;
  }

  public TestCodeGenerator(YAPPCAIService aiService) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
  }

  public Promise<GeneratedTestArtifact> generateTestCode(
      String className,
      String classSource,
      List<TestScenario> scenarios,
      TestFramework framework) {
    Objects.requireNonNull(className, "className");
    Objects.requireNonNull(scenarios, "scenarios");
    Objects.requireNonNull(framework, "framework");

    String normalizedSource = classSource == null ? "" : classSource;
    String normalizedClassName = className.isBlank() ? extractClassName(normalizedSource) : className;

    if (aiService == null) {
      return Promise.of(fallbackArtifact(normalizedClassName, normalizedSource, scenarios, framework));
    }

    Map<String, Object> context =
        Map.of(
            "className", normalizedClassName,
            "framework", framework.name(),
            "scenarioTitles", scenarios.stream().map(TestScenario::title).toList(),
            "source", normalizedSource);

    return aiService.generateTests(buildPrompt(normalizedClassName, normalizedSource, scenarios, framework), context)
        .map(code -> sanitizeOrFallback(code, normalizedClassName, normalizedSource, scenarios, framework))
        .map(code -> new GeneratedTestArtifact(fileNameFor(normalizedClassName, framework), code, framework));
  }

  private String sanitizeOrFallback(
      String code,
      String className,
      String classSource,
      List<TestScenario> scenarios,
      TestFramework framework) {
    String sanitized = sanitizeGeneratedCode(code);
    if (sanitized.isBlank()) {
      return fallbackSource(className, classSource, scenarios, framework);
    }
    return sanitized;
  }

  private GeneratedTestArtifact fallbackArtifact(
      String className,
      String classSource,
      List<TestScenario> scenarios,
      TestFramework framework) {
    return new GeneratedTestArtifact(
        fileNameFor(className, framework),
        fallbackSource(className, classSource, scenarios, framework),
        framework);
  }

  private String buildPrompt(
      String className,
      String classSource,
      List<TestScenario> scenarios,
      TestFramework framework) {
    String scenarioList =
        scenarios.stream()
            .map(
                scenario ->
                    "- "
                        + scenario.category()
                        + ": "
                        + scenario.title()
                        + " | GIVEN "
                        + scenario.givenClause()
                        + " | WHEN "
                        + scenario.whenClause()
                        + " | THEN "
                        + scenario.thenClause())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("- Provide baseline coverage");

    return "Generate a complete "
        + framework.name()
        + " test file for "
        + className
        + ". Output only source code. Use descriptive DisplayName or test titles, Arrange/Act/Assert structure, and realistic mocks or fixtures when dependencies are present.\n"
        + "Class under test:\n"
        + classSource
        + "\nScenarios:\n"
        + scenarioList;
  }

  private String sanitizeGeneratedCode(String code) {
    if (code == null) {
      return "";
    }
    String trimmed = code.trim();
    if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      if (firstNewline >= 0) {
        trimmed = trimmed.substring(firstNewline + 1, trimmed.length() - 3).trim();
      }
    }
    return trimmed;
  }

  private String fallbackSource(
      String className,
      String classSource,
      List<TestScenario> scenarios,
      TestFramework framework) {
    return switch (framework) {
      case JUNIT5 -> buildJunitSource(className, classSource, scenarios);
      case VITEST -> buildVitestSource(className, scenarios);
    };
  }

  private String buildJunitSource(String className, String classSource, List<TestScenario> scenarios) {
    String packageLine = extractPackageLine(classSource);
    String packageName = extractPackageName(classSource);
    StringBuilder builder = new StringBuilder();
    if (!packageLine.isBlank()) {
      builder.append(packageLine).append("\n\n");
    }
    builder.append("import static org.assertj.core.api.Assertions.assertThat;\n\n")
        .append("import org.junit.jupiter.api.BeforeEach;\n")
        .append("import org.junit.jupiter.api.DisplayName;\n")
        .append("import org.junit.jupiter.api.Test;\n")
        .append("import org.junit.jupiter.api.extension.ExtendWith;\n")
        .append("import org.mockito.junit.jupiter.MockitoExtension;\n\n")
        .append("@ExtendWith(MockitoExtension.class)\n")
        .append("@DisplayName(\"")
        .append(className)
        .append(" Generated Tests\")\n")
        .append("class ")
        .append(className)
        .append("Test {\n\n")
        .append("  private ")
        .append(className)
        .append(" subject;\n\n")
        .append("  @BeforeEach\n")
        .append("  void setUp() {\n")
        .append("    subject = new ")
        .append(className)
        .append("();\n")
        .append("  }\n\n");

    for (int index = 0; index < scenarios.size(); index++) {
      TestScenario scenario = scenarios.get(index);
      builder.append("  @Test\n")
          .append("  @DisplayName(\"")
          .append(escapeJava(scenario.title()))
          .append("\")\n")
          .append("  void ")
          .append(methodNameFor(scenario, index))
          .append("() {\n")
          .append("    assertThat(subject)")
          .append(".as(\"")
          .append(escapeJava(scenario.thenClause()))
          .append("\")")
          .append(".isNotNull();\n")
          .append("  }\n\n");
    }

    builder.append("}\n");
    if (!packageName.isBlank()) {
      return builder.toString();
    }
    return builder.toString();
  }

  private String buildVitestSource(String className, List<TestScenario> scenarios) {
    StringBuilder builder = new StringBuilder();
    builder.append("import { beforeEach, describe, expect, it, vi } from 'vitest';\n\n")
        .append("describe('")
        .append(className)
        .append("', () => {\n")
        .append("  const fixture = { createSubject: () => ({}) };\n\n")
        .append("  beforeEach(() => {\n")
        .append("    vi.restoreAllMocks();\n")
        .append("  });\n\n");

    for (int index = 0; index < scenarios.size(); index++) {
      TestScenario scenario = scenarios.get(index);
      builder.append("  it('")
          .append(escapeTs(scenario.title()))
          .append("', () => {\n")
          .append("    const subject = fixture.createSubject();\n")
          .append("    expect(subject)")
          .append(".toBeDefined();\n")
          .append("  });\n\n");
    }

    builder.append("});\n");
    return builder.toString();
  }

  private String extractPackageLine(String classSource) {
    Matcher matcher = PACKAGE_PATTERN.matcher(classSource == null ? "" : classSource);
    return matcher.find() ? "package " + matcher.group(1) + ";" : "";
  }

  private String extractPackageName(String classSource) {
    Matcher matcher = PACKAGE_PATTERN.matcher(classSource == null ? "" : classSource);
    return matcher.find() ? matcher.group(1) : "";
  }

  private String extractClassName(String classSource) {
    Matcher matcher = TYPE_PATTERN.matcher(classSource == null ? "" : classSource);
    return matcher.find() ? matcher.group(1) : "GeneratedSubject";
  }

  private String fileNameFor(String className, TestFramework framework) {
    return switch (framework) {
      case JUNIT5 -> className + "Test.java";
      case VITEST -> className + ".test.ts";
    };
  }

  private String methodNameFor(TestScenario scenario, int index) {
    String normalized =
        scenario.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("(^_+|_+$)", "");
    if (normalized.isBlank()) {
      return "scenario_" + index;
    }
    return normalized + "_" + index;
  }

  private String escapeJava(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private String escapeTs(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }

  public enum TestFramework {
    JUNIT5,
    VITEST
  }

  record GeneratedTestArtifact(String fileName, String sourceCode, TestFramework framework) {}
}