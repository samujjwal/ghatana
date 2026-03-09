/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.catalog;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declarative definition of an agent loaded from a catalog YAML file.
 *
 * <p>Maps 1:1 to the YAML agent definition schema used by product catalogs
 * (e.g. {@code products/yappc/config/agents/definitions/*.yaml}). This is the
 * in-memory representation after parsing.
 *
 * <p>Renamed from {@code AgentDefinition} (in this package) to eliminate the
 * name collision with {@link com.ghatana.agent.framework.config.AgentDefinition},
 * which is the runtime blueprint type. This class is the YAML parse target;
 * that class is the fully typed agent specification.
 *
 * <h2>Schema fields</h2>
 * <ul>
 *   <li>{@code id} — unique agent identifier</li>
 *   <li>{@code name} — human-readable display name</li>
 *   <li>{@code version} — semantic version</li>
 *   <li>{@code metadata} — level, domain, tags</li>
 *   <li>{@code generator} — type and steps</li>
 *   <li>{@code capabilities} — declared capability tags</li>
 *   <li>{@code routing} — input/output types</li>
 *   <li>{@code delegation} — can_delegate_to, escalates_to</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Parsed agent entry from catalog YAML
 * @doc.layer framework
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
@Value
@Builder(toBuilder = true)
public class CatalogAgentEntry {

    /** Unique agent identifier (e.g. "sentinel", "code-weaver"). */
    String id;

    /** Human-readable name. */
    String name;

    /** Semantic version. */
    @Builder.Default
    String version = "1.0.0";

    /** Description of the agent's purpose. */
    String description;

    /** Metadata: level (strategic / expert / worker), domain, tags. */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    /** Generator definition (type, steps). */
    @Builder.Default
    Map<String, Object> generator = Map.of();

    /** Memory configuration. */
    @Builder.Default
    Map<String, Object> memory = Map.of();

    /** Tool bindings. */
    @Builder.Default
    List<String> tools = List.of();

    /** Declared capability tags. */
    @Builder.Default
    Set<String> capabilities = Set.of();

    /** Routing: input_types, output_types. */
    @Builder.Default
    Map<String, Object> routing = Map.of();

    /** Delegation: can_delegate_to, escalates_to. */
    @Builder.Default
    Map<String, Object> delegation = Map.of();

    /** Governance rules. */
    @Builder.Default
    Map<String, Object> governance = Map.of();

    /** Performance SLAs. */
    @Builder.Default
    Map<String, Object> performance = Map.of();

    /** The catalog (product) this entry belongs to. */
    String catalogId;

    // ═══════════════════════════════════════════════════════════════════════════
    // Convenience accessors
    // ═══════════════════════════════════════════════════════════════════════════

    /** Returns the agent level from metadata (e.g. "strategic", "expert", "worker"). */
    public String getLevel() {
        Object level = metadata.get("level");
        return level != null ? level.toString() : "worker";
    }

    /** Returns the agent domain from metadata. */
    public String getDomain() {
        Object domain = metadata.get("domain");
        return domain != null ? domain.toString() : "general";
    }
}
