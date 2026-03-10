package com.ghatana.yappc.agent.examples;

import com.ghatana.agent.framework.llm.ProductionLLMGatewayBuilder;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating production LLM gateway setup.
 *
 * <p>Shows three usage patterns:
 *
 * <ol>
 *   <li>Simple OpenAI setup with environment-based API key
 *   <li>OpenAI with custom configuration
 *   <li>Multi-provider setup with fallback
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Production LLM gateway usage examples
 * @doc.layer product
 * @doc.pattern Example
 */
public class ProductionLLMExample {

  private static final Logger log = LoggerFactory.getLogger(ProductionLLMExample.class);

  public static void main(String[] args) {
    log.info("Production LLM Gateway Examples");

    Eventloop eventloop = Eventloop.create();
    eventloop.submit(
        () -> {
          try {
            // Example 1: Simple OpenAI setup
            example1SimpleOpenAI(eventloop);

            // Example 2: OpenAI with custom config
            example2CustomOpenAI(eventloop);

            // Example 3: Multi-provider with fallback
            example3MultiProvider(eventloop);

            log.info("All examples completed successfully");
          } catch (Exception e) {
            log.error("Example failed", e);
          }
          return Promise.complete();
        });

    eventloop.run();
  }

  /**
   * Example 1: Simple OpenAI setup with environment-based API key.
   *
   * <p>This is the recommended approach for most use cases. It uses:
   *
   * <ul>
   *   <li>API key from OPENAI_API_KEY environment variable
   *   <li>Default model: gpt-4
   *   <li>Default temperature: 0.7
   *   <li>Default max tokens: 2000
   * </ul>
   */
  private static void example1SimpleOpenAI(Eventloop eventloop) {
    log.info("=== Example 1: Simple OpenAI Setup ===");

    // Check for API key
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isEmpty()) {
      log.warn("OPENAI_API_KEY not set, skipping OpenAI examples");
      log.info("To run this example, set: export OPENAI_API_KEY=sk-...");
      return;
    }

    // Note: HttpClient creation would happen in real application
    // HttpClient httpClient = HttpClient.builder(eventloop).build();
    // MetricsCollector metrics = MetricsCollectorFactory.create(registry);

    MetricsCollector metrics = new NoopMetricsCollector();

    // API Example (actual HTTP client creation omitted for simplicity):
    // LLMGenerator.LLMGateway gateway = ProductionLLMGatewayBuilder
    //         .withOpenAI(eventloop, httpClient, metrics)
    //         .apiKey(apiKey)
    //         .model("gpt-4")
    //         .build();

    log.info("Created simple OpenAI gateway with default config");
    log.info("  Model: gpt-4");
    log.info("  Temperature: 0.7");
    log.info("  Max Tokens: 2000");

