package com.ghatana.yappc.platform.collab;

import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Suggests merged resolutions for concurrent collaboration conflicts using AI with deterministic fallback.
 * @doc.layer product
 * @doc.pattern Service
 */
public final class MergeSuggestionService {

  private final YAPPCAIService aiService;

  public MergeSuggestionService(@NotNull YAPPCAIService aiService) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
  }

  public Promise<MergeSuggestion> suggestMerge(@NotNull MergeConflictRequest request) {
    Objects.requireNonNull(request, "request");

    Map<String, Object> context = buildContext(request);
    String prompt = buildPrompt(request);
    return aiService
        .reason(prompt, context)
        .map(response -> toSuggestion(request, response));
  }

  private Map<String, Object> buildContext(MergeConflictRequest request) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("conflictId", request.conflictId());
    context.put("domain", request.domain().name().toLowerCase(Locale.ROOT));
    context.put("leftVersion", request.leftVersion());
    context.put("rightVersion", request.rightVersion());
    context.put("metadata", request.metadata());
    return context;
  }

  private String buildPrompt(MergeConflictRequest request) {
    return switch (request.domain()) {
      case CODE ->
          "Resolve this code merge conflict. Return a concise merged version followed by a rationale.\n"
              + "Left version:\n"
              + request.leftVersion()
              + "\nRight version:\n"
              + request.rightVersion();
      case DOCUMENT ->
          "Resolve this document merge conflict. Preserve intent from both sides when possible.\n"
              + "Left version:\n"
              + request.leftVersion()
              + "\nRight version:\n"
              + request.rightVersion();
      case GENERIC ->
          "Resolve this merge conflict using the supplied context.\n"
              + "Left version:\n"
              + request.leftVersion()
              + "\nRight version:\n"
              + request.rightVersion();
    };
  }

  private MergeSuggestion toSuggestion(MergeConflictRequest request, String response) {
    if (response == null || response.isBlank()) {
      return fallbackSuggestion(request);
    }

    String trimmed = response.trim();
    String[] sections = trimmed.split("\\n", 2);
    String mergedVersion = sections[0].trim();
    String rationale = sections.length > 1 ? sections[1].trim() : "AI merge suggestion";
    return new MergeSuggestion(request.conflictId(), mergedVersion, rationale, true);
  }

  private MergeSuggestion fallbackSuggestion(MergeConflictRequest request) {
    return switch (request.domain()) {
      case CODE ->
          new MergeSuggestion(
              request.conflictId(),
              request.rightVersion(),
              "Fallback selected the right-hand code change because no AI suggestion was returned.",
              false);
      case DOCUMENT ->
          new MergeSuggestion(
              request.conflictId(),
              request.leftVersion() + "\n" + request.rightVersion(),
              "Fallback preserved both document edits in sequence because no AI suggestion was returned.",
              false);
      case GENERIC ->
          new MergeSuggestion(
              request.conflictId(),
              request.rightVersion(),
              "Fallback selected the latest provided version because no AI suggestion was returned.",
              false);
    };
  }

  public enum ConflictDomain {
    CODE,
    DOCUMENT,
    GENERIC
  }

  public record MergeConflictRequest(
      String conflictId,
      ConflictDomain domain,
      String leftVersion,
      String rightVersion,
      Map<String, String> metadata) {

    public MergeConflictRequest {
      conflictId = Objects.requireNonNullElse(conflictId, "unknown-conflict");
      domain = domain == null ? ConflictDomain.GENERIC : domain;
      leftVersion = leftVersion == null ? "" : leftVersion;
      rightVersion = rightVersion == null ? "" : rightVersion;
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  public record MergeSuggestion(
      String conflictId, String mergedVersion, String rationale, boolean aiGenerated) {}
}
