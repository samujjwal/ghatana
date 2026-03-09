package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;

/**
 * Input for Artifact Publish Specialist.
 *
 * @doc.type record
 * @doc.purpose Input for publishing build artifacts
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ArtifactPublishInput(
    String buildId, String version, List<String> artifacts, String repository, boolean snapshot) {}
