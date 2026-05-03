package com.ghatana.yappc.platform.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import io.activej.promise.Promise;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Scores deployment risk from change signals and recommends a rollout strategy.
 * @doc.layer product
 * @doc.pattern Assessor
 */
public final class DeploymentRiskAssessor {

  private final YAPPCAIInterface aiService;
  private final ImpactAnalyzer impactAnalyzer;
  private final MetricsProvider metricsProvider;
  private final ObjectMapper objectMapper;

  public DeploymentRiskAssessor(
      YAPPCAIInterface aiService, ImpactAnalyzer impactAnalyzer, MetricsProvider metricsProvider) {
    this(aiService, impactAnalyzer, metricsProvider, new ObjectMapper());
  }

  DeploymentRiskAssessor(
      YAPPCAIInterface aiService,
      ImpactAnalyzer impactAnalyzer,
      MetricsProvider metricsProvider,
      ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.impactAnalyzer = Objects.requireNonNull(impactAnalyzer, "impactAnalyzer");
    this.metricsProvider = Objects.requireNonNull(metricsProvider, "metricsProvider");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<DeploymentRisk> assess(DeploymentRequest request) {
    Objects.requireNonNull(request, "request");
    return impactAnalyzer
        .analyze(request.changedModules(), request.tenantId())
        .then(
            impact ->
                metricsProvider
                    .fetch(request.projectId())
                    .then(
                        metrics -> {
                          DeploymentSignals signals = DeploymentSignals.from(request, impact, metrics);
                          return aiService
                              .reason(buildPrompt(signals), buildContext(request, signals))
                              .map(response -> parseRisk(response, signals));
                        }));
  }

  private Map<String, Object> buildContext(DeploymentRequest request, DeploymentSignals signals) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("projectId", request.projectId());
    context.put("tenantId", request.tenantId());
    context.put("modules", request.changedModules());
    context.put("signals", signals.summary());
    return context;
  }

  private String buildPrompt(DeploymentSignals signals) {
    return "Assess deployment risk and return JSON with riskScore, strategy, rationale, riskFactors, requiresApproval, canaryPercent.\n"
        + "Change signals:\n"
        + signals.summary();
  }

