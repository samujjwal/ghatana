package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from E2eTestRunner agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that executes end-to-end test suites and reports results output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record E2eTestRunnerOutput(@NotNull String runId, int passed, int failed, @NotNull List<String> failures, @NotNull Map<String, Object> metadata) {
  public E2eTestRunnerOutput {
    if (runId == null || runId.isEmpty()) {
      throw new IllegalArgumentException("runId cannot be null or empty");
    }
    if (failures == null) {
      failures = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
