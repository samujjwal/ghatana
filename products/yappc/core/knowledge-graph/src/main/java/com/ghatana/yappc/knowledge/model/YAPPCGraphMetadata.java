package com.ghatana.yappc.knowledge.model;

import java.time.Instant;
import java.util.Map;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for yappc graph metadata

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record YAPPCGraphMetadata(
    String tenantId,
    String projectId,
    String workspaceId,
    String createdBy,
    Instant createdAt,
    Instant updatedAt,
    String version,
    Map<String, String> labels
) {}
