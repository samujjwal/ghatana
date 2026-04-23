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

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DeploymentRiskAssessor Tests")
class DeploymentRiskAssessorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("assess parses AI deployment recommendation")
  void assessParsesAiDeploymentRecommendation() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "{\"riskScore\":7.6,\"strategy\":\"CANARY\",\"rationale\":\"Moderate risk due to impact\",\"riskFactors\":[\"impact\"],\"requiresApproval\":true,\"canaryPercent\":10}"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor( // GH-90000
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(6)), // GH-90000
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.03, 0.82))); // GH-90000

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise( // GH-90000
            () -> // GH-90000
                assessor.assess( // GH-90000
                    new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                        "project-a", "tenant-a", 220, 40, List.of("api", "db"), false))); // GH-90000

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY); // GH-90000
    assertThat(risk.riskScore()).isEqualTo(7.6); // GH-90000
    assertThat(risk.requiresApproval()).isTrue(); // GH-90000
    assertThat(risk.aiGenerated()).isTrue(); // GH-90000
    assertThat(risk.canaryPercent()).isEqualTo(10); // GH-90000
  }

  @Test
  @DisplayName("assess falls back to blue green for breaking API changes")
  void assessFallsBackToBlueGreenForBreakingApiChanges() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" "));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor( // GH-90000
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(2)), // GH-90000
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.95))); // GH-90000

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise( // GH-90000
            () -> // GH-90000
                assessor.assess( // GH-90000
                    new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                        "project-a", "tenant-a", 50, 10, List.of("api"), true)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.BLUE_GREEN); // GH-90000
    assertThat(risk.requiresApproval()).isTrue(); // GH-90000
    assertThat(risk.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("assess falls back to canary when AI response is malformed and coverage is low")
  void assessFallsBackToCanaryWhenAiResponseIsMalformedAndCoverageIsLow() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor( // GH-90000
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(7)), // GH-90000
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.08, 0.55))); // GH-90000

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise( // GH-90000
            () -> // GH-90000
                assessor.assess( // GH-90000
                    new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                        null, null, -10, -5, null, false)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY); // GH-90000
    assertThat(risk.riskFactors()).contains("elevated-recent-failure-rate", "low-test-coverage", "high-downstream-impact"); // GH-90000
    assertThat(risk.aiGenerated()).isFalse(); // GH-90000
    assertThat(risk.canaryPercent()).isEqualTo(5); // GH-90000
  }

  @Test
  @DisplayName("assess falls back to immediate for low risk changes")
  void assessFallsBackToImmediateForLowRiskChanges() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"strategy\":\"INVALID\"}")); // GH-90000

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor( // GH-90000
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(0)), // GH-90000
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.0, 1.0))); // GH-90000

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise( // GH-90000
            () -> // GH-90000
                assessor.assess( // GH-90000
                    new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                        "project-a", "tenant-a", 5, 1, List.of(), false))); // GH-90000

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.IMMEDIATE); // GH-90000
    assertThat(risk.canaryPercent()).isZero(); // GH-90000
    assertThat(risk.riskScore()).isLessThan(4.0); // GH-90000
  }

  @Test
  @DisplayName("assess defaults missing AI fields and non array factors")
  void assessDefaultsMissingAiFieldsAndNonArrayFactors() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "{\"riskScore\":5.2,\"strategy\":\"\",\"rationale\":\"\",\"riskFactors\":\"impact\"}"));

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor( // GH-90000
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(1)), // GH-90000
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.92))); // GH-90000

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise( // GH-90000
            () -> // GH-90000
                assessor.assess( // GH-90000
                    new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                        "project-a", "tenant-a", 60, 20, List.of("svc"), false)));

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.ROLLING); // GH-90000
    assertThat(risk.rationale()).isEqualTo("AI generated recommendation");
    assertThat(risk.riskFactors()).isEmpty(); // GH-90000
    assertThat(risk.canaryPercent()).isZero(); // GH-90000
    assertThat(risk.aiGenerated()).isTrue(); // GH-90000
  }

    @Test
    @DisplayName("assess falls back when AI response is null")
    void assessFallsBackWhenAiResponseIsNull() { // GH-90000
        when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null)); // GH-90000

        DeploymentRiskAssessor assessor =
                new DeploymentRiskAssessor( // GH-90000
                        aiService,
                        (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(1)), // GH-90000
                        projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.91))); // GH-90000

        DeploymentRiskAssessor.DeploymentRisk risk =
                runPromise( // GH-90000
                        () -> // GH-90000
                                assessor.assess( // GH-90000
                                        new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                                                "project-a", "tenant-a", 30, 10, List.of("svc"), false)));

        assertThat(risk.aiGenerated()).isFalse(); // GH-90000
        assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.IMMEDIATE); // GH-90000
    }

  @Test
  @DisplayName("assess falls back when AI score is invalid and supports rolling strategy")
  void assessFallsBackWhenAiScoreIsInvalidAndSupportsRollingStrategy() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"riskScore\":-1}")); // GH-90000

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor( // GH-90000
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(3)), // GH-90000
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.02, 0.88))); // GH-90000

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise( // GH-90000
            () -> // GH-90000
                assessor.assess( // GH-90000
                    new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                        "project-a", "tenant-a", 180, 90, List.of("api", "worker"), false))); // GH-90000

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.ROLLING); // GH-90000
    assertThat(risk.aiGenerated()).isFalse(); // GH-90000
    assertThat(risk.requiresApproval()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("assess falls back when AI strategy is invalid despite valid risk score")
  void assessFallsBackWhenAiStrategyIsInvalidDespiteValidRiskScore() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("{\"riskScore\":6.4,\"strategy\":\"SIDEWAYS\"}")); // GH-90000

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor( // GH-90000
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(3)), // GH-90000
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.88))); // GH-90000

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise( // GH-90000
            () -> // GH-90000
                assessor.assess( // GH-90000
                    new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                        "project-a", "tenant-a", 180, 60, List.of("api", "worker"), false))); // GH-90000

    assertThat(risk.aiGenerated()).isFalse(); // GH-90000
        assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.IMMEDIATE); // GH-90000
  }

  @Test
  @DisplayName("assess requires approval for very high non breaking fallback risk")
  void assessRequiresApprovalForVeryHighNonBreakingFallbackRisk() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"riskScore\":-1}")); // GH-90000

    DeploymentRiskAssessor assessor =
        new DeploymentRiskAssessor( // GH-90000
            aiService,
            (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(10)), // GH-90000
            projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.09, 0.2))); // GH-90000

    DeploymentRiskAssessor.DeploymentRisk risk =
        runPromise( // GH-90000
            () -> // GH-90000
                assessor.assess( // GH-90000
                    new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                        "project-a", "tenant-a", 700, 250, List.of("api", "worker", "db"), false))); // GH-90000

    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY); // GH-90000
    assertThat(risk.requiresApproval()).isTrue(); // GH-90000
    assertThat(risk.riskScore()).isGreaterThanOrEqualTo(8.0); // GH-90000
  }

    @Test
    @DisplayName("assess falls back to canary when score alone is high")
    void assessFallsBackToCanaryWhenScoreAloneIsHigh() { // GH-90000
        when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"riskScore\":-1}")); // GH-90000

        DeploymentRiskAssessor assessor =
                new DeploymentRiskAssessor( // GH-90000
                        aiService,
                        (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(8)), // GH-90000
                        projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.95))); // GH-90000

        DeploymentRiskAssessor.DeploymentRisk risk =
                runPromise( // GH-90000
                        () -> // GH-90000
                                assessor.assess( // GH-90000
                                        new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                                                "project-a",
                                                "tenant-a",
                                                600,
                                                200,
                                                List.of("api", "worker", "db", "jobs"), // GH-90000
                                                false)));

        assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY); // GH-90000
        assertThat(risk.aiGenerated()).isFalse(); // GH-90000
        assertThat(risk.requiresApproval()).isFalse(); // GH-90000
        assertThat(risk.riskScore()).isGreaterThanOrEqualTo(7.0); // GH-90000
    }

    @Test
    @DisplayName("assess falls back to canary when low coverage alone crosses the threshold")
    void assessFallsBackToCanaryWhenLowCoverageAloneCrossesTheThreshold() { // GH-90000
        when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("{\"riskScore\":-1}")); // GH-90000

        DeploymentRiskAssessor assessor =
                new DeploymentRiskAssessor( // GH-90000
                        aiService,
                        (modules, tenantId) -> Promise.of(new DeploymentRiskAssessor.DeploymentImpact(2)), // GH-90000
                        projectId -> Promise.of(new DeploymentRiskAssessor.DeploymentMetrics(0.01, 0.6))); // GH-90000

        DeploymentRiskAssessor.DeploymentRisk risk =
                runPromise( // GH-90000
                        () -> // GH-90000
                                assessor.assess( // GH-90000
                                        new DeploymentRiskAssessor.DeploymentRequest( // GH-90000
                                                "project-a", "tenant-a", 80, 20, List.of("api"), false)));

        assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.CANARY); // GH-90000
        assertThat(risk.aiGenerated()).isFalse(); // GH-90000
        assertThat(risk.riskFactors()).contains("low-test-coverage");
        assertThat(risk.riskScore()).isLessThan(7.0); // GH-90000
    }

  @Test
  @DisplayName("deployment records normalize invalid values")
  void deploymentRecordsNormalizeInvalidValues() { // GH-90000
    DeploymentRiskAssessor.DeploymentRisk risk =
        new DeploymentRiskAssessor.DeploymentRisk(12.0, null, null, null, false, -3, false); // GH-90000
    DeploymentRiskAssessor.DeploymentSignals signals =
        new DeploymentRiskAssessor.DeploymentSignals(500, 20, 3, 6, 0.07, 0.65, true); // GH-90000

    assertThat(risk.riskScore()).isEqualTo(10.0); // GH-90000
    assertThat(risk.strategy()).isEqualTo(DeploymentRiskAssessor.DeploymentStrategy.ROLLING); // GH-90000
    assertThat(risk.rationale()).isEmpty(); // GH-90000
    assertThat(risk.riskFactors()).isEmpty(); // GH-90000
    assertThat(risk.canaryPercent()).isZero(); // GH-90000

    assertThat(signals.riskFactors()) // GH-90000
        .contains( // GH-90000
            "breaking-api-changes",
            "elevated-recent-failure-rate",
            "low-test-coverage",
            "high-downstream-impact",
            "large-change-set");
  }

  @Test
  @DisplayName("deployment signals omit factors when thresholds are not met")
  void deploymentSignalsOmitFactorsWhenThresholdsAreNotMet() { // GH-90000
    DeploymentRiskAssessor.DeploymentSignals signals =
        new DeploymentRiskAssessor.DeploymentSignals(50, 10, 1, 1, 0.01, 0.95, false); // GH-90000

    assertThat(signals.riskFactors()).isEmpty(); // GH-90000
  }
}
