package com.ghatana.yappc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.ai.canvas.llm.LLMProvider;
import com.ghatana.yappc.ai.canvas.llm.LLMRequest;
import com.ghatana.yappc.ai.canvas.llm.LLMResponse;
import com.ghatana.yappc.framework.core.config.FeatureFlag;
import com.ghatana.yappc.framework.core.config.FeatureFlags;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialist agent for architecture intake step.
 *
 * <p>
 * Validates and structures incoming requirements for architecture phase.
 * Uses LLM for advanced NLP extraction when feature flag is enabled.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for requirements intake with LLM support
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class IntakeSpecialistAgent extends YAPPCAgentBase<IntakeInput, IntakeOutput> {

  private static final Logger log = LoggerFactory.getLogger(IntakeSpecialistAgent.class);

  private final MemoryStore memoryStore;
  private final LLMProvider llmProvider;

  /**
   * Construct with LLM provider for enhanced AI extraction.
   */
  public IntakeSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<IntakeInput>, StepResult<IntakeOutput>> generator,
      LLMProvider llmProvider) {
    super(
        "IntakeSpecialistAgent",
        "architecture.intake",
        new StepContract(
            "architecture.intake",
            "#/definitions/IntakeInput",
            "#/definitions/IntakeOutput",
            List.of("requirements", "validation"),
            Map.of("description", "Validates and structures requirements", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
    this.llmProvider = llmProvider;
  }

  /**
   * Construct without LLM provider (rule-based extraction only).
   */
  public IntakeSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<IntakeInput>, StepResult<IntakeOutput>> generator) {
    this(memoryStore, generator, null);
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull IntakeInput input) {
    if (input.requirements() == null || input.requirements().isEmpty()) {
      return ValidationResult.fail("Requirements cannot be empty");
    }
    if (input.requirements().length() < 10) {
      return ValidationResult.fail("Requirements too short - need more detail");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<IntakeInput> perceive(
      @NotNull StepRequest<IntakeInput> request, @NotNull AgentContext context) {

    IntakeInput input = request.input();
    log.info(
        "Perceiving requirements intake: {}",
        input.requirements().substring(0, Math.min(50, input.requirements().length())));

    // Check if LLM-based extraction is enabled
    if (FeatureFlags.isEnabled(FeatureFlag.AI_REQUIREMENT_EXTRACTION)) {
      log.info("LLM-based requirement extraction enabled");
      // LLM extraction will be performed in the generator
    } else {
      log.info("Using rule-based requirement extraction");
    }

    return request;
  }

  /** LLM-enhanced generator for intake. */
  public static class IntakeGenerator
      implements OutputGenerator<StepRequest<IntakeInput>, StepResult<IntakeOutput>> {

    private static final Logger log = LoggerFactory.getLogger(IntakeGenerator.class);

    private final LLMProvider llmProvider;

    public IntakeGenerator(LLMProvider llmProvider) {
      this.llmProvider = llmProvider;
    }

    public IntakeGenerator() {
      this(null);
    }

    @Override
    public @NotNull Promise<StepResult<IntakeOutput>> generate(
        @NotNull StepRequest<IntakeInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      IntakeInput intakeInput = input.input();

      log.info("Processing intake for requirements");

      // Use LLM if feature flag is enabled, otherwise fall back to rule-based
      if (FeatureFlags.isEnabled(FeatureFlag.AI_REQUIREMENT_EXTRACTION)) {
        return extractWithLLM(intakeInput, start);
      } else {
        return extractWithRules(intakeInput, start);
      }
    }

    private Promise<StepResult<IntakeOutput>> extractWithLLM(
        IntakeInput intakeInput, Instant start) {

      String prompt = buildExtractionPrompt(intakeInput.requirements());

      LLMRequest request = LLMRequest.builder()
          .prompt(prompt)
          .maxTokens(2000)
          .temperature(0.3)
          .build();

      return llmProvider.generate(request)
          .then(response -> {
            try {
              StructuredRequirements structured = parseLLMResponse(response.getText());

              IntakeOutput output = new IntakeOutput(
                  structured.functionalReqs(),
                  structured.nonFunctionalReqs(),
                  structured.constraints(),
                  Map.of(
                      "source", intakeInput.source(),
                      "processedAt", start.toString(),
                      "requirementsLength", intakeInput.requirements().length(),
                      "extractionMethod", "llm",
                      "tokensUsed", response.getTokensUsed()));

              return Promise.of(StepResult.success(
                  output,
                  Map.of(
                      "extractedReqs", structured.functionalReqs().size(),
                      "tokensUsed", response.getTokensUsed()),
                  start,
                  Instant.now()));
            } catch (Exception e) {
              log.error("Failed to parse LLM response, falling back to rule-based", e);
              return extractWithRules(intakeInput, start);
            }
          })
          .whenComplete((v, e) -> {
            if (e != null) {
              log.error("LLM extraction failed, falling back to rule-based", e);
            }
          });
    }

    private Promise<StepResult<IntakeOutput>> extractWithRules(
        IntakeInput intakeInput, Instant start) {

      // Extract structured requirements using rule-based approach
      List<String> functionalReqs = extractFunctionalRequirements(intakeInput.requirements());
      List<String> nonFunctionalReqs = extractNonFunctionalRequirements(intakeInput.requirements());
      Map<String, String> constraints = extractConstraints(intakeInput.requirements());

      IntakeOutput output = new IntakeOutput(
          functionalReqs,
          nonFunctionalReqs,
          constraints,
          Map.of(
              "source", intakeInput.source(),
              "processedAt", start.toString(),
              "requirementsLength", intakeInput.requirements().length(),
              "extractionMethod", "rule-based"));

      return Promise.of(
          StepResult.success(
              output, Map.of("extractedReqs", functionalReqs.size()), start, Instant.now()));
    }

    private String buildExtractionPrompt(String requirements) {
      return """
          Analyze the following software requirements and extract structured information.

          Requirements:
          %s

          Extract and categorize the requirements into:
          1. Functional Requirements (what the system should do)
          2. Non-Functional Requirements (performance, security, usability, etc.)
          3. Constraints (budget, timeline, technical limitations)

          Format your response as JSON:
          {
            "functional": ["requirement 1", "requirement 2", ...],
            "nonFunctional": ["requirement 1", "requirement 2", ...],
            "constraints": {"key": "value", ...}
          }
          """.formatted(requirements);
    }

    private StructuredRequirements parseLLMResponse(String response) {
      // Simple JSON parsing - in production, use Jackson or similar
      // This is a simplified implementation
      List<String> functionalReqs = new ArrayList<>();
      List<String> nonFunctionalReqs = new ArrayList<>();
      Map<String, String> constraints = new HashMap<>();

      // Extract JSON from response (handle markdown code blocks)
      String json = response;
      if (response.contains("```json")) {
        json = response.substring(response.indexOf("```json") + 7, response.lastIndexOf("```"));
      } else if (response.contains("```")) {
        json = response.substring(response.indexOf("```") + 3, response.lastIndexOf("```"));
      }

      // Parse functional requirements
      if (json.contains("\"functional\"")) {
        String funcSection = json.substring(json.indexOf("\"functional\""));
        // Extract array items
        functionalReqs.addAll(extractArrayItems(funcSection));
      }

      // Parse non-functional requirements
      if (json.contains("\"nonFunctional\"")) {
        String nfrSection = json.substring(json.indexOf("\"nonFunctional\""));
        nonFunctionalReqs.addAll(extractArrayItems(nfrSection));
      }

      // Parse constraints
      if (json.contains("\"constraints\"")) {
        String constraintSection = json.substring(json.indexOf("\"constraints\""));
        constraints.putAll(extractMapItems(constraintSection));
      }

      return new StructuredRequirements(functionalReqs, nonFunctionalReqs, constraints);
    }

    private List<String> extractArrayItems(String section) {
      List<String> items = new ArrayList<>();
      int start = section.indexOf("[");
      int end = section.indexOf("]");
      if (start >= 0 && end > start) {
        String array = section.substring(start + 1, end);
        String[] parts = array.split(",");
        for (String part : parts) {
          String cleaned = part.trim().replace("\"", "");
          if (!cleaned.isEmpty()) {
            items.add(cleaned);
          }
        }
      }
      return items;
    }

    private Map<String, String> extractMapItems(String section) {
      Map<String, String> map = new HashMap<>();
      int start = section.indexOf("{");
      int end = section.indexOf("}");
      if (start >= 0 && end > start) {
        String content = section.substring(start + 1, end);
        String[] pairs = content.split(",");
        for (String pair : pairs) {
          String[] kv = pair.split(":");
          if (kv.length == 2) {
            String key = kv[0].trim().replace("\"", "");
            String value = kv[1].trim().replace("\"", "");
            map.put(key, value);
          }
        }
      }
      return map;
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<IntakeInput> input, @NotNull AgentContext context) {
      if (FeatureFlags.isEnabled(FeatureFlag.AI_REQUIREMENT_EXTRACTION)) {
        // Estimate based on input length
        int tokens = input.input().requirements().length() / 4;
        return Promise.of(tokens * 0.0001); // Approximate cost per token
      }
      return Promise.of(0.0); // Rule-based, no LLM cost
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("IntakeGenerator")
          .type(FeatureFlags.isEnabled(FeatureFlag.AI_REQUIREMENT_EXTRACTION) ? "llm-enhanced" : "rule-based")
          .description("Extracts structured requirements from natural language using LLM or rules")
          .version("2.0.0")
          .build();
    }

    private List<String> extractFunctionalRequirements(String requirements) {
      // Simple keyword extraction (fallback when LLM is disabled)
      List<String> reqs = new ArrayList<>();
      if (requirements.toLowerCase().contains("must")) {
        reqs.add("MUST requirement extracted");
      }
      if (requirements.toLowerCase().contains("should")) {
        reqs.add("SHOULD requirement extracted");
      }
      return reqs;
    }

    private List<String> extractNonFunctionalRequirements(String requirements) {
      List<String> nfrs = new ArrayList<>();
      if (requirements.toLowerCase().contains("performance")
          || requirements.toLowerCase().contains("fast")) {
        nfrs.add("Performance requirement");
      }
      if (requirements.toLowerCase().contains("secure")
          || requirements.toLowerCase().contains("security")) {
        nfrs.add("Security requirement");
      }
      return nfrs;
    }

    private Map<String, String> extractConstraints(String requirements) {
      Map<String, String> constraints = new HashMap<>();
      if (requirements.toLowerCase().contains("budget")) {
        constraints.put("budget", "Limited budget mentioned");
      }
      if (requirements.toLowerCase().contains("deadline")
          || requirements.toLowerCase().contains("timeline")) {
        constraints.put("timeline", "Time constraint mentioned");
      }
      return constraints;
    }
  }

  /** Record for structured requirements from LLM. */
  private record StructuredRequirements(
      List<String> functionalReqs,
      List<String> nonFunctionalReqs,
      Map<String, String> constraints) {
  }
}
