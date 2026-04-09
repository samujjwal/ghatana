package com.ghatana.yappc.platform.collab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayName("MergeSuggestionService Tests")
class MergeSuggestionServiceTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  MergeSuggestionServiceTest() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("suggestMerge calls AI service with code conflict context")
  void suggestMergeCallsAiServiceWithCodeConflictContext() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("mergedCode();\nPrefer the right side but preserve the shared API."));

    MergeSuggestionService service = new MergeSuggestionService(aiService);
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise(
            () ->
                service.suggestMerge(
                    new MergeSuggestionService.MergeConflictRequest(
                        "conflict-1",
                        MergeSuggestionService.ConflictDomain.CODE,
                        "leftCode();",
                        "mergedCode();",
                        Map.of("file", "Editor.tsx"))));

    assertThat(suggestion.conflictId()).isEqualTo("conflict-1");
    assertThat(suggestion.mergedVersion()).isEqualTo("mergedCode();");
    assertThat(suggestion.rationale()).contains("Prefer the right side");
    assertThat(suggestion.aiGenerated()).isTrue();
    verify(aiService).reason(anyString(), anyMap());
  }

  @Test
  @DisplayName("suggestMerge falls back for blank document response")
  void suggestMergeFallsBackForBlankDocumentResponse() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" "));

    MergeSuggestionService service = new MergeSuggestionService(aiService);
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise(
            () ->
                service.suggestMerge(
                    new MergeSuggestionService.MergeConflictRequest(
                        "doc-1",
                        MergeSuggestionService.ConflictDomain.DOCUMENT,
                        "Heading A",
                        "Heading B",
                        Map.of("section", "summary"))));

    assertThat(suggestion.mergedVersion()).isEqualTo("Heading A\nHeading B");
    assertThat(suggestion.rationale()).contains("preserved both document edits");
    assertThat(suggestion.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("suggestMerge falls back for null code response with normalized right version")
  void suggestMergeFallsBackForNullCodeResponseWithNormalizedRightVersion() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null));

    MergeSuggestionService service = new MergeSuggestionService(aiService);
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise(
            () ->
                service.suggestMerge(
                    new MergeSuggestionService.MergeConflictRequest(
                        "code-2",
                        MergeSuggestionService.ConflictDomain.CODE,
                        "left()",
                        null,
                        Map.of())));

    assertThat(suggestion.conflictId()).isEqualTo("code-2");
    assertThat(suggestion.mergedVersion()).isEmpty();
    assertThat(suggestion.rationale()).contains("right-hand code change");
    assertThat(suggestion.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("suggestMerge falls back for null generic response and normalizes request defaults")
  void suggestMergeFallsBackForNullGenericResponseAndNormalizesRequestDefaults() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null));

    MergeSuggestionService service = new MergeSuggestionService(aiService);
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise(
            () ->
                service.suggestMerge(
                    new MergeSuggestionService.MergeConflictRequest(
                        null,
                        null,
                        null,
                        "right payload",
                        null)));

    assertThat(suggestion.conflictId()).isEqualTo("unknown-conflict");
    assertThat(suggestion.mergedVersion()).isEqualTo("right payload");
    assertThat(suggestion.rationale()).contains("latest provided version");
    assertThat(suggestion.aiGenerated()).isFalse();
  }

  @Test
  @DisplayName("suggestMerge uses single-line AI response as merged version")
  void suggestMergeUsesSingleLineAiResponseAsMergedVersion() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("Use client B version"));

    MergeSuggestionService service = new MergeSuggestionService(aiService);
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise(
            () ->
                service.suggestMerge(
                    new MergeSuggestionService.MergeConflictRequest(
                        "generic-2",
                        MergeSuggestionService.ConflictDomain.GENERIC,
                        "left",
                        "right",
                        Map.of("kind", "text"))));

    assertThat(suggestion.mergedVersion()).isEqualTo("Use client B version");
    assertThat(suggestion.rationale()).isEqualTo("AI merge suggestion");
    assertThat(suggestion.aiGenerated()).isTrue();
  }

  @Test
  @DisplayName("constructor rejects null ai service")
  void constructorRejectsNullAiService() {
    assertThatThrownBy(() -> new MergeSuggestionService(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("aiService");
  }
}
