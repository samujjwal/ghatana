package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import com.ghatana.yappc.services.metrics.BusinessMetrics;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import com.ghatana.yappc.services.platform.PlatformPolicy;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhasePacketServiceImpl} covering normal packet building,
 * degraded-state scenarios, and optional-dependency fallback behavior.
 *
 * @doc.type class
 * @doc.purpose Unit tests for PhasePacketServiceImpl degraded behavior and core packet building
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PhasePacketServiceImpl Tests")
class PhasePacketServiceImplTest extends EventloopTestBase {

    @Mock private DataCloudClient dataCloudClient;
    @Mock private YappcArtifactRepository artifactRepository;
    @Mock private PhaseGateValidator phaseGateValidator;
    @Mock private PolicyEngine policyEngine;
    @Mock private CapabilityEvaluationService capabilityEvaluationService;
    @Mock private TransitionConfigLoader transitionConfigLoader;
    @Mock private PlatformIntegrationClient platformIntegrationClient;
    @Mock private PlatformRunStatusService platformRunStatusService;
    @Mock private BusinessMetrics metrics;
    @Mock private AuditService auditService;

    private static final String TENANT_ID = "tenant-test";
    private static final String PROJECT_ID = "project-test";
    private static final String WORKSPACE_ID = "workspace-test";
    private static final String PHASE = "INTENT";
    private static final String CORRELATION_ID = "corr-test-123";

    private Principal testPrincipal;
    private PhasePacketServiceImpl service;

