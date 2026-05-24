package com.ghatana.agent.runtime.replay;

/**
 * Replay behavior for agent execution.
 *
 * @doc.type enum
 * @doc.purpose Enumerates replay modes for deterministic and opt-in live agent execution
 * @doc.layer agent-runtime
 * @doc.pattern Enumeration
 */
public enum AgentReplayMode {
    RECORDED_OUTPUT,
    RECORDED_PROMPT_AND_RETRIEVAL,
    LIVE_MODEL_OPT_IN
}
