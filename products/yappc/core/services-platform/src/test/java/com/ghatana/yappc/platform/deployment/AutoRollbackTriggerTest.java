package com.ghatana.yappc.platform.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AutoRollbackTrigger Tests")
class AutoRollbackTriggerTest extends EventloopTestBase {

  @Test
  @DisplayName("startMonitoring schedules checkpoints and cancelAll cancels them")
  void startMonitoringSchedulesCheckpointsAndCancelAllCancelsThem() { // GH-90000
    RecordingScheduler scheduler = new RecordingScheduler(); // GH-90000
    AutoRollbackTrigger trigger =
        trigger(metrics(0.01, 120, 0.99), new RecordingRollbackExecutor(), scheduler, new RecordingDecisionPublisher(), new RecordingFailureReporter()); // GH-90000

    AutoRollbackTrigger.MonitoringPlan plan =
        trigger.startMonitoring("deploy-1", new AutoRollbackTrigger.DeploymentBaseline(0.01, 120, 0.99, "v1.0.0")); // GH-90000

    assertThat(plan.deploymentId()).isEqualTo("deploy-1");
    assertThat(plan.scheduledChecks()).hasSize(5); // GH-90000
    assertThat(scheduler.delays) // GH-90000
        .containsExactly( // GH-90000
            Duration.ofMinutes(5), // GH-90000
            Duration.ofMinutes(10), // GH-90000
            Duration.ofMinutes(20), // GH-90000
            Duration.ofMinutes(30), // GH-90000
            Duration.ofMinutes(60)); // GH-90000

    plan.cancelAll(); // GH-90000

    assertThat(scheduler.cancelledCount).isEqualTo(5); // GH-90000
  }

  @Test
  @DisplayName("evaluateCheckpoint rolls back on error rate increase")
  void evaluateCheckpointRollsBackOnErrorRateIncrease() { // GH-90000
    RecordingRollbackExecutor rollbackExecutor = new RecordingRollbackExecutor(); // GH-90000
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher(); // GH-90000
    AutoRollbackTrigger trigger =
        trigger(metrics(0.08, 120, 0.99), rollbackExecutor, new RecordingScheduler(), publisher, new RecordingFailureReporter()); // GH-90000

    AutoRollbackTrigger.RollbackDecision decision =
        runPromise( // GH-90000
            () -> // GH-90000
                trigger.evaluateCheckpoint( // GH-90000
                    "deploy-2",
                    new AutoRollbackTrigger.DeploymentBaseline(0.02, 120, 0.99, "v1.0.0"), // GH-90000
                    Duration.ofMinutes(5))); // GH-90000

    assertThat(decision.rollbackTriggered()).isTrue(); // GH-90000
    assertThat(rollbackExecutor.actions).containsExactly("deploy-2:v1.0.0");
    assertThat(publisher.decisions).containsExactly(decision); // GH-90000
  }

  @Test
  @DisplayName("evaluateCheckpoint rolls back on latency increase")
  void evaluateCheckpointRollsBackOnLatencyIncrease() { // GH-90000
    RecordingRollbackExecutor rollbackExecutor = new RecordingRollbackExecutor(); // GH-90000
    AutoRollbackTrigger trigger =
        trigger(metrics(0.01, 900, 0.99), rollbackExecutor, new RecordingScheduler(), new RecordingDecisionPublisher(), new RecordingFailureReporter()); // GH-90000

    AutoRollbackTrigger.RollbackDecision decision =
        runPromise( // GH-90000
            () -> // GH-90000
                trigger.evaluateCheckpoint( // GH-90000
                    "deploy-3",
                    new AutoRollbackTrigger.DeploymentBaseline(0.01, 200, 0.99, "v1.0.0"), // GH-90000
                    Duration.ofMinutes(10))); // GH-90000

    assertThat(decision.rollbackTriggered()).isTrue(); // GH-90000
    assertThat(decision.reason()).contains("P99 latency increased");
    assertThat(rollbackExecutor.actions).containsExactly("deploy-3:v1.0.0");
  }

