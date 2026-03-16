package com.ghatana.appplatform.ems.domain;

import java.time.Instant;

/**
 * @doc.type      Record
 * @doc.purpose   Tracks the state of a FIX protocol session with a counterparty exchange.
 * @doc.layer     Domain
 * @doc.pattern   Immutable Value Object
 */
public record FixSession(
        String sessionId,
        String senderCompId,
        String targetCompId,
        String fixVersion,
        FixSessionState state,
        int nextSendSeqNum,
        int nextExpectedSeqNum,
        Instant logonAt,
        Instant lastHeartbeatAt
) {
    public FixSession withState(FixSessionState newState) {
        return new FixSession(sessionId, senderCompId, targetCompId, fixVersion,
                newState, nextSendSeqNum, nextExpectedSeqNum, logonAt, lastHeartbeatAt);
    }

    public FixSession incrementSend() {
        return new FixSession(sessionId, senderCompId, targetCompId, fixVersion,
                state, nextSendSeqNum + 1, nextExpectedSeqNum, logonAt, lastHeartbeatAt);
    }

    public FixSession withHeartbeat(Instant now) {
        return new FixSession(sessionId, senderCompId, targetCompId, fixVersion,
                state, nextSendSeqNum, nextExpectedSeqNum, logonAt, now);
    }
}
