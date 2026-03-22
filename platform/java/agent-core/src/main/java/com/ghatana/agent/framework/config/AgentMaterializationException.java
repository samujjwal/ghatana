/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

/**
 * Thrown when agent YAML materialization fails.
 *
 * @doc.type class
 * @doc.purpose Agent materialization exception
 * @doc.layer platform
 *
 * @author Ghatana AI Platform
 * @since 3.0.0
 */
public class AgentMaterializationException extends RuntimeException {

    public AgentMaterializationException(String message) {
        super(message);
    }

    public AgentMaterializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
