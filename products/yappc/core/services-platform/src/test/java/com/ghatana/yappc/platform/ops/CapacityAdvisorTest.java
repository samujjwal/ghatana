package com.ghatana.yappc.platform.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.platform.ops.CapacityAdvisor.CapacityRecommendation;
import com.ghatana.yappc.platform.ops.CapacityAdvisor.CapacityRequest;
import com.ghatana.yappc.platform.ops.CapacityAdvisor.CostSnapshot;
import com.ghatana.yappc.platform.ops.CapacityAdvisor.ScaleAction;
import com.ghatana.yappc.platform.ops.CapacityAdvisor.UsageSnapshot;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CapacityAdvisor Tests")
class CapacityAdvisorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;
  @Mock private CapacityAdvisor.UsageProvider usageProvider;
  @Mock private CapacityAdvisor.CostProvider costProvider;

  @Test
  @DisplayName("advise parses structured AI recommendation")
  void adviseParsesStructuredAiRecommendation() {
    when(usageProvider.fetch("project-a", "tenant-a"))
        .thenReturn(Promise.of(new UsageSnapshot(0.61, 0.79, 0.58, 0.12)));
    when(costProvider.fetch("project-a", "tenant-a"))
        .thenReturn(Promise.of(new CostSnapshot(1200.0, 1280.0, 140.0)));
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "{\"action\":\"scale_up\",\"targetReplicas\":6,\"rationale\":\"Traffic is climbing\",\"costDelta\":180.5,\"confidence\":0.93}"));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider);
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-a", "tenant-a", 4)));

    assertThat(recommendation.action()).isEqualTo(ScaleAction.SCALE_UP);
    assertThat(recommendation.targetReplicas()).isEqualTo(6);
    assertThat(recommendation.rationale()).isEqualTo("Traffic is climbing");
    assertThat(recommendation.costDelta()).isEqualTo(180.5);
    assertThat(recommendation.confidence()).isEqualTo(0.93);
    assertThat(recommendation.aiGenerated()).isTrue();
  }

  @Test
  @DisplayName("advise falls back to scale up when peak load and growth are high")
  void adviseFallsBackToScaleUpWhenPeakLoadAndGrowthAreHigh() {
    when(usageProvider.fetch("project-b", "tenant-b"))
        .thenReturn(Promise.of(new UsageSnapshot(0.72, 0.91, 0.64, 0.28)));
    when(costProvider.fetch("project-b", "tenant-b"))
        .thenReturn(Promise.of(new CostSnapshot(1500.0, 1800.0, 160.0)));
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(""));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider);
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-b", "tenant-b", 4)));

    assertThat(recommendation.action()).isEqualTo(ScaleAction.SCALE_UP);
    assertThat(recommendation.targetReplicas()).isEqualTo(5);
    assertThat(recommendation.costDelta()).isEqualTo(160.0);
    assertThat(recommendation.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("advise falls back to scale down when utilization is consistently low")
  void adviseFallsBackToScaleDownWhenUtilizationIsConsistentlyLow() {
    when(usageProvider.fetch("project-c", "tenant-c"))
        .thenReturn(Promise.of(new UsageSnapshot(0.18, 0.27, 0.22, 0.02)));
    when(costProvider.fetch("project-c", "tenant-c"))
        .thenReturn(Promise.of(new CostSnapshot(900.0, 880.0, 120.0)));
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider);
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-c", "tenant-c", 3)));

    assertThat(recommendation.action()).isEqualTo(ScaleAction.SCALE_DOWN);
    assertThat(recommendation.targetReplicas()).isEqualTo(2);
    assertThat(recommendation.costDelta()).isEqualTo(-120.0);
    assertThat(recommendation.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("advise falls back to rightsize when cost rises faster than utilization")
  void adviseFallsBackToRightsizeWhenCostRisesFasterThanUtilization() {
    when(usageProvider.fetch("project-d", "tenant-d"))
        .thenReturn(Promise.of(new UsageSnapshot(0.42, 0.49, 0.44, 0.05)));
    when(costProvider.fetch("project-d", "tenant-d"))
        .thenReturn(Promise.of(new CostSnapshot(1000.0, 1250.0, 110.0)));
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" "));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider);
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-d", "tenant-d", 2)));

    assertThat(recommendation.action()).isEqualTo(ScaleAction.RIGHTSIZE);
    assertThat(recommendation.targetReplicas()).isEqualTo(2);
    assertThat(recommendation.costDelta()).isEqualTo(-250.0);
    assertThat(recommendation.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("advise holds steady for balanced utilization")
  void adviseHoldsSteadyForBalancedUtilization() {
    when(usageProvider.fetch("project-e", "tenant-e"))
        .thenReturn(Promise.of(new UsageSnapshot(0.58, 0.71, 0.55, 0.04)));
    when(costProvider.fetch("project-e", "tenant-e"))
        .thenReturn(Promise.of(new CostSnapshot(1300.0, 1360.0, 140.0)));
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{"));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider);
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-e", "tenant-e", 4)));

    assertThat(recommendation.action()).isEqualTo(ScaleAction.HOLD);
    assertThat(recommendation.targetReplicas()).isEqualTo(4);
    assertThat(recommendation.costDelta()).isZero();
    assertThat(recommendation.aiGenerated()).isFalse();
  }
}