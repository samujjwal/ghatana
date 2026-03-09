package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from CloudResourceDiscovery agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that discovers and inventories cloud resources output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloudResourceDiscoveryOutput(@NotNull String discoveryId, @NotNull List<Map<String, Object>> resources, int totalCount, @NotNull Map<String, Object> metadata) {
  public CloudResourceDiscoveryOutput {
    if (discoveryId == null || discoveryId.isEmpty()) {
      throw new IllegalArgumentException("discoveryId cannot be null or empty");
    }
    if (resources == null) {
      resources = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
