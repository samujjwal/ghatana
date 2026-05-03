package com.ghatana.yappc.platform.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import io.activej.promise.Promise;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Evaluates live canary metrics and decides whether to hold, promote, or roll back a deployment.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class CanaryAnalyzer {

  private final YAPPCAIInterface aiService;
  private final MetricsProvider metricsProvider;
  private final DeploymentController deploymentController;
  private final DecisionPublisher decisionPublisher;
  private final ObjectMapper objectMapper;

  public CanaryAnalyzer(
      YAPPCAIInterface aiService,
      MetricsProvider metricsProvider,
      DeploymentController deploymentController,
      DecisionPublisher decisionPublisher) {
    this(aiService, metricsProvider, deploymentController, decisionPublisher, new ObjectMapper());
  }

  CanaryAnalyzer(
      YAPPCAIInterface aiService,
      MetricsProvider metricsProvider,
      DeploymentController deploymentController,
      DecisionPublisher decisionPublisher,
      ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.metricsProvider = Objects.requireNonNull(metricsProvider, "metricsProvider");
    this.deploymentController = Objects.requireNonNull(deploymentController, "deploymentController");
    this.decisionPublisher = Objects.requireNonNull(decisionPublisher, "decisionPublisher");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<CanaryDecision> evaluate(String deploymentId, CanaryConfig config) {
    Objects.requireNonNull(deploymentId, "deploymentId");
    Objects.requireNonNull(config, "config");

    return metricsProvider
        .fetch(deploymentId)
        .then(
            metrics -> {
              CanaryDecision fastDecision = fastDecision(metrics, config);
              Promise<CanaryDecision> decisionPromise =
                  fastDecision != null
                      ? Promise.of(fastDecision)
                      : aiService
                          .reason(buildPrompt(deploymentId, metrics, config), buildContext(deploymentId, metrics, config))
                          .map(response -> parseDecision(response));
              return decisionPromise.then(decision -> applyDecision(deploymentId, config, metrics, decision));
            });
  }

  private CanaryDecision fastDecision(CanaryMetrics metrics, CanaryConfig config) {
    if (metrics.sampleSize() < config.minimumSampleSize()) {
      return new CanaryDecision(
          CanaryDecisionType.HOLD,
          "Canary sample is still too small for a rollout decision.",
          1.0,
          false);
    }
    if (metrics.errorRate() > config.errorRateThreshold()) {
      return new CanaryDecision(
          CanaryDecisionType.ROLLBACK,
          String.format(
              Locale.ROOT,
              "Error rate %.2f%% exceeded threshold %.2f%%.",
              metrics.errorRate() * 100.0,
              config.errorRateThreshold() * 100.0),
          1.0,
          false);
    }
    if (metrics.latencyP99Millis() > config.latencyP99ThresholdMillis()) {
      return new CanaryDecision(
          CanaryDecisionType.ROLLBACK,
          String.format(
              Locale.ROOT,
              "P99 latency %dms exceeded threshold %dms.",
              metrics.latencyP99Millis(),
              config.latencyP99ThresholdMillis()),
          1.0,
          false);
    }
    if (metrics.successRate() < config.minimumSuccessRate()) {
      return new CanaryDecision(
          CanaryDecisionType.ROLLBACK,
          String.format(
              Locale.ROOT,
              "Success rate %.2f%% dropped below %.2f%%.",
              metrics.successRate() * 100.0,
              config.minimumSuccessRate() * 100.0),
          1.0,
          false);
    }
    return null;
  }

  private Map<String, Object> buildContext(
      String deploymentId, CanaryMetrics metrics, CanaryConfig config) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("deploymentId", deploymentId);
    context.put("metrics", metrics.summary());
    context.put("config", config.summary());
    return context;
  }

  private String buildPrompt(String deploymentId, CanaryMetrics metrics, CanaryConfig config) {
    return "Evaluate this canary deployment and return JSON with decision, rationale, confidence."
        + "\nDeployment: "
        + deploymentId
        + "\nMetrics: "
        + metrics.summary()
        + "\nConfig: "
        + config.summary();
  }

  private CanaryDecision parseDecision(String response) {
    if (response == null || response.isBlank()) {
      return new CanaryDecision(
          CanaryDecisionType.HOLD,
          "AI response was empty; keep observing the canary.",
          0.0,
          false);
    }

    try {
      JsonNode root = objectMapper.readTree(response);
      String decisionValue = root.path("decision").asText();
      CanaryDecisionType decisionType =
          decisionValue.isBlank()
              ? CanaryDecisionType.HOLD
              : CanaryDecisionType.valueOf(decisionValue.toUpperCase(Locale.ROOT));
      String rationale = root.path("rationale").asText();
      return new CanaryDecision(
          decisionType,
          rationale.isBlank() ? "AI generated canary recommendation." : rationale,
          root.path("confidence").asDouble(0.6),
          true);
    } catch (IOException | IllegalArgumentException exception) {
      return new CanaryDecision(
          CanaryDecisionType.HOLD,
          "Failed to parse AI canary decision; manual review required.",
          0.0,
          false);
    }
  }

  private Promise<CanaryDecision> applyDecision(
      String deploymentId,
      CanaryConfig config,
      CanaryMetrics metrics,
      CanaryDecision decision) {
    Promise<Void> actionPromise =
        switch (decision.type()) {
          case PROMOTE -> deploymentController.promote(deploymentId, config.targetEnvironment());
          case ROLLBACK ->
              deploymentController.rollback(
                  deploymentId, config.rollbackVersion(), decision.rationale());
          case HOLD -> Promise.complete();
        };

    return actionPromise
        .then(() -> decisionPublisher.publish(deploymentId, decision, metrics))
        .map(ignored -> decision);
  }

  public interface MetricsProvider {
    Promise<CanaryMetrics> fetch(String deploymentId);
  }

  public interface DeploymentController {
    Promise<Void> promote(String deploymentId, String targetEnvironment);

    Promise<Void> rollback(String deploymentId, String rollbackVersion, String rationale);
  }

  public interface DecisionPublisher {
    Promise<Void> publish(String deploymentId, CanaryDecision decision, CanaryMetrics metrics);
  }

  public record CanaryConfig(
      double errorRateThreshold,
      long latencyP99ThresholdMillis,
      double minimumSuccessRate,
      int minimumSampleSize,
      String targetEnvironment,
      String rollbackVersion) {

    public CanaryConfig {
      errorRateThreshold = Math.max(0.0, Math.min(1.0, errorRateThreshold));
      latencyP99ThresholdMillis = Math.max(1L, latencyP99ThresholdMillis);
      minimumSuccessRate = Math.max(0.0, Math.min(1.0, minimumSuccessRate));
      minimumSampleSize = Math.max(1, minimumSampleSize);
      targetEnvironment = Objects.requireNonNullElse(targetEnvironment, "production");
      rollbackVersion = Objects.requireNonNullElse(rollbackVersion, "previous-stable");
    }

    String summary() {
      return String.format(
          Locale.ROOT,
          "errorRateThreshold=%.3f, latencyP99ThresholdMillis=%d, minimumSuccessRate=%.3f, minimumSampleSize=%d, targetEnvironment=%s, rollbackVersion=%s",
          errorRateThreshold,
          latencyP99ThresholdMillis,
          minimumSuccessRate,
          minimumSampleSize,
          targetEnvironment,
          rollbackVersion);
    }
  }

  public record CanaryMetrics(
      double errorRate, long latencyP99Millis, double successRate, int sampleSize) {

    public CanaryMetrics {
      errorRate = Math.max(0.0, Math.min(1.0, errorRate));
      latencyP99Millis = Math.max(0L, latencyP99Millis);
      successRate = Math.max(0.0, Math.min(1.0, successRate));
      sampleSize = Math.max(0, sampleSize);
    }

    String summary() {
      return String.format(
          Locale.ROOT,
          "errorRate=%.3f, latencyP99Millis=%d, successRate=%.3f, sampleSize=%d",
          errorRate,
          latencyP99Millis,
          successRate,
          sampleSize);
    }
  }

  public record CanaryDecision(
      CanaryDecisionType type, String rationale, double confidence, boolean aiGenerated) {

    public CanaryDecision {
      type = type == null ? CanaryDecisionType.HOLD : type;
      rationale = Objects.requireNonNullElse(rationale, "");
      confidence = Math.max(0.0, Math.min(1.0, confidence));
    }
  }

  public enum CanaryDecisionType {
    PROMOTE,
    HOLD,
    ROLLBACK
  }
}
