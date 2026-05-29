package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import com.ghatana.yappc.services.platform.PlatformPolicy;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Queries governance decisions for phase packets.
 *
 * @doc.type class
 * @doc.purpose Queries governance decisions for phase packets from platform integration
 * @doc.layer service
 * @doc.pattern Service
 */
public final class PhaseGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(PhaseGovernanceService.class);

    private final PlatformIntegrationClient platformIntegrationClient;

    public PhaseGovernanceService(@NotNull PlatformIntegrationClient platformIntegrationClient) {
        this.platformIntegrationClient = Objects.requireNonNull(platformIntegrationClient, "platformIntegrationClient");
    }

    List<PhasePacket.GovernanceRecord> queryGovernanceRecords(
            String phase,
            String projectId,
            String workspaceId,
            String tenantId,
            String correlationId
    ) {
        try {
            PlatformPolicy.PolicyRequest policyRequest =
                    new PlatformPolicy.PolicyRequest(
                            "PHASE_GOVERNANCE",
                            Map.of("phase", phase, "projectId", projectId, "workspaceId", workspaceId),
                            tenantId,
                            workspaceId,
                            projectId
                    );

            PlatformPolicy policyDecision = platformIntegrationClient.evaluatePolicy(policyRequest);

            List<PhasePacket.GovernanceRecord> records = new ArrayList<>();
            if (!policyDecision.isAllowed()) {
                for (String reason : policyDecision.deniedReasons()) {
                    records.add(new PhasePacket.GovernanceRecord(
                            "POLICY_DENIAL",
                            "POLICY_DENIAL",
                            "DENIED",
                            "system",
                            Instant.now(),
                            Map.of(
                                    "phase", phase,
                                    "projectId", projectId,
                                    "workspaceId", workspaceId,
                                    "reason", reason
                            ),
                            policyDecision.policyId()
                    ));
                }
            } else {
                records.add(new PhasePacket.GovernanceRecord(
                        "POLICY_APPROVAL",
                        "POLICY_APPROVAL",
                        "APPROVED",
                        "system",
                        Instant.now(),
                        Map.of(
                                "phase", phase,
                                "projectId", projectId,
                                "workspaceId", workspaceId
                        ),
                        policyDecision.policyId()
                ));
            }

            return records;
        } catch (Exception exception) {
            log.error(
                    "Error querying governance records: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                    tenantId,
                    workspaceId,
                    projectId,
                    phase,
                    correlationId,
                    exception
            );
            return List.of(new PhasePacket.GovernanceRecord(
                    "GOVERNANCE_QUERY_FAILED",
                    "POLICY_DENIAL",
                    "DENIED",
                    "system",
                    Instant.now(),
                    Map.of(
                            "phase", phase,
                            "projectId", projectId,
                            "workspaceId", workspaceId,
                            "tenantId", tenantId,
                            "correlationId", correlationId,
                            "reason", exception.getClass().getSimpleName()),
                    "governance-query-failed:" + projectId + ":" + phase));
        }
    }
}