    @BeforeEach
    void setUp() {
        testPrincipal = new Principal("test-user", List.of("EDITOR"), TENANT_ID);

        service = new PhasePacketServiceImpl(
                dataCloudClient,
                artifactRepository,
                phaseGateValidator,
                policyEngine,
                capabilityEvaluationService,
                transitionConfigLoader,
                platformIntegrationClient,
                metrics,
                auditService,
                null,
                platformRunStatusService,
                null
        );

        // Common happy-path stubs — lenient because not every test exercises the full path
        lenient().when(dataCloudClient.findById(anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(Optional.of(
                        DataCloudClient.Entity.of(PROJECT_ID, "projects",
                                Map.of("name", "Test Project", "tier", "PRO", "status", "active")))));

        lenient().when(phaseGateValidator.validate(anyString(), any(), any(PhaseGateValidator.PhaseGateContext.class)))
                .thenReturn(Promise.of(new PhaseGateValidator.ValidationResult(
                        com.ghatana.yappc.domain.PhaseType.INTENT, true, List.of())));

        lenient().when(capabilityEvaluationService.evaluate(any()))
                .thenReturn(Promise.of(CapabilityEvaluationService.CapabilityModel.allGranted()));

        lenient().when(platformIntegrationClient.searchEvidence(any())).thenReturn(List.of());
        lenient().when(platformIntegrationClient.evaluatePolicy(any()))
                .thenReturn(new PlatformPolicy("policy-1", true, List.of(), Map.of(), Instant.now()));

        lenient().when(artifactRepository.listVersions(anyString(), any()))
                .thenReturn(Promise.of(List.of()));

        lenient().when(auditService.queryByPhase(anyString(), anyString(), any(), any()))
                .thenReturn(Promise.of(List.of()));
        lenient().when(platformRunStatusService.findLatest(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(Optional.empty()));
    }

    // ─── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Happy path: builds full PhasePacket with the supplied correlationId")
    void happyPath_buildPhasePacket_returnsPacketWithCorrectCorrelationId() {
        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet).isNotNull();
        assertThat(packet.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(packet.phase()).isEqualTo(PHASE);
        assertThat(packet.projectId()).isEqualTo(PROJECT_ID);
        assertThat(packet.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("Happy path: packet contains non-null capabilities when evaluation succeeds")
    void happyPath_buildPhasePacket_capabilitiesPresentWhenEvaluationSucceeds() {
        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet).isNotNull();
        assertThat(packet.capabilities()).isNotNull();
    }

    // ─── Correlation ID handling ────────────────────────────────────────────────

    @Test
    @DisplayName("Null correlationId: auto-generates a non-null correlationId in the packet")
    void nullCorrelationId_generatesCorrelationId_inPacket() {
        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, null));

        assertThat(packet).isNotNull();
        assertThat(packet.correlationId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("Supplied correlationId is propagated verbatim to the returned packet")
    void suppliedCorrelationId_propagatedToPacket() {
        String suppliedId = "explicit-corr-abc";

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, suppliedId));

        assertThat(packet).isNotNull();
        assertThat(packet.correlationId()).isEqualTo(suppliedId);
    }

    // ─── Degraded project-state scenarios ─────────────────────────────────────

    @Test
    @DisplayName("Missing project state: returns degraded packet when DataCloud finds no entity")
    void missingProjectState_buildDegradedPacket_withNotFoundReason() {
        lenient().when(dataCloudClient.findById(anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(Optional.empty()));

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet).isNotNull();
        assertThat(packet.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(packet.blockers()).isNotEmpty();
        // Degraded packet should not allow advancement
        assertThat(packet.readiness()).isNotNull();
        assertThat(packet.readiness().canAdvance()).isFalse();
    }

    @Test
    @DisplayName("DataCloud query failure: returns degraded packet when findById throws")
    void dataCloudQueryFailure_buildDegradedPacket_withQueryFailedReason() {
        lenient().when(dataCloudClient.findById(anyString(), anyString(), anyString()))
                .thenReturn(Promise.ofException(new RuntimeException("DataCloud unreachable")));

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet).isNotNull();
        assertThat(packet.blockers()).isNotEmpty();
        assertThat(packet.readiness().canAdvance()).isFalse();
    }

    @Test
    @DisplayName("Degraded packet: projectName is the degraded sentinel, not a real project name")
    void missingProjectState_degradedPacket_hasDegradedSentinelProjectName() {
        lenient().when(dataCloudClient.findById(anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(Optional.empty()));

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet).isNotNull();
        // Degraded packet uses explicit sentinel values
        assertThat(packet.projectName()).isEqualTo("degraded-project");
    }

    // ─── Phase blocker propagation ─────────────────────────────────────────────

    @Test
    @DisplayName("Phase blockers from gate validator appear in the returned packet")
    void phaseBlockers_presentInPacket_whenGateValidatorReturnsBlockers() {
        lenient().when(phaseGateValidator.validate(anyString(), any(), any(PhaseGateValidator.PhaseGateContext.class)))
                .thenReturn(Promise.of(new PhaseGateValidator.ValidationResult(
                        com.ghatana.yappc.domain.PhaseType.INTENT,
                        false,
                        List.of("missing-artifact:spec.yaml", "entry-criterion:intent-review-approved"))));

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet).isNotNull();
        assertThat(packet.blockers()).isNotEmpty();
        assertThat(packet.readiness().canAdvance()).isFalse();
    }

    // ─── Optional dependency fallbacks ────────────────────────────────────────

