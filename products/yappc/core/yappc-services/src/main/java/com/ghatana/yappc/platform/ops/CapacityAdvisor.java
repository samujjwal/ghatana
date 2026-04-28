package com.ghatana.yappc.platform.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Recommends near-term capacity actions from utilization, growth, and cost signals with AI guidance and deterministic fallback.
 * @doc.layer product
 * @doc.pattern Advisor
 */
public final class CapacityAdvisor {

  private final YAPPCAIService aiService;
  private final UsageProvider usageProvider;
  private final CostProvider costProvider;
  private final ObjectMapper objectMapper;

  public CapacityAdvisor(
      YAPPCAIService aiService, UsageProvider usageProvider, CostProvider costProvider) {
    this(aiService, usageProvider, costProvider, new ObjectMapper());
  }

  CapacityAdvisor(
      YAPPCAIService aiService,
      UsageProvider usageProvider,
      CostProvider costProvider,
      ObjectMapper objectMapper) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.usageProvider = Objects.requireNonNull(usageProvider, "usageProvider");
    this.costProvider = Objects.requireNonNull(costProvider, "costProvider");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public Promise<CapacityRecommendation> advise(CapacityRequest request) {
    Objects.requireNonNull(request, "request");
    return usageProvider
        .fetch(request.projectId(), request.tenantId())
        .then(
            usage ->
                costProvider
                    .fetch(request.projectId(), request.tenantId())
                    .then(
                        cost -> {
                          CapacitySignals signals = CapacitySignals.from(request, usage, cost);
                          return aiService
                              .reason(buildPrompt(signals), buildContext(request, signals))
                              .map(response -> parseRecommendation(response, signals));
                        }));
  }

