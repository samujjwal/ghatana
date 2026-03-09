package com.ghatana.yappc.sdlc.agent.specialists;

import java.time.Instant;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from PublishArchitectureSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Architecture documentation publishing results
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record PublishArchitectureOutput(
    @NotNull String architectureId,
    @NotNull String version,
    @NotNull Map<String, String> publishedUrls,
    int documentsPublished,
    int diagramsPublished,
    int contractsPublished,
    @NotNull Instant publishedAt,
    boolean success,
    String message) {}
