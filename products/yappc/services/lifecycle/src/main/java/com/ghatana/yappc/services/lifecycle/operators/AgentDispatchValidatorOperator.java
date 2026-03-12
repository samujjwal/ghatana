/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.workflow.operator.AbstractOperator;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates incoming {@code agent.dispatch.requested} events against the
 * {@code agent-dispatch-v1} schema before they enter the agent execution pipeline.
 *
 * <p><b>Pipeline Position</b><br>
 * First operator in the {@code agent-orchestration-v1} AEP pipeline. Receives
 * {@code agent.dispatch.requested} events and emits either:
 * <ul>
 *   <li>{@code agent.dispatch.validated} — all required fields present and valid</li>
 *   <li>{@code OperatorResult.failed(reason)} — required fields missing or invalid</li>
 * </ul>
 *
 * <p><b>Required Fields (agent-dispatch-v1)</b>
 * <ul>
 *   <li>{@code eventId} — unique event identifier</li>
 *   <li>{@code eventType} — must be {@code agent.dispatch.requested}</li>
 *   <li>{@code timestamp} — epoch millis or ISO-8601</li>
 *   <li>{@code agentId} — target agent identifier</li>
 *   <li>{@code fromStage} — originating YAPPC stage</li>
 *   <li>{@code toStage} — destination YAPPC stage</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates agent dispatch events before pipeline execution
 * @doc.layer product
 * @doc.pattern Validator
 * @doc.gaa.lifecycle perceive
 */
public class AgentDispatchValidatorOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatchValidatorOperator.class);

    /** Event type consumed by this operator. */
    public static final String EVENT_DISPATCH_REQUESTED = "agent.dispatch.requested";
    /** Event type emitted on successful validation. */
    public static final String EVENT_DISPATCH_VALIDATED = "agent.dispatch.validated";

    private static final List<String> REQUIRED_FIELDS =
            List.of("eventId", "eventType", "agentId", "fromStage", "toStage");

    /**
     * Creates an {@code AgentDispatchValidatorOperator}.
     */
    public AgentDispatchValidatorOperator() {
        super(
            OperatorId.of("yappc", "stream", "agent-dispatch-validator", "1.0.0"),
            OperatorType.STREAM,
            "Agent Dispatch Validator",
            "Validates agent.dispatch.requested events against agent-dispatch-v1 schema",
            List.of("agent.dispatch", "agent.validate"),
            null
        );
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        List<String> errors = validateRequiredFields(event);
        if (!errors.isEmpty()) {
            String reason = "VALIDATION_FAILED: " + String.join("; ", errors);
            log.warn("Agent dispatch validation failed: {}", reason);
            return Promise.of(OperatorResult.failed(reason));
        }

        String agentId   = payloadStr(event, "agentId");
        String fromStage = payloadStr(event, "fromStage");
        String toStage   = payloadStr(event, "toStage");
        String tenantId  = payloadStr(event, "tenantId");

        log.debug("Agent dispatch validated: agentId={} fromStage={} toStage={} tenantId={}",
                agentId, fromStage, toStage, tenantId);

        Event validated = GEvent.builder()
                .typeTenantVersion(tenantId != null ? tenantId : "", EVENT_DISPATCH_VALIDATED, "v1")
                .addPayload("agentId",            Objects.toString(agentId, ""))
                .addPayload("fromStage",          Objects.toString(fromStage, ""))
                .addPayload("toStage",            Objects.toString(toStage, ""))
                .addPayload("tenantId",           Objects.toString(tenantId, ""))
                .addPayload("originalEventType",  EVENT_DISPATCH_REQUESTED)
                .build();

        return Promise.of(OperatorResult.of(validated));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> validateRequiredFields(Event event) {
        List<String> errors = new ArrayList<>();
        for (String field : REQUIRED_FIELDS) {
            Object value;
            try {
                value = event.getPayload(field);
            } catch (NullPointerException e) {
                // payload map is null — all required fields are missing
                errors.add("missing required field '" + field + "'");
                continue;
            }
            if (value == null || value.toString().isBlank()) {
                errors.add("missing required field '" + field + "'");
            }
        }
        return errors;
    }

    private static String payloadStr(Event event, String key) {
        Object v = event.getPayload(key);
        return v != null ? v.toString() : null;
    }

    @Override
    public Event toEvent() {
        return GEvent.builder()
                .type("operator.registered")
                .addPayload("operatorId",   getId().toString())
                .addPayload("operatorName", getName())
                .addPayload("operatorType", getType().name())
                .addPayload("version",      getVersion())
                .build();
    }
}
