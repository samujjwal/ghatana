package com.ghatana.yappc.platform.ai;

import com.ghatana.yappc.platform.ai.analyzers.ArchitectureAdvisor;
import com.ghatana.yappc.platform.ai.analyzers.CodeQualityAnalyzer;
import com.ghatana.yappc.platform.ai.analyzers.PerformanceAdvisor;
import com.ghatana.yappc.platform.ai.analyzers.RequirementsConsistencyChecker;
import com.ghatana.yappc.platform.ai.analyzers.SecurityPatternDetector;
import com.ghatana.yappc.platform.ai.analyzers.TestGapAnalyzer;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.ArchitectureChangedEvent;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.RequirementChangedEvent;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Dispatches background analysis events to the relevant analyzer set and flattens results for publishing.
 * @doc.layer product
 * @doc.pattern Dispatcher
 */
public final class AnalysisDispatcher implements BackgroundAnalysisPipeline.AnalysisDispatcher {

  private final CodeQualityAnalyzer codeQualityAnalyzer;
  private final SecurityPatternDetector securityPatternDetector;
  private final TestGapAnalyzer testGapAnalyzer;
  private final PerformanceAdvisor performanceAdvisor;
  private final RequirementsConsistencyChecker requirementsConsistencyChecker;
  private final ArchitectureAdvisor architectureAdvisor;

  public AnalysisDispatcher(
      CodeQualityAnalyzer codeQualityAnalyzer,
      SecurityPatternDetector securityPatternDetector,
      TestGapAnalyzer testGapAnalyzer,
      PerformanceAdvisor performanceAdvisor,
      RequirementsConsistencyChecker requirementsConsistencyChecker,
      ArchitectureAdvisor architectureAdvisor) {
    this.codeQualityAnalyzer = Objects.requireNonNull(codeQualityAnalyzer, "codeQualityAnalyzer");
    this.securityPatternDetector =
        Objects.requireNonNull(securityPatternDetector, "securityPatternDetector");
    this.testGapAnalyzer = Objects.requireNonNull(testGapAnalyzer, "testGapAnalyzer");
    this.performanceAdvisor = Objects.requireNonNull(performanceAdvisor, "performanceAdvisor");
    this.requirementsConsistencyChecker =
        Objects.requireNonNull(requirementsConsistencyChecker, "requirementsConsistencyChecker");
    this.architectureAdvisor = Objects.requireNonNull(architectureAdvisor, "architectureAdvisor");
  }

  @Override
  public Promise<List<AIInsight>> dispatch(AnalysisEvent event) {
    Objects.requireNonNull(event, "event");

    if (event instanceof CodeChangedEvent codeChangedEvent) {
      return Promises.toList(
              List.of(
                  codeQualityAnalyzer.analyze(codeChangedEvent),
                  securityPatternDetector.analyze(codeChangedEvent),
            testGapAnalyzer.analyze(codeChangedEvent),
            performanceAdvisor.analyze(codeChangedEvent)))
          .map(this::flatten);
    }

    if (event instanceof RequirementChangedEvent requirementChangedEvent) {
      return requirementsConsistencyChecker.analyze(requirementChangedEvent);
    }

    if (event instanceof ArchitectureChangedEvent architectureChangedEvent) {
      return architectureAdvisor.analyze(architectureChangedEvent);
    }

    return Promise.of(List.of());
  }

  private List<AIInsight> flatten(List<List<AIInsight>> batches) {
    if (batches == null || batches.isEmpty()) {
      return List.of();
    }
    return batches.stream()
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .filter(Objects::nonNull)
        .toList();
  }
}
