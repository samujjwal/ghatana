package com.ghatana.yappc.infrastructure.datacloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ghatana.products.yappc.domain.Identifiable;

import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Durable YAPPC artifact content entity stored in Data Cloud
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactContentEntity(
    UUID id,
    String path,
    String version,
    String content,
    int size,
    String tenantId,
    long createdAt
) implements Identifiable<UUID> {

    @Override
    public UUID getId() {
        return id;
    }
}