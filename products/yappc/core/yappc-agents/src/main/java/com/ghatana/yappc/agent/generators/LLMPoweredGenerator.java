package com.ghatana.yappc.agent.generators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator.LLMConfig;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.prompts.AgentPromptTemplate;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for LLM-powered OutputGenerators that use prompt templates.
 *
 * <p>This generator:
 *
 * <ul>
 *   <li>Renders prompts using {@link AgentPromptTemplate} with context injection
 *   <li>Sends prompts to LLM via {@link LLMGenerator.LLMGateway}
 *   <li>Parses JSON responses into typed outputs
 *   <li>Tracks costs and performance metrics
 * </ul>
 *
 * <p><strong>Usage</strong>:
 *
 * <pre>{@code
 * LLMPoweredGenerator<IntakeInput, IntakeOutput> generator =
 *     new LLMPoweredGenerator<>(
 *         llmGateway,
 *         promptTemplate,
 *         (input, context) -> Map.of("requirements", input.requirements()),
 *         (json, mapper) -> parseIntakeOutput(json, mapper),
 *         "IntakeGenerator",
 *         "1.0.0"
 *     );
 * }</pre>
 *
 * @param <I> Input type (e.g., IntakeInput)
 * @param <O> Output type (e.g., IntakeOutput)
 * @doc.type class
 * @doc.purpose LLM-powered generator using prompt templates
 * @doc.layer product
 * @doc.pattern Generator
 */
public class LLMPoweredGenerator<I, O> implements OutputGenerator<StepRequest<I>, StepResult<O>> {

  private static final Logger log = LoggerFactory.getLogger(LLMPoweredGenerator.class);
  private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

  private final LLMGenerator.LLMGateway llmGateway;
  private final AgentPromptTemplate promptTemplate;
  private final BiFunction<I, AgentContext, Map<String, Object>> contextBuilder;
  private final BiFunction<JsonNode, ObjectMapper, O> responseParser;
  private final String generatorName;
  private final String version;
  private final LLMConfig llmConfig;

  /**
   * Creates an LLM-powered generator.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param promptTemplate Prompt template with variable placeholders
   * @param contextBuilder Function to build context map from input
   * @param responseParser Function to parse JSON response into output type
   * @param generatorName Generator name for metadata
   * @param version Generator version
   */
  public LLMPoweredGenerator(
      @NotNull LLMGenerator.LLMGateway llmGateway,
      @NotNull AgentPromptTemplate promptTemplate,
      @NotNull BiFunction<I, AgentContext, Map<String, Object>> contextBuilder,
      @NotNull BiFunction<JsonNode, ObjectMapper, O> responseParser,
      @NotNull String generatorName,
      @NotNull String version,
      @NotNull LLMConfig llmConfig) {
    this.llmGateway = llmGateway;
    this.promptTemplate = promptTemplate;
    this.contextBuilder = contextBuilder;
    this.responseParser = responseParser;
    this.generatorName = generatorName;
    this.version = version;
    this.llmConfig = llmConfig;
  }

  @Override
  public @NotNull Promise<StepResult<O>> generate(
      @NotNull StepRequest<I> request, @NotNull AgentContext context) {

    Instant start = Instant.now();

    try {
      // 1. Build context from input
      Map<String, Object> promptContext = contextBuilder.apply(request.input(), context);

      // 2. Render prompt template
      String prompt = promptTemplate.render(promptContext);

      log.info("Generated prompt ({} chars) for {}", prompt.length(), generatorName);
      log.debug("Prompt preview: {}...", prompt.substring(0, Math.min(200, prompt.length())));

      // 3. Send to LLM
      return llmGateway
          .complete(prompt, llmConfig, context)
          .map(
              response -> {
                Instant end = Instant.now();

                try {
                  // 4. Parse JSON response
                  String responseContent = response.getContent();
                  log.debug(
                      "LLM response: {}",
                      responseContent.substring(0, Math.min(500, responseContent.length())));

                  // Extract JSON from response (handle markdown code blocks)
                  String jsonStr = extractJson(responseContent);
                  JsonNode jsonNode = objectMapper.readTree(jsonStr);

                  // 5. Convert to typed output
                  O output = responseParser.apply(jsonNode, objectMapper);

                  // 6. Return success result with metrics
                  Map<String, Object> metrics =
                      Map.of(
                          "promptLength", prompt.length(),
                          "responseLength", responseContent.length(),
                          "durationMs", java.time.Duration.between(start, end).toMillis(),
                          "provider", "llm");

                  return StepResult.success(output, metrics, start, end);

                } catch (Exception e) {
                  log.error("Failed to parse LLM response: {}", e.getMessage(), e);
                  return StepResult.failed(
                      List.of("Failed to parse LLM response: " + e.getMessage()),
                      Map.of(),
                      start,
                      Instant.now());
                }
              });

    } catch (Exception e) {
      log.error("Failed to generate output: {}", e.getMessage(), e);
      return Promise.of(
          StepResult.failed(
              List.of("Failed to generate: " + e.getMessage()), Map.of(), start, Instant.now()));
    }
  }

