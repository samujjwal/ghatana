package com.ghatana.yappc.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BackgroundAnalysisPipeline Tests")
class BackgroundAnalysisPipelineTest extends EventloopTestBase {

  @Test
  @DisplayName("onEvent schedules debounced analysis and publishes results")
  void onEventSchedulesDebouncedAnalysisAndPublishesResults() {
    AtomicReference<Runnable> scheduledAction = new AtomicReference<>();
    ChangeDebouncer debouncer =
        new ChangeDebouncer(
            (delay, action) -> {
              scheduledAction.set(action);
              return () -> {};
            });
    AtomicReference<String> publishedTenant = new AtomicReference<>();

    BackgroundAnalysisPipeline pipeline =
        new BackgroundAnalysisPipeline(
            debouncer,
            event -> Promise.of(List.of(insight(event.tenantId(), event.sourceRef()))),
            new InsightPublisher(
                insights -> Promise.of(insights),
                (tenantId, insights) -> {
                  publishedTenant.set(tenantId);
                  return Promise.complete();
                },
                count -> {}),
            (event, error) -> {
              throw new AssertionError(error);
            });

    pipeline.onEvent(new CodeChangedEvent("tenant-a", "project-a", "src/App.ts", "+const x = 1;"));
    scheduledAction.get().run();

    assertThat(publishedTenant.get()).isEqualTo("tenant-a");
  }

  @Test
  @DisplayName("analyzeNow reports failures without swallowing them")
  void analyzeNowReportsFailuresWithoutSwallowingThem() {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicReference<Runnable> scheduledAction = new AtomicReference<>();

    BackgroundAnalysisPipeline pipeline =
        new BackgroundAnalysisPipeline(
            new ChangeDebouncer(
                (delay, action) -> {
                  scheduledAction.set(action);
                  return () -> {};
                }),
            event -> Promise.ofException(new IllegalStateException("boom")),
            new InsightPublisher(insights -> Promise.of(insights), (tenantId, insights) -> Promise.complete(), count -> {}),
            (event, error) -> failure.set(error));

    pipeline.onEvent(new CodeChangedEvent("tenant-b", "project-b", "src/Svc.ts", "+return 1;"));
    scheduledAction.get().run();

    assertThat(failure.get()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
  }

  @Test
  @DisplayName("analyzeNow publishes empty list when dispatcher returns null")
  void analyzeNowPublishesEmptyListWhenDispatcherReturnsNull() {
    AtomicReference<Integer> publishedCount = new AtomicReference<>();

    BackgroundAnalysisPipeline pipeline =
        new BackgroundAnalysisPipeline(
            new ChangeDebouncer((delay, action) -> () -> {}),
            event -> Promise.of(null),
            new InsightPublisher(
                insights -> Promise.of(insights),
                (tenantId, insights) -> Promise.complete(),
                publishedCount::set),
            (event, error) -> {
              throw new AssertionError(error);
            });

    runPromise(
        () -> pipeline.analyzeNow(new CodeChangedEvent("tenant-c", "project-c", "src/Null.ts", "+x")));

    assertThat(publishedCount.get()).isZero();
  }

  private AIInsight insight(String tenantId, String sourceRef) {
    return new AIInsight(
        "insight-1",
        tenantId,
        "project-a",
        AIInsight.InsightType.CODE_QUALITY,
        AIInsight.InsightSeverity.INFO,
        "Title",
        "Description",
        "Suggestion",
        0.8,
        sourceRef,
        1,
        List.of("tag"),
        Instant.now(),
        false);
  }
}
