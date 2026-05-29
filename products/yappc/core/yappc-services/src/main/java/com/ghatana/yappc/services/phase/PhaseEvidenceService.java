package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.api.PlatformEvidence;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Queries phase evidence records from platform integrations.
 *
 * @doc.type class
 * @doc.purpose Queries phase evidence records from platform integrations
 * @doc.layer service
 * @doc.pattern Service
 */
public final class PhaseEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(PhaseEvidenceService.class);

    private final PlatformIntegrationClient platformIntegrationClient;

    public PhaseEvidenceService(@NotNull PlatformIntegrationClient platformIntegrationClient) {
        this.platformIntegrationClient = Objects.requireNonNull(platformIntegrationClient, "platformIntegrationClient");
    }

    List<PhasePacket.PhaseEvidence> queryPhaseEvidence(
            String phase,
            String projectId,
            String workspaceId,
            String tenantId,
            String correlationId
    ) {
        try {
            PlatformEvidence.SearchQuery query = new PlatformEvidence.SearchQuery(
                    phase + " phase evidence",
                    projectId,
                    workspaceId,
                    List.of(phase.toUpperCase() + "_EVIDENCE"),
                    Instant.now().minus(java.time.Duration.ofDays(30)),
                    Instant.now(),
                    Map.of("phase", phase, "projectId", projectId, "workspaceId", workspaceId, "tenantId", tenantId)
            );

            List<PlatformEvidence.SearchResult> searchResults = platformIntegrationClient.searchEvidence(query);
            List<PhasePacket.PhaseEvidence> evidenceList = new ArrayList<>();
            for (PlatformEvidence.SearchResult result : searchResults) {
                Map<String, Object> metadata = new HashMap<>();
                if (result.metadata() != null) {
                    result.metadata().forEach(metadata::put);
                }
                evidenceList.add(new PhasePacket.PhaseEvidence(
                        result.evidenceId(),
                        result.evidenceType(),
                        result.contentPreview(),
                        "PLATFORM",
                        result.timestamp(),
                        metadata,
                        result.evidenceId()
                ));
            }
            return evidenceList;
        } catch (Exception exception) {
            log.error(
                    "Error querying phase evidence: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                    tenantId,
                    workspaceId,
                    projectId,
                    phase,
                    correlationId,
                    exception
            );
            return List.of(new PhasePacket.PhaseEvidence(
                    "EVIDENCE_QUERY_FAILED",
                    "SYSTEM_DEGRADED",
                    "Phase evidence unavailable",
                    "Evidence service failure blocks unsafe phase advancement until runtime truth is available.",
                    Instant.now(),
                    Map.of(
                            "phase", phase,
                            "projectId", projectId,
                            "workspaceId", workspaceId,
                            "tenantId", tenantId,
                            "correlationId", correlationId,
                            "reason", exception.getClass().getSimpleName()),
                    "evidence-query-failed:" + exception.getClass().getSimpleName()));
        }
    }
}
