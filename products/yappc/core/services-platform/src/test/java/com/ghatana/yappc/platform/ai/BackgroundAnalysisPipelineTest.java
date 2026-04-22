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

@DisplayName("BackgroundAnalysisPipeline Tests [GH-90000]")
class BackgroundAnalysisPipelineTest extends EventloopTestBase {

  @Test
  @DisplayName("onEvent schedules debounced analysis and publishes results [GH-90000]")
  void onEventSchedulesDebouncedAnalysisAndPublishesResults() { // GH-90000
    AtomicReference<Runnable> scheduledAction = new AtomicReference<>(); // GH-90000
    ChangeDebouncer debouncer =
        new ChangeDebouncer( // GH-90000
            (delay, action) -> { // GH-90000
              scheduledAction.set(action); // GH-90000
              return () -> {}; // GH-90000
            });
    AtomicReference<String> publishedTenant = new AtomicReference<>(); // GH-90000

    BackgroundAnalysisPipeline pipeline =
        new BackgroundAnalysisPipeline( // GH-90000
            debouncer,
            event -> Promise.of(List.of(insight(event.tenantId(), event.sourceRef()))), // GH-90000
            new InsightPublisher( // GH-90000
                insights -> Promise.of(insights), // GH-90000
                (tenantId, insights) -> { // GH-90000
                  publishedTenant.set(tenantId); // GH-90000
                  return Promise.complete(); // GH-90000
                },
                count -> {}),
            (event, error) -> { // GH-90000
              throw new AssertionError(error); // GH-90000
            });

    pipeline.onEvent(new CodeChangedEvent("tenant-a", "project-a", "src/App.ts", "+const x = 1;")); // GH-90000
    scheduledAction.get().run(); // GH-90000

    assertThat(publishedTenant.get()).isEqualTo("tenant-a [GH-90000]");
  }

  @Test
  @DisplayName("analyzeNow reports failures without swallowing them [GH-90000]")
  void analyzeNowReportsFailuresWithoutSwallowingThem() { // GH-90000
    AtomicReference<Throwable> failure = new AtomicReference<>(); // GH-90000
    AtomicReference<Runnable> scheduledAction = new AtomicReference<>(); // GH-90000

    BackgroundAnalysisPipeline pipeline =
        new BackgroundAnalysisPipeline( // GH-90000
            new ChangeDebouncer( // GH-90000
                (delay, action) -> { // GH-90000
                  scheduledAction.set(action); // GH-90000
                  return () -> {}; // GH-90000
                }),
            event -> Promise.ofException(new IllegalStateException("boom [GH-90000]")),
            new InsightPublisher(insights -> Promise.of(insights), (tenantId, insights) -> Promise.complete(), count -> {}), // GH-90000
            (event, error) -> failure.set(error)); // GH-90000

    pipeline.onEvent(new CodeChangedEvent("tenant-b", "project-b", "src/Svc.ts", "+return 1;")); // GH-90000
    scheduledAction.get().run(); // GH-90000

    assertThat(failure.get()).isInstanceOf(IllegalStateException.class).hasMessage("boom [GH-90000]");
  }

  @Test
  @DisplayName("analyzeNow publishes empty list when dispatcher returns null [GH-90000]")
  void analyzeNowPublishesEmptyListWhenDispatcherReturnsNull() { // GH-90000
    AtomicReference<Integer> publishedCount = new AtomicReference<>(); // GH-90000

    BackgroundAnalysisPipeline pipeline =
        new BackgroundAnalysisPipeline( // GH-90000
            new ChangeDebouncer((delay, action) -> () -> {}), // GH-90000
            event -> Promise.of(null), // GH-90000
            new InsightPublisher( // GH-90000
                insights -> Promise.of(insights), // GH-90000
                (tenantId, insights) -> Promise.complete(), // GH-90000
                publishedCount::set),
            (event, error) -> { // GH-90000
              throw new AssertionError(error); // GH-90000
            });

    runPromise( // GH-90000
        () -> pipeline.analyzeNow(new CodeChangedEvent("tenant-c", "project-c", "src/Null.ts", "+x"))); // GH-90000

    assertThat(publishedCount.get()).isZero(); // GH-90000
  }

  private AIInsight insight(String tenantId, String sourceRef) { // GH-90000
    return new AIInsight( // GH-90000
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
        List.of("tag [GH-90000]"),
        Instant.now(), // GH-90000
        false);
  }
}
