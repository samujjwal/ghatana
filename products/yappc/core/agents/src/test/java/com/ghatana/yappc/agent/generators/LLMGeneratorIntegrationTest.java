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
import com.ghatana.yappc.agents.code.*;
import com.ghatana.yappc.agents.architecture.*;
import com.ghatana.yappc.agents.testing.*;
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
 * # Pull model (one-time) // GH-90000
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
@DisplayName("LLM Generator Integration Tests [GH-90000]")
class LLMGeneratorIntegrationTest extends EventloopTestBase {
  private static final Logger log = LoggerFactory.getLogger(LLMGeneratorIntegrationTest.class); // GH-90000

  private static MetricsCollector metrics;
  private static boolean ollamaAvailable;

  @BeforeAll
  static void setUpClass() { // GH-90000
    // Create metrics collector
    SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
    metrics = MetricsCollectorFactory.create(registry); // GH-90000

    // Check if Ollama is available
    ollamaAvailable = isOllamaRunning(); // GH-90000

    if (ollamaAvailable) { // GH-90000
      log.info("\u2713 Ollama detected at http://localhost:11434 [GH-90000]");
    } else {
      log.info("\u26a0 Ollama not available - use 'ollama serve && ollama pull llama3' to enable [GH-90000]");
    }
  }

  @Test
  @DisplayName("Should create IntakeGenerator with mock gateway [GH-90000]")
  void shouldCreateIntakeGenerator() { // GH-90000
    // GIVEN
    LLMGenerator.LLMGateway mockGateway = mock(LLMGenerator.LLMGateway.class); // GH-90000
    LLMGenerator.LLMResponse mockResponse = mock(LLMGenerator.LLMResponse.class); // GH-90000
    when(mockResponse.getContent()) // GH-90000
        .thenReturn("{\"functionalRequirements\": [], \"nonFunctionalRequirements\": []}"); // GH-90000
    when(mockGateway.complete(anyString(), any(), any())).thenReturn(Promise.of(mockResponse)); // GH-90000

    LLMGenerator.LLMConfig llmConfig =
        LLMGenerator.LLMConfig.builder().model("llama3 [GH-90000]").temperature(0.7).maxTokens(4000).build();

    // WHEN
    OutputGenerator<StepRequest<IntakeInput>, StepResult<IntakeOutput>> generator =
        LLMGeneratorFactory.createIntakeGenerator(mockGateway, llmConfig); // GH-90000

    // THEN
    assertThat(generator).isNotNull(); // GH-90000
    assertThat(generator).isInstanceOf(LLMPoweredGenerator.class); // GH-90000
    log.info("\u2713 IntakeGenerator created successfully [GH-90000]");
  }

  @Test
  @DisplayName("Should create all 12 LLM generators [GH-90000]")
  void shouldCreateAllGenerators() { // GH-90000
    // GIVEN
    LLMGenerator.LLMGateway mockGateway = mock(LLMGenerator.LLMGateway.class); // GH-90000
    LLMGenerator.LLMResponse mockResponse = mock(LLMGenerator.LLMResponse.class); // GH-90000
    when(mockResponse.getContent()).thenReturn("{} [GH-90000]");
    when(mockGateway.complete(anyString(), any(), any())).thenReturn(Promise.of(mockResponse)); // GH-90000

    LLMGenerator.LLMConfig llmConfig =
        LLMGenerator.LLMConfig.builder().model("llama3 [GH-90000]").temperature(0.7).maxTokens(4000).build();

    // WHEN - Create all 12 generators
    var intake = LLMGeneratorFactory.createIntakeGenerator(mockGateway, llmConfig); // GH-90000
    var design = LLMGeneratorFactory.createDesignGenerator(mockGateway, llmConfig); // GH-90000
    var scaffold = LLMGeneratorFactory.createScaffoldGenerator(mockGateway, llmConfig); // GH-90000
    var planUnits = LLMGeneratorFactory.createPlanUnitsGenerator(mockGateway, llmConfig); // GH-90000
    var implement = LLMGeneratorFactory.createImplementGenerator(mockGateway, llmConfig); // GH-90000
    var review = LLMGeneratorFactory.createReviewGenerator(mockGateway, llmConfig); // GH-90000
    var build = LLMGeneratorFactory.createBuildGenerator(mockGateway, llmConfig); // GH-90000
    var genTests = LLMGeneratorFactory.createGenerateTestsGenerator(mockGateway, llmConfig); // GH-90000
    var deployStaging = LLMGeneratorFactory.createDeployStagingGenerator(mockGateway, llmConfig); // GH-90000
    var monitor = LLMGeneratorFactory.createMonitorGenerator(mockGateway, llmConfig); // GH-90000
    var incident = LLMGeneratorFactory.createIncidentResponseGenerator(mockGateway, llmConfig); // GH-90000
    var canary = LLMGeneratorFactory.createCanaryGenerator(mockGateway, llmConfig); // GH-90000

    // THEN - All generators created successfully
    assertThat(intake).isNotNull(); // GH-90000
    assertThat(design).isNotNull(); // GH-90000
    assertThat(scaffold).isNotNull(); // GH-90000
    assertThat(planUnits).isNotNull(); // GH-90000
    assertThat(implement).isNotNull(); // GH-90000
    assertThat(review).isNotNull(); // GH-90000
    assertThat(build).isNotNull(); // GH-90000
    assertThat(genTests).isNotNull(); // GH-90000
    assertThat(deployStaging).isNotNull(); // GH-90000
    assertThat(monitor).isNotNull(); // GH-90000
    assertThat(incident).isNotNull(); // GH-90000
    assertThat(canary).isNotNull(); // GH-90000

    log.info("\u2713 All 12 LLM-powered generators created successfully [GH-90000]");
  }

  @Test
  @DisplayName("Ollama availability status [GH-90000]")
  void ollamaAvailabilityStatus() { // GH-90000
    log.info("Ollama running: {}", ollamaAvailable); // GH-90000
    if (ollamaAvailable) { // GH-90000
      log.info("\u2713 Ready for manual end-to-end LLM tests [GH-90000]");
      log.info("  Model: llama3 (local, cost-free) [GH-90000]");
      log.info("  Endpoint: http://localhost:11434 [GH-90000]");
    } else {
      log.info("\u26a0 To enable: ollama serve && ollama pull llama3 [GH-90000]");
    }
  }

  // Helper methods

  private static boolean isOllamaRunning() { // GH-90000
    try (Socket socket = new Socket("localhost", 11434)) { // GH-90000
      return true;
    } catch (IOException e) { // GH-90000
      return false;
    }
  }
}
