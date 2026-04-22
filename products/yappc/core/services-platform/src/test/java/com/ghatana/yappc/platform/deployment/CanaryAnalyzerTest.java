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

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("CanaryAnalyzer Tests [GH-90000]")
class CanaryAnalyzerTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("evaluate holds when the sample size is too small [GH-90000]")
  void evaluateHoldsWhenSampleSizeIsTooSmall() { // GH-90000
    RecordingDeploymentController controller = new RecordingDeploymentController(); // GH-90000
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher(); // GH-90000
    CanaryAnalyzer analyzer = analyzer(metrics(0.01, 120, 0.99, 5), controller, publisher); // GH-90000

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-1", config())); // GH-90000

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD); // GH-90000
    assertThat(publisher.decisions).containsExactly(decision); // GH-90000
    assertThat(controller.actions).isEmpty(); // GH-90000
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("evaluate rolls back when error rate exceeds threshold [GH-90000]")
  void evaluateRollsBackWhenErrorRateExceedsThreshold() { // GH-90000
    RecordingDeploymentController controller = new RecordingDeploymentController(); // GH-90000
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher(); // GH-90000
    CanaryAnalyzer analyzer = analyzer(metrics(0.08, 120, 0.95, 120), controller, publisher); // GH-90000

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-1", config())); // GH-90000

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.ROLLBACK); // GH-90000
    assertThat(controller.actions).containsExactly("rollback:deploy-1:v1.0.0 [GH-90000]");
    assertThat(decision.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("evaluate rolls back when latency exceeds threshold [GH-90000]")
  void evaluateRollsBackWhenLatencyExceedsThreshold() { // GH-90000
    RecordingDeploymentController controller = new RecordingDeploymentController(); // GH-90000
    CanaryAnalyzer analyzer = analyzer(metrics(0.01, 900, 0.95, 120), controller, new RecordingDecisionPublisher()); // GH-90000

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-2", config())); // GH-90000

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.ROLLBACK); // GH-90000
    assertThat(controller.actions).containsExactly("rollback:deploy-2:v1.0.0 [GH-90000]");
  }

  @Test
  @DisplayName("evaluate rolls back when success rate drops below threshold [GH-90000]")
  void evaluateRollsBackWhenSuccessRateDropsBelowThreshold() { // GH-90000
    RecordingDeploymentController controller = new RecordingDeploymentController(); // GH-90000
    CanaryAnalyzer analyzer = analyzer(metrics(0.01, 150, 0.85, 120), controller, new RecordingDecisionPublisher()); // GH-90000

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-3", config())); // GH-90000

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.ROLLBACK); // GH-90000
    assertThat(controller.actions).containsExactly("rollback:deploy-3:v1.0.0 [GH-90000]");
  }

  @Test
  @DisplayName("evaluate promotes when AI recommends promotion [GH-90000]")
  void evaluatePromotesWhenAiRecommendsPromotion() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("{\"decision\":\"PROMOTE\",\"rationale\":\"Metrics are stable\",\"confidence\":0.91}")); // GH-90000

    RecordingDeploymentController controller = new RecordingDeploymentController(); // GH-90000
    RecordingDecisionPublisher publisher = new RecordingDecisionPublisher(); // GH-90000
    CanaryAnalyzer analyzer = analyzer(metrics(0.01, 120, 0.99, 120), controller, publisher); // GH-90000

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-4", config())); // GH-90000

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.PROMOTE); // GH-90000
    assertThat(decision.aiGenerated()).isTrue(); // GH-90000
    assertThat(controller.actions).containsExactly("promote:deploy-4:production [GH-90000]");
    assertThat(publisher.decisions).containsExactly(decision); // GH-90000
  }

  @Test
  @DisplayName("evaluate holds when AI response is blank or null [GH-90000]")
  void evaluateHoldsWhenAiResponseIsBlankOrNull() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
      .thenReturn(Promise.of("  [GH-90000]"))
      .thenReturn(Promise.of(null)); // GH-90000

    CanaryAnalyzer analyzer =
        analyzer(metrics(0.01, 120, 0.99, 120), new RecordingDeploymentController(), new RecordingDecisionPublisher()); // GH-90000

    assertThat(runPromise(() -> analyzer.evaluate("deploy-5", config())).type()) // GH-90000
        .isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD); // GH-90000
    assertThat(runPromise(() -> analyzer.evaluate("deploy-6", config())).type()) // GH-90000
        .isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD); // GH-90000
  }

  @Test
  @DisplayName("evaluate holds when AI response is malformed [GH-90000]")
  void evaluateHoldsWhenAiResponseIsMalformed() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json [GH-90000]"));

    CanaryAnalyzer analyzer =
        analyzer(metrics(0.01, 120, 0.99, 120), new RecordingDeploymentController(), new RecordingDecisionPublisher()); // GH-90000

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-7", config())); // GH-90000

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD); // GH-90000
    assertThat(decision.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("evaluate defaults blank AI decision fields to hold with generated rationale [GH-90000]")
  void evaluateDefaultsBlankAiDecisionFieldsToHoldWithGeneratedRationale() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("{\"decision\":\"\",\"rationale\":\"\",\"confidence\":0.4}")); // GH-90000

    CanaryAnalyzer analyzer =
        analyzer(metrics(0.01, 120, 0.99, 120), new RecordingDeploymentController(), new RecordingDecisionPublisher()); // GH-90000

    CanaryAnalyzer.CanaryDecision decision =
        runPromise(() -> analyzer.evaluate("deploy-8", config())); // GH-90000

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD); // GH-90000
    assertThat(decision.rationale()).isEqualTo("AI generated canary recommendation. [GH-90000]");
    assertThat(decision.aiGenerated()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("canary records normalize invalid values [GH-90000]")
  void canaryRecordsNormalizeInvalidValues() { // GH-90000
    CanaryAnalyzer.CanaryConfig config =
        new CanaryAnalyzer.CanaryConfig(-1.0, -5, 2.0, -2, null, null); // GH-90000
    CanaryAnalyzer.CanaryMetrics metrics = new CanaryAnalyzer.CanaryMetrics(-1.0, -10, 2.0, -4); // GH-90000
    CanaryAnalyzer.CanaryDecision decision = new CanaryAnalyzer.CanaryDecision(null, null, 2.0, true); // GH-90000

    assertThat(config.errorRateThreshold()).isZero(); // GH-90000
    assertThat(config.latencyP99ThresholdMillis()).isEqualTo(1); // GH-90000
    assertThat(config.minimumSuccessRate()).isEqualTo(1.0); // GH-90000
    assertThat(config.minimumSampleSize()).isEqualTo(1); // GH-90000
    assertThat(config.targetEnvironment()).isEqualTo("production [GH-90000]");

    assertThat(metrics.errorRate()).isZero(); // GH-90000
    assertThat(metrics.latencyP99Millis()).isZero(); // GH-90000
    assertThat(metrics.successRate()).isEqualTo(1.0); // GH-90000
    assertThat(metrics.sampleSize()).isZero(); // GH-90000
    assertThat(metrics.summary()).contains("sampleSize=0 [GH-90000]");

    assertThat(decision.type()).isEqualTo(CanaryAnalyzer.CanaryDecisionType.HOLD); // GH-90000
    assertThat(decision.rationale()).isEmpty(); // GH-90000
    assertThat(decision.confidence()).isEqualTo(1.0); // GH-90000
  }

  private CanaryAnalyzer analyzer( // GH-90000
      CanaryAnalyzer.CanaryMetrics metrics,
      RecordingDeploymentController controller,
      RecordingDecisionPublisher publisher) {
    return new CanaryAnalyzer( // GH-90000
        aiService,
        deploymentId -> Promise.of(metrics), // GH-90000
        controller,
        publisher);
  }

  private CanaryAnalyzer.CanaryMetrics metrics( // GH-90000
      double errorRate, long latencyP99Millis, double successRate, int sampleSize) {
    return new CanaryAnalyzer.CanaryMetrics(errorRate, latencyP99Millis, successRate, sampleSize); // GH-90000
  }

  private CanaryAnalyzer.CanaryConfig config() { // GH-90000
    return new CanaryAnalyzer.CanaryConfig(0.05, 500, 0.9, 25, "production", "v1.0.0"); // GH-90000
  }

  private static final class RecordingDeploymentController
      implements CanaryAnalyzer.DeploymentController {
    private final List<String> actions = new ArrayList<>(); // GH-90000

    @Override
    public Promise<Void> promote(String deploymentId, String targetEnvironment) { // GH-90000
      actions.add("promote:" + deploymentId + ":" + targetEnvironment); // GH-90000
      return Promise.complete(); // GH-90000
    }

    @Override
    public Promise<Void> rollback(String deploymentId, String rollbackVersion, String rationale) { // GH-90000
      actions.add("rollback:" + deploymentId + ":" + rollbackVersion); // GH-90000
      return Promise.complete(); // GH-90000
    }
  }

  private static final class RecordingDecisionPublisher implements CanaryAnalyzer.DecisionPublisher {
    private final List<CanaryAnalyzer.CanaryDecision> decisions = new ArrayList<>(); // GH-90000

    @Override
    public Promise<Void> publish( // GH-90000
        String deploymentId,
        CanaryAnalyzer.CanaryDecision decision,
        CanaryAnalyzer.CanaryMetrics metrics) {
      decisions.add(decision); // GH-90000
      return Promise.complete(); // GH-90000
    }
  }
}
