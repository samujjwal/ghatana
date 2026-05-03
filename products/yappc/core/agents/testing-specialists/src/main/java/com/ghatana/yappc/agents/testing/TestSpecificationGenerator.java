package com.ghatana.yappc.agents.testing;

import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose Generates test scenarios from source code and requirements before test code synthesis
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TestSpecificationGenerator {
  private static final Pattern FIELD_PATTERN = Pattern.compile("^(SCENARIO|CATEGORY|GIVEN|WHEN|THEN|COVERAGE):\\s*(.+)$");

  private final YAPPCAIInterface aiService;

  public TestSpecificationGenerator(YAPPCAIInterface aiService) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
  }

  public Promise<List<TestScenario>> generateSpecifications(TestSpecificationRequest request) {
    Objects.requireNonNull(request, "request");

    Map<String, Object> context = Map.of(
        "className", request.className(),
        "requirements", request.requirements(),
        "classSource", request.classSource(),
        "expectedCategories", List.of("HAPPY_PATH", "EDGE_CASE", "BOUNDARY_VALUE"));

    return aiService.reason(buildPrompt(request), context)
        .map(response -> ensureCoverageCategories(parseResponse(response), request));
  }

  private String buildPrompt(TestSpecificationRequest request) {
    return "Create human-readable GIVEN/WHEN/THEN test scenarios for the class under test. "
        + "Return one or more blocks using this exact structure:\n"
        + "SCENARIO: <title>\n"
        + "CATEGORY: HAPPY_PATH|EDGE_CASE|BOUNDARY_VALUE\n"
        + "GIVEN: <context>\n"
        + "WHEN: <action>\n"
        + "THEN: <expected outcome>\n"
        + "COVERAGE: <comma separated targets>\n"
        + "---\n"
        + "Class: " + request.className() + "\n"
        + "Requirements: " + String.join(" | ", request.requirements()) + "\n"
        + "Source:\n" + request.classSource();
  }

  private List<TestScenario> parseResponse(String response) {
    if (response == null || response.isBlank()) {
      return List.of();
    }

    List<TestScenario> scenarios = new ArrayList<>();
    Map<String, String> block = new LinkedHashMap<>();

    for (String rawLine : response.replace("\r", "").split("\n")) {
      String line = rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }
      if ("---".equals(line)) {
        addParsedScenario(scenarios, block);
        block = new LinkedHashMap<>();
        continue;
      }

      Matcher matcher = FIELD_PATTERN.matcher(line);
      if (matcher.matches()) {
        String key = matcher.group(1);
        String value = matcher.group(2).trim();
        if ("SCENARIO".equals(key) && !block.isEmpty()) {
          addParsedScenario(scenarios, block);
          block = new LinkedHashMap<>();
        }
        block.put(key, value);
      }
    }

    addParsedScenario(scenarios, block);
    return scenarios;
  }

  private void addParsedScenario(List<TestScenario> scenarios, Map<String, String> block) {
    if (block.isEmpty() || !block.containsKey("SCENARIO") || !block.containsKey("CATEGORY")
        || !block.containsKey("GIVEN") || !block.containsKey("WHEN") || !block.containsKey("THEN")) {
      return;
    }

    List<String> coverageTargets = block.containsKey("COVERAGE")
        ? List.of(block.get("COVERAGE").split(","))
            .stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList()
        : List.of();

    scenarios.add(new TestScenario(
        block.get("SCENARIO"),
        parseCategory(block.get("CATEGORY")),
        block.get("GIVEN"),
        block.get("WHEN"),
        block.get("THEN"),
        coverageTargets));
  }

  private TestScenario.ScenarioCategory parseCategory(String value) {
    return switch (value.trim().toUpperCase(Locale.ROOT).replace(' ', '_')) {
      case "HAPPY_PATH", "HAPPY" -> TestScenario.ScenarioCategory.HAPPY_PATH;
      case "EDGE_CASE", "EDGE", "ERROR_CASE" -> TestScenario.ScenarioCategory.EDGE_CASE;
      case "BOUNDARY_VALUE", "BOUNDARY", "BOUNDARY_VALUES" -> TestScenario.ScenarioCategory.BOUNDARY_VALUE;
      default -> TestScenario.ScenarioCategory.EDGE_CASE;
    };
  }

  private List<TestScenario> ensureCoverageCategories(
      List<TestScenario> parsedScenarios,
      TestSpecificationRequest request) {
    Map<TestScenario.ScenarioCategory, TestScenario> byCategory = new LinkedHashMap<>();
    for (TestScenario scenario : parsedScenarios) {
      byCategory.putIfAbsent(scenario.category(), scenario);
    }

    byCategory.putIfAbsent(TestScenario.ScenarioCategory.HAPPY_PATH, fallbackHappyPath(request));
    byCategory.putIfAbsent(TestScenario.ScenarioCategory.EDGE_CASE, fallbackEdgeCase(request));
    byCategory.putIfAbsent(TestScenario.ScenarioCategory.BOUNDARY_VALUE, fallbackBoundary(request));

    return List.copyOf(byCategory.values());
  }

  private TestScenario fallbackHappyPath(TestSpecificationRequest request) {
    String primaryRequirement = request.requirements().isEmpty()
        ? "the primary supported workflow"
        : request.requirements().getFirst();
    return new TestScenario(
        request.className() + " handles primary workflow",
        TestScenario.ScenarioCategory.HAPPY_PATH,
        "a valid caller and inputs aligned with " + primaryRequirement,
        request.className() + " executes the main operation",
        "the operation succeeds and returns the expected result",
        List.of("main-flow", "requirement"));
  }

  private TestScenario fallbackEdgeCase(TestSpecificationRequest request) {
    return new TestScenario(
        request.className() + " rejects invalid input",
        TestScenario.ScenarioCategory.EDGE_CASE,
        "missing, null, or malformed input reaches " + request.className(),
        "the class validates the request",
        "the failure is explicit and no silent success is returned",
        List.of("validation", "error-handling"));
  }

  private TestScenario fallbackBoundary(TestSpecificationRequest request) {
    String source = request.classSource();
    String boundaryTarget = determineBoundaryTarget(source);
    return new TestScenario(
        request.className() + " respects boundary limits",
        TestScenario.ScenarioCategory.BOUNDARY_VALUE,
        "inputs are set at " + boundaryTarget,
        request.className() + " processes the boundary input",
        "the boundary is handled predictably without overflow or truncation bugs",
        List.of("boundary", boundaryTarget));
  }

  private String determineBoundaryTarget(String source) {
    String normalized = source.toLowerCase(Locale.ROOT);
    if (normalized.contains("list<") || normalized.contains("set<") || normalized.contains("collection")) {
      return "the maximum supported collection size";
    }
    if (normalized.contains("string") || normalized.contains("charsequence")) {
      return "minimum and maximum supported string lengths";
    }
    if (normalized.contains("int") || normalized.contains("long") || normalized.contains("double")
        || normalized.contains("float") || normalized.contains("bigdecimal")) {
      return "numeric minimum and maximum values";
    }
    return "the documented operational limits";
  }
}
