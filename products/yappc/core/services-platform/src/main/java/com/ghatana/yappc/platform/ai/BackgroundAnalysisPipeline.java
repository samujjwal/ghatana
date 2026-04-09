package com.ghatana.yappc.platform.ai;

import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent;
import io.activej.promise.Promise;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Coordinates debounced background analysis runs and publishes tenant-scoped AI insights.
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public final class BackgroundAnalysisPipeline {

  private static final Duration DEFAULT_DEBOUNCE_DELAY = Duration.ofSeconds(2);

  private final ChangeDebouncer debouncer;
  private final AnalysisDispatcher dispatcher;
  private final InsightPublisher publisher;
  private final FailureReporter failureReporter;

  public BackgroundAnalysisPipeline(
      ChangeDebouncer debouncer,
      AnalysisDispatcher dispatcher,
      InsightPublisher publisher,
      FailureReporter failureReporter) {
    this.debouncer = Objects.requireNonNull(debouncer, "debouncer");
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter");
  }

  public void onEvent(AnalysisEvent event) {
    Objects.requireNonNull(event, "event");
    debouncer.debounce(
        event.correlationKey(),
        DEFAULT_DEBOUNCE_DELAY,
        () -> analyzeNow(event).whenException(error -> failureReporter.report(event, error)));
  }

  public Promise<Void> analyzeNow(AnalysisEvent event) {
    Objects.requireNonNull(event, "event");
    return dispatcher
        .dispatch(event)
        .then(
            insights -> {
              List<AIInsight> publishable = insights == null ? List.of() : insights;
              return publisher.publish(publishable, event.tenantId());
            });
  }

  public interface AnalysisDispatcher {
    Promise<List<AIInsight>> dispatch(AnalysisEvent event);
  }

  public interface FailureReporter {
    void report(AnalysisEvent event, Throwable error);
  }
}
