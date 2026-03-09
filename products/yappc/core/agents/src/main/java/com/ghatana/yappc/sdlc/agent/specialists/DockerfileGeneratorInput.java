package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DockerfileGenerator agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates optimized Dockerfiles for services input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DockerfileGeneratorInput(@NotNull String serviceId, @NotNull String technology, @NotNull Map<String, Object> config) {
  public DockerfileGeneratorInput {
    if (serviceId == null || serviceId.isEmpty()) {
      throw new IllegalArgumentException("serviceId cannot be null or empty");
    }
    if (technology == null || technology.isEmpty()) {
      throw new IllegalArgumentException("technology cannot be null or empty");
    }
    if (config == null) {
      config = Map.of();
    }
  }
}