  @Override
  public @NotNull Promise<Double> estimateCost(
      @NotNull StepRequest<I> request, @NotNull AgentContext context) {

    try {
      Map<String, Object> promptContext = contextBuilder.apply(request.input(), context);
      String prompt = promptTemplate.render(promptContext);

      // Estimate based on token count (rough approximation: 1 token ≈ 4 chars)
      int estimatedTokens = prompt.length() / 4;

      // Assume GPT-4 pricing: $0.03 per 1K input tokens, $0.06 per 1K output tokens
      // Estimate 2x output tokens as input tokens
      double inputCost = (estimatedTokens / 1000.0) * 0.03;
      double outputCost = ((estimatedTokens * 2) / 1000.0) * 0.06;

      return Promise.of(inputCost + outputCost);

    } catch (Exception e) {
      log.error("Failed to estimate cost: {}", e.getMessage());
      return Promise.of(0.0);
    }
  }

  @Override
  public @NotNull GeneratorMetadata getMetadata() {
    return GeneratorMetadata.builder()
        .name(generatorName)
        .type("llm-powered")
        .description("Uses " + promptTemplate.getAgentName() + " prompt template")
        .version(version)
        .build();
  }

  /**
   * Extracts JSON from LLM response, handling markdown code blocks.
   *
   * <p>LLMs often wrap JSON in ```json ... ``` blocks.
   *
   * @param response Raw LLM response
   * @return Extracted JSON string
   */
  private String extractJson(String response) {
    // Remove markdown code blocks if present
    String cleaned = response.trim();

    if (cleaned.startsWith("```json")) {
      cleaned = cleaned.substring(7); // Remove ```json
    } else if (cleaned.startsWith("```")) {
      cleaned = cleaned.substring(3); // Remove ```
    }

    if (cleaned.endsWith("```")) {
      cleaned = cleaned.substring(0, cleaned.length() - 3); // Remove trailing ```
    }

    return cleaned.trim();
  }

  /**
   * Builder for creating LLM-powered generators with fluent API.
   *
   * @param <I> Input type
   * @param <O> Output type
   */
  public static class Builder<I, O> {
    private LLMGenerator.LLMGateway llmGateway;
    private AgentPromptTemplate promptTemplate;
    private BiFunction<I, AgentContext, Map<String, Object>> contextBuilder;
    private BiFunction<JsonNode, ObjectMapper, O> responseParser;
    private String generatorName;
    private String version = "1.0.0";
    private LLMConfig llmConfig;

    public Builder<I, O> llmGateway(LLMGenerator.LLMGateway gateway) {
      this.llmGateway = gateway;
      return this;
    }

    public Builder<I, O> promptTemplate(AgentPromptTemplate template) {
      this.promptTemplate = template;
      return this;
    }

    public Builder<I, O> contextBuilder(BiFunction<I, AgentContext, Map<String, Object>> builder) {
      this.contextBuilder = builder;
      return this;
    }

    public Builder<I, O> responseParser(BiFunction<JsonNode, ObjectMapper, O> parser) {
      this.responseParser = parser;
      return this;
    }

    public Builder<I, O> name(String name) {
      this.generatorName = name;
      return this;
    }

    public Builder<I, O> version(String version) {
      this.version = version;
      return this;
    }

    public Builder<I, O> llmConfig(LLMConfig config) {
      this.llmConfig = config;
      return this;
    }

    public LLMPoweredGenerator<I, O> build() {
      if (llmGateway == null
          || promptTemplate == null
          || contextBuilder == null
          || responseParser == null
          || generatorName == null
          || llmConfig == null) {
        throw new IllegalStateException(
            "All required fields must be set (llmGateway, promptTemplate, contextBuilder, responseParser, generatorName, llmConfig)");
      }
      return new LLMPoweredGenerator<>(
          llmGateway,
          promptTemplate,
          contextBuilder,
          responseParser,
          generatorName,
          version,
          llmConfig);
    }
  }

  public static <I, O> Builder<I, O> builder() {
    return new Builder<>();
  }
}
