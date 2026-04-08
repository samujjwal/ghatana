/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-process implementation of {@link AgentInteractionProtocol}.
 *
 * <p>Messages are delivered to a registered {@link Function} dispatcher
 * that maps {@code (agentId, message) → Promise<AgentResponse>}. Events are
 * fan-out to all registered {@link AgentEventHandler}s whose
 * {@link AgentEventHandler#supportedEventType()} matches.
 *
 * @doc.type class
 * @doc.purpose In-process inter-agent interaction protocol
 * @doc.layer platform
 * @doc.pattern Protocol / Default Implementation
 */
public final class DefaultAgentInteractionProtocol implements AgentInteractionProtocol {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentInteractionProtocol.class);

    /** Maps recipientAgentId → message handler function. */
    private final Map<String, Function<AgentMessage, Promise<AgentResponse>>> messageHandlers =
            new ConcurrentHashMap<>();

    /** Maps eventType → event handler. */
    private final Map<String, AgentEventHandler> eventHandlers = new ConcurrentHashMap<>();

    /**
     * Registers a direct message handler for a specific agent ID.
     *
     * <p>Used by agent implementations to declare they can receive messages.
     *
     * @param agentId the recipient agent ID
     * @param handler the message handler
     */
    public void registerMessageHandler(
            @NotNull String agentId,
            @NotNull Function<AgentMessage, Promise<AgentResponse>> handler) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(handler, "handler");
        messageHandlers.put(agentId, handler);
    }

    @Override
    @NotNull
    public Promise<AgentResponse> send(@NotNull AgentMessage message) {
        Objects.requireNonNull(message, "message");
        String recipientId = message.recipientAgentId();
        if (recipientId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("Point-to-point send requires a non-null recipientAgentId"));
        }
        Function<AgentMessage, Promise<AgentResponse>> handler = messageHandlers.get(recipientId);
        if (handler == null) {
            log.warn("No message handler registered for agent [{}], returning NOT_FOUND", recipientId);
            return Promise.of(AgentResponse.error(
                    message.messageId(), recipientId,
                    "No handler registered for agent: " + recipientId));
        }
        return handler.apply(message);
    }

    @Override
    @NotNull
    public Promise<Void> publish(@NotNull AgentEvent event) {
        Objects.requireNonNull(event, "event");
        AgentEventHandler handler = eventHandlers.get(event.eventType());
        if (handler == null) {
            log.debug("No handler for event type [{}], event dropped", event.eventType());
            return Promise.complete();
        }
        return handler.handle(event);
    }

    @Override
    public void registerHandler(@NotNull AgentEventHandler handler) {
        Objects.requireNonNull(handler, "handler");
        eventHandlers.put(handler.supportedEventType(), handler);
    }
}
