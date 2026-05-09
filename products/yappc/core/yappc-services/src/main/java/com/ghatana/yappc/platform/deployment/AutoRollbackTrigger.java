package com.ghatana.yappc.platform.deployment;

import io.activej.promise.Promise;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Schedules post-deployment health checks and triggers automatic rollback when key metrics regress.
 * @doc.layer product
 * @doc.pattern Trigger
 */
public final class AutoRollbackTrigger {

  private static final List<Duration> DEFAULT_CHECKPOINTS =
      List.of(
          Duration.ofMinutes(5),
          Duration.ofMinutes(10),
          Duration.ofMinutes(20),
          Duration.ofMinutes(30),
          Duration.ofMinutes(60));

  private final MetricsProvider metricsProvider;
  private final RollbackExecutor rollbackExecutor;
  private final Scheduler scheduler;
  private final DecisionPublisher decisionPublisher;
  private final FailureReporter failureReporter;
  private final List<Duration> checkpoints;

  public AutoRollbackTrigger(
      MetricsProvider metricsProvider,
      RollbackExecutor rollbackExecutor,
      Scheduler scheduler,
      DecisionPublisher decisionPublisher,
      FailureReporter failureReporter) {
    this(
        metricsProvider,
        rollbackExecutor,
        scheduler,
        decisionPublisher,
        failureReporter,
        DEFAULT_CHECKPOINTS);
  }

  AutoRollbackTrigger(
      MetricsProvider metricsProvider,
      RollbackExecutor rollbackExecutor,
      Scheduler scheduler,
      DecisionPublisher decisionPublisher,
      FailureReporter failureReporter,
      List<Duration> checkpoints) {
    this.metricsProvider = Objects.requireNonNull(metricsProvider, "metricsProvider");
    this.rollbackExecutor = Objects.requireNonNull(rollbackExecutor, "rollbackExecutor");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.decisionPublisher = Objects.requireNonNull(decisionPublisher, "decisionPublisher");
    this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter");
    this.checkpoints = List.copyOf(Objects.requireNonNull(checkpoints, "checkpoints"));
  }

  public MonitoringPlan startMonitoring(String deploymentId, DeploymentBaseline baseline) {
    Objects.requireNonNull(deploymentId, "deploymentId");
    Objects.requireNonNull(baseline, "baseline");

    List<ScheduledCheck> scheduledChecks = new ArrayList<>();
    for (Duration checkpoint : checkpoints) {
      Cancellation cancellation =
          scheduler.schedule(
              checkpoint,
              () ->
                  evaluateCheckpoint(deploymentId, baseline, checkpoint)
                      .whenException(error -> failureReporter.report(deploymentId, error)));
      scheduledChecks.add(new ScheduledCheck(checkpoint, cancellation));
    }
    return new MonitoringPlan(deploymentId, scheduledChecks);
  }

  public Promise<RollbackDecision> evaluateCheckpoint(
      String deploymentId, DeploymentBaseline baseline, Duration elapsed) {
    Objects.requireNonNull(deploymentId, "deploymentId");
    Objects.requireNonNull(baseline, "baseline");
    Objects.requireNonNull(elapsed, "elapsed");

    return metricsProvider
        .fetch(deploymentId)
        .then(
            currentMetrics -> {
              RollbackDecision decision = decide(baseline, currentMetrics, elapsed);
              Promise<Void> actionPromise =
                  decision.rollbackTriggered()
                      ? rollbackExecutor.rollback(
                          deploymentId, baseline.rollbackVersion(), decision.reason())
                      : Promise.complete();
              return actionPromise
                  .then(() -> decisionPublisher.publish(deploymentId, decision, currentMetrics))
                  .map(ignored -> decision);
            });
  }

