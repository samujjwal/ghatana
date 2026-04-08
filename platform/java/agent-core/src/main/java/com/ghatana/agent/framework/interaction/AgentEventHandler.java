/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Handles a specific class of {@link AgentEvent} published on the platform event bus.
 *
 * <p>Implementations are registered with {@link AgentInteractionProtocol} and are
 * called whenever an event matching the supported type arrives.
 *
 * @doc.type interface
 * @doc.purpose SPI for handling inter-agent events
 * @doc.layer platform
 * @doc.pattern Observer / Handler
 */
public interface AgentEventHandler {

    /**
     * Returns the event type this handler is interested in.
     *
     * <p>The returned string is compared with {@link AgentEvent#eventType()} for routing.
     */
    @NotNull
    String supportedEventType();

    /**
     * Handles the given event.
     *
     * @param event the event to handle; never null
     * @return a {@link Promise} completing when handling is done (result is ignored)
     */
    @NotNull
    Promise<Void> handle(@NotNull AgentEvent event);
}
