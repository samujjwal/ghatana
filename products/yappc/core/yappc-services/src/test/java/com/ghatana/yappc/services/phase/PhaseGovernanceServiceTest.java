package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import com.ghatana.yappc.services.platform.PlatformPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhaseGovernanceService}.
 *
 * @doc.type class
 * @doc.purpose Verifies governance records carry action/phase/policy identifiers for authorization decisions
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PhaseGovernanceService")
class PhaseGovernanceServiceTest {

    @Mock
    private PlatformIntegrationClient platformIntegrationClient;

    @Test
    @DisplayName("queryGovernanceRecords includes policy and action identifiers on denied decisions")
    void queryGovernanceRecords_includesPolicyAndActionIdentifiersOnDeniedDecision() {
        when(platformIntegrationClient.evaluatePolicy(any()))
                .thenReturn(new PlatformPolicy(
                        "policy-phase-advance",
                        false,
                        List.of("missing-approval"),
                        Map.of(),
                        Instant.now()));

        PhaseGovernanceService service = new PhaseGovernanceService(platformIntegrationClient);

        List<PhasePacket.GovernanceRecord> records = service.queryGovernanceRecords(
                "run",
                "project-1",
                "workspace-1",
                "tenant-1",
                "corr-1");

        assertThat(records).hasSize(1);
        PhasePacket.GovernanceRecord record = records.getFirst();
        assertThat(record.outcome()).isEqualTo("DENIED");
        assertThat(record.policyDecisionId()).isEqualTo("policy-phase-advance");
        assertThat(record.metadata()).containsEntry("actionId", "phase.advance");
        assertThat(record.metadata()).containsEntry("phase", "run");
        assertThat(record.metadata()).containsEntry("policyId", "policy-phase-advance");
    }
}
