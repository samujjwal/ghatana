/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.config.materializer;

import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.release.AgentReleaseBuilder;
import com.ghatana.agent.release.AgentReleaseState;
import org.jetbrains.annotations.NotNull;

/**
 * Materializes an {@link AgentReleaseBuilder} from an {@link AgentDefinition}.
 *
 * <p>This class is responsible for creating a release draft builder rooted in
 * the exact canonical agent definition.
 *
 * @doc.type class
 * @doc.purpose Materializes AgentReleaseBuilder from AgentDefinition
 * @doc.layer platform
 * @doc.pattern Materializer
 */
public final class ReleaseDraftMaterializer {

    private ReleaseDraftMaterializer() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a release draft builder rooted in an exact canonical agent definition.
     *
     * @param definition the agent definition
     * @param releaseVersion the release version
     * @param createdBy the user who created the release
     * @return release draft builder
     */
    @NotNull
    public static AgentReleaseBuilder materialize(
            @NotNull AgentDefinition definition,
            @NotNull String releaseVersion,
            @NotNull String createdBy) {
        return new AgentReleaseBuilder()
                .agentId(definition.getId())
                .specVersion(definition.getVersion())
                .releaseVersion(releaseVersion)
                .state(AgentReleaseState.DRAFT)
                .specDigest(definition.canonicalDigest())
                .createdBy(createdBy);
    }
}
