/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.yappc.api.domain.AISuggestion.SuggestionType;
import io.activej.promise.Promise;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM-powered suggestion generator using libs/java/ai-integration.
 *
 * <p><b>Purpose</b><br>
 * Integrates with CompletionService to generate real AI suggestions for requirements, test cases,
 * clarifications, etc.
 *
 * <p><b>Reuse</b><br>
 * Uses existing CompletionService from libs/java/ai-integration. No duplicate LLM code - follows
 * Golden Rule #1.
 *
 * <p><b>Prompt Engineering</b><br>
 * - System prompts define persona-aware behavior - Structured JSON output for reliable parsing -
 * Confidence scoring based on context richness
 *
 * @doc.type class
 * @doc.purpose LLM-powered suggestion generation
 * @doc.layer application
 * @doc.pattern Service, Adapter
 */
public class LLMSuggestionGenerator {

  private static final Logger logger = LoggerFactory.getLogger(LLMSuggestionGenerator.class);
  private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

  private final CompletionService completionService;

  /** System prompts per suggestion type for persona-aware generation. */
  private static final Map<SuggestionType, String> SYSTEM_PROMPTS =
      Map.ofEntries(
          Map.entry(
              SuggestionType.REQUIREMENT,
              """
            You are an expert requirements engineer. Analyze the given context and generate
            a well-structured requirement following SMART criteria (Specific, Measurable,
            Achievable, Relevant, Time-bound).

            Output JSON format:
            {
                "title": "Short descriptive title",
                "content": "Detailed requirement text",
                "rationale": "Why this requirement is suggested",
                "acceptanceCriteria": ["criterion 1", "criterion 2"],
                "priority": "HIGH|MEDIUM|LOW",
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.CLARIFICATION,
              """
            You are a requirements analyst. Identify ambiguities and unclear points in the
            given requirement. Generate clarifying questions that would help make the
            requirement testable and implementable.

            Output JSON format:
            {
                "title": "Clarification needed for [aspect]",
                "content": "The specific clarification question or concern",
                "rationale": "Why this needs clarification",
                "suggestedAnswers": ["possible answer 1", "possible answer 2"],
                "impactIfUnresolved": "What could go wrong",
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.TESTABILITY,
              """
            You are a QA specialist. Analyze the requirement for testability issues.
            Generate suggestions to improve testability with concrete acceptance criteria.

            Output JSON format:
            {
                "title": "Testability improvement for [aspect]",
                "content": "Specific testability concern and improvement",
                "rationale": "Why this is hard to test currently",
                "suggestedTestCases": ["test case 1", "test case 2"],
                "testabilityScore": 0-100,
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.SECURITY,
              """
            You are a security engineer. Analyze the requirement for security implications.
            Identify potential vulnerabilities and suggest security requirements.

            Output JSON format:
            {
                "title": "Security consideration for [aspect]",
                "content": "Security concern and recommended mitigation",
                "rationale": "Why this is a security concern",
                "threatType": "OWASP category or threat type",
                "mitigation": "Recommended security control",
                "severity": "CRITICAL|HIGH|MEDIUM|LOW",
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.DEPENDENCY,
              """
            You are a systems architect. Analyze the requirement for dependencies on
            other requirements, systems, or components.

            Output JSON format:
            {
                "title": "Dependency identified: [dependency name]",
                "content": "Description of the dependency relationship",
                "rationale": "Why this dependency matters",
                "dependencyType": "REQUIRES|CONFLICTS|EXTENDS|RELATES_TO",
                "targetEntities": ["entity1", "entity2"],
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.EDGE_CASE,
              """
            You are a QA engineer specializing in edge cases. Analyze the requirement
            and identify potential edge cases, boundary conditions, and error scenarios.

            Output JSON format:
            {
                "title": "Edge case: [scenario name]",
                "content": "Description of the edge case scenario",
                "rationale": "Why this edge case is important",
                "inputConditions": ["condition 1", "condition 2"],
                "expectedBehavior": "What should happen",
                "riskIfMissed": "Impact of not handling this",
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.REFINEMENT,
              """
            You are a senior product manager. Analyze the requirement and suggest
            refinements to improve clarity, completeness, and value.

            Output JSON format:
            {
                "title": "Refinement: [aspect improved]",
                "content": "The improved/refined text",
                "rationale": "Why this refinement improves the requirement",
                "originalIssue": "What was unclear or incomplete",
                "valueAdded": "Benefit of this refinement",
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.ALTERNATIVE,
              """
            You are a solution architect. Analyze the requirement and suggest
            alternative approaches that might better achieve the underlying goal.

            Output JSON format:
            {
                "title": "Alternative approach: [approach name]",
                "content": "Description of the alternative approach",
                "rationale": "Why this alternative might be better",
                "tradeoffs": {"pros": ["pro1"], "cons": ["con1"]},
                "implementationComplexity": "LOW|MEDIUM|HIGH",
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.DECOMPOSITION,
              """
            You are a product owner. Analyze the requirement and break it down into
            smaller, independently deliverable user stories or sub-requirements.

            Output JSON format:
            {
                "title": "Decomposition of [requirement name]",
                "content": "Overview of the decomposition",
                "rationale": "Why this should be broken down",
                "subRequirements": [
                    {"title": "Sub-requirement 1", "description": "..."},
                    {"title": "Sub-requirement 2", "description": "..."}
                ],
                "suggestedOrder": ["sub1", "sub2"],
                "confidence": 0.0-1.0
            }
            """),
          Map.entry(
              SuggestionType.PERFORMANCE,
              """
            You are a performance engineer. Analyze the requirement for performance
            implications and suggest performance requirements or optimizations.

            Output JSON format:
            {
                "title": "Performance consideration: [aspect]",
                "content": "Performance concern and recommendation",
                "rationale": "Why this affects performance",
                "metrics": ["metric1: target", "metric2: target"],
                "scalabilityImpact": "How this affects scale",
                "confidence": 0.0-1.0
            }
            """));

  /**
   * Creates an LLMSuggestionGenerator.
   *
   * @param completionService the LLM completion service from libs/java/ai-integration
   */
  public LLMSuggestionGenerator(CompletionService completionService) {
    this.completionService =
        Objects.requireNonNull(completionService, "CompletionService must not be null");
  }

  /**
   * Generate a suggestion using the LLM.
   *
   * @param type the suggestion type
   * @param context the context (requirement text, project info, etc.)
   * @param persona the user's persona (for tailored responses)
   * @return Promise of generated suggestion data
   */
  public Promise<GeneratedSuggestion> generate(
      SuggestionType type, String context, String persona) {

    Objects.requireNonNull(type, "Type must not be null");
    Objects.requireNonNull(context, "Context must not be null");

    String systemPrompt =
        SYSTEM_PROMPTS.getOrDefault(type, SYSTEM_PROMPTS.get(SuggestionType.REQUIREMENT));

    String userPrompt = buildUserPrompt(type, context, persona);

    List<ChatMessage> messages =
        List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userPrompt));

    CompletionRequest request =
        CompletionRequest.builder()
            .messages(messages)
            .maxTokens(1000)
            .temperature(0.7)
            .responseFormat("json_object")
            .build();

    logger.info("Generating {} suggestion with LLM for persona: {}", type, persona);

    return completionService
        .complete(request)
        .map(result -> parseResponse(type, result))
        .mapException(
            e -> {
              logger.error("LLM suggestion generation failed: {}", e.getMessage());
              return new RuntimeException("Failed to generate suggestion: " + e.getMessage(), e);
            });
  }

  /**
   * Generate multiple suggestions in batch.
   *
   * @param type the suggestion type
   * @param context the context
   * @param count number of suggestions to generate
   * @param persona the user's persona
   * @return Promise of list of generated suggestions
   */
  public Promise<List<GeneratedSuggestion>> generateBatch(
      SuggestionType type, String context, int count, String persona) {

    // Modify prompt to request multiple suggestions
    String systemPrompt =
        SYSTEM_PROMPTS.getOrDefault(type, SYSTEM_PROMPTS.get(SuggestionType.REQUIREMENT));
    String batchSystemPrompt =
        systemPrompt.replace(
            "Output JSON format:",
            "Generate exactly " + count + " suggestions. Output JSON array format:");

    String userPrompt = buildUserPrompt(type, context, persona);

    List<ChatMessage> messages =
        List.of(ChatMessage.system(batchSystemPrompt), ChatMessage.user(userPrompt));

    CompletionRequest request =
        CompletionRequest.builder()
            .messages(messages)
            .maxTokens(count * 500)
            .temperature(0.8)
            .responseFormat("json_object")
            .build();

    return completionService.complete(request).map(result -> parseBatchResponse(type, result));
  }

  private String buildUserPrompt(SuggestionType type, String context, String persona) {
    StringBuilder prompt = new StringBuilder();

    if (persona != null && !persona.isEmpty()) {
      prompt.append("You are assisting a ").append(persona).append(".\n\n");
    }

    prompt
        .append("Analyze the following context and generate a ")
        .append(type.name().toLowerCase().replace("_", " "))
        .append(" suggestion:\n\n");

    prompt.append("CONTEXT:\n").append(context).append("\n\n");

    prompt.append("Generate a helpful suggestion in the specified JSON format.");

    return prompt.toString();
  }

  private GeneratedSuggestion parseResponse(SuggestionType type, CompletionResult result) {
    try {
      String text = result.getText();
      Map<String, Object> parsed = objectMapper.readValue(text, new TypeReference<>() {});

      return new GeneratedSuggestion(
          (String) parsed.getOrDefault("title", "Untitled Suggestion"),
          (String) parsed.getOrDefault("content", text),
          (String) parsed.getOrDefault("rationale", "AI-generated suggestion"),
          parseConfidence(parsed.get("confidence")),
          result.getModelUsed(),
          parsed);
    } catch (Exception e) {
      logger.warn("Failed to parse LLM response as JSON, using raw text: {}", e.getMessage());
      return new GeneratedSuggestion(
          "AI Suggestion",
          result.getText(),
          "Generated by AI",
          0.7,
          result.getModelUsed(),
          Map.of());
    }
  }

  private List<GeneratedSuggestion> parseBatchResponse(
      SuggestionType type, CompletionResult result) {
    try {
      String text = result.getText();

      // Try parsing as array
      if (text.trim().startsWith("[")) {
        List<Map<String, Object>> items = objectMapper.readValue(text, new TypeReference<>() {});
        return items.stream()
            .map(
                item ->
                    new GeneratedSuggestion(
                        (String) item.getOrDefault("title", "Untitled"),
                        (String) item.getOrDefault("content", ""),
                        (String) item.getOrDefault("rationale", "AI-generated"),
                        parseConfidence(item.get("confidence")),
                        result.getModelUsed(),
                        item))
            .toList();
      }

      // Try parsing as object with "suggestions" array
      Map<String, Object> parsed = objectMapper.readValue(text, new TypeReference<>() {});
      if (parsed.containsKey("suggestions")) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions =
            (List<Map<String, Object>>) parsed.get("suggestions");
        return suggestions.stream()
            .map(
                item ->
                    new GeneratedSuggestion(
                        (String) item.getOrDefault("title", "Untitled"),
                        (String) item.getOrDefault("content", ""),
                        (String) item.getOrDefault("rationale", "AI-generated"),
                        parseConfidence(item.get("confidence")),
                        result.getModelUsed(),
                        item))
            .toList();
      }

      // Fallback: return single suggestion
      return List.of(parseResponse(type, result));

    } catch (Exception e) {
      logger.warn("Failed to parse batch LLM response: {}", e.getMessage());
      return List.of(parseResponse(type, result));
    }
  }

  private double parseConfidence(Object value) {
    if (value == null) return 0.7;
    if (value instanceof Number) return ((Number) value).doubleValue();
    try {
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException e) {
      return 0.7;
    }
  }

  /** Result from LLM suggestion generation. */
  public record GeneratedSuggestion(
      String title,
      String content,
      String rationale,
      double confidence,
      String modelUsed,
      Map<String, Object> metadata) {}
}
