package com.ghatana.yappc.sdlc.agent.specialists;

import java.time.Instant;
import java.util.List;

/**
 * Output from Artifact Publish Specialist.
 *
 * @doc.type record
 * @doc.purpose Result of artifact publishing
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ArtifactPublishOutput(
    String buildId,
    String version,
    List<String> publishedArtifacts,
    String repositoryUrl,
    Instant publishedAt,
    boolean success,
    String message) {}
