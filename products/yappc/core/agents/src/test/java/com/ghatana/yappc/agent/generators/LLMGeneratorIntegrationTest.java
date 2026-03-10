package com.ghatana.yappc.agent.generators;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.specialists.*;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.Socket;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for LLM-powered generators.
 *
 * <p>Tests verify:
 *
 * <ul>
 *   <li>Generator creation and configuration
 *   <li>All 12 generators can be instantiated
 *   <li>Ollama availability detection
 * </ul>
 *
 * <p>To test with real Ollama:
 *
 * <pre>{@code
 * # Start Ollama
 * ollama serve
 *
 * # Pull model (one-time)
 * ollama pull llama3
 *
 * # Run manual end-to-end tests
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Integration tests for LLM generators
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("LLM Generator Integration Tests")
class LLMGeneratorIntegrationTest extends EventloopTestBase {
  private static final Logger log = LoggerFactory.getLogger(LLMGeneratorIntegrationTest.class);

  private static MetricsCollector metrics;
  private static boolean ollamaAvailable;

  @BeforeAll
  static void setUpClass() {
    // Create metrics collector
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    metrics = MetricsCollectorFactory.create(registry);

    // Check if Ollama is available
    ollamaAvailable = isOllamaRunning();

    if (ollamaAvailable) {
      log.info("\u2713 Ollama detected at http://localhost:11434");
    } else {
      log.info("\u26a0 Ollama not available - use 'ollama serve && ollama pull llama3' to enable");
    }
  }

  @Test
  @DisplayName("Should create IntakeGenerator with mock gateway")
  void shouldCreateIntakeGenerator() {
    // GIVEN
    LLMGenerator.LLMGateway mockGateway = mock(LLMGenerator.LLMGateway.class);
    LLMGenerator.LLMResponse mockResponse = mock(LLMGenerator.LLMResponse.class);
    when(mockResponse.getContent())
        .thenReturn("{\"functionalRequirements\": [], \"nonFunctionalRequirements\": []}");
    when(mockGateway.complete(anyString(), any(), any())).thenReturn(Promise.of(mockResponse));

    LLMGenerator.LLMConfig llmConfig =
        LLMGenerator.LLMConfig.builder().model("llama3").temperature(0.7).maxTokens(4000).build();

    // WHEN
    OutputGenerator<StepRequest<IntakeInput>, StepResult<IntakeOutput>> generator =
        LLMGeneratorFactory.createIntakeGenerator(mockGateway, llmConfig);

    // THEN
    assertThat(generator).isNotNull();
    assertThat(generator).isInstanceOf(LLMPoweredGenerator.class);
    log.info("\u2713 IntakeGenerator created successfully");
  }

  @Test
  @DisplayName("Should create all 12 LLM generators")
  void shouldCreateAllGenerators() {
    // GIVEN
    LLMGenerator.LLMGateway mockGateway = mock(LLMGenerator.LLMGateway.class);
    LLMGenerator.LLMResponse mockResponse = mock(LLMGenerator.LLMResponse.class);
    when(mockResponse.getContent()).thenReturn("{}");
    when(mockGateway.complete(anyString(), any(), any())).thenReturn(Promise.of(mockResponse));

    LLMGenerator.LLMConfig llmConfig =
        LLMGenerator.LLMConfig.builder().model("llama3").temperature(0.7).maxTokens(4000).build();

    // WHEN - Create all 12 generators
    var intake = LLMGeneratorFactory.createIntakeGenerator(mockGateway, llmConfig);
    var design = LLMGeneratorFactory.createDesignGenerator(mockGateway, llmConfig);
    var scaffold = LLMGeneratorFactory.createScaffoldGenerator(mockGateway, llmConfig);
    var planUnits = LLMGeneratorFactory.createPlanUnitsGenerator(mockGateway, llmConfig);
    var implement = LLMGeneratorFactory.createImplementGenerator(mockGateway, llmConfig);
    var review = LLMGeneratorFactory.createReviewGenerator(mockGateway, llmConfig);
    var build = LLMGeneratorFactory.createBuildGenerator(mockGateway, llmConfig);
    var genTests = LLMGeneratorFactory.createGenerateTestsGenerator(mockGateway, llmConfig);
    var deployStaging = LLMGeneratorFactory.createDeployStagingGenerator(mockGateway, llmConfig);
    var monitor = LLMGeneratorFactory.createMonitorGenerator(mockGateway, llmConfig);
    var incident = LLMGeneratorFactory.createIncidentResponseGenerator(mockGateway, llmConfig);
    var canary = LLMGeneratorFactory.createCanaryGenerator(mockGateway, llmConfig);

    // THEN - All generators created successfully
    assertThat(intake).isNotNull();
    assertThat(design).isNotNull();
    assertThat(scaffold).isNotNull();
    assertThat(planUnits).isNotNull();
    assertThat(implement).isNotNull();
    assertThat(review).isNotNull();
    assertThat(build).isNotNull();
    assertThat(genTests).isNotNull();
    assertThat(deployStaging).isNotNull();
    assertThat(monitor).isNotNull();
    assertThat(incident).isNotNull();
    assertThat(canary).isNotNull();

    log.info("\u2713 All 12 LLM-powered generators created successfully");
  }

  @Test
  @DisplayName("Ollama availability status")
  void ollamaAvailabilityStatus() {
    log.info("Ollama running: {}", ollamaAvailable);
    if (ollamaAvailable) {
      log.info("\u2713 Ready for manual end-to-end LLM tests");
      log.info("  Model: llama3 (local, cost-free)");
      log.info("  Endpoint: http://localhost:11434");
    } else {
      log.info("\u26a0 To enable: ollama serve && ollama pull llama3");
    }
  }

  // Helper methods

  private static boolean isOllamaRunning() {
    try (Socket socket = new Socket("localhost", 11434)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
