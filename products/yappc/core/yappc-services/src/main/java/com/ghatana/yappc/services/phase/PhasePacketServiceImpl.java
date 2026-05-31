/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.core.runtime.PreviewRuntimeService;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.api.AdminFeatureFlagController;
import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.api.PlatformEvidence;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import com.ghatana.yappc.services.metrics.BusinessMetrics;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import com.ghatana.yappc.services.platform.PlatformPolicy;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Production implementation of PhasePacketService.
 *
 * <p>Builds phase packets by aggregating project state from DataCloud,
 * phase-specific blockers from PhaseGateValidator, evidence from AEP integration,
 * governance records from PolicyEngine, and available actions based on role/permissions.
 *
 * @doc.type class
 * @doc.purpose Production implementation of PhasePacketService
 * @doc.layer services
 * @doc.pattern Service
 */
public final class PhasePacketServiceImpl implements PhasePacketService {

    private static final Logger log = LoggerFactory.getLogger(PhasePacketServiceImpl.class);

    private final DataCloudClient dataCloudClient;
    private final YappcArtifactRepository artifactRepository;
    private final PhaseGateValidator phaseGateValidator;
    private final CapabilityEvaluationService capabilityEvaluationService;
    private final PlatformIntegrationClient platformIntegrationClient;
    @Nullable
    private final BusinessMetrics metrics;
    private final AuditService auditService;
    private final PlatformRunStatusService platformRunStatusService;
    private final PhaseActionAuthorizationService phaseActionAuthorizationService;
    private final PhaseRequiredArtifactProvider requiredArtifactProvider;
    private final DegradedPhasePacketFactory degradedPhasePacketFactory;
    private final PhaseFeatureFlagProvider phaseFeatureFlagProvider;
    private final PhaseProjectStateService phaseProjectStateService;
    private final PhaseEvidenceService phaseEvidenceService;
    private final PhaseGovernanceService phaseGovernanceService;
    private final PhaseActivityFeedService phaseActivityFeedService;
    private final PhaseBlockerMapper phaseBlockerMapper;
    private final PhaseGateContextFactory phaseGateContextFactory;
    private final PhaseReadinessEvaluator phaseReadinessEvaluator;
    private final PhaseHealthSignalProvider phaseHealthSignalProvider;
    private final LearningWorkflowService learningWorkflowService;
    private final EvolutionWorkflowService evolutionWorkflowService;
    private final PhasePacketAssembler phasePacketAssembler;

