package com.ghatana.yappc.agent.examples;

import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.yappc.agent.prompts.AgentPromptTemplate;
import com.ghatana.yappc.agent.prompts.YAPPCPromptTemplates;
import io.activej.eventloop.Eventloop;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating how to integrate agent prompt templates with the production LLM gateway.
 *
 * <p>This example shows three integration patterns:
 *
 * <ol>
 *   <li>Basic prompt rendering with context
 *   <li>Multi-agent workflow with specialized prompts
 *   <li>Environment-aware configuration (dev vs prod models)
 * </ol>
 *
 * <p><strong>Note</strong>: This example requires valid API keys:
 *
 * <ul>
 *   <li>{@code OPENAI_API_KEY} for OpenAI models
 *   <li>{@code ANTHROPIC_API_KEY} for Anthropic models
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Demonstrate prompt template integration with LLM gateway
 * @doc.layer product
 * @doc.pattern Example
 */
public class PromptIntegrationExample {

  private static final Logger log = LoggerFactory.getLogger(PromptIntegrationExample.class);

  public static void main(String[] args) {
    Eventloop eventloop = Eventloop.builder().build();
    MetricsCollector metrics = new NoopMetricsCollector();

    log.info("=== Prompt Integration Examples ===\n");

    example1PromptRendering();
    // example2MultiAgentWorkflow(eventloop, metrics);
    // example3EnvironmentAwareConfig(eventloop, metrics);

    log.info("\n=== Examples Complete ===");
  }

  /**
   * Example 1: Basic prompt rendering with context.
   *
   * <p>Demonstrates:
   *
   * <ul>
   *   <li>Retrieving prompt templates from YAPPCPromptTemplates
   *   <li>Variable substitution with context map
   *   <li>Accessing different agent phases
   * </ul>
   */
  private static void example1PromptRendering() {
    log.info("--- Example 1: Prompt Rendering ---");

    // Get intake specialist prompt
    AgentPromptTemplate intakeTemplate = YAPPCPromptTemplates.get("architecture.intake");

    // Prepare context
    Map<String, Object> context =
        Map.of(
            "requirements",
            "Build a REST API for user management with CRUD operations. "
                + "Must support authentication with JWT tokens. "
                + "Expected load: 1000 concurrent users.",
            "constraints",
            "Must use Java 21, Spring Boot 3.2, PostgreSQL. Deploy to AWS EKS.");

    // Render prompt
    String prompt = intakeTemplate.render(context);

    log.info(
        "Intake Prompt ({}  chars):\n{}\n",
        prompt.length(),
        prompt.substring(0, Math.min(500, prompt.length())) + "...");

    // Show different phases
    AgentPromptTemplate designTemplate = YAPPCPromptTemplates.get("architecture.design");
    AgentPromptTemplate implementTemplate =
        YAPPCPromptTemplates.get("implementation.detailed_implement");
    AgentPromptTemplate testTemplate = YAPPCPromptTemplates.get("testing.unit_test");
    AgentPromptTemplate deployTemplate = YAPPCPromptTemplates.get("operations.deploy");

    log.info("Available agent prompts:");
    log.info(
        "  Architecture: intake, design, plan_units, scaffold, build, review, hitl_review, deploy, monitor, canary, publish");
    log.info(
        "  Implementation: detailed_implement, unit_testing, integration, code_review, refactor, document, optimize, security_scan");
    log.info("  Testing: unit_test, integration_test, e2e_test, performance_test");
    log.info("  Operations: deploy, monitor, incident, rollback");
  }

  /**
   * Example 2: Multi-agent workflow with specialized prompts.
   *
   * <p>Shows how to orchestrate multiple agents with their specialized prompts in a complete SDLC
   * workflow: Intake → Design → Implementation → Testing → Deployment.
   *
   * <p><strong>Requires</strong>: Valid API keys in environment variables.
   */
  @SuppressWarnings("unused")
  private static void example2MultiAgentWorkflow(Eventloop eventloop, MetricsCollector metrics) {
    log.info("\n--- Example 2: Multi-Agent Workflow ---");

    // NOTE: This example shows the API pattern.
    // Uncomment and provide real API keys to execute actual LLM calls.

    /*
    String openaiKey = System.getenv("OPENAI_API_KEY");
    String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

    if (openaiKey == null || anthropicKey == null) {
        log.warn("Skipping Example 2: Missing API keys");
        log.info("Set OPENAI_API_KEY and ANTHROPIC_API_KEY environment variables");
        return;
    }

    // Build multi-provider LLM gateway
    LLMGenerator.LLMGateway gateway = ProductionLLMGatewayBuilder
            .create(eventloop, metrics)
            .addOpenAI(httpClient, openaiKey, "gpt-4")
            .addAnthropic(httpClient, anthropicKey, "claude-3-opus-20240229")
            .primaryProvider("openai")
            .fallbackOrder(List.of("openai", "anthropic"))
            .build();

    // Step 1: Intake - Extract requirements
    AgentPromptTemplate intakeTemplate = YAPPCPromptTemplates.get("architecture.intake");
    Map<String, Object> intakeContext = Map.of(
            "requirements", "E-commerce platform with user auth, product catalog, shopping cart, checkout"
    );
    String intakePrompt = intakeTemplate.render(intakeContext);
    // String intakeResult = gateway.complete(intakePrompt); // Send to LLM

    log.info("Step 1: Intake specialist extracts structured requirements");

    // Step 2: Design - Architecture design
    AgentPromptTemplate designTemplate = YAPPCPromptTemplates.get("architecture.design");
    Map<String, Object> designContext = Map.of(
            "requirements", "intakeResult", // Use output from step 1
            "constraints", "Microservices, event-driven, AWS"
    );
    String designPrompt = designTemplate.render(designContext);
    // String designResult = gateway.complete(designPrompt);

    log.info("Step 2: Design specialist creates architecture");

    // Step 3: Implementation - Generate code
    AgentPromptTemplate implTemplate = YAPPCPromptTemplates.get("implementation.detailed_implement");
    Map<String, Object> implContext = Map.of(
            "unitName", "UserService",
            "unitType", "service",
            "dependencies", "UserRepository, AuthService",
            "specification", "designResult" // Use output from step 2
    );
    String implPrompt = implTemplate.render(implContext);
    // String implResult = gateway.complete(implPrompt);

    log.info("Step 3: Implementation specialist generates code");

    // Step 4: Testing - Generate tests
    AgentPromptTemplate testTemplate = YAPPCPromptTemplates.get("testing.unit_test");
    Map<String, Object> testContext = Map.of(
            "component", "implResult" // Use output from step 3
    );
    String testPrompt = testTemplate.render(testContext);
    // String testResult = gateway.complete(testPrompt);

    log.info("Step 4: Test specialist generates unit tests");

    // Step 5: Deployment - Create deployment artifacts
    AgentPromptTemplate deployTemplate = YAPPCPromptTemplates.get("operations.deploy");
    Map<String, Object> deployContext = Map.of(
            "application", "UserService",
            "environment", "staging",
            "strategy", "blue-green"
    );
    String deployPrompt = deployTemplate.render(deployContext);
    // String deployResult = gateway.complete(deployPrompt);

    log.info("Step 5: Ops specialist creates deployment manifests");

    log.info("\n✓ Complete SDLC workflow with 5 specialized agents");
    */

    log.info("Pattern shown above (uncomment and add API keys to execute)");
  }

