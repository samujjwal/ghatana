package com.ghatana.yappc.knowledge.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingGenerator Tests")
class EmbeddingGeneratorTest extends EventloopTestBase {

  @Mock private EmbeddingService embeddingService;

  @Test
  @DisplayName("default constructor uses the standard embedding dimension")
  void defaultConstructorUsesTheStandardEmbeddingDimension() {
    when(embeddingService.createEmbedding("default"))
        .thenReturn(Promise.of(new EmbeddingResult("default", new float[] {7.0f}, "model")));

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService);

    assertThat(runPromise(() -> generator.generate("default"))).hasSize(1536).startsWith(7.0f);
  }

  @Test
  @DisplayName("generate returns embedding unchanged when dimension already matches")
  void generateReturnsEmbeddingUnchangedWhenDimensionMatches() {
    float[] vector = new float[] {0.1f, 0.2f, 0.3f};
    when(embeddingService.createEmbedding("billing"))
        .thenReturn(Promise.of(new EmbeddingResult("billing", vector, "model")));

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService, 3);

    assertThat(runPromise(() -> generator.generate("billing"))).containsExactly(0.1f, 0.2f, 0.3f);
  }

  @Test
  @DisplayName("generate pads and truncates vectors to configured dimension")
  void generatePadsAndTruncatesVectorsToConfiguredDimension() {
    when(embeddingService.createEmbedding("short"))
        .thenReturn(Promise.of(new EmbeddingResult("short", new float[] {1.0f, 2.0f}, "model")));
    when(embeddingService.createEmbedding("long"))
        .thenReturn(Promise.of(new EmbeddingResult("long", new float[] {1.0f, 2.0f, 3.0f, 4.0f}, "model")));

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService, 3);

    assertThat(runPromise(() -> generator.generate("short"))).containsExactly(1.0f, 2.0f, 0.0f);
    assertThat(runPromise(() -> generator.generate("long"))).containsExactly(1.0f, 2.0f, 3.0f);
  }

  @Test
  @DisplayName("generate falls back to zero vector for empty vector and failures")
  void generateFallsBackToZeroVectorForEmptyVectorAndFailures() {
    when(embeddingService.createEmbedding("empty-vector"))
        .thenReturn(Promise.of(new EmbeddingResult("empty-vector", new float[] {}, "model")));
    when(embeddingService.createEmbedding("failed-vector"))
        .thenReturn(Promise.ofException(new IllegalStateException("embedding failed")));

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService, 2);

    assertThat(runPromise(() -> generator.generate("empty-vector"))).containsExactly(0.0f, 0.0f);
    assertThat(runPromise(() -> generator.generate("failed-vector"))).containsExactly(0.0f, 0.0f);
  }

  @Test
  @DisplayName("constructor clamps invalid dimension to one")
  void constructorClampsInvalidDimensionToOne() {
    when(embeddingService.createEmbedding("single"))
        .thenReturn(Promise.of(new EmbeddingResult("single", new float[] {9.0f, 8.0f}, "model")));

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService, 0);

    assertThat(runPromise(() -> generator.generate("single"))).containsExactly(9.0f);
  }
}