  private RollbackDecision decide(
      DeploymentBaseline baseline, DeploymentMetrics currentMetrics, Duration elapsed) {
    double errorRateThreshold = Math.max(baseline.errorRate() * 1.5, baseline.errorRate() + 0.02);
    if (currentMetrics.errorRate() > errorRateThreshold) {
      return new RollbackDecision(
          true,
          String.format(
              Locale.ROOT,
              "Error rate increased from %.2f%% to %.2f%%.",
              baseline.errorRate() * 100.0,
              currentMetrics.errorRate() * 100.0),
          elapsed);
    }

    long latencyThreshold = Math.max(baseline.latencyP99Millis() * 2, baseline.latencyP99Millis() + 250);
    if (currentMetrics.latencyP99Millis() > latencyThreshold) {
      return new RollbackDecision(
          true,
          String.format(
              Locale.ROOT,
              "P99 latency increased from %dms to %dms.",
              baseline.latencyP99Millis(),
              currentMetrics.latencyP99Millis()),
          elapsed);
    }

    if (currentMetrics.availability() < baseline.availability() - 0.03) {
      return new RollbackDecision(
          true,
          String.format(
              Locale.ROOT,
              "Availability dropped from %.2f%% to %.2f%%.",
              baseline.availability() * 100.0,
              currentMetrics.availability() * 100.0),
          elapsed);
    }

    return new RollbackDecision(false, "Deployment remains within rollback thresholds.", elapsed);
  }

  public interface MetricsProvider {
    Promise<DeploymentMetrics> fetch(String deploymentId);
  }

  public interface RollbackExecutor {
    Promise<Void> rollback(String deploymentId, String rollbackVersion, String reason);
  }

  public interface Scheduler {
    Cancellation schedule(Duration delay, Runnable task);
  }

  public interface Cancellation {
    void cancel();
  }

  public interface DecisionPublisher {
    Promise<Void> publish(
        String deploymentId, RollbackDecision decision, DeploymentMetrics currentMetrics);
  }

  public interface FailureReporter {
    void report(String deploymentId, Throwable error);
  }

  public record DeploymentBaseline(
      double errorRate, long latencyP99Millis, double availability, String rollbackVersion) {

    public DeploymentBaseline {
      errorRate = Math.max(0.0, Math.min(1.0, errorRate));
      latencyP99Millis = Math.max(0L, latencyP99Millis);
      availability = Math.max(0.0, Math.min(1.0, availability));
      String defaultVersion = System.getenv("YAPPC_DEFAULT_ROLLBACK_VERSION");
      rollbackVersion = Objects.requireNonNullElse(rollbackVersion,
          (defaultVersion != null && !defaultVersion.isBlank()) ? defaultVersion : "previous-stable");
    }
  }

  public record DeploymentMetrics(double errorRate, long latencyP99Millis, double availability) {

    public DeploymentMetrics {
      errorRate = Math.max(0.0, Math.min(1.0, errorRate));
      latencyP99Millis = Math.max(0L, latencyP99Millis);
      availability = Math.max(0.0, Math.min(1.0, availability));
    }
  }

  public record RollbackDecision(boolean rollbackTriggered, String reason, Duration elapsed) {

    public RollbackDecision {
      reason = Objects.requireNonNullElse(reason, "");
      elapsed = elapsed == null ? Duration.ZERO : elapsed;
    }
  }

  public record ScheduledCheck(Duration delay, Cancellation cancellation) {

    public ScheduledCheck {
      delay = delay == null ? Duration.ZERO : delay;
      cancellation = cancellation == null ? () -> {} : cancellation;
    }
  }

  public record MonitoringPlan(String deploymentId, List<ScheduledCheck> scheduledChecks) {

    public MonitoringPlan {
      deploymentId = Objects.requireNonNullElse(deploymentId, "unknown-deployment");
      scheduledChecks = scheduledChecks == null ? List.of() : List.copyOf(scheduledChecks);
    }

    public void cancelAll() {
      scheduledChecks.forEach(check -> check.cancellation().cancel());
    }
  }
}