  /**
   * Example 3: Environment-aware configuration.
   *
   * <p>Shows how to configure different models for dev vs prod:
   *
   * <ul>
   *   <li><strong>Development</strong>: Faster, cheaper models (gpt-3.5-turbo, claude-3-haiku)
   *   <li><strong>Production</strong>: More capable models (gpt-4, claude-3-opus)
   * </ul>
   */
  @SuppressWarnings("unused")
  private static void example3EnvironmentAwareConfig(
      Eventloop eventloop, MetricsCollector metrics) {
    log.info("\n--- Example 3: Environment-Aware Config ---");

    String environment = System.getenv("ENVIRONMENT"); // "development" | "production"
    boolean isProduction = "production".equalsIgnoreCase(environment);

    log.info(
        "Environment: {} (isProduction={})",
        environment != null ? environment : "development",
        isProduction);

    // NOTE: This example shows the API pattern.
    // Uncomment and provide real API keys to execute actual LLM calls.

    /*
    String openaiKey = System.getenv("OPENAI_API_KEY");
    String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

    if (openaiKey == null || anthropicKey == null) {
        log.warn("Skipping Example 3: Missing API keys");
        return;
    }

    // Select models based on environment
    String openaiModel = isProduction ? "gpt-4" : "gpt-3.5-turbo";
    String anthropicModel = isProduction ? "claude-3-opus-20240229" : "claude-3-haiku-20240307";

    log.info("Selected models:");
    log.info("  OpenAI: {}", openaiModel);
    log.info("  Anthropic: {}", anthropicModel);

    LLMGenerator.LLMGateway gateway = ProductionLLMGatewayBuilder
            .create(eventloop, metrics)
            .addOpenAI(httpClient, openaiKey, openaiModel)
            .addAnthropic(httpClient, anthropicKey, anthropicModel)
            .primaryProvider(isProduction ? "openai" : "anthropic") // Cost optimization
            .fallbackOrder(List.of("anthropic", "openai"))
            .build();

    log.info("Gateway configured with environment-specific models");

    // Use with any agent prompt
    AgentPromptTemplate template = YAPPCPromptTemplates.get("architecture.intake");
    Map<String, Object> context = Map.of("requirements", "Sample request");
    String prompt = template.render(context);
    // String result = gateway.complete(prompt);

    log.info("✓ Same prompt works with different models per environment");
    */

    log.info("Pattern shown above (uncomment and add API keys to execute)");
  }

  /**
   * Helper: Create environment-aware gateway configuration.
   *
   * <p>This pattern allows teams to:
   *
   * <ul>
   *   <li>Use cheaper models during development and testing
   *   <li>Use more capable models in production
   *   <li>Configure fallback strategies per environment
   * </ul>
   *
   * @param eventloop Event loop for async operations
   * @param metrics Metrics collector
   * @param environment Environment name ("development" | "production")
   * @return Configured LLM gateway (commented out - requires API keys)
   */
  @SuppressWarnings("unused")
  private static LLMGenerator.LLMGateway createEnvironmentAwareGateway(
      Eventloop eventloop, MetricsCollector metrics, String environment) {

    // NOTE: Uncomment and provide real implementation
    throw new UnsupportedOperationException("Requires API keys - see example2 and example3");

    /*
    boolean isProduction = "production".equalsIgnoreCase(environment);

    String openaiKey = System.getenv("OPENAI_API_KEY");
    String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

    String openaiModel = isProduction ? "gpt-4" : "gpt-3.5-turbo";
    String anthropicModel = isProduction ? "claude-3-opus-20240229" : "claude-3-haiku-20240307";

    return ProductionLLMGatewayBuilder
            .create(eventloop, metrics)
            .addOpenAI(httpClient, openaiKey, openaiModel)
            .addAnthropic(httpClient, anthropicKey, anthropicModel)
            .primaryProvider(isProduction ? "openai" : "anthropic")
            .fallbackOrder(isProduction ?
                    List.of("openai", "anthropic") :
                    List.of("anthropic", "openai")
            )
            .build();
    */
  }
}
