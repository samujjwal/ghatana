package com.ghatana.agent;

import java.util.Set;

/**
 * Metadata describing an Agent's capabilities.
 *
 * @param name The display name of the agent
 * @param role The role or function of the agent
 * @param description A description of what the agent does
 * @param supportedTaskTypes Set of task types this agent can handle
 * @param tools Set of tool names this agent can use
 *
 * @doc.type record
 * @doc.purpose Agent metadata and capabilities
 * @doc.layer core
 */
public record AgentCapabilities(
    String name,
    String role,
    String description,
    Set<String> supportedTaskTypes,
    Set<String> tools
) {}
