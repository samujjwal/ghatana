package com.ghatana.yappc.platform.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CanaryAnalyzer Tests")
class CanaryAnalyzerTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("evaluate holds when the sample size is too small")
  void evaluateHoldsWhenSampleSizeIsTooSmall() {
    RecordingDeploymentController controller = new RecordingDeploymentController();
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher();
    CanaryAnalyzer analyzer = analyzer(metrics(0.01, 120, 0.99, 5), controller, publisher);

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-1", config()));

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD);
    assertThat(publisher.decisions).containsExactly(decision);
    assertThat(controller.actions).isEmpty();
    verifyNoInteractions(aiService);
  }

  @Test
  @DisplayName("evaluate rolls back when error rate exceeds threshold")
  void evaluateRollsBackWhenErrorRateExceedsThreshold() {
    RecordingDeploymentController controller = new RecordingDeploymentController();
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher();
    CanaryAnalyzer analyzer = analyzer(metrics(0.08, 120, 0.95, 120), controller, publisher);

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-1", config()));

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.ROLLBACK);
    assertThat(controller.actions).containsExactly("rollback:deploy-1:v1.0.0");
    assertThat(decision.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("evaluate rolls back when latency exceeds threshold")
  void evaluateRollsBackWhenLatencyExceedsThreshold() {
    RecordingDeploymentController controller = new RecordingDeploymentController();
    CanaryAnalyzer analyzer = analyzer(metrics(0.01, 900, 0.95, 120), controller, new RecordingDecisionPublisher());

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-2", config()));

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.ROLLBACK);
    assertThat(controller.actions).containsExactly("rollback:deploy-2:v1.0.0");
  }

  @Test
  @DisplayName("evaluate rolls back when success rate drops below threshold")
  void evaluateRollsBackWhenSuccessRateDropsBelowThreshold() {
    RecordingDeploymentController controller = new RecordingDeploymentController();
    CanaryAnalyzer analyzer = analyzer(metrics(0.01, 150, 0.85, 120), controller, new RecordingDecisionPublisher());

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-3", config()));

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.ROLLBACK);
    assertThat(controller.actions).containsExactly("rollback:deploy-3:v1.0.0");
  }

  @Test
  @DisplayName("evaluate promotes when AI recommends promotion")
  void evaluatePromotesWhenAiRecommendsPromotion() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("{\"decision\":\"PROMOTE\",\"rationale\":\"Metrics are stable\",\"confidence\":0.91}"));

    RecordingDeploymentController controller = new RecordingDeploymentController();
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher();
    CanaryAnalyzer analyzer = analyzer(metrics(0.01, 120, 0.99, 120), controller, publisher);

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-4", config()));

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.PROMOTE);
    assertThat(decision.aiGenerated()).isTrue();
    assertThat(controller.actions).containsExactly("promote:deploy-4:production");
    assertThat(publisher.decisions).containsExactly(decision);
  }

  @Test
  @DisplayName("evaluate holds when AI response is blank or null")
  void evaluateHoldsWhenAiResponseIsBlankOrNull() {
    when(aiService.reason(anyString(), anyMap()))
      .thenReturn(Promise.of(" "))
      .thenReturn(Promise.of(null));

    CanaryAnalyzer analyzer =
        analyzer(metrics(0.01, 120, 0.99, 120), new RecordingDeploymentController(), new RecordingDecisionPublisher());

    assertThat(runPromise(() -> analyzer.evaluate("deploy-5", config())).type())
        .isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD);
    assertThat(runPromise(() -> analyzer.evaluate("deploy-6", config())).type())
        .isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD);
  }

  @Test
  @DisplayName("evaluate holds when AI response is malformed")
  void evaluateHoldsWhenAiResponseIsMalformed() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    CanaryAnalyzer analyzer =
        analyzer(metrics(0.01, 120, 0.99, 120), new RecordingDeploymentController(), new RecordingDecisionPublisher());

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-7", config()));

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD);
    assertThat(decision.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("evaluate defaults blank AI decision fields to hold with generated rationale")
  void evaluateDefaultsBlankAiDecisionFieldsToHoldWithGeneratedRationale() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("{\"decision\":\"\",\"rationale\":\"\",\"confidence\":0.4}"));

    CanaryAnalyzer analyzer =
        analyzer(metrics(0.01, 120, 0.99, 120), new RecordingDeploymentController(), new RecordingDecisionPublisher());

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-8", config()));

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD);
    assertThat(decision.rationale()).isEqualTo("AI generated canary recommendation.");
    assertThat(decision.aiGenerated()).isTrue();
  }

  @Test
  @DisplayName("canary records normalize invalid values")
  void canaryRecordsNormalizeInvalidValues() {
    CanaryAnalyzer.CanaryConfig config =
        new CanaryAnalyzer.CanaryConfig(-1.0, -5, 2.0, -2, null, null);
    CanaryAnalyzer.CanaryMetrics metrics = new CanaryAnalyzer.CanaryMetrics(-1.0, -10, 2.0, -4);
    CanaryAnalyzer.CanaryDecision decision = new CanaryAnalyzer.CanaryDecision(null, null, 2.0, true);

    assertThat(config.errorRateThreshold()).isZero();
    assertThat(config.latencyP99ThresholdMillis()).isEqualTo(1);
    assertThat(config.minimumSuccessRate()).isEqualTo(1.0);
    assertThat(config.minimumSampleSize()).isEqualTo(1);
    assertThat(config.targetEnvironment()).isEqualTo("production");

    assertThat(metrics.errorRate()).isZero();
    assertThat(metrics.latencyP99Millis()).isZero();
    assertThat(metrics.successRate()).isEqualTo(1.0);
    assertThat(metrics.sampleSize()).isZero();
    assertThat(metrics.summary()).contains("sampleSize=0");

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD);
    assertThat(decision.rationale()).isEmpty();
    assertThat(decision.confidence()).isEqualTo(1.0);
  }

  private CanaryAnalyzer analyzer(
      CanaryAnalyzer.CanaryMetrics metrics,
      RecordingDeploymentController controller,
      RecordingDecisionPublisher publisher) {
    return new CanaryAnalyzer(
        aiService,
        deploymentId -> Promise.of(metrics),
        controller,
        publisher);
  }

  private CanaryAnalyzer.CanaryMetrics metrics(
      double errorRate, long latencyP99Millis, double successRate, int sampleSize) {
    return new CanaryAnalyzer.CanaryMetrics(errorRate, latencyP99Millis, successRate, sampleSize);
  }

  private CanaryAnalyzer.CanaryConfig config() {
    return new CanaryAnalyzer.CanaryConfig(0.05, 500, 0.9, 25, "production", "v1.0.0");
  }

  private static final class RecordingDeploymentController
      implements CanaryAnalyzer.DeploymentController {
    private final List<String> actions = new ArrayList<>();

    @Override
    public Promise<Void> promote(String deploymentId, String targetEnvironment) {
      actions.add("promote:" + deploymentId + ":" + targetEnvironment);
      return Promise.complete();
    }

    @Override
    public Promise<Void> rollback(String deploymentId, String rollbackVersion, String rationale) {
      actions.add("rollback:" + deploymentId + ":" + rollbackVersion);
      return Promise.complete();
    }
  }

  private static final class RecordingDecisionPublisher implements CanaryAnalyzer.DecisionPublisher {
    private final List<CanaryAnalyzer.CanaryDecision> decisions = new ArrayList<>();

    @Override
    public Promise<Void> publish(
        String deploymentId,
        CanaryAnalyzer.CanaryDecision decision,
        CanaryAnalyzer.CanaryMetrics metrics) {
      decisions.add(decision);
      return Promise.complete();
    }
  }
}