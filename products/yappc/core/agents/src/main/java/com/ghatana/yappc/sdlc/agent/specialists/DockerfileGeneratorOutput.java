package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DockerfileGenerator agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates optimized Dockerfiles for services output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DockerfileGeneratorOutput(@NotNull String dockerfileContent, @NotNull List<String> stages, @NotNull Map<String, Object> metadata) {
  public DockerfileGeneratorOutput {
    if (dockerfileContent == null || dockerfileContent.isEmpty()) {
      throw new IllegalArgumentException("dockerfileContent cannot be null or empty");
    }
    if (stages == null) {
      stages = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
