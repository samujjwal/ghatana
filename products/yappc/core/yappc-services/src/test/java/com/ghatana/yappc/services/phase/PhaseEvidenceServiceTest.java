package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.api.PlatformEvidence;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhaseEvidenceService}.
 *
 * @doc.type class
 * @doc.purpose Verifies evidence query remains scoped by tenant/workspace/project/phase
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PhaseEvidenceService")
class PhaseEvidenceServiceTest {

    @Mock
    private PlatformIntegrationClient platformIntegrationClient;

    @Test
    @DisplayName("queryPhaseEvidence sends phase-native scoped query filters")
    void queryPhaseEvidence_sendsPhaseScopedQueryFilters() {
        when(platformIntegrationClient.searchEvidence(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new PlatformEvidence.SearchResult(
                        "evidence-1",
                        "RUN_EVIDENCE",
                        "run failure trace",
                        0.9,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Map.of("scope", "project"))));

        PhaseEvidenceService service = new PhaseEvidenceService(platformIntegrationClient);

        List<PhasePacket.PhaseEvidence> evidence = service.queryPhaseEvidence(
                "run",
                "project-1",
                "workspace-1",
                "tenant-1",
                "corr-1");

        ArgumentCaptor<PlatformEvidence.SearchQuery> queryCaptor = ArgumentCaptor.forClass(PlatformEvidence.SearchQuery.class);
        verify(platformIntegrationClient).searchEvidence(queryCaptor.capture());
        PlatformEvidence.SearchQuery query = queryCaptor.getValue();

        assertThat(query.projectId()).isEqualTo("project-1");
        assertThat(query.workspaceId()).isEqualTo("workspace-1");
        assertThat(query.evidenceTypes()).containsExactly("RUN_EVIDENCE");
        assertThat(query.filters()).containsEntry("phase", "run");
        assertThat(query.filters()).containsEntry("projectId", "project-1");
        assertThat(query.filters()).containsEntry("workspaceId", "workspace-1");
        assertThat(query.filters()).containsEntry("tenantId", "tenant-1");
        assertThat(evidence).hasSize(1);
    }
}
