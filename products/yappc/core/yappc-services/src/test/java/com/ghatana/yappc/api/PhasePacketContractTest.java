package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Contract tests for the serialized PhaseCockpitPacket payload
 * @doc.layer api
 * @doc.pattern ContractTest
 */
@DisplayName("PhasePacket Contract Tests")
class PhasePacketContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Serialized packet exposes degraded dependency details and artifact provenance")
    void serializedPacket_includesDegradedDetailsAndArtifactProvenance() throws Exception {
        PhasePacket packet = new PhasePacket(
                "generate",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("actor-1", "Owner", "owner", true, true),
                "generate",
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.CapabilityModel(true, true, true, false, true, false, true),
                List.of(),
                new PhasePacket.PhaseReadiness(false, "run", List.of("Data Cloud service unavailable"), 0.0, true),
                List.of(new PhasePacket.RequiredArtifact("shape-model", "SHAPE_MODEL", "Shape Model", "Canonical shape", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "intent-spec",
                        "INTENT_SPEC",
                        "v1",
                        "Intent Spec",
                        Instant.parse("2026-05-26T10:15:30Z"),
                        "actor-1",
                        "evidence-1"
                )),
                List.of(),
                List.of(),
                List.of(),
                new PhasePacket.PlatformRunStatus(
                        "run-1",
                        "DEGRADED",
                        "data-cloud-aep",
                        Instant.parse("2026-05-26T10:15:30Z"),
                        null,
                        "trace-1",
                        List.of("evidence-1")
                ),
                List.of(),
                new PhasePacket.DashboardActionClassification(null, List.of("all"), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(false, "degraded", List.of("Data Cloud service unavailable")),
                        new PhasePacket.GenerationHealth(false, "degraded", null, List.of("Data Cloud service unavailable")),
                        new PhasePacket.RuntimeHealth(false, "degraded", null, List.of("Data Cloud service unavailable")),
                        new PhasePacket.AgentGovernanceHealth(
                                true,
                                "healthy",
                                "policy-approved",
                                "evidence-backed",
                                List.of("learn-run-1"),
                                List.of())
                ),
                new PhasePacket.DegradedPacketDetails(
                        "DATA_CLOUD",
                        "Project state not found",
                        "projects",
                        "Restore Data Cloud project state access and retry phase packet retrieval.",
                        List.of("phase-readiness", "phase-actions")
                ),
                1_779_791_730_000L,
                "corr-1"
        );

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(packet));

        assertThat(json.at("/degradedDetails/dependency").asText()).isEqualTo("DATA_CLOUD");
        assertThat(json.at("/degradedDetails/truthSource").asText()).isEqualTo("projects");
        assertThat(json.at("/degradedDetails/recoveryAction").asText()).contains("Restore Data Cloud");
        assertThat(json.at("/degradedDetails/impactedFeatures/0").asText()).isEqualTo("phase-readiness");
        assertThat(json.at("/completedArtifacts/0/version").asText()).isEqualTo("v1");
        assertThat(json.at("/completedArtifacts/0/evidenceId").asText()).isEqualTo("evidence-1");
        assertThat(json.at("/correlationId").asText()).isEqualTo("corr-1");
        assertThat(json.at("/healthSignals/runtime/status").asText()).isEqualTo("degraded");
        assertThat(json.at("/healthSignals/agentGovernance/governanceState").asText()).isEqualTo("policy-approved");
        assertThat(json.at("/healthSignals/agentGovernance/evidenceIds/0").asText()).isEqualTo("learn-run-1");
    }
}
