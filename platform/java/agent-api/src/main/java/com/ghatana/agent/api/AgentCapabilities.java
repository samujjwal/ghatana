/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

import java.util.Set;

/**
 * Simple agent metadata for backward compatibility.
 *
 * @deprecated Use {@link AgentDescriptor} for richer metadata.
 *
 * @doc.type record
 * @doc.purpose Legacy agent capability metadata
 * @doc.layer core
 * @doc.pattern ValueObject
 */
@Deprecated
public record AgentCapabilities(
    String name,
    String role,
    String description,
    Set<String> supportedTaskTypes,
    Set<String> tools
) {

    /**
     * Creates capabilities with no tools.
     */
    public static AgentCapabilities of(String name, String role, String description,
                                       Set<String> supportedTaskTypes) {
        return new AgentCapabilities(name, role, description, supportedTaskTypes, Set.of());
    }
}
