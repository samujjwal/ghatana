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
  void startMonitoringSchedulesCheckpointsAndCancelAllCancelsThem() { 
    RecordingScheduler scheduler = new RecordingScheduler(); 
    AutoRollbackTrigger trigger =
        trigger(metrics(0.01, 120, 0.99), new RecordingRollbackExecutor(), scheduler, new RecordingDecisionPublisher(), new RecordingFailureReporter()); 

    AutoRollbackTrigger.MonitoringPlan plan =
        trigger.startMonitoring("deploy-1", new AutoRollbackTrigger.DeploymentBaseline(0.01, 120, 0.99, "v1.0.0")); 

    assertThat(plan.deploymentId()).isEqualTo("deploy-1");
    assertThat(plan.scheduledChecks()).hasSize(5); 
    assertThat(scheduler.delays) 
        .containsExactly( 
            Duration.ofMinutes(5), 
            Duration.ofMinutes(10), 
            Duration.ofMinutes(20), 
            Duration.ofMinutes(30), 
            Duration.ofMinutes(60)); 

    plan.cancelAll(); 

    assertThat(scheduler.cancelledCount).isEqualTo(5); 
  }

  @Test
  @DisplayName("evaluateCheckpoint rolls back on error rate increase")
  void evaluateCheckpointRollsBackOnErrorRateIncrease() { 
    RecordingRollbackExecutor rollbackExecutor = new RecordingRollbackExecutor(); 
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher(); 
    AutoRollbackTrigger trigger =
        trigger(metrics(0.08, 120, 0.99), rollbackExecutor, new RecordingScheduler(), publisher, new RecordingFailureReporter()); 

    AutoRollbackTrigger.RollbackDecision decision =
        runPromise( 
            () -> 
                trigger.evaluateCheckpoint( 
                    "deploy-2",
                    new AutoRollbackTrigger.DeploymentBaseline(0.02, 120, 0.99, "v1.0.0"), 
                    Duration.ofMinutes(5))); 

    assertThat(decision.rollbackTriggered()).isTrue(); 
    assertThat(rollbackExecutor.actions).containsExactly("deploy-2:v1.0.0");
    assertThat(publisher.decisions).containsExactly(decision); 
  }

  @Test
  @DisplayName("evaluateCheckpoint rolls back on latency increase")
  void evaluateCheckpointRollsBackOnLatencyIncrease() { 
    RecordingRollbackExecutor rollbackExecutor = new RecordingRollbackExecutor(); 
    AutoRollbackTrigger trigger =
        trigger(metrics(0.01, 900, 0.99), rollbackExecutor, new RecordingScheduler(), new RecordingDecisionPublisher(), new RecordingFailureReporter()); 

    AutoRollbackTrigger.RollbackDecision decision =
        runPromise( 
            () -> 
                trigger.evaluateCheckpoint( 
                    "deploy-3",
                    new AutoRollbackTrigger.DeploymentBaseline(0.01, 200, 0.99, "v1.0.0"), 
                    Duration.ofMinutes(10))); 

    assertThat(decision.rollbackTriggered()).isTrue(); 
    assertThat(decision.reason()).contains("P99 latency increased");
    assertThat(rollbackExecutor.actions).containsExactly("deploy-3:v1.0.0");
  }

  @Test
  @DisplayName("evaluateCheckpoint rolls back on availability drop")
  void evaluateCheckpointRollsBackOnAvailabilityDrop() { 
    RecordingRollbackExecutor rollbackExecutor = new RecordingRollbackExecutor(); 
    AutoRollbackTrigger trigger =
        trigger(metrics(0.01, 120, 0.9), rollbackExecutor, new RecordingScheduler(), new RecordingDecisionPublisher(), new RecordingFailureReporter()); 

    AutoRollbackTrigger.RollbackDecision decision =
        runPromise( 
            () -> 
                trigger.evaluateCheckpoint( 
                    "deploy-4",
                    new AutoRollbackTrigger.DeploymentBaseline(0.01, 120, 0.99, "v1.0.0"), 
                    Duration.ofMinutes(20))); 

    assertThat(decision.rollbackTriggered()).isTrue(); 
    assertThat(decision.reason()).contains("Availability dropped");
    assertThat(rollbackExecutor.actions).containsExactly("deploy-4:v1.0.0");
  }

  @Test
  @DisplayName("evaluateCheckpoint publishes stable decision when thresholds are not breached")
  void evaluateCheckpointPublishesStableDecisionWhenThresholdsAreNotBreached() { 
    RecordingRollbackExecutor rollbackExecutor = new RecordingRollbackExecutor(); 
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher(); 
    AutoRollbackTrigger trigger =
        trigger(metrics(0.015, 180, 0.98), rollbackExecutor, new RecordingScheduler(), publisher, new RecordingFailureReporter()); 

    AutoRollbackTrigger.RollbackDecision decision =
        runPromise( 
            () -> 
                trigger.evaluateCheckpoint( 
                    "deploy-5",
                    new AutoRollbackTrigger.DeploymentBaseline(0.01, 120, 0.99, "v1.0.0"), 
                    Duration.ofMinutes(30))); 

    assertThat(decision.rollbackTriggered()).isFalse(); 
    assertThat(rollbackExecutor.actions).isEmpty(); 
    assertThat(publisher.decisions).containsExactly(decision); 
  }

  @Test
  @DisplayName("startMonitoring reports async checkpoint failures")
  void startMonitoringReportsAsyncCheckpointFailures() { 
    RecordingScheduler scheduler = new RecordingScheduler(); 
    RecordingFailureReporter failureReporter = new RecordingFailureReporter(); 
    AutoRollbackTrigger trigger =
        new AutoRollbackTrigger( 
            deploymentId -> Promise.ofException(new IllegalStateException("metrics unavailable")),
            new RecordingRollbackExecutor(), 
            scheduler,
            new RecordingDecisionPublisher(), 
            failureReporter,
            List.of(Duration.ofMinutes(5))); 

    trigger.startMonitoring("deploy-6", new AutoRollbackTrigger.DeploymentBaseline(0.01, 120, 0.99, "v1.0.0")); 
    scheduler.tasks.getFirst().run(); 

    assertThat(failureReporter.failures).hasSize(1); 
    assertThat(failureReporter.failures.getFirst()).contains("deploy-6:metrics unavailable");
  }

  @Test
  @DisplayName("auto rollback records normalize invalid values")
  void recordsNormalizeInvalidValues() { 
    AutoRollbackTrigger.DeploymentBaseline baseline =
        new AutoRollbackTrigger.DeploymentBaseline(-1.0, -5, 2.0, null); 
    AutoRollbackTrigger.DeploymentMetrics metrics =
        new AutoRollbackTrigger.DeploymentMetrics(-1.0, -8, 2.0); 
    AutoRollbackTrigger.RollbackDecision decision =
        new AutoRollbackTrigger.RollbackDecision(true, null, null); 
    AutoRollbackTrigger.ScheduledCheck scheduledCheck =
        new AutoRollbackTrigger.ScheduledCheck(null, null); 
    AutoRollbackTrigger.MonitoringPlan plan = new AutoRollbackTrigger.MonitoringPlan(null, null); 

    assertThat(baseline.errorRate()).isZero(); 
    assertThat(baseline.latencyP99Millis()).isZero(); 
    assertThat(baseline.availability()).isEqualTo(1.0); 
    assertThat(baseline.rollbackVersion()).isEqualTo("previous-stable");

    assertThat(metrics.errorRate()).isZero(); 
    assertThat(metrics.latencyP99Millis()).isZero(); 
    assertThat(metrics.availability()).isEqualTo(1.0); 

    assertThat(decision.reason()).isEmpty(); 
    assertThat(decision.elapsed()).isEqualTo(Duration.ZERO); 
    assertThat(scheduledCheck.delay()).isEqualTo(Duration.ZERO); 
    scheduledCheck.cancellation().cancel(); 
    assertThat(plan.deploymentId()).isEqualTo("unknown-deployment");
    assertThat(plan.scheduledChecks()).isEmpty(); 
  }

  private AutoRollbackTrigger trigger( 
      AutoRollbackTrigger.DeploymentMetrics metrics,
      RecordingRollbackExecutor rollbackExecutor,
      RecordingScheduler scheduler,
      RecordingDecisionPublisher decisionPublisher,
      RecordingFailureReporter failureReporter) {
    return new AutoRollbackTrigger( 
        deploymentId -> Promise.of(metrics), 
        rollbackExecutor,
        scheduler,
        decisionPublisher,
        failureReporter);
  }

  private AutoRollbackTrigger.DeploymentMetrics metrics( 
      double errorRate, long latencyP99Millis, double availability) {
    return new AutoRollbackTrigger.DeploymentMetrics(errorRate, latencyP99Millis, availability); 
  }

  private static final class RecordingRollbackExecutor
      implements AutoRollbackTrigger.RollbackExecutor {
    private final List<String> actions = new ArrayList<>(); 

    @Override
    public Promise<Void> rollback(String deploymentId, String rollbackVersion, String reason) { 
      actions.add(deploymentId + ":" + rollbackVersion); 
      return Promise.complete(); 
    }
  }

  private static final class RecordingScheduler implements AutoRollbackTrigger.Scheduler {
    private final List<Duration> delays = new ArrayList<>(); 
    private final List<Runnable> tasks = new ArrayList<>(); 
    private int cancelledCount;

    @Override
    public AutoRollbackTrigger.Cancellation schedule(Duration delay, Runnable task) { 
      delays.add(delay); 
      tasks.add(task); 
      return () -> cancelledCount++; 
    }
  }

  private static final class RecordingDecisionPublisher
      implements AutoRollbackTrigger.DecisionPublisher {
    private final List<AutoRollbackTrigger.RollbackDecision> decisions = new ArrayList<>(); 

    @Override
    public Promise<Void> publish( 
        String deploymentId,
        AutoRollbackTrigger.RollbackDecision decision,
        AutoRollbackTrigger.DeploymentMetrics currentMetrics) {
      decisions.add(decision); 
      return Promise.complete(); 
    }
  }

  private static final class RecordingFailureReporter
      implements AutoRollbackTrigger.FailureReporter {
    private final List<String> failures = new ArrayList<>(); 

    @Override
    public void report(String deploymentId, Throwable error) { 
      failures.add(deploymentId + ":" + error.getMessage()); 
    }
  }
}
