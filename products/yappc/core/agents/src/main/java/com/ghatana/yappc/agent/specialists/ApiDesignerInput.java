package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ApiDesigner agent.
 *
 * @doc.type record
 * @doc.purpose Expert API designer for REST, GraphQL and contract-first design input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ApiDesignerInput(@NotNull String serviceId, @NotNull String apiSpec, @NotNull String designApproach, @NotNull Map<String, Object> context) {
  public ApiDesignerInput {
    if (serviceId == null || serviceId.isEmpty()) {
      throw new IllegalArgumentException("serviceId cannot be null or empty");
    }
    if (apiSpec == null || apiSpec.isEmpty()) {
      throw new IllegalArgumentException("apiSpec cannot be null or empty");
    }
    if (designApproach == null || designApproach.isEmpty()) {
      throw new IllegalArgumentException("designApproach cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
