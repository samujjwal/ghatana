package com.ghatana.agent.runtime.replay;

import java.util.Objects;

/**
 * Decision produced before replaying an agent execution.
 *
 * @doc.type record
 * @doc.purpose Describes whether replay may use recorded output or requires live-model opt-in
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record AgentReplayDecision(
        boolean allowed,
        AgentReplayMode mode,
        String reason
) {

    public AgentReplayDecision {
        Objects.requireNonNull(mode, "mode");
        reason = reason == null ? "" : reason;
    }
}
