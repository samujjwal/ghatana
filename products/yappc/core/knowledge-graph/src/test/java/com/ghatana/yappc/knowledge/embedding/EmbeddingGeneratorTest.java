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

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("EmbeddingGenerator Tests [GH-90000]")
class EmbeddingGeneratorTest extends EventloopTestBase {

  @Mock private EmbeddingService embeddingService;

  @Test
  @DisplayName("default constructor uses the standard embedding dimension [GH-90000]")
  void defaultConstructorUsesTheStandardEmbeddingDimension() { // GH-90000
    when(embeddingService.createEmbedding("default [GH-90000]"))
        .thenReturn(Promise.of(new EmbeddingResult("default", new float[] {7.0f}, "model"))); // GH-90000

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService); // GH-90000

    assertThat(runPromise(() -> generator.generate("default [GH-90000]"))).hasSize(1536).startsWith(7.0f);
  }

  @Test
  @DisplayName("generate returns embedding unchanged when dimension already matches [GH-90000]")
  void generateReturnsEmbeddingUnchangedWhenDimensionMatches() { // GH-90000
    float[] vector = new float[] {0.1f, 0.2f, 0.3f};
    when(embeddingService.createEmbedding("billing [GH-90000]"))
        .thenReturn(Promise.of(new EmbeddingResult("billing", vector, "model"))); // GH-90000

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService, 3); // GH-90000

    assertThat(runPromise(() -> generator.generate("billing [GH-90000]"))).containsExactly(0.1f, 0.2f, 0.3f);
  }

  @Test
  @DisplayName("generate pads and truncates vectors to configured dimension [GH-90000]")
  void generatePadsAndTruncatesVectorsToConfiguredDimension() { // GH-90000
    when(embeddingService.createEmbedding("short [GH-90000]"))
        .thenReturn(Promise.of(new EmbeddingResult("short", new float[] {1.0f, 2.0f}, "model"))); // GH-90000
    when(embeddingService.createEmbedding("long [GH-90000]"))
        .thenReturn(Promise.of(new EmbeddingResult("long", new float[] {1.0f, 2.0f, 3.0f, 4.0f}, "model"))); // GH-90000

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService, 3); // GH-90000

    assertThat(runPromise(() -> generator.generate("short [GH-90000]"))).containsExactly(1.0f, 2.0f, 0.0f);
    assertThat(runPromise(() -> generator.generate("long [GH-90000]"))).containsExactly(1.0f, 2.0f, 3.0f);
  }

  @Test
  @DisplayName("generate falls back to zero vector for empty vector and failures [GH-90000]")
  void generateFallsBackToZeroVectorForEmptyVectorAndFailures() { // GH-90000
    when(embeddingService.createEmbedding("empty-vector [GH-90000]"))
        .thenReturn(Promise.of(new EmbeddingResult("empty-vector", new float[] {}, "model"))); // GH-90000
    when(embeddingService.createEmbedding("failed-vector [GH-90000]"))
        .thenReturn(Promise.ofException(new IllegalStateException("embedding failed [GH-90000]")));

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService, 2); // GH-90000

    assertThat(runPromise(() -> generator.generate("empty-vector [GH-90000]"))).containsExactly(0.0f, 0.0f);
    assertThat(runPromise(() -> generator.generate("failed-vector [GH-90000]"))).containsExactly(0.0f, 0.0f);
  }

  @Test
  @DisplayName("constructor clamps invalid dimension to one [GH-90000]")
  void constructorClampsInvalidDimensionToOne() { // GH-90000
    when(embeddingService.createEmbedding("single [GH-90000]"))
        .thenReturn(Promise.of(new EmbeddingResult("single", new float[] {9.0f, 8.0f}, "model"))); // GH-90000

    EmbeddingGenerator generator = new EmbeddingGenerator(embeddingService, 0); // GH-90000

    assertThat(runPromise(() -> generator.generate("single [GH-90000]"))).containsExactly(9.0f);
  }
}