  private Map<String, Object> buildContext(CapacityRequest request, CapacitySignals signals) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("projectId", request.projectId());
    context.put("tenantId", request.tenantId());
    context.put("currentReplicas", request.currentReplicas());
    context.put("signals", signals.summary());
    return context;
  }

  private String buildPrompt(CapacitySignals signals) {
    return "Recommend a capacity action and return JSON with action, targetReplicas, rationale, costDelta, and confidence.\n"
        + signals.summary();
  }

  private CapacityRecommendation parseRecommendation(String response, CapacitySignals signals) {
    if (response == null || response.isBlank()) {
      return fallback(signals, "AI response was empty");
    }

    try {
      JsonNode root = objectMapper.readTree(response);
      JsonNode actionNode = root.path("action");
      JsonNode rationaleNode = root.path("rationale");
      ScaleAction action =
          ScaleAction.valueOf(
          (actionNode.isTextual() ? actionNode.asText() : "HOLD").toUpperCase(Locale.ROOT));
      int targetReplicas = root.path("targetReplicas").asInt(signals.currentReplicas());
      String rationale =
        rationaleNode.isTextual()
          ? rationaleNode.asText()
          : "AI generated capacity recommendation";
      double costDelta = root.path("costDelta").asDouble(0.0);
      double confidence = root.path("confidence").asDouble(0.0);
      return new CapacityRecommendation(
          action, targetReplicas, rationale, costDelta, confidence, true);
    } catch (IOException | IllegalArgumentException exception) {
      return fallback(signals, "Failed to parse AI capacity recommendation");
    }
  }

  private CapacityRecommendation fallback(CapacitySignals signals, String rationale) {
    if (signals.peakCpuUtilization() >= 0.85 || signals.weeklyGrowthRate() >= 0.2) {
      int targetReplicas = Math.max(signals.currentReplicas() + 1, signals.currentReplicas() + signals.scaleStep());
      return new CapacityRecommendation(
          ScaleAction.SCALE_UP,
          targetReplicas,
          rationale,
          signals.projectedScaleUpCostDelta(),
          0.76,
          false);
    }

    if (signals.avgCpuUtilization() <= 0.3
        && signals.avgMemoryUtilization() <= 0.35
        && signals.currentReplicas() > 1) {
      return new CapacityRecommendation(
          ScaleAction.SCALE_DOWN,
          Math.max(1, signals.currentReplicas() - 1),
          rationale,
          -signals.estimatedUnitCost(),
          0.79,
          false);
    }

    if (signals.avgCpuUtilization() <= 0.5 && signals.projectedMonthlyCost() > signals.currentMonthlyCost() * 1.1) {
      return new CapacityRecommendation(
          ScaleAction.RIGHTSIZE,
          signals.currentReplicas(),
          rationale,
          -Math.max(0.0, signals.projectedMonthlyCost() - signals.currentMonthlyCost()),
          0.68,
          false);
    }

    return new CapacityRecommendation(
        ScaleAction.HOLD,
        signals.currentReplicas(),
        rationale,
        0.0,
        0.65,
        false);
  }

  public interface UsageProvider {
    Promise<UsageSnapshot> fetch(String projectId, String tenantId);
  }

  public interface CostProvider {
    Promise<CostSnapshot> fetch(String projectId, String tenantId);
  }

  public record CapacityRequest(String projectId, String tenantId, int currentReplicas) {
    public CapacityRequest {
      projectId = Objects.requireNonNullElse(projectId, "unknown-project");
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      currentReplicas = Math.max(1, currentReplicas);
    }
  }

  public record UsageSnapshot(
      double avgCpuUtilization,
      double peakCpuUtilization,
      double avgMemoryUtilization,
      double weeklyGrowthRate) {

    public UsageSnapshot {
      avgCpuUtilization = clamp(avgCpuUtilization);
      peakCpuUtilization = clamp(peakCpuUtilization);
      avgMemoryUtilization = clamp(avgMemoryUtilization);
      weeklyGrowthRate = Math.max(-1.0, weeklyGrowthRate);
    }
  }

  public record CostSnapshot(double currentMonthlyCost, double projectedMonthlyCost, double estimatedUnitCost) {
    public CostSnapshot {
      currentMonthlyCost = Math.max(0.0, currentMonthlyCost);
      projectedMonthlyCost = Math.max(0.0, projectedMonthlyCost);
      estimatedUnitCost = Math.max(0.0, estimatedUnitCost);
    }
  }

  record CapacitySignals(
      int currentReplicas,
      double avgCpuUtilization,
      double peakCpuUtilization,
      double avgMemoryUtilization,
      double weeklyGrowthRate,
      double currentMonthlyCost,
      double projectedMonthlyCost,
      double estimatedUnitCost) {

    static CapacitySignals from(
        CapacityRequest request, UsageSnapshot usage, CostSnapshot cost) {
      return new CapacitySignals(
          request.currentReplicas(),
          usage.avgCpuUtilization(),
          usage.peakCpuUtilization(),
          usage.avgMemoryUtilization(),
          usage.weeklyGrowthRate(),
          cost.currentMonthlyCost(),
          cost.projectedMonthlyCost(),
          cost.estimatedUnitCost());
    }

    int scaleStep() {
      return Math.max(1, (int) Math.ceil(currentReplicas * 0.25));
    }

    double projectedScaleUpCostDelta() {
      return estimatedUnitCost * scaleStep();
    }

    String summary() {
      return String.format(
          Locale.ROOT,
          "currentReplicas=%d, avgCpu=%.3f, peakCpu=%.3f, avgMemory=%.3f, weeklyGrowth=%.3f, currentMonthlyCost=%.2f, projectedMonthlyCost=%.2f, estimatedUnitCost=%.2f",
          currentReplicas,
          avgCpuUtilization,
          peakCpuUtilization,
          avgMemoryUtilization,
          weeklyGrowthRate,
          currentMonthlyCost,
          projectedMonthlyCost,
          estimatedUnitCost);
    }
  }

  public enum ScaleAction {
    SCALE_UP,
    SCALE_DOWN,
    RIGHTSIZE,
    HOLD
  }

  public record CapacityRecommendation(
      ScaleAction action,
      int targetReplicas,
      String rationale,
      double costDelta,
      double confidence,
      boolean aiGenerated) {

    public CapacityRecommendation {
      action = action == null ? ScaleAction.HOLD : action;
      targetReplicas = Math.max(1, targetReplicas);
      rationale = Objects.requireNonNullElse(rationale, "");
      confidence = clamp(confidence);
    }
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
