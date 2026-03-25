package com.ghatana.agent;

import java.util.Set;

/**
 * Metadata describing an Agent's basic capabilities.
 *
 * @param name The display name of the agent
 * @param role The role or function of the agent
 * @param description A description of what the agent does
 * @param supportedTaskTypes Set of task types this agent can handle
 * @param tools Set of tool names this agent can use
 *
 * @deprecated Use {@link AgentDescriptor} instead.
 *             {@code AgentDescriptor} provides richer identity metadata including
 *             type, subtype, SLAs, determinism guarantees, state mutability,
 *             and failure modes. {@link AgentDescriptor#toCapabilities()} provides
 *             backward-compatible conversion.
 *
 * @doc.type record
 * @doc.purpose Deprecated simple agent metadata — use AgentDescriptor for new code
 * @doc.layer core
 * @doc.pattern ValueObject
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public record AgentCapabilities(
    String name,
    String role,
    String description,
    Set<String> supportedTaskTypes,
    Set<String> tools
) {}
