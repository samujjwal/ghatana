package com.ghatana.yappc.platform.ai.analyzers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Detects under-tested production changes by combining coverage signals, test history, and AI suggestions.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class TestGapAnalyzer {

  private static final TypeReference<List<Map<String, Object>>> ISSUE_LIST_TYPE =
      new TypeReference<>() {};

  private final YAPPCAIInterface aiService;
  private final CoverageReportProvider coverageReportProvider;
  private final TestHistoryProvider testHistoryProvider;
  private final ObjectMapper objectMapper;

  public TestGapAnalyzer(
      YAPPCAIInterface aiService,
      CoverageReportProvider coverageReportProvider,
      TestHistoryProvider testHistoryProvider) {
    this(aiService, coverageReportProvider, testHistoryProvider, new ObjectMapper());
  }

  TestGapAnalyzer(
      YAPPCAIInterface aiService,
      CoverageReportProvider coverageReportProvider,
      TestHistoryProvider testHistoryProvider,
      ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.coverageReportProvider = Objects.requireNonNull(coverageReportProvider, "coverageReportProvider");
    this.testHistoryProvider = Objects.requireNonNull(testHistoryProvider, "testHistoryProvider");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
    Objects.requireNonNull(event, "event");
    if (isTestFile(event.filePath())) {
      return Promise.of(List.of());
    }

    return coverageReportProvider
        .fetch(event.projectId(), event.filePath())
        .then(
            coverage ->
                testHistoryProvider
                    .fetch(event.projectId(), event.filePath())
                    .then(
                        history -> {
                          List<AIInsight> deterministicFindings =
                              runDeterministicChecks(event, coverage, history);
                          if (!deterministicFindings.isEmpty()) {
                            return Promise.of(deterministicFindings);
                          }

                          Map<String, Object> context = new LinkedHashMap<>();
                          context.put("filePath", event.filePath());
                          context.put("coverage", coverage.summary());
                          context.put("testHistory", history.summary());
                          context.put("projectId", event.projectId());
                          context.put("tenantId", event.tenantId());

                          return aiService
                              .reason(buildPrompt(event, coverage, history), context)
                              .map(response -> parseInsights(response, event));
                        }));
  }

  private List<AIInsight> runDeterministicChecks(
      CodeChangedEvent event, CoverageSnapshot coverage, RecentTestHistory history) {
    List<AIInsight> findings = new ArrayList<>();

    if (history.relatedTestFiles().isEmpty()) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.WARNING,
              "No related tests found",
              "The changed production file does not have any mapped related test files.",
              "Create or link a test file for this area before merging the change.",
              0,
              List.of("test-gap", "mapping"),
              0.96));
    }

    if (coverage.lineCoverage() < 0.8
        || coverage.branchCoverage() < 0.75
        || coverage.missedLines() > 0
        || coverage.missedBranches() > 0) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.WARNING,
              "Coverage gap detected",
              coverage.summary(),
              "Add tests for the uncovered paths before relying on this change in automation.",
              0,
              List.of("test-gap", "coverage"),
              0.91));
    }

    if (history.recentFailures() > 0 || history.flakyTests() > 0) {
      findings.add(
          createInsight(
              event,
              AIInsight.InsightSeverity.INFO,
              "Existing tests are unstable",
              history.summary(),
              "Stabilize the related test suite before adding more coverage on top of it.",
              0,
              List.of("test-gap", "stability"),
              0.78));
    }

    return findings;
  }

  private List<AIInsight> parseInsights(String response, CodeChangedEvent event) {
    if (response == null || response.isBlank()) {
      return List.of();
    }

    try {
      List<Map<String, Object>> payload = objectMapper.readValue(response, ISSUE_LIST_TYPE);
      return payload.stream().map(entry -> fromPayload(entry, event)).toList();
    } catch (IOException exception) {
      return List.of(
          createInsight(
              event,
              AIInsight.InsightSeverity.INFO,
              "Manual test gap review recommended",
              response.trim(),
              "Review the AI suggestion manually because the structured response could not be parsed.",
              0,
              List.of("test-gap", "ai-review"),
              0.5));
    }
  }

  private AIInsight fromPayload(Map<String, Object> entry, CodeChangedEvent event) {
    AIInsight.InsightSeverity severity =
        AIInsight.InsightSeverity.valueOf(
            String.valueOf(entry.getOrDefault("severity", "WARNING")).toUpperCase(Locale.ROOT));
    String uncoveredPath = String.valueOf(entry.getOrDefault("uncoveredPath", "Untested path"));
    return createInsight(
        event,
        severity,
        uncoveredPath,
        String.valueOf(entry.getOrDefault("description", uncoveredPath)),
        String.valueOf(entry.getOrDefault("suggestedTest", "Add focused regression coverage.")),
        asInt(entry.get("lineNumber")),
        List.of("test-gap", "ai"),
        asDouble(entry.get("confidence")));
  }

  private AIInsight createInsight(
      CodeChangedEvent event,
      AIInsight.InsightSeverity severity,
      String title,
      String description,
      String suggestion,
      int lineNumber,
      List<String> tags,
      double confidence) {
    return new AIInsight(
        UUID.randomUUID().toString(),
        event.tenantId(),
        event.projectId(),
        AIInsight.InsightType.TEST_GAP,
        severity,
        title,
        description,
        suggestion,
        confidence,
        event.filePath(),
        lineNumber,
        tags,
        Instant.now(),
        false);
  }

  private String buildPrompt(
      CodeChangedEvent event, CoverageSnapshot coverage, RecentTestHistory history) {
    return "Identify missing or weak tests for this production code change. Return a JSON array with severity, uncoveredPath, description, suggestedTest, lineNumber, and confidence."
        + "\nChanged file: "
        + event.filePath()
        + "\nCoverage: "
        + coverage.summary()
        + "\nTest history: "
        + history.summary()
        + "\nDiff:\n"
        + event.diff();
  }

  private boolean isTestFile(String filePath) {
    return filePath.endsWith("Test.java")
        || filePath.contains("/__tests__/")
        || filePath.endsWith(".test.ts")
        || filePath.endsWith(".test.tsx");
  }

  private int asInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null) {
      return 0.0;
    }
    return Double.parseDouble(String.valueOf(value));
  }

  public interface CoverageReportProvider {
    Promise<CoverageSnapshot> fetch(String projectId, String filePath);
  }

  public interface TestHistoryProvider {
    Promise<RecentTestHistory> fetch(String projectId, String filePath);
  }

  public record CoverageSnapshot(
      double lineCoverage,
      double branchCoverage,
      int missedLines,
      int missedBranches,
      List<String> uncoveredPaths) {

    public CoverageSnapshot {
      lineCoverage = Math.max(0.0, Math.min(1.0, lineCoverage));
      branchCoverage = Math.max(0.0, Math.min(1.0, branchCoverage));
      missedLines = Math.max(0, missedLines);
      missedBranches = Math.max(0, missedBranches);
      uncoveredPaths = uncoveredPaths == null ? List.of() : List.copyOf(uncoveredPaths);
    }

    public String summary() {
      return String.format(
          Locale.ROOT,
          "lineCoverage=%.0f%%, branchCoverage=%.0f%%, missedLines=%d, missedBranches=%d, uncoveredPaths=%s",
          lineCoverage * 100.0,
          branchCoverage * 100.0,
          missedLines,
          missedBranches,
          uncoveredPaths);
    }
  }

  public record RecentTestHistory(
      List<String> relatedTestFiles, int recentFailures, int flakyTests, int lastRunAgeDays) {

    public RecentTestHistory {
      relatedTestFiles = relatedTestFiles == null ? List.of() : List.copyOf(relatedTestFiles);
      recentFailures = Math.max(0, recentFailures);
      flakyTests = Math.max(0, flakyTests);
      lastRunAgeDays = Math.max(0, lastRunAgeDays);
    }

    public String summary() {
      return String.format(
          Locale.ROOT,
          "relatedTests=%s, recentFailures=%d, flakyTests=%d, lastRunAgeDays=%d",
          relatedTestFiles,
          recentFailures,
          flakyTests,
          lastRunAgeDays);
    }
  }
}
