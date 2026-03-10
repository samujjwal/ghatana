package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ReactComponentWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates React/TypeScript components from specifications input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReactComponentWriterInput(@NotNull String componentName, @NotNull String specification, @NotNull Map<String, Object> props) {
  public ReactComponentWriterInput {
    if (componentName == null || componentName.isEmpty()) {
      throw new IllegalArgumentException("componentName cannot be null or empty");
    }
    if (specification == null || specification.isEmpty()) {
      throw new IllegalArgumentException("specification cannot be null or empty");
    }
    if (props == null) {
      props = Map.of();
    }
  }
}