    public PhasePacketServiceImpl(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull YappcArtifactRepository artifactRepository,
            @NotNull PhaseGateValidator phaseGateValidator,
            @NotNull PolicyEngine policyEngine,
            @NotNull CapabilityEvaluationService capabilityEvaluationService,
            @NotNull TransitionConfigLoader transitionConfigLoader,
            @NotNull PlatformIntegrationClient platformIntegrationClient,
            @Nullable BusinessMetrics metrics,
            @NotNull AuditService auditService,
            @NotNull PreviewRuntimeService previewRuntimeService,
            @NotNull PlatformRunStatusService platformRunStatusService,
            @NotNull PhaseActionAuthorizationService phaseActionAuthorizationService,
            @NotNull PhaseRequiredArtifactProvider requiredArtifactProvider,
            @NotNull DegradedPhasePacketFactory degradedPhasePacketFactory,
            @NotNull PhaseFeatureFlagProvider phaseFeatureFlagProvider,
            @NotNull PhaseProjectStateService phaseProjectStateService,
            @NotNull PhaseEvidenceService phaseEvidenceService,
            @NotNull PhaseGovernanceService phaseGovernanceService,
            @NotNull PhaseActivityFeedService phaseActivityFeedService,
            @NotNull PhaseBlockerMapper phaseBlockerMapper,
            @NotNull PhaseGateContextFactory phaseGateContextFactory,
            @NotNull PhaseReadinessEvaluator phaseReadinessEvaluator,
            @NotNull PhaseHealthSignalProvider phaseHealthSignalProvider,
            @NotNull LearningWorkflowService learningWorkflowService,
            @NotNull EvolutionWorkflowService evolutionWorkflowService,
            @NotNull PhasePacketAssembler phasePacketAssembler
            ) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository");
        this.phaseGateValidator = Objects.requireNonNull(phaseGateValidator, "phaseGateValidator");
        Objects.requireNonNull(policyEngine, "policyEngine");
        this.capabilityEvaluationService = Objects.requireNonNull(capabilityEvaluationService, "capabilityEvaluationService");
            Objects.requireNonNull(transitionConfigLoader, "transitionConfigLoader");
        this.platformIntegrationClient = Objects.requireNonNull(platformIntegrationClient, "platformIntegrationClient");
        this.metrics = metrics;
        this.auditService = Objects.requireNonNull(auditService, "auditService");
            Objects.requireNonNull(previewRuntimeService, "previewRuntimeService");
        this.platformRunStatusService = Objects.requireNonNull(platformRunStatusService, "platformRunStatusService");
        this.phaseActionAuthorizationService = Objects.requireNonNull(phaseActionAuthorizationService, "phaseActionAuthorizationService");
        this.requiredArtifactProvider = Objects.requireNonNull(requiredArtifactProvider, "requiredArtifactProvider");
        this.degradedPhasePacketFactory = Objects.requireNonNull(degradedPhasePacketFactory, "degradedPhasePacketFactory");
            this.phaseFeatureFlagProvider = Objects.requireNonNull(phaseFeatureFlagProvider, "phaseFeatureFlagProvider");
            this.phaseProjectStateService = Objects.requireNonNull(phaseProjectStateService, "phaseProjectStateService");
            this.phaseEvidenceService = Objects.requireNonNull(phaseEvidenceService, "phaseEvidenceService");
            this.phaseGovernanceService = Objects.requireNonNull(phaseGovernanceService, "phaseGovernanceService");
            this.phaseActivityFeedService = Objects.requireNonNull(phaseActivityFeedService, "phaseActivityFeedService");
            this.phaseBlockerMapper = Objects.requireNonNull(phaseBlockerMapper, "phaseBlockerMapper");
            this.phaseGateContextFactory = Objects.requireNonNull(phaseGateContextFactory, "phaseGateContextFactory");
            this.phaseReadinessEvaluator = Objects.requireNonNull(phaseReadinessEvaluator, "phaseReadinessEvaluator");
            this.phaseHealthSignalProvider = Objects.requireNonNull(phaseHealthSignalProvider, "phaseHealthSignalProvider");
                this.learningWorkflowService = Objects.requireNonNull(learningWorkflowService, "learningWorkflowService");
                this.evolutionWorkflowService = Objects.requireNonNull(evolutionWorkflowService, "evolutionWorkflowService");
            this.phasePacketAssembler = Objects.requireNonNull(phasePacketAssembler, "phasePacketAssembler");
    }

                public PhasePacketServiceImpl(
                    @NotNull DataCloudClient dataCloudClient,
                    @NotNull YappcArtifactRepository artifactRepository,
                    @NotNull PhaseGateValidator phaseGateValidator,
                    @NotNull PolicyEngine policyEngine,
                    @NotNull CapabilityEvaluationService capabilityEvaluationService,
                    @NotNull TransitionConfigLoader transitionConfigLoader,
                    @NotNull PlatformIntegrationClient platformIntegrationClient,
                    @Nullable BusinessMetrics metrics,
                    @NotNull AuditService auditService,
                    @NotNull PreviewRuntimeService previewRuntimeService,
                    @NotNull PlatformRunStatusService platformRunStatusService,
                    @NotNull PhaseActionAuthorizationService phaseActionAuthorizationService,
                    @NotNull PhaseRequiredArtifactProvider requiredArtifactProvider,
                    @NotNull DegradedPhasePacketFactory degradedPhasePacketFactory,
                    @NotNull PhaseFeatureFlagProvider phaseFeatureFlagProvider,
                    @NotNull PhaseProjectStateService phaseProjectStateService,
                    @NotNull PhaseEvidenceService phaseEvidenceService,
                    @NotNull PhaseGovernanceService phaseGovernanceService,
                    @NotNull PhaseActivityFeedService phaseActivityFeedService,
                    @NotNull PhaseBlockerMapper phaseBlockerMapper,
                    @NotNull PhaseGateContextFactory phaseGateContextFactory,
                    @NotNull PhaseReadinessEvaluator phaseReadinessEvaluator,
                    @NotNull PhaseHealthSignalProvider phaseHealthSignalProvider,
                    @NotNull PhasePacketAssembler phasePacketAssembler
                ) {
                this(
                    dataCloudClient,
                    artifactRepository,
                    phaseGateValidator,
                    policyEngine,
                    capabilityEvaluationService,
                    transitionConfigLoader,
                    platformIntegrationClient,
                    metrics,
                    auditService,
                    previewRuntimeService,
                    platformRunStatusService,
                    phaseActionAuthorizationService,
                    requiredArtifactProvider,
                    degradedPhasePacketFactory,
                    phaseFeatureFlagProvider,
                    phaseProjectStateService,
                    phaseEvidenceService,
                    phaseGovernanceService,
                    phaseActivityFeedService,
                    phaseBlockerMapper,
                    phaseGateContextFactory,
                    phaseReadinessEvaluator,
                    phaseHealthSignalProvider,
                    LearningWorkflowService.noop(),
                    EvolutionWorkflowService.unavailable(),
                    phasePacketAssembler
                );
                }

    @Override
    public Promise<PhasePacket> buildPhasePacket(
            @NotNull String phase,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull Principal principal,
            String correlationId
    ) {
        long startTime = System.currentTimeMillis();
        String effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("Building phase packet: phase={}, projectId={}, workspaceId={}, tenantId={}, correlationId={}",
            phase, projectId, workspaceId, principal.getTenantId(), effectiveCorrelationId);

        String tenantId = principal.getTenantId();
        // Query project state from DataCloud
        return queryProjectState(phase, projectId, workspaceId, tenantId, effectiveCorrelationId)
            .then(projectState -> {
                // Check if project is in degraded state
                boolean isDegraded = Boolean.TRUE.equals(projectState.get("degraded"));
                if (isDegraded) {
                    String degradedReason = (String) projectState.get("degradedReason");
                    log.warn(
                            "Project in degraded state: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}, reason={}",
                            tenantId,
                            workspaceId,
                            projectId,
                            phase,
                            effectiveCorrelationId,
                            degradedReason);

                    // Return degraded packet with explicit degradation reason
                    if (metrics != null) {
                        metrics.recordPhaseGateValidation(tenantId, phase, "DEGRADED", System.currentTimeMillis() - startTime);
                        metrics.recordPhasePacketBuild(
                                tenantId,
                                workspaceId,
                                projectId,
                                phase,
                                "build",
                                "DEGRADED",
                                true,
                                degradedReason,
                                effectiveCorrelationId);
                    }
                    return Promise.of(degradedPhasePacketFactory.build(
                        phase,
                        projectId,
                        workspaceId,
                        principal,
                        effectiveCorrelationId,
                        degradedReason
                    ));
                }
                PhasePacket.TenantTier tier = determineTenantTier(projectState, principal);
                Set<String> enabledFlags = phaseFeatureFlagProvider.determineEnabledFlags(projectState, tier);
                List<PhasePacket.RequiredArtifact> requiredArtifacts =
                        requiredArtifactProvider.queryRequiredArtifacts(phase, projectId);
                List<PhasePacket.PhaseEvidence> evidence =
                        queryPhaseEvidence(phase, projectId, workspaceId, tenantId, effectiveCorrelationId);
                List<PhasePacket.GovernanceRecord> governance =
                        queryGovernanceRecords(phase, projectId, workspaceId, tenantId, effectiveCorrelationId);
                recordEvidenceAndGovernanceMetrics(
                        tenantId,
                        workspaceId,
                        projectId,
                        phase,
                        evidence,
                        governance,
                        effectiveCorrelationId);
                    PhasePacket.HealthSignals healthSignals = phaseHealthSignalProvider.build(phase, projectId, projectState);

                return queryCompletedArtifacts(phase, projectId, principal.getTenantId())
                    .then(completedArtifacts -> queryPhaseBlockers(
                            phase,
                            projectId,
                            workspaceId,
                            projectState,
                            requiredArtifacts,
                            completedArtifacts,
                            evidence,
                            governance,
                            healthSignals,
                            enabledFlags,
                            tenantId,
                            effectiveCorrelationId
                    ).then(blockers -> {
                        PhasePacket.PhaseReadiness readiness = phaseReadinessEvaluator.calculate(
                                phase,
                                projectId,
                                blockers,
                                requiredArtifacts,
                                completedArtifacts,
                                evidence,
                                governance,
                                healthSignals,
                                projectState
                        );

                        return queryActivityFeed(phase, projectId, principal.getTenantId())
                        .then(activityFeed ->
                            capabilityEvaluationService.evaluate(
                                new CapabilityEvaluationService.CapabilityEvaluationRequest(
                                    tenantId,
                                    principal.getName(),
                                     workspaceId,
                                     projectId,
                                     null,
                                     "phase:read",
                                     phase
                                 )
                             ).then(capabilities -> platformRunStatusService.findLatest(
                                     tenantId,
                                    workspaceId,
                                    projectId,
                                    phase
                            ).then(platformRunStatus -> {
                RunActionContext runActionContext = RunActionContext.fromPlatformRunStatus(
                        platformRunStatus.orElse(null),
                        evidence.stream().map(PhasePacket.PhaseEvidence::id).toList(),
                        blockers
                );
                List<PhasePacket.PhaseAction> actions = phaseActionAuthorizationService.determineAvailableActions(
                    phase,
                        capabilities,
                        tier,
                        enabledFlags,
                        readiness,
                        blockers,
                    governance,
                    !Boolean.TRUE.equals(projectState.get("featureFlagsDegraded")),
                    runActionContext
                );
                PhasePacket.DashboardActionClassification dashboardActions = buildDashboardActionClassification(actions, blockers);
                String projectName = extractProjectName(projectState);
                String workspaceName = extractWorkspaceName(projectState, workspaceId);
                PhasePacket.ActorContext actor = buildActorContext(principal, projectState);
                String lifecyclePhase = extractLifecyclePhase(phase, projectState);
                String fallbackSourceEvent = activityFeed.isEmpty()
                    ? "No source event available"
                    : activityFeed.get(0).summary();
                String fallbackLearningState = healthSignals.agentGovernance().governanceState();
                List<String> fallbackEvidenceIds = healthSignals.agentGovernance().evidenceIds();
                LearningWorkflowState fallbackLearningWorkflow = LearningWorkflowState.fallback(
                    fallbackSourceEvent,
                    readiness.predictionConfidence() == null ? 0.5d : readiness.predictionConfidence(),
                    fallbackLearningState,
                    fallbackEvidenceIds);
                EvolutionWorkflowState fallbackEvolutionWorkflow = EvolutionWorkflowState.fallback(
                    activityFeed.isEmpty() ? "No evolution proposal is available yet." : "Proposal derived from latest lifecycle activity.",
                    "Evidence: " + evidence.size() + ", Governance: " + governance.size(),
                    activityFeed.isEmpty() ? "No diff summary available" : activityFeed.get(0).summary(),
                    blockers.isEmpty() ? List.of("No additional validation blockers.") : List.of("Resolve lifecycle blockers before approval."),
                    blockers.isEmpty() ? "READY_FOR_REVIEW" : "PENDING_REMEDIATION",
                    readiness.nextPhase() == null ? "observe" : readiness.nextPhase());

                return learningWorkflowService.resolveLatest(
                    tenantId,
                    workspaceId,
                    projectId,
                    fallbackLearningWorkflow.sourceEvent(),
                    fallbackLearningWorkflow.confidence(),
                    fallbackLearningWorkflow.approvalState(),
                    fallbackLearningWorkflow.evidenceIds())
                    .then(learningWorkflowState -> evolutionWorkflowService.resolveLatest(
                        tenantId,
                        workspaceId,
                        projectId,
                        fallbackEvolutionWorkflow)
                        .then(evolutionWorkflowState -> {
                            PhasePacket packet = phasePacketAssembler.assemble(
                                phase,
                                projectId,
                                projectName,
                                tenantId,
                                workspaceId,
                                workspaceName,
                                actor,
                                lifecyclePhase,
                                tier,
                                enabledFlags,
                                new com.ghatana.yappc.api.PhasePacket.CapabilityModel(
                                    capabilities.canRead(),
                                    capabilities.canCreate(),
                                    capabilities.canUpdate(),
                                    capabilities.canDelete(),
                                    capabilities.canApprove(),
                                    capabilities.canReject(),
                                    capabilities.canRollback()),
                                blockers,
                                readiness,
                                requiredArtifacts,
                                completedArtifacts,
                                activityFeed,
                                evidence,
                                governance,
                                platformRunStatus.orElse(null),
                                actions,
                                dashboardActions,
                                healthSignals,
                                learningWorkflowState,
                                evolutionWorkflowState,
                                null,
                                Instant.now().toEpochMilli(),
                                effectiveCorrelationId);

                            if (metrics != null) {
                            metrics.recordPhaseGateValidation(principal.getTenantId(), phase, "BUILT", System.currentTimeMillis() - startTime);
                            metrics.recordPhasePacketBuild(
                                principal.getTenantId(),
                                workspaceId,
                                projectId,
                                phase,
                                "build",
                                readiness.isDegraded() ? "DEGRADED" : "SUCCESS",
                                readiness.isDegraded(),
                                null,
                                effectiveCorrelationId);
                            }

                            log.debug(
                                "Built phase packet successfully: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                                tenantId,
                                workspaceId,
                                projectId,
                                phase,
                                effectiveCorrelationId);

                            return Promise.of(packet);
                        }));
                             })));
                    }));
            });
    }

    private void recordEvidenceAndGovernanceMetrics(
            String tenantId,
            String workspaceId,
            String projectId,
            String phase,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance,
            String correlationId
    ) {
        if (metrics == null) {
            return;
        }
        boolean evidenceMiss = evidence == null || evidence.isEmpty() || evidence.stream()
                .anyMatch(record -> "SYSTEM_DEGRADED".equalsIgnoreCase(record.type()));
        metrics.recordPlatformEvidenceSearch(
                tenantId,
                workspaceId,
                projectId,
                phase,
                "phase-evidence",
                evidenceMiss ? "MISS" : "HIT",
                evidenceMiss,
                evidenceMiss ? "EvidenceUnavailable" : null,
                correlationId);

        boolean policyDenied = governance != null && governance.stream()
                .anyMatch(record -> "DENIED".equalsIgnoreCase(record.outcome()));
        metrics.recordPolicyEvaluation(
                tenantId,
                workspaceId,
                projectId,
                phase,
                "phase-governance",
                policyDenied ? "DENIED" : "ALLOWED",
                false,
                null,
                correlationId);
    }

    /**
     * Queries project state from DataCloud.
     */
    private Promise<Map<String, Object>> queryProjectState(
            String phase,
            String projectId,
            String workspaceId,
            String tenantId,
            String correlationId
    ) {
        return phaseProjectStateService.queryProjectState(phase, projectId, workspaceId, tenantId, correlationId);
    }

    /**
     * Queries phase-specific blockers using PhaseGateValidator.
     */
    private Promise<List<PhasePacket.PhaseBlocker>> queryPhaseBlockers(
            String phase,
            String projectId,
            String workspaceId,
            Map<String, Object> projectState,
            List<PhasePacket.RequiredArtifact> requiredArtifacts,
            List<PhasePacket.CompletedArtifact> completedArtifacts,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance,
            PhasePacket.HealthSignals healthSignals,
            Set<String> enabledFlags,
            String tenantId,
            String correlationId
    ) {
        try {
            com.ghatana.yappc.domain.PhaseType phaseType;
            try {
                phaseType = com.ghatana.yappc.domain.PhaseType.valueOf(phase.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Invalid phase type, returning fail-closed blocker: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                        tenantId,
                        workspaceId,
                        projectId,
                        phase,
                        correlationId);
                return Promise.of(List.of(new PhasePacket.PhaseBlocker(
                        "INVALID_PHASE",
                        "CRITERION",
                        "Invalid lifecycle phase",
                        "Requested lifecycle phase is invalid and cannot be validated safely.",
                        "CRITICAL",
                        "invalid-phase:" + phase + ":" + projectId + ":" + workspaceId + ":" + correlationId,
                        false
                )));
            }

                PhaseGateValidator.PhaseGateContext gateContext = phaseGateContextFactory.build(
                    phase,
                    projectId,
                    workspaceId,
                    projectState,
                    requiredArtifacts,
                    completedArtifacts,
                    evidence,
                    governance,
                    healthSignals,
                    enabledFlags
            );

            return phaseGateValidator.validate(projectId, phaseType, gateContext)
                .map(validationResult -> {
                    if (validationResult == null || validationResult.allClear()) {
                        return List.<PhasePacket.PhaseBlocker>of();
                    }
                    return phaseBlockerMapper.map(validationResult.blockers());
                })
                .whenException(e -> log.error(
                        "Error querying phase blockers: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                        tenantId,
                        workspaceId,
                        projectId,
                        phase,
                        correlationId,
                        e));
        } catch (Exception e) {
            log.error(
                    "Error querying phase blockers: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                    tenantId,
                    workspaceId,
                    projectId,
                    phase,
                    correlationId,
                    e);
            return Promise.of(List.of(new PhasePacket.PhaseBlocker(
                    "BLOCKER_QUERY_FAILED",
                    "CRITERION",
                    "Phase blocker query failed",
                    "Unable to validate phase gate due to query failure. Please retry or contact support.",
                    "CRITICAL",
                    "blocker-query-failed:" + phase + ":" + projectId + ":" + workspaceId + ":" + correlationId,
                    false
            )));
        }
    }

    /**
     * Queries phase evidence from AEP integration.
     */
    private List<PhasePacket.PhaseEvidence> queryPhaseEvidence(
            String phase,
            String projectId,
            String workspaceId,
            String tenantId,
            String correlationId
    ) {
        return phaseEvidenceService.queryPhaseEvidence(phase, projectId, workspaceId, tenantId, correlationId);
    }

    /**
     * Queries governance records from PolicyEngine.
     */
    private List<PhasePacket.GovernanceRecord> queryGovernanceRecords(
            String phase,
            String projectId,
            String workspaceId,
            String tenantId,
            String correlationId
    ) {
        return phaseGovernanceService.queryGovernanceRecords(phase, projectId, workspaceId, tenantId, correlationId);
    }

    /**
     * Determines tenant tier from project state or principal.
     */
    private PhasePacket.TenantTier determineTenantTier(
            Map<String, Object> projectState,
            Principal principal
    ) {
        try {
            Object tierValue = projectState.get("tier");
            if (!(tierValue instanceof String tierStr) || tierStr.isBlank()) {
                log.warn("Missing or invalid tenant tier value, failing closed to FREE: {}", tierValue);
                return PhasePacket.TenantTier.FREE;
            }

            return switch (tierStr.trim().toUpperCase()) {
                case "FREE" -> PhasePacket.TenantTier.FREE;
                case "PRO" -> PhasePacket.TenantTier.PRO;
                case "ENTERPRISE" -> PhasePacket.TenantTier.ENTERPRISE;
                default -> {
                    log.warn("Unknown tenant tier '{}', failing closed to FREE", tierStr);
                    yield PhasePacket.TenantTier.FREE;
                }
            };
        } catch (Exception e) {
            log.warn("Error determining tenant tier, defaulting to FREE", e);
            return PhasePacket.TenantTier.FREE;
        }
    }

    /**
     * Queries completed artifacts for the phase.
     */
    private Promise<List<PhasePacket.CompletedArtifact>> queryCompletedArtifacts(
            String phase,
            String projectId,
            String tenantId
    ) {
        try {
            com.ghatana.yappc.domain.PhaseType phaseType;
            try {
                phaseType = com.ghatana.yappc.domain.PhaseType.valueOf(phase.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid phase type: {}, returning explicit completed-artifact degraded marker", phase);
                return Promise.of(List.of(new PhasePacket.CompletedArtifact(
                        "INVALID_PHASE",
                        "SYSTEM_DEGRADED",
                        null,
                        "Completed artifacts unavailable due to invalid phase",
                        Instant.now(),
                        "system",
                        null
                )));
            }

            return artifactRepository.listCompletedArtifactMetadata(projectId, phaseType)
                .map(artifacts -> artifacts.stream()
                    .<PhasePacket.CompletedArtifact>map(artifact -> new PhasePacket.CompletedArtifact(
                        artifact.artifactId(),
                        artifact.artifactType(),
                        artifact.version(),
                        artifact.title(),
                        artifact.completedAt(),
                        artifact.completedBy(),
                        artifact.evidenceId()
                    ))
                    .toList())
                .then((artifacts, error) -> {
                    if (error == null) {
                        return Promise.of(artifacts);
                    }
                    log.error("Error querying completed artifacts: phase={}, projectId={}, tenantId={}", phase, projectId, tenantId, error);
                    return Promise.of(List.of(new PhasePacket.CompletedArtifact(
                            "COMPLETED_ARTIFACT_QUERY_FAILED",
                            "SYSTEM_DEGRADED",
                            null,
                            "Completed artifacts unavailable",
                            Instant.now(),
                            "system",
                            null)));
                });
        } catch (Exception e) {
            log.error("Error querying completed artifacts: phase={}, projectId={}, tenantId={}", phase, projectId, tenantId, e);
            return Promise.of(List.of(new PhasePacket.CompletedArtifact(
                    "COMPLETED_ARTIFACT_QUERY_FAILED",
                    "SYSTEM_DEGRADED",
                    null,
                    "Completed artifacts unavailable",
                    Instant.now(),
                    "system",
                    null)));
        }
    }

    /**
     * Queries activity feed for the phase.
     */
    private Promise<List<PhasePacket.ActivityFeedEntry>> queryActivityFeed(
            String phase,
            String projectId,
            String tenantId
    ) {
        return phaseActivityFeedService.queryActivityFeed(phase, projectId, tenantId);
    }

    private static String activityAction(String eventType, Map<String, Object> details) {
        return firstString(details, "action", "auditType", "operation", "command")
                .orElse(eventType != null ? eventType : "audit.event");
    }

    private static String activitySummary(Map<String, Object> details) {
        return firstString(details, "summary", "description", "message")
                .orElse("Audit event");
    }

    private static String activityOutcome(@Nullable Boolean success, Map<String, Object> details) {
        return firstString(details, "outcome", "status", "result")
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .orElseGet(() -> {
                    if (Boolean.FALSE.equals(success)) {
                        return "FAILURE";
                    }
                    if (Boolean.TRUE.equals(success)) {
                        return "SUCCESS";
                    }
                    return "UNKNOWN";
                });
    }

    @Nullable
    private static Boolean activitySuccess(@Nullable Boolean success, String outcome) {
        if (success != null) {
            return success;
        }
        if ("SUCCESS".equalsIgnoreCase(outcome) || "SUCCEEDED".equalsIgnoreCase(outcome)) {
            return true;
        }
        if ("FAILURE".equalsIgnoreCase(outcome) || "FAILED".equalsIgnoreCase(outcome) || "ERROR".equalsIgnoreCase(outcome)) {
            return false;
        }
        return null;
    }

    private static String activitySeverity(@Nullable Boolean success, Map<String, Object> details) {
        String severity = firstString(details, "severity", "level")
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .orElse(Boolean.FALSE.equals(success) ? "ERROR" : "INFO");
        if ("WARN".equals(severity)) {
            return "WARNING";
        }
        if ("WARNING".equals(severity) || "ERROR".equals(severity) || "INFO".equals(severity)) {
            return severity;
        }
        return Boolean.FALSE.equals(success) ? "ERROR" : "INFO";
    }

    @Nullable
    private static String activityCorrelationId(Map<String, Object> details) {
        return firstString(details, "correlationId", "correlation_id", "correlation", "requestId", "request_id")
                .orElse(null);
    }

    private static Optional<String> firstString(Map<String, Object> details, String... keys) {
        if (details == null || details.isEmpty()) {
            return Optional.empty();
        }
        for (String key : keys) {
            Object value = details.get(key);
            if (value != null && !value.toString().isBlank()) {
                return Optional.of(value.toString());
            }
        }
        return Optional.empty();
    }

    /**
     * Builds dashboard action classification.
     */
    private PhasePacket.DashboardActionClassification buildDashboardActionClassification(
            List<PhasePacket.PhaseAction> actions,
            List<PhasePacket.PhaseBlocker> blockers
    ) {
        try {
            List<String> blockedActions = new ArrayList<>();
            List<String> reviewRequiredActions = new ArrayList<>();
            List<String> safeToContinueActions = new ArrayList<>();

            String primaryAction = null;

            for (PhasePacket.PhaseAction action : actions) {
                if (!action.enabled()) {
                    blockedActions.add(action.actionId());
                } else if ("advance-phase".equals(action.actionId()) && !blockers.isEmpty()) {
                    reviewRequiredActions.add(action.actionId());
                    if (primaryAction == null) {
                        primaryAction = action.actionId();
                    }
                } else {
                    safeToContinueActions.add(action.actionId());
                    if (primaryAction == null) {
                        primaryAction = action.actionId();
                    }
                }
            }

            return new PhasePacket.DashboardActionClassification(
                primaryAction != null ? primaryAction : "view",
                blockedActions,
                reviewRequiredActions,
                safeToContinueActions
            );
        } catch (Exception e) {
            log.error("Error building dashboard action classification", e);
            return new PhasePacket.DashboardActionClassification(
                "view",
                List.of(),
                List.of(),
                List.of()
            );
        }
    }

    /**
     * Extracts project name from project state.
     */
    private String extractProjectName(Map<String, Object> projectState) {
        return (String) projectState.getOrDefault("name", "Unnamed Project");
    }

    /**
     * Extracts workspace name from project state.
     */
    private String extractWorkspaceName(Map<String, Object> projectState, String workspaceId) {
        return (String) projectState.getOrDefault("workspaceName", "Workspace-" + workspaceId);
    }

    /**
     * Extracts the canonical runtime lifecycle phase from project state.
     */
    private String extractLifecyclePhase(String requestedPhase, Map<String, Object> projectState) {
        Object lifecyclePhase = projectState.get("lifecyclePhase");
        if (lifecyclePhase instanceof String value && !value.isBlank()) {
            return value;
        }
        Object currentPhase = projectState.get("currentPhase");
        if (currentPhase instanceof String value && !value.isBlank()) {
            return value;
        }
        return requestedPhase;
    }

    /**
     * Builds actor context from principal and project state.
     */
    private PhasePacket.ActorContext buildActorContext(
            Principal principal,
            Map<String, Object> projectState
    ) {
        String role = principal.getRoles() != null && !principal.getRoles().isEmpty()
            ? principal.getRoles().iterator().next()
            : "VIEWER";

        boolean isAdmin = role.equals("ADMIN") || role.equals("OWNER");

        return new PhasePacket.ActorContext(
            principal.getName(),
            principal.getName(),
            role,
            isAdmin,
            isAdmin
        );
    }

}
