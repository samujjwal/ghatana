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

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("CapacityAdvisor Tests [GH-90000]")
class CapacityAdvisorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;
  @Mock private CapacityAdvisor.UsageProvider usageProvider;
  @Mock private CapacityAdvisor.CostProvider costProvider;

  @Test
  @DisplayName("advise parses structured AI recommendation [GH-90000]")
  void adviseParsesStructuredAiRecommendation() { // GH-90000
    when(usageProvider.fetch("project-a", "tenant-a")) // GH-90000
        .thenReturn(Promise.of(new UsageSnapshot(0.61, 0.79, 0.58, 0.12))); // GH-90000
    when(costProvider.fetch("project-a", "tenant-a")) // GH-90000
        .thenReturn(Promise.of(new CostSnapshot(1200.0, 1280.0, 140.0))); // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "{\"action\":\"scale_up\",\"targetReplicas\":6,\"rationale\":\"Traffic is climbing\",\"costDelta\":180.5,\"confidence\":0.93}"));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider); // GH-90000
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-a", "tenant-a", 4))); // GH-90000

    assertThat(recommendation.action()).isEqualTo(ScaleAction.SCALE_UP); // GH-90000
    assertThat(recommendation.targetReplicas()).isEqualTo(6); // GH-90000
    assertThat(recommendation.rationale()).isEqualTo("Traffic is climbing [GH-90000]");
    assertThat(recommendation.costDelta()).isEqualTo(180.5); // GH-90000
    assertThat(recommendation.confidence()).isEqualTo(0.93); // GH-90000
    assertThat(recommendation.aiGenerated()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("advise falls back to scale up when peak load and growth are high [GH-90000]")
  void adviseFallsBackToScaleUpWhenPeakLoadAndGrowthAreHigh() { // GH-90000
    when(usageProvider.fetch("project-b", "tenant-b")) // GH-90000
        .thenReturn(Promise.of(new UsageSnapshot(0.72, 0.91, 0.64, 0.28))); // GH-90000
    when(costProvider.fetch("project-b", "tenant-b")) // GH-90000
        .thenReturn(Promise.of(new CostSnapshot(1500.0, 1800.0, 160.0))); // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" [GH-90000]"));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider); // GH-90000
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-b", "tenant-b", 4))); // GH-90000

    assertThat(recommendation.action()).isEqualTo(ScaleAction.SCALE_UP); // GH-90000
    assertThat(recommendation.targetReplicas()).isEqualTo(5); // GH-90000
    assertThat(recommendation.costDelta()).isEqualTo(160.0); // GH-90000
    assertThat(recommendation.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("advise falls back to scale down when utilization is consistently low [GH-90000]")
  void adviseFallsBackToScaleDownWhenUtilizationIsConsistentlyLow() { // GH-90000
    when(usageProvider.fetch("project-c", "tenant-c")) // GH-90000
        .thenReturn(Promise.of(new UsageSnapshot(0.18, 0.27, 0.22, 0.02))); // GH-90000
    when(costProvider.fetch("project-c", "tenant-c")) // GH-90000
        .thenReturn(Promise.of(new CostSnapshot(900.0, 880.0, 120.0))); // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json [GH-90000]"));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider); // GH-90000
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-c", "tenant-c", 3))); // GH-90000

    assertThat(recommendation.action()).isEqualTo(ScaleAction.SCALE_DOWN); // GH-90000
    assertThat(recommendation.targetReplicas()).isEqualTo(2); // GH-90000
    assertThat(recommendation.costDelta()).isEqualTo(-120.0); // GH-90000
    assertThat(recommendation.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("advise falls back to rightsize when cost rises faster than utilization [GH-90000]")
  void adviseFallsBackToRightsizeWhenCostRisesFasterThanUtilization() { // GH-90000
    when(usageProvider.fetch("project-d", "tenant-d")) // GH-90000
        .thenReturn(Promise.of(new UsageSnapshot(0.42, 0.49, 0.44, 0.05))); // GH-90000
    when(costProvider.fetch("project-d", "tenant-d")) // GH-90000
        .thenReturn(Promise.of(new CostSnapshot(1000.0, 1250.0, 110.0))); // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("  [GH-90000]"));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider); // GH-90000
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-d", "tenant-d", 2))); // GH-90000

    assertThat(recommendation.action()).isEqualTo(ScaleAction.RIGHTSIZE); // GH-90000
    assertThat(recommendation.targetReplicas()).isEqualTo(2); // GH-90000
    assertThat(recommendation.costDelta()).isEqualTo(-250.0); // GH-90000
    assertThat(recommendation.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("advise holds steady for balanced utilization [GH-90000]")
  void adviseHoldsSteadyForBalancedUtilization() { // GH-90000
    when(usageProvider.fetch("project-e", "tenant-e")) // GH-90000
        .thenReturn(Promise.of(new UsageSnapshot(0.58, 0.71, 0.55, 0.04))); // GH-90000
    when(costProvider.fetch("project-e", "tenant-e")) // GH-90000
        .thenReturn(Promise.of(new CostSnapshot(1300.0, 1360.0, 140.0))); // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{ [GH-90000]"));

    CapacityAdvisor advisor = new CapacityAdvisor(aiService, usageProvider, costProvider); // GH-90000
    CapacityRecommendation recommendation =
        runPromise(() -> advisor.advise(new CapacityRequest("project-e", "tenant-e", 4))); // GH-90000

    assertThat(recommendation.action()).isEqualTo(ScaleAction.HOLD); // GH-90000
    assertThat(recommendation.targetReplicas()).isEqualTo(4); // GH-90000
    assertThat(recommendation.costDelta()).isZero(); // GH-90000
    assertThat(recommendation.aiGenerated()).isFalse(); // GH-90000
  }
}