    @Test
    @DisplayName("Null auditService: DegradedAuditService used — no NullPointerException thrown")
    void nullAuditService_degradedAuditServiceUsed_noException() {
        PhasePacketServiceImpl serviceWithoutAudit = new PhasePacketServiceImpl(
                dataCloudClient,
                artifactRepository,
                phaseGateValidator,
                policyEngine,
                capabilityEvaluationService,
                transitionConfigLoader,
                platformIntegrationClient,
                metrics,
                null,  // auditService null → DegradedAuditService wired
                null,
                platformRunStatusService,
                null
        );

        PhasePacket packet = runPromise(() ->
                serviceWithoutAudit.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet).isNotNull();
        assertThat(packet.correlationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    @DisplayName("Null previewRuntimeService: DegradedPreviewRuntimeService used — health signals reflect degraded state")
    void nullPreviewRuntimeService_degradedHealthSignals_inPacket() {
        // service fixture already has null previewRuntimeService (set in @BeforeEach)
        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet).isNotNull();
        assertThat(packet.healthSignals()).isNotNull();
        // Degraded preview runtime → preview health is not healthy
        assertThat(packet.healthSignals().preview().isHealthy()).isFalse();
    }

    @Test
    @DisplayName("Happy path: packet includes latest platform run status when available")
    void happyPath_platformRunStatusPresentWhenResolved() {
        PhasePacket.PlatformRunStatus runStatus = new PhasePacket.PlatformRunStatus(
                "run-123",
                "RUNNING",
                "data-cloud-aep",
                Instant.parse("2026-05-01T00:00:00Z"),
                null,
                "trace-123",
                List.of("ev-1"));
        lenient().when(platformRunStatusService.findLatest(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(Optional.of(runStatus)));

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet.platformRunStatus()).isNotNull();
        assertThat(packet.platformRunStatus().runId()).isEqualTo("run-123");
        assertThat(packet.platformRunStatus().evidenceIds()).containsExactly("ev-1");
    }

    @Test
    @DisplayName("Evidence provider failure returns degraded evidence and blocks advancement")
    void evidenceProviderFailure_returnsDegradedEvidenceAndBlocksAdvance() {
        when(platformIntegrationClient.searchEvidence(any()))
                .thenThrow(new RuntimeException("AEP evidence unavailable"));

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet.evidence())
                .anySatisfy(evidence -> {
                    assertThat(evidence.id()).isEqualTo("EVIDENCE_QUERY_FAILED");
                    assertThat(evidence.type()).isEqualTo("SYSTEM_DEGRADED");
                });
        assertThat(packet.readiness().canAdvance()).isFalse();
        assertThat(packet.readiness().missingPrerequisites()).contains("Phase evidence unavailable");
    }

    @Test
    @DisplayName("Governance provider failure fails closed with policy denial")
    void governanceProviderFailure_returnsPolicyDenial() {
        when(platformIntegrationClient.evaluatePolicy(any()))
                .thenThrow(new RuntimeException("policy service unavailable"));

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet.governance())
                .anySatisfy(record -> {
                    assertThat(record.id()).isEqualTo("GOVERNANCE_QUERY_FAILED");
                    assertThat(record.type()).isEqualTo("POLICY_DENIAL");
                    assertThat(record.outcome()).isEqualTo("DENIED");
                });
        assertThat(packet.readiness().canAdvance()).isFalse();
        assertThat(packet.readiness().missingPrerequisites()).contains("Policy approval");
    }

    @Test
    @DisplayName("Project feature flags are surfaced in packet")
    void projectFeatureFlags_areSurfacedInPacket() {
        lenient().when(dataCloudClient.findById(anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(Optional.of(
                        DataCloudClient.Entity.of(PROJECT_ID, "projects",
                                Map.of(
                                        "name", "Test Project",
                                        "tier", "ENTERPRISE",
                                        "enabledPhaseFlags", List.of("custom.phase.flag"),
                                        "status", "active")))));

        PhasePacket packet = runPromise(() ->
                service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        assertThat(packet.enabledPhaseFlags()).contains("custom.phase.flag", "phase.report.export");
    }

    @Test
    @DisplayName("Phase gate validator receives typed gate context")
    void phaseGateValidatorReceivesTypedGateContext() {
        lenient().when(dataCloudClient.findById(anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(Optional.of(
                        DataCloudClient.Entity.of(PROJECT_ID, "projects",
                                Map.of(
                                        "name", "Test Project",
                                        "tier", "ENTERPRISE",
                                        "enabledPhaseFlags", List.of("custom.phase.flag"),
                                        "status", "active",
                                        "conditions", Map.of("intent.reviewed", true))))));

        runPromise(() -> service.buildPhasePacket(PHASE, PROJECT_ID, WORKSPACE_ID, testPrincipal, CORRELATION_ID));

        ArgumentCaptor<PhaseGateValidator.PhaseGateContext> captor =
                ArgumentCaptor.forClass(PhaseGateValidator.PhaseGateContext.class);
        verify(phaseGateValidator).validate(anyString(), any(), captor.capture());

        PhaseGateValidator.PhaseGateContext context = captor.getValue();
        assertThat(context.enabledFlags()).contains("custom.phase.flag", "phase.advance");
        assertThat(context.conditionVerdicts()).containsEntry("intent.reviewed", true);
        assertThat(context.conditionVerdicts()).containsKeys(
                "evidence.available",
                "policyAllowed",
                "previewHealthy",
                "generationHealthy",
                "runtimeHealthy");
    }

}
