package com.ghatana.yappc.kg.service.domain;

import java.util.Set;

/**
 * <b>REMOVED</b> — Replaced by {@link com.ghatana.yappc.knowledge.model.YAPPCGraphNode}.
 *
 * <p>This tombstone record produces a compile error if any code still references it.
 * Migrate all usages to the {@code com.ghatana.yappc.knowledge.model} package.
 *
 * @doc.type record
 * @doc.purpose Tombstone for removed graph node record
 * @doc.layer core
 * @doc.pattern ValueObject
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public record GraphNode(
    String id,
    String label,
    Set<String> types,
    Set<String> tags
) {}
