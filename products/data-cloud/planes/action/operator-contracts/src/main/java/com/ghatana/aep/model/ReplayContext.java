package com.ghatana.aep.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Carries replay identity, mode, and recorded agent-output metadata into EventOperators
 * @doc.layer product
 * @doc.pattern Contract
 */
public record ReplayContext(
        ReplayMode mode,
        Optional<String> replayId,
        Optional<String> sourceOffset,
        Optional<String> targetOffset,
        Map<String, Object> recordedOutputs) {

    public ReplayContext {
        mode = Objects.requireNonNull(mode, "mode must not be null");
        replayId = replayId != null ? replayId : Optional.empty();
        sourceOffset = sourceOffset != null ? sourceOffset : Optional.empty();
        targetOffset = targetOffset != null ? targetOffset : Optional.empty();
        recordedOutputs = Map.copyOf(recordedOutputs != null ? recordedOutputs : Map.of());
    }

    public enum ReplayMode {
        LIVE,
        DETERMINISTIC,
        RECORDED_AGENT_OUTPUT,
        RECORDED_PROMPT_AND_RETRIEVAL,
        LIVE_MODEL_OPT_IN,
        BLOCKED
    }
}
