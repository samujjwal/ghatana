package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for IntegrationTestWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates integration tests for services and APIs input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IntegrationTestWriterInput(@NotNull String serviceId, @NotNull String apiSpec, @NotNull Map<String, Object> testScenarios) {
  public IntegrationTestWriterInput {
    if (serviceId == null || serviceId.isEmpty()) {
      throw new IllegalArgumentException("serviceId cannot be null or empty");
    }
    if (apiSpec == null || apiSpec.isEmpty()) {
      throw new IllegalArgumentException("apiSpec cannot be null or empty");
    }
    if (testScenarios == null) {
      testScenarios = Map.of();
    }
  }
}
