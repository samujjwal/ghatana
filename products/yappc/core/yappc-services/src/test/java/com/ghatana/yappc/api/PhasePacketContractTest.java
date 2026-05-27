package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(degradedPacketFixture()));

        assertThat(json).isEqualTo(readGoldenJson("golden/phase-packet.degraded.json"));
        assertThat(json.at("/degradedDetails/dependency").asText()).isEqualTo("DATA_CLOUD");
        assertThat(json.at("/degradedDetails/truthSource").asText()).isEqualTo("projects");
        assertThat(json.at("/degradedDetails/recoveryAction").asText()).contains("Restore Data Cloud");
        assertThat(json.at("/degradedDetails/impactedFeatures/0").asText()).isEqualTo("phase-readiness");
        assertThat(json.at("/completedArtifacts/0/version").asText()).isEqualTo("v1");
        assertThat(json.at("/completedArtifacts/0/evidenceId").asText()).isEqualTo("evidence-1");
        assertThat(json.at("/availableActions/0/category").asText()).isEqualTo("phase-transition");
        assertThat(json.at("/availableActions/0/severity").asText()).isEqualTo("high");
        assertThat(json.at("/availableActions/0/confirmationRequired").asBoolean()).isTrue();
        assertThat(json.at("/availableActions/0/idempotencyKey").asText()).isEqualTo("phase.advance");
        assertThat(json.at("/availableActions/0/auditType").asText()).isEqualTo("phase.advance.requested");
        assertThat(json.at("/readiness/estimatedReadyIn").asText()).isEqualTo("Blocked");
        assertThat(json.at("/readiness/estimatedReadyInHours").asInt()).isEqualTo(24);
        assertThat(json.at("/readiness/predictionConfidence").asDouble()).isEqualTo(0.35);
        assertThat(json.at("/activityFeed/0/eventType").asText()).isEqualTo("PHASE_ACTION_EXECUTED");
        assertThat(json.at("/activityFeed/0/success").asBoolean()).isFalse();
        assertThat(json.at("/activityFeed/0/outcome").asText()).isEqualTo("FAILURE");
        assertThat(json.at("/activityFeed/0/correlationId").asText()).isEqualTo("corr-activity-1");
        assertThat(json.at("/correlationId").asText()).isEqualTo("corr-1");
        assertThat(json.at("/healthSignals/runtime/status").asText()).isEqualTo("degraded");
        assertThat(json.at("/healthSignals/agentGovernance/governanceState").asText()).isEqualTo("policy-approved");
        assertThat(json.at("/healthSignals/agentGovernance/evidenceIds/0").asText()).isEqualTo("learn-run-1");
    }

    private static PhasePacket degradedPacketFixture() {
        return new PhasePacket(
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
                new PhasePacket.PhaseReadiness(
                        false,
                        "run",
                        List.of("Data Cloud service unavailable"),
                        0.0,
                        true,
                        "Blocked",
                        24,
                        0.35
                ),
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
                List.of(new PhasePacket.ActivityFeedEntry(
                        "audit-1",
                        "PHASE_ACTION_EXECUTED",
                        "phase.advance",
                        "Phase advance failed policy check",
                        "actor-1",
                        Instant.parse("2026-05-26T10:15:31Z"),
                        "ERROR",
                        "PHASE_ACTION_EXECUTED",
                        false,
                        "FAILURE",
                        "corr-activity-1"
                )),
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
                List.of(new PhasePacket.PhaseAction(
                        "advance-phase",
                        "phaseAction.advancePhase.label",
                        "phaseAction.advancePhase.description",
                        false,
                        "phaseAction.disabled.blockersMustResolve",
                        "phase:advance",
                        "phase-transition",
                        "high",
                        true,
                        "phase.advance",
                        "phase.advance.requested",
                        java.util.Map.of("nextPhase", "run")
                )),
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
    }

    private static JsonNode readGoldenJson(String resourcePath) throws Exception {
        URI uri = PhasePacketContractTest.class.getClassLoader()
                .getResource(resourcePath)
                .toURI();
        return MAPPER.readTree(Files.readString(Path.of(uri), StandardCharsets.UTF_8));
    }
}