  @Test
  @DisplayName("evaluateCheckpoint rolls back on availability drop")
  void evaluateCheckpointRollsBackOnAvailabilityDrop() { // GH-90000
    RecordingRollbackExecutor rollbackExecutor = new RecordingRollbackExecutor(); // GH-90000
    AutoRollbackTrigger trigger =
        trigger(metrics(0.01, 120, 0.9), rollbackExecutor, new RecordingScheduler(), new RecordingDecisionPublisher(), new RecordingFailureReporter()); // GH-90000

    AutoRollbackTrigger.RollbackDecision decision =
        runPromise( // GH-90000
            () -> // GH-90000
                trigger.evaluateCheckpoint( // GH-90000
                    "deploy-4",
                    new AutoRollbackTrigger.DeploymentBaseline(0.01, 120, 0.99, "v1.0.0"), // GH-90000
                    Duration.ofMinutes(20))); // GH-90000

    assertThat(decision.rollbackTriggered()).isTrue(); // GH-90000
    assertThat(decision.reason()).contains("Availability dropped");
    assertThat(rollbackExecutor.actions).containsExactly("deploy-4:v1.0.0");
  }

  @Test
  @DisplayName("evaluateCheckpoint publishes stable decision when thresholds are not breached")
  void evaluateCheckpointPublishesStableDecisionWhenThresholdsAreNotBreached() { // GH-90000
    RecordingRollbackExecutor rollbackExecutor = new RecordingRollbackExecutor(); // GH-90000
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher(); // GH-90000
    AutoRollbackTrigger trigger =
        trigger(metrics(0.015, 180, 0.98), rollbackExecutor, new RecordingScheduler(), publisher, new RecordingFailureReporter()); // GH-90000

    AutoRollbackTrigger.RollbackDecision decision =
        runPromise( // GH-90000
            () -> // GH-90000
                trigger.evaluateCheckpoint( // GH-90000
                    "deploy-5",
                    new AutoRollbackTrigger.DeploymentBaseline(0.01, 120, 0.99, "v1.0.0"), // GH-90000
                    Duration.ofMinutes(30))); // GH-90000

    assertThat(decision.rollbackTriggered()).isFalse(); // GH-90000
    assertThat(rollbackExecutor.actions).isEmpty(); // GH-90000
    assertThat(publisher.decisions).containsExactly(decision); // GH-90000
  }

  @Test
  @DisplayName("startMonitoring reports async checkpoint failures")
  void startMonitoringReportsAsyncCheckpointFailures() { // GH-90000
    RecordingScheduler scheduler = new RecordingScheduler(); // GH-90000
    RecordingFailureReporter failureReporter = new RecordingFailureReporter(); // GH-90000
    AutoRollbackTrigger trigger =
        new AutoRollbackTrigger( // GH-90000
            deploymentId -> Promise.ofException(new IllegalStateException("metrics unavailable")),
            new RecordingRollbackExecutor(), // GH-90000
            scheduler,
            new RecordingDecisionPublisher(), // GH-90000
            failureReporter,
            List.of(Duration.ofMinutes(5))); // GH-90000

    trigger.startMonitoring("deploy-6", new AutoRollbackTrigger.DeploymentBaseline(0.01, 120, 0.99, "v1.0.0")); // GH-90000
    scheduler.tasks.getFirst().run(); // GH-90000

    assertThat(failureReporter.failures).hasSize(1); // GH-90000
    assertThat(failureReporter.failures.getFirst()).contains("deploy-6:metrics unavailable");
  }

