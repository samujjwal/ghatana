/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.services.lifecycle.StageConfigLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Resolves required lifecycle artifacts for phase packet assembly.
 *
 * @doc.type class
 * @doc.purpose Converts lifecycle stage configuration into phase packet required artifact records
 * @doc.layer services
 * @doc.pattern Collaborator
 */
public final class PhaseRequiredArtifactProvider {

    private static final Logger log = LoggerFactory.getLogger(PhaseRequiredArtifactProvider.class);

    private final StageConfigLoader stageConfigLoader;

    public PhaseRequiredArtifactProvider(@NotNull StageConfigLoader stageConfigLoader) {
        this.stageConfigLoader = Objects.requireNonNull(stageConfigLoader, "stageConfigLoader");
    }

    public List<PhasePacket.RequiredArtifact> queryRequiredArtifacts(
            @NotNull String phase,
            @NotNull String projectId
    ) {
        try {
            PhaseType phaseType;
            try {
                phaseType = PhaseType.valueOf(phase.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid phase type: {}, defaulting to INTENT", phase);
                phaseType = PhaseType.INTENT;
            }

            var stageOpt = stageConfigLoader.findById(phaseType.name().toLowerCase());
            if (stageOpt.isEmpty()) {
                log.warn("No stage spec found for phase: {}", phase);
                return List.of();
            }

            return stageOpt.get().getArtifacts().stream()
                    .<PhasePacket.RequiredArtifact>map(artifactKey -> new PhasePacket.RequiredArtifact(
                            artifactKey,
                            artifactKey,
                            artifactKey,
                            "Required lifecycle artifact: " + artifactKey,
                            false
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Error querying required artifacts: phase={}, projectId={}", phase, projectId, e);
            return List.of();
        }
    }
}
