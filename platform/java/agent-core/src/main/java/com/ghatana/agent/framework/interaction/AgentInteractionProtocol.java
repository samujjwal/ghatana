/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * SPI for the inter-agent communication channel.
 *
 * <p>Provides message passing and event publication capabilities.
 * Implementations may route messages over in-process queues, an event bus,
 * or a remote transport. All operations are non-blocking.
 *
 * @doc.type interface
 * @doc.purpose SPI for inter-agent message passing and event publication
 * @doc.layer platform
 * @doc.pattern Protocol / Message Bus
 */
public interface AgentInteractionProtocol {

    /**
     * Sends a point-to-point message to the specified recipient agent and
     * returns the response produced by that agent.
     *
     * @param message the message to send; never null
     * @return a {@link Promise} resolving to the agent's response
     */
    @NotNull
    Promise<AgentResponse> send(@NotNull AgentMessage message);

    /**
     * Publishes an event to all registered {@link AgentEventHandler}s whose
     * {@link AgentEventHandler#supportedEventType()} matches the event type.
     *
     * @param event the event to publish; never null
     * @return a {@link Promise} completing when all handlers have been invoked
     */
    @NotNull
    Promise<Void> publish(@NotNull AgentEvent event);

    /**
     * Registers an event handler for a specific event type.
     *
     * <p>If a handler for the same event type is already registered it is replaced.
     *
     * @param handler the handler to register; never null
     */
    void registerHandler(@NotNull AgentEventHandler handler);
}
