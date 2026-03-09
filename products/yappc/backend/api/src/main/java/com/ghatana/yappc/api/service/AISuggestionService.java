/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.repository.AISuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified application service for AI suggestion management.
 *
 * @doc.type class
 * @doc.purpose AI suggestion management service (simplified)
 * @doc.layer application
 * @doc.pattern Service
 */
public class AISuggestionService {

  private static final Logger logger = LoggerFactory.getLogger(AISuggestionService.class);

  private final AISuggestionRepository repository;
  private final AuditService auditService;
  private final LLMGateway llmGateway;

  public AISuggestionService(AISuggestionRepository repository, AuditService auditService, LLMGateway llmGateway) {
    this.repository = repository;
    this.auditService = auditService;
    this.llmGateway = llmGateway;
  }

  /**
   * Generates a new AI suggestion based on the request.
   *
   * @param tenantId The tenant ID
   * @param request The generation request
   * @return The generated suggestion response
   */
  public io.activej.promise.Promise<com.ghatana.yappc.api.ai.dto.AISuggestionResponse> generateSuggestion(String tenantId, com.ghatana.yappc.api.ai.dto.GenerateSuggestionRequest request) {
    // Map DTO SuggestionType to Domain SuggestionType
    com.ghatana.yappc.api.domain.AISuggestion.SuggestionType type = mapSuggestionType(request.suggestionType());

    CompletionRequest llmRequest =
        CompletionRequest.builder()
            .prompt(
                "You are a software engineering assistant. Generate a suggestion for the following.\n\n"
                    + "Type: "
                    + request.suggestionType()
                    + "\n"
                    + "User prompt: "
                    + request.userPrompt()
                    + "\n\nRespond with a title on the first line, then the detailed suggestion content.")
            .maxTokens(1024)
            .temperature(0.7)
            .build();

    io.activej.promise.Promise<CompletionResult> completionPromise = llmGateway.complete(llmRequest);
    if (completionPromise == null) {
      logger.warn("LLM gateway returned null completion promise, using fallback suggestion");
      completionPromise = io.activej.promise.Promise.ofException(
          new IllegalStateException("LLM gateway returned null completion promise"));
    }

    return completionPromise
        .map(result -> toGeneratedSuggestion(request, result))
        .then(
            io.activej.promise.Promise::of,
            e -> {
              logger.warn("LLM call failed for suggestion generation, using fallback", e);
              return io.activej.promise.Promise.of(fallbackSuggestion(request));
            })
        .then(
            generated -> {
              var suggestion =
                  com.ghatana.yappc.api.domain.AISuggestion.builder()
                      .id(java.util.UUID.randomUUID())
                      .tenantId(tenantId)
                      .projectId(request.projectId())
                      .type(type)
                      .status(com.ghatana.yappc.api.domain.AISuggestion.SuggestionStatus.PENDING)
                      .title(generated.title())
                      .content(generated.content())
                      .build();

              return repository
                  .save(suggestion)
                  .map(saved -> mapToResponse(saved, request.workspaceId(), generated.modelId()));
            });
  }

  private GeneratedSuggestion toGeneratedSuggestion(
      com.ghatana.yappc.api.ai.dto.GenerateSuggestionRequest request, CompletionResult result) {
    String text = result.getText() != null ? result.getText().trim() : "";
    String modelId = result.getModelUsed() != null ? result.getModelUsed() : "unknown";
    String suggestionType = request.suggestionType() != null ? request.suggestionType().name() : "REQUIREMENT";

    if (text.isEmpty()) {
      return new GeneratedSuggestion(
          "AI Suggestion for " + suggestionType,
          "Suggestion generated based on user prompt: " + request.userPrompt(),
          modelId);
    }

    int newline = text.indexOf('\n');
    if (newline > 0) {
      return new GeneratedSuggestion(
          text.substring(0, newline).trim(), text.substring(newline + 1).trim(), modelId);
    }

    return new GeneratedSuggestion("AI Suggestion for " + suggestionType, text, modelId);
  }

  private GeneratedSuggestion fallbackSuggestion(com.ghatana.yappc.api.ai.dto.GenerateSuggestionRequest request) {
    String suggestionType = request.suggestionType() != null ? request.suggestionType().name() : "REQUIREMENT";
    return new GeneratedSuggestion(
        "AI Generated Suggestion for " + suggestionType,
        "Suggestion generated based on user prompt: " + request.userPrompt(),
        "fallback");
  }

  private record GeneratedSuggestion(String title, String content, String modelId) {}

  private com.ghatana.yappc.api.domain.AISuggestion.SuggestionType mapSuggestionType(com.ghatana.yappc.api.ai.dto.GenerateSuggestionRequest.SuggestionType dtoType) {
      if (dtoType == null) return com.ghatana.yappc.api.domain.AISuggestion.SuggestionType.REQUIREMENT;
      try {
          return com.ghatana.yappc.api.domain.AISuggestion.SuggestionType.valueOf(dtoType.name());
      } catch (IllegalArgumentException e) {
          // Fallback mapping
          return com.ghatana.yappc.api.domain.AISuggestion.SuggestionType.REQUIREMENT;
      }
  }

  /**
   * Retrieves suggestions for a workspace.
   *
   * @param tenantId Tenant ID
   * @param workspaceId Workspace ID
   * @param projectId Project ID (optional)
   * @return List of suggestions
   */
  public io.activej.promise.Promise<java.util.List<com.ghatana.yappc.api.ai.dto.AISuggestionResponse>> getSuggestions(String tenantId, String workspaceId, String projectId) {
      if (projectId != null) {
          return repository.findPendingByProject(tenantId, projectId)
              .map(list -> list.stream().map(s -> mapToResponse(s, workspaceId, "unknown")).toList());
      } else {
          // Fallback to finding all pending for tenant if no project specified (simplified)
          return repository.findPending(tenantId)
              .map(list -> list.stream().map(s -> mapToResponse(s, workspaceId, "unknown")).toList());
      }
  }

  private com.ghatana.yappc.api.ai.dto.AISuggestionResponse mapToResponse(com.ghatana.yappc.api.domain.AISuggestion domain, String workspaceId, String modelId) {
      return new com.ghatana.yappc.api.ai.dto.AISuggestionResponse(
          domain.getId().toString(),
          workspaceId != null ? workspaceId : "unknown", // Domain doesn't have workspaceId
          domain.getProjectId(),
          domain.getType().name(),
          domain.getStatus().name(),
          domain.getTargetResourceId(),
          domain.getTargetResourceType(),
          new com.ghatana.yappc.api.ai.dto.AISuggestionResponse.SuggestionContent(
              domain.getTitle(),
              domain.getContent(),
              java.util.List.of(),
              "",
              java.util.Map.of()
          ),
          new com.ghatana.yappc.api.ai.dto.AISuggestionResponse.ConfidenceScore("medium", domain.getConfidence(), java.util.List.of()),
          domain.getRationale(),
          domain.getCreatedAt(),
          null,
          "LLM-Gateway",
          modelId,
          java.util.Map.of()
      );
  }
}
