package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for CloudResourceDiscovery agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that discovers and inventories cloud resources input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloudResourceDiscoveryInput(@NotNull String cloudAccountId, @NotNull String provider, @NotNull List<String> regions) {
  public CloudResourceDiscoveryInput {
    if (cloudAccountId == null || cloudAccountId.isEmpty()) {
      throw new IllegalArgumentException("cloudAccountId cannot be null or empty");
    }
    if (provider == null || provider.isEmpty()) {
      throw new IllegalArgumentException("provider cannot be null or empty");
    }
    if (regions == null) {
      regions = List.of();
    }
  }
}
