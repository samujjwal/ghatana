/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Records Kernel-governed agent lifecycle action evidence to the trace ledger.
 *
 * @doc.type class
 * @doc.purpose Trace ledger writer for product-development agent action evidence
 * @doc.layer agent-runtime
 * @doc.pattern Service
 */
public final class AgentLifecycleActionTraceRecorder {

    private final AgentTraceLedger ledger;

    public AgentLifecycleActionTraceRecorder(@NotNull AgentTraceLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    @NotNull
    public Promise<TraceEvent> record(
            @NotNull AgentLifecycleActionTraceRecord record,
            @NotNull String previousHash) {
        Objects.requireNonNull(record, "record");
        TraceEventBuilder builder = new TraceEventBuilder(
                record.correlationId(),
                record.agentId(),
                record.tenantId(),
                Objects.requireNonNull(previousHash, "previousHash"));
        TraceEvent event = builder.build(
                eventTypeFor(record.outcome()),
                summaryFor(record),
                record.toPayload());
        return ledger.append(event).map($ -> event);
    }

    @NotNull
    private static TraceEventType eventTypeFor(@NotNull AgentLifecycleActionOutcome outcome) {
        return switch (outcome) {
            case RECEIVED -> TraceEventType.DISPATCH_REQUESTED;
            case POLICY_DENIED -> TraceEventType.ACTION_DENIED;
            case REQUIRES_APPROVAL -> TraceEventType.APPROVAL_REQUESTED;
            case ACCEPTED -> TraceEventType.ACTION_EXECUTED;
            case FAILED -> TraceEventType.INVARIANT_VIOLATED;
            case FALLBACK_RECORDED -> TraceEventType.VERIFICATION_CHECKED;
        };
    }

    @NotNull
    private static String summaryFor(@NotNull AgentLifecycleActionTraceRecord record) {
        return "Agent lifecycle action %s for ProductUnit %s: %s"
                .formatted(record.action(), record.productUnitId(), record.outcome().name());
    }
}
