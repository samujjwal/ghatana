package com.ghatana.yappc.platform.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentRiskAssessor Tests")
class DeploymentRiskAssessorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("assess parses AI deployment recommendation")
  void assessParsesAiDeploymentRecommendation() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "{\"riskScore\":7.6,\"strategy\":\"CANARY\",\"rationale\":\"Moderate risk due to impact\",\"riskFactors\":[\"impact\"],\"requiresApproval\":true,\"canaryPercent\":10}"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor(
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(6)),
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.03, 0.82)));

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise(
            () ->
                assessor.assess(
                    new DeploymentRiskAssessor.DeploymentRequest(
                        "project-a", "tenant-a", 220, 40, List.of("api", "db"), false)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY);
    assertThat(risk.riskScore()).isEqualTo(7.6);
    assertThat(risk.requiresApproval()).isTrue();
    assertThat(risk.aiGenerated()).isTrue();
    assertThat(risk.canaryPercent()).isEqualTo(10);
  }

  @Test
  @DisplayName("assess falls back to blue green for breaking API changes")
  void assessFallsBackToBlueGreenForBreakingApiChanges() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" "));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor(
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(2)),
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.95)));

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise(
            () ->
                assessor.assess(
                    new DeploymentRiskAssessor.DeploymentRequest(
                        "project-a", "tenant-a", 50, 10, List.of("api"), true)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.BLUE_GREEN);
    assertThat(risk.requiresApproval()).isTrue();
    assertThat(risk.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("assess falls back to canary when AI response is malformed and coverage is low")
  void assessFallsBackToCanaryWhenAiResponseIsMalformedAndCoverageIsLow() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor(
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(7)),
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.08, 0.55)));

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise(
            () ->
                assessor.assess(
                    new DeploymentRiskAssessor.DeploymentRequest(
                        null, null, -10, -5, null, false)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY);
    assertThat(risk.riskFactors()).contains("elevated-recent-failure-rate", "low-test-coverage", "high-downstream-impact");
    assertThat(risk.aiGenerated()).isFalse();
    assertThat(risk.canaryPercent()).isEqualTo(5);
  }

  @Test
  @DisplayName("assess falls back to immediate for low risk changes")
  void assessFallsBackToImmediateForLowRiskChanges() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"strategy\":\"INVALID\"}"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor(
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(0)),
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.0, 1.0)));

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise(
            () ->
                assessor.assess(
                    new DeploymentRiskAssessor.DeploymentRequest(
                        "project-a", "tenant-a", 5, 1, List.of(), false)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.IMMEDIATE);
    assertThat(risk.canaryPercent()).isZero();
    assertThat(risk.riskScore()).isLessThan(4.0);
  }

  @Test
  @DisplayName("assess defaults missing AI fields and non array factors")
  void assessDefaultsMissingAiFieldsAndNonArrayFactors() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "{\"riskScore\":5.2,\"strategy\":\"\",\"rationale\":\"\",\"riskFactors\":\"impact\"}"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor(
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(1)),
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.92)));

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise(
            () ->
                assessor.assess(
                    new DeploymentRiskAssessor.DeploymentRequest(
                        "project-a", "tenant-a", 60, 20, List.of("svc"), false)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.ROLLING);
    assertThat(risk.rationale()).isEqualTo("AI generated recommendation");
    assertThat(risk.riskFactors()).isEmpty();
    assertThat(risk.canaryPercent()).isZero();
    assertThat(risk.aiGenerated()).isTrue();
  }

    @Test
    @DisplayName("assess falls back when AI response is null")
    void assessFallsBackWhenAiResponseIsNull() {
        when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null));

        DeploymentRiskAssessor assessor =
                new DeploymentRiskAssessor(
                        aiService,
                        (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(1)),
                        projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.91)));

        DeploymentRiskAssessor.DeploymentRisk risk =
                runPromise(
                        () ->
                                assessor.assess(
                                        new DeploymentRiskAssessor.DeploymentRequest(
                                                "project-a", "tenant-a", 30, 10, List.of("svc"), false)));

        assertThat(risk.aiGenerated()).isFalse();
        assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.IMMEDIATE);
    }

  @Test
  @DisplayName("assess falls back when AI score is invalid and supports rolling strategy")
  void assessFallsBackWhenAiScoreIsInvalidAndSupportsRollingStrategy() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"riskScore\":-1}"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor(
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(3)),
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.02, 0.88)));

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise(
            () ->
                assessor.assess(
                    new DeploymentRiskAssessor.DeploymentRequest(
                        "project-a", "tenant-a", 180, 90, List.of("api", "worker"), false)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.ROLLING);
    assertThat(risk.aiGenerated()).isFalse();
    assertThat(risk.requiresApproval()).isFalse();
  }

  @Test
  @DisplayName("assess falls back when AI strategy is invalid despite valid risk score")
  void assessFallsBackWhenAiStrategyIsInvalidDespiteValidRiskScore() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("{\"riskScore\":6.4,\"strategy\":\"SIDEWAYS\"}"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor(
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(3)),
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.88)));

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise(
            () ->
                assessor.assess(
                    new DeploymentRiskAssessor.DeploymentRequest(
                        "project-a", "tenant-a", 180, 60, List.of("api", "worker"), false)));

    assertThat(risk.aiGenerated()).isFalse();
        assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.IMMEDIATE);
  }

  @Test
  @DisplayName("assess requires approval for very high non breaking fallback risk")
  void assessRequiresApprovalForVeryHighNonBreakingFallbackRisk() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"riskScore\":-1}"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor(
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(10)),
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.09, 0.2)));

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise(
            () ->
                assessor.assess(
                    new DeploymentRiskAssessor.DeploymentRequest(
                        "project-a", "tenant-a", 700, 250, List.of("api", "worker", "db"), false)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY);
    assertThat(risk.requiresApproval()).isTrue();
    assertThat(risk.riskScore()).isGreaterThanOrEqualTo(8.0);
  }

    @Test
    @DisplayName("assess falls back to canary when score alone is high")
    void assessFallsBackToCanaryWhenScoreAloneIsHigh() {
        when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"riskScore\":-1}"));

        DeploymentRiskAssessor assessor =
                new DeploymentRiskAssessor(
                        aiService,
                        (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(8)),
                        projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.95)));

        DeploymentRiskAssessor.DeploymentRisk risk =
                runPromise(
                        () ->
                                assessor.assess(
                                        new DeploymentRiskAssessor.DeploymentRequest(
                                                "project-a",
                                                "tenant-a",
                                                600,
                                                200,
                                                List.of("api", "worker", "db", "jobs"),
                                                false)));

        assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY);
        assertThat(risk.aiGenerated()).isFalse();
        assertThat(risk.requiresApproval()).isFalse();
        assertThat(risk.riskScore()).isGreaterThanOrEqualTo(7.0);
    }

    @Test
    @DisplayName("assess falls back to canary when low coverage alone crosses the threshold")
    void assessFallsBackToCanaryWhenLowCoverageAloneCrossesTheThreshold() {
        when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"riskScore\":-1}"));

        DeploymentRiskAssessor assessor =
                new DeploymentRiskAssessor(
                        aiService,
                        (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(2)),
                        projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.6)));

        DeploymentRiskAssessor.DeploymentRisk risk =
                runPromise(
                        () ->
                                assessor.assess(
                                        new DeploymentRiskAssessor.DeploymentRequest(
                                                "project-a", "tenant-a", 80, 20, List.of("api"), false)));

        assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY);
        assertThat(risk.aiGenerated()).isFalse();
        assertThat(risk.riskFactors()).contains("low-test-coverage");
        assertThat(risk.riskScore()).isLessThan(7.0);
    }

  @Test
  @DisplayName("deployment records normalize invalid values")
  void deploymentRecordsNormalizeInvalidValues() {
    DeploymentRiskAssessor.DeploymentRisk risk =
        new DeploymentRiskAssessor.DeploymentRisk(12.0, null, null, null, false, -3, false);
    DeploymentRiskAssessor.DeploymentSignals signals =
        new DeploymentRiskAssessor.DeploymentSignals(500, 20, 3, 6, 0.07, 0.65, true);

    assertThat(risk.riskScore()).isEqualTo(10.0);
    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.ROLLING);
    assertThat(risk.rationale()).isEmpty();
    assertThat(risk.riskFactors()).isEmpty();
    assertThat(risk.canaryPercent()).isZero();

    assertThat(signals.riskFactors())
        .contains(
            "breaking-api-changes",
            "elevated-recent-failure-rate",
            "low-test-coverage",
            "high-downstream-impact",
            "large-change-set");
  }

  @Test
  @DisplayName("deployment signals omit factors when thresholds are not met")
  void deploymentSignalsOmitFactorsWhenThresholdsAreNotMet() {
    DeploymentRiskAssessor.DeploymentSignals signals =
        new DeploymentRiskAssessor.DeploymentSignals(50, 10, 1, 1, 0.01, 0.95, false);

    assertThat(signals.riskFactors()).isEmpty();
  }
}
