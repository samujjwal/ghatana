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

@DisplayName("MergeSuggestionService Tests [GH-90000]")
class MergeSuggestionServiceTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  MergeSuggestionServiceTest() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
  }

  @Test
  @DisplayName("suggestMerge calls AI service with code conflict context [GH-90000]")
  void suggestMergeCallsAiServiceWithCodeConflictContext() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("mergedCode();\nPrefer the right side but preserve the shared API. [GH-90000]"));

    MergeSuggestionService service = new MergeSuggestionService(aiService); // GH-90000
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise( // GH-90000
            () -> // GH-90000
                service.suggestMerge( // GH-90000
                    new MergeSuggestionService.MergeConflictRequest( // GH-90000
                        "conflict-1",
                        MergeSuggestionService.ConflictDomain.CODE,
                        "leftCode();", // GH-90000
                        "mergedCode();", // GH-90000
                        Map.of("file", "Editor.tsx")))); // GH-90000

    assertThat(suggestion.conflictId()).isEqualTo("conflict-1 [GH-90000]");
    assertThat(suggestion.mergedVersion()).isEqualTo("mergedCode(); [GH-90000]");
    assertThat(suggestion.rationale()).contains("Prefer the right side [GH-90000]");
    assertThat(suggestion.aiGenerated()).isTrue(); // GH-90000
    verify(aiService).reason(anyString(), anyMap()); // GH-90000
  }

  @Test
  @DisplayName("suggestMerge falls back for blank document response [GH-90000]")
  void suggestMergeFallsBackForBlankDocumentResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("  [GH-90000]"));

    MergeSuggestionService service = new MergeSuggestionService(aiService); // GH-90000
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise( // GH-90000
            () -> // GH-90000
                service.suggestMerge( // GH-90000
                    new MergeSuggestionService.MergeConflictRequest( // GH-90000
                        "doc-1",
                        MergeSuggestionService.ConflictDomain.DOCUMENT,
                        "Heading A",
                        "Heading B",
                        Map.of("section", "summary")))); // GH-90000

    assertThat(suggestion.mergedVersion()).isEqualTo("Heading A\nHeading B [GH-90000]");
    assertThat(suggestion.rationale()).contains("preserved both document edits [GH-90000]");
    assertThat(suggestion.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("suggestMerge falls back for null code response with normalized right version [GH-90000]")
  void suggestMergeFallsBackForNullCodeResponseWithNormalizedRightVersion() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null)); // GH-90000

    MergeSuggestionService service = new MergeSuggestionService(aiService); // GH-90000
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise( // GH-90000
            () -> // GH-90000
                service.suggestMerge( // GH-90000
                    new MergeSuggestionService.MergeConflictRequest( // GH-90000
                        "code-2",
                        MergeSuggestionService.ConflictDomain.CODE,
                        "left()", // GH-90000
                        null,
                        Map.of()))); // GH-90000

    assertThat(suggestion.conflictId()).isEqualTo("code-2 [GH-90000]");
    assertThat(suggestion.mergedVersion()).isEmpty(); // GH-90000
    assertThat(suggestion.rationale()).contains("right-hand code change [GH-90000]");
    assertThat(suggestion.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("suggestMerge falls back for null generic response and normalizes request defaults [GH-90000]")
  void suggestMergeFallsBackForNullGenericResponseAndNormalizesRequestDefaults() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null)); // GH-90000

    MergeSuggestionService service = new MergeSuggestionService(aiService); // GH-90000
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise( // GH-90000
            () -> // GH-90000
                service.suggestMerge( // GH-90000
                    new MergeSuggestionService.MergeConflictRequest( // GH-90000
                        null,
                        null,
                        null,
                        "right payload",
                        null)));

    assertThat(suggestion.conflictId()).isEqualTo("unknown-conflict [GH-90000]");
    assertThat(suggestion.mergedVersion()).isEqualTo("right payload [GH-90000]");
    assertThat(suggestion.rationale()).contains("latest provided version [GH-90000]");
    assertThat(suggestion.aiGenerated()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("suggestMerge uses single-line AI response as merged version [GH-90000]")
  void suggestMergeUsesSingleLineAiResponseAsMergedVersion() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("Use client B version [GH-90000]"));

    MergeSuggestionService service = new MergeSuggestionService(aiService); // GH-90000
    MergeSuggestionService.MergeSuggestion suggestion =
        runPromise( // GH-90000
            () -> // GH-90000
                service.suggestMerge( // GH-90000
                    new MergeSuggestionService.MergeConflictRequest( // GH-90000
                        "generic-2",
                        MergeSuggestionService.ConflictDomain.GENERIC,
                        "left",
                        "right",
                        Map.of("kind", "text")))); // GH-90000

    assertThat(suggestion.mergedVersion()).isEqualTo("Use client B version [GH-90000]");
    assertThat(suggestion.rationale()).isEqualTo("AI merge suggestion [GH-90000]");
    assertThat(suggestion.aiGenerated()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("constructor rejects null ai service [GH-90000]")
  void constructorRejectsNullAiService() { // GH-90000
    assertThatThrownBy(() -> new MergeSuggestionService(null)) // GH-90000
        .isInstanceOf(NullPointerException.class) // GH-90000
        .hasMessageContaining("aiService [GH-90000]");
  }
}