  @Test
  @DisplayName("auto rollback records normalize invalid values")
  void recordsNormalizeInvalidValues() { // GH-90000
    AutoRollbackTrigger.DeploymentBaseline baseline =
        new AutoRollbackTrigger.DeploymentBaseline(-1.0, -5, 2.0, null); // GH-90000
    AutoRollbackTrigger.DeploymentMetrics metrics =
        new AutoRollbackTrigger.DeploymentMetrics(-1.0, -8, 2.0); // GH-90000
    AutoRollbackTrigger.RollbackDecision decision =
        new AutoRollbackTrigger.RollbackDecision(true, null, null); // GH-90000
    AutoRollbackTrigger.ScheduledCheck scheduledCheck =
        new AutoRollbackTrigger.ScheduledCheck(null, null); // GH-90000
    AutoRollbackTrigger.MonitoringPlan plan = new AutoRollbackTrigger.MonitoringPlan(null, null); // GH-90000

    assertThat(baseline.errorRate()).isZero(); // GH-90000
    assertThat(baseline.latencyP99Millis()).isZero(); // GH-90000
    assertThat(baseline.availability()).isEqualTo(1.0); // GH-90000
    assertThat(baseline.rollbackVersion()).isEqualTo("previous-stable");

    assertThat(metrics.errorRate()).isZero(); // GH-90000
    assertThat(metrics.latencyP99Millis()).isZero(); // GH-90000
    assertThat(metrics.availability()).isEqualTo(1.0); // GH-90000

    assertThat(decision.reason()).isEmpty(); // GH-90000
    assertThat(decision.elapsed()).isEqualTo(Duration.ZERO); // GH-90000
    assertThat(scheduledCheck.delay()).isEqualTo(Duration.ZERO); // GH-90000
    scheduledCheck.cancellation().cancel(); // GH-90000
    assertThat(plan.deploymentId()).isEqualTo("unknown-deployment");
    assertThat(plan.scheduledChecks()).isEmpty(); // GH-90000
  }

  private AutoRollbackTrigger trigger( // GH-90000
      AutoRollbackTrigger.DeploymentMetrics metrics,
      RecordingRollbackExecutor rollbackExecutor,
      RecordingScheduler scheduler,
      RecordingDecisionPublisher decisionPublisher,
      RecordingFailureReporter failureReporter) {
    return new AutoRollbackTrigger( // GH-90000
        deploymentId -> Promise.of(metrics), // GH-90000
        rollbackExecutor,
        scheduler,
        decisionPublisher,
        failureReporter);
  }

  private AutoRollbackTrigger.DeploymentMetrics metrics( // GH-90000
      double errorRate, long latencyP99Millis, double availability) {
    return new AutoRollbackTrigger.DeploymentMetrics(errorRate, latencyP99Millis, availability); // GH-90000
  }

  private static final class RecordingRollbackExecutor
      implements AutoRollbackTrigger.RollbackExecutor {
    private final List<String> actions = new ArrayList<>(); // GH-90000

    @Override
    public Promise<Void> rollback(String deploymentId, String rollbackVersion, String reason) { // GH-90000
      actions.add(deploymentId + ":" + rollbackVersion); // GH-90000
      return Promise.complete(); // GH-90000
    }
  }

  private static final class RecordingScheduler implements AutoRollbackTrigger.Scheduler {
    private final List<Duration> delays = new ArrayList<>(); // GH-90000
    private final List<Runnable> tasks = new ArrayList<>(); // GH-90000
    private int cancelledCount;

    @Override
    public AutoRollbackTrigger.Cancellation schedule(Duration delay, Runnable task) { // GH-90000
      delays.add(delay); // GH-90000
      tasks.add(task); // GH-90000
      return () -> cancelledCount++; // GH-90000
    }
  }

  private static final class RecordingDecisionPublisher
      implements AutoRollbackTrigger.DecisionPublisher {
    private final List<AutoRollbackTrigger.RollbackDecision> decisions = new ArrayList<>(); // GH-90000

    @Override
    public Promise<Void> publish( // GH-90000
        String deploymentId,
        AutoRollbackTrigger.RollbackDecision decision,
        AutoRollbackTrigger.DeploymentMetrics currentMetrics) {
      decisions.add(decision); // GH-90000
      return Promise.complete(); // GH-90000
    }
  }

  private static final class RecordingFailureReporter
      implements AutoRollbackTrigger.FailureReporter {
    private final List<String> failures = new ArrayList<>(); // GH-90000

    @Override
    public void report(String deploymentId, Throwable error) { // GH-90000
      failures.add(deploymentId + ":" + error.getMessage()); // GH-90000
    }
  }
}
