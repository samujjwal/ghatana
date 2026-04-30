package com.ghatana.yappc.infrastructure.datacloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ghatana.yappc.domain.Identifiable;

import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Durable YAPPC artifact metadata entity stored in Data Cloud
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactMetadataEntity(
    UUID id,
    String path,
    Map<String, String> metadata,
    String tenantId,
    long createdAt
) implements Identifiable<UUID> {

    @Override
    public UUID getId() {
        return id;
    }
}