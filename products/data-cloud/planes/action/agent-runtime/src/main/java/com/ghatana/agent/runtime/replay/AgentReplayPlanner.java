package com.ghatana.agent.runtime.replay;

import java.util.Objects;

/**
 * Determines whether an agent execution can be replayed safely.
 *
 * @doc.type class
 * @doc.purpose Applies replay policy to recorded agent execution snapshots
 * @doc.layer agent-runtime
 * @doc.pattern Policy
 */
public final class AgentReplayPlanner {

    private AgentReplayPlanner() {}

    public static AgentReplayDecision decide(AgentExecutionRecord record) {
        Objects.requireNonNull(record, "record");
        AgentReplayPolicy policy = record.replayPolicy();
        if (policy.mode() == AgentReplayMode.RECORDED_OUTPUT) {
            return record.hasRecordedOutput()
                ? new AgentReplayDecision(true, AgentReplayMode.RECORDED_OUTPUT, "recorded output available")
                : new AgentReplayDecision(false, AgentReplayMode.RECORDED_OUTPUT, "recorded output missing");
        }
        if (policy.mode() == AgentReplayMode.LIVE_MODEL_OPT_IN) {
            return policy.liveModelReplayAllowed()
                ? new AgentReplayDecision(true, AgentReplayMode.LIVE_MODEL_OPT_IN, "live model replay explicitly allowed")
                : new AgentReplayDecision(false, AgentReplayMode.LIVE_MODEL_OPT_IN, "live model replay requires opt-in");
        }
        return new AgentReplayDecision(true, AgentReplayMode.RECORDED_PROMPT_AND_RETRIEVAL, "prompt and retrieval snapshots available");
    }
}
