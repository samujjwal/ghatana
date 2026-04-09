package com.ghatana.yappc.knowledge.embedding;

import com.ghatana.ai.embedding.EmbeddingService;
import io.activej.promise.Promise;
import java.util.Arrays;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Generates normalized embeddings for knowledge graph content and provides a deterministic zero-vector fallback.
 * @doc.layer product
 * @doc.pattern Generator
 */
public final class EmbeddingGenerator {

  private static final int DEFAULT_DIMENSION = 1536;

  private final EmbeddingService embeddingService;
  private final int dimension;

  public EmbeddingGenerator(EmbeddingService embeddingService) {
    this(embeddingService, DEFAULT_DIMENSION);
  }

  public EmbeddingGenerator(EmbeddingService embeddingService, int dimension) {
    this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
    this.dimension = Math.max(1, dimension);
  }

  public Promise<float[]> generate(String text) {
    Objects.requireNonNull(text, "text");
    return embeddingService
        .createEmbedding(text)
        .then(result -> Promise.of(normalize(result.getVector())), error -> Promise.of(zeroVector()));
  }

  private float[] normalize(float[] vector) {
    if (vector.length == 0) {
      return zeroVector();
    }
    if (vector.length == dimension) {
      return vector.clone();
    }
    return Arrays.copyOf(vector, dimension);
  }

  private float[] zeroVector() {
    return new float[dimension];
  }
}