    // Gateway is now ready to use with agents
    // Example: YAPPCAgentBootstrap.create().llmGateway(gateway).build();
  }

  /**
   * Example 2: OpenAI with custom configuration.
   *
   * <p>This example shows how to customize LLM parameters:
   *
   * <ul>
   *   <li>Model: gpt-4-turbo-preview (faster, cheaper)
   *   <li>Temperature: 0.3 (more deterministic)
   *   <li>Max tokens: 4000 (longer responses)
   * </ul>
   */
  private static void example2CustomOpenAI(Eventloop eventloop) {
    log.info("=== Example 2: Custom OpenAI Configuration ===");

    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isEmpty()) {
      log.warn("OPENAI_API_KEY not set, skipping example");
      return;
    }

    MetricsCollector metrics = new NoopMetricsCollector();

    // API Example:
    // LLMGenerator.LLMGateway gateway = ProductionLLMGatewayBuilder
    //         .withOpenAI(eventloop, httpClient, metrics)
    //         .apiKey(apiKey)
    //         .model("gpt-4-turbo-preview")  // Faster, cheaper model
    //         .temperature(0.3)               // More deterministic
    //         .maxTokens(4000)                // Longer responses
    //         .build();

    log.info("Created OpenAI gateway with custom config");
    log.info("  Model: gpt-4-turbo-preview");
    log.info("  Temperature: 0.3 (deterministic)");
    log.info("  Max Tokens: 4000");

    // Use case: Architecture agents benefit from longer, detailed responses
    // Use case: Code generation agents benefit from deterministic output
  }

  /**
   * Example 3: Multi-provider setup with fallback.
   *
   * <p>This example demonstrates:
   *
   * <ul>
   *   <li>Multiple LLM providers (OpenAI + Anthropic)
   *   <li>Primary provider selection
   *   <li>Automatic fallback on failure
   * </ul>
   *
   * <p><b>Note:</b> Requires both OPENAI_API_KEY and ANTHROPIC_API_KEY.
   */
  private static void example3MultiProvider(Eventloop eventloop) {
    log.info("=== Example 3: Multi-Provider with Fallback ===");

    String openaiKey = System.getenv("OPENAI_API_KEY");
    String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

    if (openaiKey == null || openaiKey.isEmpty()) {
      log.warn("OPENAI_API_KEY not set, skipping multi-provider example");
      return;
    }

    if (anthropicKey == null || anthropicKey.isEmpty()) {
      log.warn("ANTHROPIC_API_KEY not set, will use OpenAI only");
      example2CustomOpenAI(eventloop);
      return;
    }

    MetricsCollector metrics = new NoopMetricsCollector();

    // Multi-provider gateway example (HTTP client creation omitted):
    // LLMGenerator.LLMGateway gateway = ProductionLLMGatewayBuilder
    //         .create(eventloop, metrics)
    //         .addOpenAI(httpClient, openaiKey, "gpt-4")
    //         .addAnthropic(httpClient, anthropicKey, "claude-3-opus-20240229")
    //         .primaryProvider("openai")
    //         .fallbackOrder(List.of("openai", "anthropic"))
    //         .build();

    log.info("Created multi-provider gateway");
    log.info("  Primary: OpenAI (gpt-4)");
    log.info("  Fallback: Anthropic (claude-3-opus-20240229)");
    log.info("  Automatic failover on rate limits or errors");

    // Use case: High availability - if OpenAI is down, Anthropic takes over
    // Use case: Cost optimization - use cheaper provider as primary, expensive as fallback
    // Use case: Feature coverage - different models for different capabilities
  }

  /** Helper: Check if running in production environment. */
  private static boolean isProduction() {
    String env = System.getenv("ENVIRONMENT");
    return "production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env);
  }

  /** Helper: Get appropriate model for environment. */
  private static String getModelForEnvironment() {
    if (isProduction()) {
      return "gpt-4"; // Best quality for production
    } else {
      return "gpt-4-turbo-preview"; // Faster/cheaper for dev
    }
  }

  /**
   * Example: Environment-aware configuration.
   *
   * <p>This pattern allows different configs for dev vs prod:
   *
   * <ul>
   *   <li>Production: gpt-4 (best quality)
   *   <li>Development: gpt-4-turbo-preview (faster/cheaper)
   * </ul>
   */
  public static LLMGenerator.LLMGateway createEnvironmentAwareGateway(
      Eventloop eventloop, HttpClient httpClient, MetricsCollector metrics) {

    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isEmpty()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is required");
    }

    String model = getModelForEnvironment();
    double temperature = isProduction() ? 0.5 : 0.7;

    log.info("Creating environment-aware LLM gateway");
    log.info("  Environment: {}", isProduction() ? "PRODUCTION" : "DEVELOPMENT");
    log.info("  Model: {}", model);
    log.info("  Temperature: {}", temperature);

    return ProductionLLMGatewayBuilder.withOpenAI(eventloop, httpClient, metrics)
        .apiKey(apiKey)
        .model(model)
        .temperature(temperature)
        .build();
  }
}