  private DeploymentRisk parseRisk(String response, DeploymentSignals signals) {
    if (response == null || response.isBlank()) {
      return fallback(signals, "AI response was empty");
    }

    try {
      JsonNode root = objectMapper.readTree(response);
      double riskScore = root.path("riskScore").asDouble(-1.0);
      if (riskScore < 0.0) {
        return fallback(signals, "AI response did not contain a valid risk score");
      }
      String strategyValue = root.path("strategy").asText();
      DeploymentStrategy strategy =
          DeploymentStrategy.valueOf(
              (strategyValue.isBlank() ? "ROLLING" : strategyValue).toUpperCase(Locale.ROOT));
      String rationaleValue = root.path("rationale").asText();
      String rationale =
          rationaleValue.isBlank() ? "AI generated recommendation" : rationaleValue;
      boolean requiresApproval = root.path("requiresApproval").asBoolean(false);
      int canaryPercent = root.path("canaryPercent").asInt(defaultCanaryPercent(strategy));
      List<String> riskFactors =
          root.path("riskFactors").isArray()
              ? objectMapper.convertValue(root.path("riskFactors"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
              : List.of();
      return new DeploymentRisk(riskScore, strategy, rationale, riskFactors, requiresApproval, canaryPercent, true);
    } catch (IOException | IllegalArgumentException exception) {
      return fallback(signals, "Failed to parse AI deployment risk response");
    }
  }

  private DeploymentRisk fallback(DeploymentSignals signals, String reason) {
    double riskScore = signals.estimatedRiskScore();
    DeploymentStrategy strategy;
    if (signals.hasBreakingApiChanges()) {
      strategy = DeploymentStrategy.BLUE_GREEN;
    } else if (riskScore >= 7.0 || signals.recentFailureRate() >= 0.05 || signals.testCoverage() < 0.7) {
      strategy = DeploymentStrategy.CANARY;
    } else if (riskScore >= 4.0) {
      strategy = DeploymentStrategy.ROLLING;
    } else {
      strategy = DeploymentStrategy.IMMEDIATE;
    }
    return new DeploymentRisk(
        riskScore,
        strategy,
        reason,
        signals.riskFactors(),
        strategy == DeploymentStrategy.BLUE_GREEN || riskScore >= 8.0,
        defaultCanaryPercent(strategy),
        false);
  }

  private int defaultCanaryPercent(DeploymentStrategy strategy) {
    return strategy == DeploymentStrategy.CANARY ? 5 : 0;
  }

  public interface ImpactAnalyzer {
    Promise<DeploymentImpact> analyze(List<String> changedModules, String tenantId);
  }

  public interface MetricsProvider {
    Promise<DeploymentMetrics> fetch(String projectId);
  }

  public record DeploymentRequest(
      String projectId,
      String tenantId,
      int linesAdded,
      int linesRemoved,
      List<String> changedModules,
      boolean hasBreakingApiChanges) {

    public DeploymentRequest {
      projectId = Objects.requireNonNullElse(projectId, "unknown-project");
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      linesAdded = Math.max(0, linesAdded);
      linesRemoved = Math.max(0, linesRemoved);
      changedModules = changedModules == null ? List.of() : List.copyOf(changedModules);
    }
  }

  public record DeploymentImpact(int affectedNodeCount) {
    public DeploymentImpact {
      affectedNodeCount = Math.max(0, affectedNodeCount);
    }
  }

  public record DeploymentMetrics(double recentFailureRate, double testCoverage) {
    public DeploymentMetrics {
      recentFailureRate = Math.max(0.0, recentFailureRate);
      testCoverage = Math.max(0.0, Math.min(1.0, testCoverage));
    }
  }

  record DeploymentSignals(
      int linesAdded,
      int linesRemoved,
      int modulesAffected,
      int downstreamImpact,
      double recentFailureRate,
      double testCoverage,
      boolean hasBreakingApiChanges) {

    static DeploymentSignals from(
        DeploymentRequest request, DeploymentImpact impact, DeploymentMetrics metrics) {
      return new DeploymentSignals(
          request.linesAdded(),
          request.linesRemoved(),
          request.changedModules().size(),
          impact.affectedNodeCount(),
          metrics.recentFailureRate(),
          metrics.testCoverage(),
          request.hasBreakingApiChanges());
    }

    double estimatedRiskScore() {
      double sizeFactor = Math.min(4.0, (linesAdded + linesRemoved) / 120.0);
      double moduleFactor = Math.min(2.0, modulesAffected * 0.5);
      double impactFactor = Math.min(2.0, downstreamImpact * 0.2);
      double failureFactor = Math.min(2.0, recentFailureRate * 20.0);
      double coveragePenalty = testCoverage >= 0.9 ? 0.0 : (0.9 - testCoverage) * 5.0;
      double breakingPenalty = hasBreakingApiChanges ? 2.0 : 0.0;
      return Math.min(10.0, sizeFactor + moduleFactor + impactFactor + failureFactor + coveragePenalty + breakingPenalty);
    }

    List<String> riskFactors() {
      List<String> factors = new java.util.ArrayList<>();
      if (hasBreakingApiChanges) {
        factors.add("breaking-api-changes");
      }
      if (recentFailureRate >= 0.05) {
        factors.add("elevated-recent-failure-rate");
      }
      if (testCoverage < 0.7) {
        factors.add("low-test-coverage");
      }
      if (downstreamImpact >= 5) {
        factors.add("high-downstream-impact");
      }
      if (linesAdded + linesRemoved >= 400) {
        factors.add("large-change-set");
      }
      return factors;
    }

    String summary() {
      return String.format(
          Locale.ROOT,
          "linesAdded=%d, linesRemoved=%d, modulesAffected=%d, downstreamImpact=%d, recentFailureRate=%.3f, testCoverage=%.3f, breakingApiChanges=%s",
          linesAdded,
          linesRemoved,
          modulesAffected,
          downstreamImpact,
          recentFailureRate,
          testCoverage,
          hasBreakingApiChanges);
    }
  }

  public enum DeploymentStrategy {
    IMMEDIATE,
    ROLLING,
    CANARY,
    BLUE_GREEN
  }

  public record DeploymentRisk(
      double riskScore,
      DeploymentStrategy strategy,
      String rationale,
      List<String> riskFactors,
      boolean requiresApproval,
      int canaryPercent,
      boolean aiGenerated) {

    public DeploymentRisk {
      riskScore = Math.max(0.0, Math.min(10.0, riskScore));
      strategy = strategy == null ? DeploymentStrategy.ROLLING : strategy;
      rationale = Objects.requireNonNullElse(rationale, "");
      riskFactors = riskFactors == null ? List.of() : List.copyOf(riskFactors);
      canaryPercent = Math.max(0, canaryPercent);
    }
  }
}
