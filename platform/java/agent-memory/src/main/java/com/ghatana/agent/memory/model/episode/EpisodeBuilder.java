package com.ghatana.agent.memory.model.episode;

import com.ghatana.agent.memory.model.*;
import com.ghatana.agent.framework.memory.Episode;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Factory for creating {@link EnhancedEpisode} instances.
 * Provides migration helpers for converting legacy Episode objects.
 *
 * @doc.type factory
 * @doc.purpose Episode migration and construction
 * @doc.layer agent-memory
 */
public final class EpisodeBuilder {

    private EpisodeBuilder() {
        // Static factory — no instances
    }

    /**
     * Converts a legacy {@link Episode} from agent-framework into an
     * {@link EnhancedEpisode} with default provenance and validity.
     *
     * @param legacy  The legacy Episode
     * @param tenantId Tenant ID for multi-tenancy
     * @return Enhanced episode with canonical envelope
     */
    @NotNull
    public static EnhancedEpisode fromLegacyEpisode(
            @NotNull Episode legacy,
            @NotNull String tenantId) {

        return EnhancedEpisode.builder()
                .id(legacy.getId() != null ? legacy.getId() : UUID.randomUUID().toString())
                .createdAt(legacy.getTimestamp())
                .updatedAt(legacy.getTimestamp())
                .provenance(Provenance.builder()
                        .source("legacy:episode")
                        .confidenceSource(Provenance.ConfidenceSource.TOOL_OUTPUT)
                        .agentId(legacy.getAgentId())
                        .build())
                .validity(Validity.builder()
                        .confidence(1.0)
                        .status(ValidityStatus.ACTIVE)
                        .build())
                .tenantId(tenantId)
                .agentId(legacy.getAgentId())
                .turnId(legacy.getTurnId())
                .timestamp(legacy.getTimestamp())
                .input(legacy.getInput())
                .output(legacy.getOutput())
                .action(legacy.getAction())
                .context(legacy.getContext())
                .tags(legacy.getTags())
                .reward(legacy.getReward() != null ? legacy.getReward() : 0.0)
                // Note: legacy embedding is String, EnhancedEpisode uses float[] — skip
                .build();
    }
}
