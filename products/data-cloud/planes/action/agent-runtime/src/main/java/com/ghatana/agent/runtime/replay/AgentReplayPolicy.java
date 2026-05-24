package com.ghatana.agent.runtime.replay;

import java.util.Objects;

/**
 * Policy controlling how an agent execution may be replayed.
 *
 * @doc.type record
 * @doc.purpose Captures deterministic and live-model replay permissions for an agent invocation
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record AgentReplayPolicy(
        AgentReplayMode mode,
        boolean liveModelReplayAllowed,
        String reason
) {

    public AgentReplayPolicy {
        Objects.requireNonNull(mode, "mode");
        if (mode == AgentReplayMode.LIVE_MODEL_OPT_IN && !liveModelReplayAllowed) {
            throw new IllegalArgumentException("live model replay requires explicit opt-in");
        }
        reason = reason == null ? "" : reason;
    }

    public static AgentReplayPolicy recordedOutput() {
        return new AgentReplayPolicy(AgentReplayMode.RECORDED_OUTPUT, false, "");
    }
}
