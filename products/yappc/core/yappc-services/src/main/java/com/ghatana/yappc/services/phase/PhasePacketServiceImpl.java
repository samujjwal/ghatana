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
import java.util.Collection;
import java.util.LinkedHashSet;
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
    private final TransitionConfigLoader transitionConfigLoader;
    private final PlatformIntegrationClient platformIntegrationClient;
    @Nullable
    private final BusinessMetrics metrics;
    private final AuditService auditService;
    private final PreviewRuntimeService previewRuntimeService;
    private final PlatformRunStatusService platformRunStatusService;
    private final PhaseActionAuthorizationService phaseActionAuthorizationService;
    private final PhaseRequiredArtifactProvider requiredArtifactProvider;
    private final DegradedPhasePacketFactory degradedPhasePacketFactory;

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
            @NotNull DegradedPhasePacketFactory degradedPhasePacketFactory
    ) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository");
        this.phaseGateValidator = Objects.requireNonNull(phaseGateValidator, "phaseGateValidator");
        Objects.requireNonNull(policyEngine, "policyEngine");
        this.capabilityEvaluationService = Objects.requireNonNull(capabilityEvaluationService, "capabilityEvaluationService");
        this.transitionConfigLoader = Objects.requireNonNull(transitionConfigLoader, "transitionConfigLoader");
        this.platformIntegrationClient = Objects.requireNonNull(platformIntegrationClient, "platformIntegrationClient");
        this.metrics = metrics;
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.previewRuntimeService = Objects.requireNonNull(previewRuntimeService, "previewRuntimeService");
        this.platformRunStatusService = Objects.requireNonNull(platformRunStatusService, "platformRunStatusService");
        this.phaseActionAuthorizationService = Objects.requireNonNull(phaseActionAuthorizationService, "phaseActionAuthorizationService");
        this.requiredArtifactProvider = Objects.requireNonNull(requiredArtifactProvider, "requiredArtifactProvider");
        this.degradedPhasePacketFactory = Objects.requireNonNull(degradedPhasePacketFactory, "degradedPhasePacketFactory");
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
                Set<String> enabledFlags = determineEnabledFlags(projectState, principal);
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
                PhasePacket.HealthSignals healthSignals = buildHealthSignals(phase, projectId, projectState);

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
                        PhasePacket.PhaseReadiness readiness = calculatePhaseReadiness(
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
                List<PhasePacket.PhaseAction> actions = phaseActionAuthorizationService.determineAvailableActions(
                        capabilities,
                        tier,
                        enabledFlags,
                        readiness,
                        blockers,
                        governance
                );
                PhasePacket.DashboardActionClassification dashboardActions = buildDashboardActionClassification(actions, blockers);
                String projectName = extractProjectName(projectState);
                String workspaceName = extractWorkspaceName(projectState, workspaceId);
                PhasePacket.ActorContext actor = buildActorContext(principal, projectState);

                PhasePacket packet = new PhasePacket(
                    phase,
                    projectId,
                    projectName,
                    tenantId,
                    workspaceId,
                    workspaceName,
                    actor,
                    phase,
                    tier,
                    enabledFlags,
                    new com.ghatana.yappc.api.PhasePacket.CapabilityModel(
                        capabilities.canRead(),
                        capabilities.canCreate(),
                        capabilities.canUpdate(),
                        capabilities.canDelete(),
                        capabilities.canApprove(),
                        capabilities.canReject(),
                        capabilities.canRollback()
                    ),
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
                    null,
                    Instant.now().toEpochMilli(),
                    effectiveCorrelationId
                );

                // Record metrics if available
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
        try {
            // Query project metadata from DataCloud; handle async failures explicitly
            return dataCloudClient.findById(tenantId, "projects", projectId)
                .then((entityOpt, e) -> {
                    if (e != null) {
                        log.error(
                                "DataCloud query failed for project: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                                tenantId,
                                workspaceId,
                                projectId,
                                phase,
                                correlationId,
                                e);
                        return Promise.of(Map.of(
                            "projectId", projectId,
                            "workspaceId", workspaceId,
                            "tenantId", tenantId,
                            "degraded", true,
                            "degradedReason", "PROJECT_STATE_QUERY_FAILED"
                        ));
                    }
                    Map<String, Object> data = entityOpt.isPresent() ? entityOpt.get().data() : Map.of();
                    if (data.isEmpty()) {
                        log.warn(
                                "Project state not found: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                                tenantId,
                                workspaceId,
                                projectId,
                                phase,
                                correlationId);
                        return Promise.of(Map.of(
                            "projectId", projectId,
                            "workspaceId", workspaceId,
                            "tenantId", tenantId,
                            "degraded", true,
                            "degradedReason", "PROJECT_STATE_NOT_FOUND"
                        ));
                    }
                    Map<String, Object> state = new HashMap<>(data);
                    state.putIfAbsent("projectId", projectId);
                    state.putIfAbsent("workspaceId", workspaceId);
                    state.putIfAbsent("tenantId", tenantId);
                    state.putIfAbsent("name", "Project-" + projectId);
                    state.putIfAbsent("tier", "PRO");
                    state.putIfAbsent("status", "active");
                    state.putIfAbsent("createdAt", Instant.now().toString());
                    return queryEnabledTenantFeatureFlags(tenantId)
                            .map(tenantFlags -> {
                                if (!tenantFlags.isEmpty()) {
                                    LinkedHashSet<String> merged = new LinkedHashSet<>();
                                    addFlagValues(merged, state.get("enabledPhaseFlags"));
                                    addFlagValues(merged, state.get("featureFlags"));
                                    addFlagValues(merged, tenantFlags);
                                    state.put("featureFlags", List.copyOf(merged));
                                    state.put("featureFlagsSource", "yappc_feature_flags");
                                }
                                return Map.copyOf(state);
                            });
                });
        } catch (Exception e) {
            log.error(
                    "Unexpected error in queryProjectState: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                    tenantId,
                    workspaceId,
                    projectId,
                    phase,
                    correlationId,
                    e);
            return Promise.of(Map.of(
                "projectId", projectId,
                "workspaceId", workspaceId,
                "tenantId", tenantId,
                "degraded", true,
                "degradedReason", "PROJECT_STATE_QUERY_FAILED"
            ));
        }
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
                        "Invalid phase type, defaulting to INTENT: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                        tenantId,
                        workspaceId,
                        projectId,
                        phase,
                        correlationId);
                phaseType = com.ghatana.yappc.domain.PhaseType.INTENT;
            }

            PhaseGateValidator.PhaseGateContext gateContext = buildPhaseGateContext(
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

                    return validationResult.blockers().stream()
                        .<PhasePacket.PhaseBlocker>map(blocker -> {
                            String severity = blocker.startsWith("missing-artifact") ? "CRITICAL" : "WARNING";
                            String title = blocker.replace("entry-criterion: ", "")
                                                .replace("prior-exit-criterion: ", "")
                                                .replace("missing-artifact: ", "");
                            return new PhasePacket.PhaseBlocker(
                                blocker,
                                blocker.startsWith("missing-artifact") ? "ARTIFACT" : "CRITERION",
                                title,
                                blocker,
                                severity,
                                blocker,
                                true
                            );
                        })
                        .toList();
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
            return Promise.of(List.<PhasePacket.PhaseBlocker>of());
        }
    }

    private PhaseGateValidator.PhaseGateContext buildPhaseGateContext(
            String phase,
            String projectId,
            String workspaceId,
            Map<String, Object> projectState,
            List<PhasePacket.RequiredArtifact> requiredArtifacts,
            List<PhasePacket.CompletedArtifact> completedArtifacts,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance,
            PhasePacket.HealthSignals healthSignals,
            Set<String> enabledFlags
    ) {
        Map<String, Boolean> context = new HashMap<>();
        Set<String> requiredArtifactIds = requiredArtifacts.stream()
                .map(PhasePacket.RequiredArtifact::artifactId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> completedArtifactIds = completedArtifacts.stream()
                .map(PhasePacket.CompletedArtifact::artifactId)
                .collect(java.util.stream.Collectors.toSet());

        context.put("project.workspace-scoped", workspaceId != null && !workspaceId.isBlank());
        context.put("project.tenant-scoped", projectState.get("tenantId") != null);
        context.put("project.state-loaded", !projectState.isEmpty());
        context.put("evidence.available", !evidence.isEmpty());
        context.put(
                "policyAllowed",
                governance.stream().noneMatch(record -> "DENIED".equalsIgnoreCase(record.outcome()))
        );
        context.put("previewHealthy", healthSignals.preview().isHealthy());
        context.put("generationHealthy", healthSignals.generation().isHealthy());
        context.put("runtimeHealthy", healthSignals.runtime().isHealthy());
        context.put("phase.advance-enabled", enabledFlags.contains("phase.advance"));

        for (PhasePacket.RequiredArtifact artifact : requiredArtifacts) {
            boolean completed = completedArtifactIds.contains(artifact.artifactId());
            context.put(artifact.artifactId(), completed);
            context.put("artifact:" + artifact.artifactId(), completed);
        }

        addBooleanConditionValues(context, projectState.get("conditions"));
        addBooleanConditionValues(context, projectState.get("gateConditions"));
        addBooleanConditionValues(context, projectState.get("criteriaStatus"));
        addCollectionConditions(context, projectState.get("satisfiedCriteria"), true);
        addCollectionConditions(context, projectState.get("unsatisfiedCriteria"), false);
        return new PhaseGateValidator.PhaseGateContext(
                requiredArtifactIds,
                completedArtifactIds,
                !evidence.isEmpty(),
                governance.stream().noneMatch(record -> "DENIED".equalsIgnoreCase(record.outcome())),
                healthSignals.preview().isHealthy(),
                healthSignals.generation().isHealthy(),
                healthSignals.runtime().isHealthy(),
                enabledFlags,
                context);
    }

    @SuppressWarnings("unchecked")
    private static void addBooleanConditionValues(Map<String, Boolean> target, Object rawConditions) {
        if (!(rawConditions instanceof Map<?, ?> source)) {
            return;
        }
        source.forEach((key, value) -> {
            if (key != null && value instanceof Boolean satisfied) {
                target.put(String.valueOf(key), satisfied);
            }
        });
    }

    private static void addCollectionConditions(Map<String, Boolean> target, Object rawCriteria, boolean satisfied) {
        if (!(rawCriteria instanceof Collection<?> criteria)) {
            return;
        }
        criteria.stream()
                .filter(value -> value != null && !String.valueOf(value).isBlank())
                .forEach(value -> target.put(String.valueOf(value), satisfied));
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
                    result.metadata().forEach((k, v) -> metadata.put(k, v));
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
        } catch (Exception e) {
            log.error(
                    "Error querying phase evidence: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                    tenantId,
                    workspaceId,
                    projectId,
                    phase,
                    correlationId,
                    e
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
                            "reason", e.getClass().getSimpleName()),
                    "evidence-query-failed:" + e.getClass().getSimpleName()));
        }
    }

    private Promise<List<String>> queryEnabledTenantFeatureFlags(String tenantId) {
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("enabled", true))
                .limit(500)
                .build();
        return dataCloudClient.query(tenantId, AdminFeatureFlagController.FLAG_COLLECTION, query)
                .map(records -> records.stream()
                        .map(record -> record.data().get("key"))
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(flag -> !flag.isBlank())
                        .distinct()
                        .toList())
                .then((flags, error) -> {
                    if (error == null) {
                        return Promise.of(flags);
                    }
                    log.error("DataCloud query failed for tenant feature flags: tenantId={}", tenantId, error);
                    return Promise.of(List.of());
                });
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
        } catch (Exception e) {
            log.error(
                    "Error querying governance records: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                    tenantId,
                    workspaceId,
                    projectId,
                    phase,
                    correlationId,
                    e
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
                            "reason", e.getClass().getSimpleName()),
                    "governance-query-failed:" + projectId + ":" + phase));
        }
    }

    /**
     * Determines tenant tier from project state or principal.
     */
    private PhasePacket.TenantTier determineTenantTier(
            Map<String, Object> projectState,
            Principal principal
    ) {
        try {
            String tierStr = (String) projectState.getOrDefault("tier", "PRO");
            return switch (tierStr.toUpperCase()) {
                case "FREE" -> PhasePacket.TenantTier.FREE;
                case "ENTERPRISE" -> PhasePacket.TenantTier.ENTERPRISE;
                default -> PhasePacket.TenantTier.PRO;
            };
        } catch (Exception e) {
            log.warn("Error determining tenant tier, defaulting to PRO", e);
            return PhasePacket.TenantTier.PRO;
        }
    }

    /**
     * Determines enabled feature flags from project state.
     */
    private Set<String> determineEnabledFlags(
            Map<String, Object> projectState,
            Principal principal
    ) {
        try {
            LinkedHashSet<String> flags = new LinkedHashSet<>();
            addFlagValues(flags, projectState.get("enabledPhaseFlags"));
            addFlagValues(flags, projectState.get("featureFlags"));
            addFlagValues(flags, projectState.get("entitlements"));

            PhasePacket.TenantTier tier = determineTenantTier(projectState, principal);
            if (tier == PhasePacket.TenantTier.ENTERPRISE) {
                flags.add("phase.report.export");
                flags.add("phase.governance.configure");
            }
            if (tier == PhasePacket.TenantTier.PRO || tier == PhasePacket.TenantTier.ENTERPRISE) {
                flags.add("phase.advance");
            }
            return Set.copyOf(flags);
        } catch (Exception e) {
            log.error("Error determining enabled flags", e);
            return Set.of();
        }
    }

    private void addFlagValues(Set<String> flags, Object value) {
        if (value instanceof String text) {
            for (String flag : text.split(",")) {
                if (!flag.isBlank()) {
                    flags.add(flag.trim());
                }
            }
        } else if (value instanceof Collection<?> values) {
            values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .filter(flag -> !flag.isBlank())
                    .forEach(flags::add);
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, enabled) -> {
                if (key instanceof String flag && Boolean.TRUE.equals(enabled)) {
                    flags.add(flag);
                }
            });
        }
    }

    /**
     * Calculates phase readiness based on blockers and project state.
     */
    private PhasePacket.PhaseReadiness calculatePhaseReadiness(
            String phase,
            String projectId,
            List<PhasePacket.PhaseBlocker> blockers,
            List<PhasePacket.RequiredArtifact> requiredArtifacts,
            List<PhasePacket.CompletedArtifact> completedArtifacts,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance,
            PhasePacket.HealthSignals healthSignals,
            Map<String, Object> projectState
    ) {
        try {
            List<String> missingPrerequisites = new ArrayList<>();

            for (PhasePacket.PhaseBlocker blocker : blockers) {
                if ("CRITICAL".equals(blocker.severity())) {
                    missingPrerequisites.add(blocker.title());
                }
            }

            long completedRequired = requiredArtifacts.stream()
                    .filter(required -> isArtifactComplete(required, completedArtifacts))
                    .count();
            if (!requiredArtifacts.isEmpty()) {
                requiredArtifacts.stream()
                        .filter(required -> !isArtifactComplete(required, completedArtifacts))
                        .map(PhasePacket.RequiredArtifact::title)
                        .forEach(missingPrerequisites::add);
            }

            boolean policyAllowed = governance.stream().noneMatch(this::isPolicyDenied);
            if (!policyAllowed) {
                missingPrerequisites.add("Policy approval");
            }

            boolean evidenceAvailable = evidence.stream().noneMatch(this::isEvidenceDegraded);
            if (!evidenceAvailable) {
                missingPrerequisites.add("Phase evidence unavailable");
            }

            boolean healthReady = healthSignals.preview().isHealthy()
                    && healthSignals.generation().isHealthy()
                    && healthSignals.runtime().isHealthy();
            if (!healthReady) {
                missingPrerequisites.add("Healthy preview, generation, and runtime signals");
            }

            double artifactScore = requiredArtifacts.isEmpty()
                    ? 1.0
                    : (double) completedRequired / (double) requiredArtifacts.size();
            double blockerScore = blockers.isEmpty()
                    ? 1.0
                    : blockers.stream().anyMatch(blocker -> "CRITICAL".equals(blocker.severity())) ? 0.0 : 0.5;
            double evidenceScore = evidence.isEmpty() || !evidenceAvailable ? 0.0 : 1.0;
            double governanceScore = policyAllowed ? 1.0 : 0.0;
            double healthScore = healthReady ? 1.0 : 0.0;
            double completenessScore = roundScore(
                    artifactScore * 0.40
                            + blockerScore * 0.25
                            + evidenceScore * 0.15
                            + governanceScore * 0.10
                            + healthScore * 0.10
            );
            boolean canAdvance = missingPrerequisites.isEmpty()
                    && blockers.isEmpty()
                    && evidenceAvailable
                    && completenessScore >= 0.90
                    && "active".equalsIgnoreCase(String.valueOf(projectState.getOrDefault("status", "active")));
            List<String> distinctMissingPrerequisites = missingPrerequisites.stream().distinct().toList();
            int estimatedReadyInHours = estimateReadyInHours(canAdvance, distinctMissingPrerequisites, completenessScore);

            return new PhasePacket.PhaseReadiness(
                canAdvance,
                getNextPhase(phase),
                distinctMissingPrerequisites,
                completenessScore,
                false,
                humanizeReadyInHours(estimatedReadyInHours),
                estimatedReadyInHours,
                predictionConfidence(completenessScore, distinctMissingPrerequisites.size(), evidenceAvailable, healthReady)
            );
        } catch (Exception e) {
            log.error("Error calculating phase readiness: phase={}, projectId={}", phase, projectId, e);
            return new PhasePacket.PhaseReadiness(
                false,
                getNextPhase(phase),
                List.of("Error calculating readiness"),
                0.0,
                true,
                "Blocked",
                24,
                0.35
            );
        }
    }

    private int estimateReadyInHours(
            boolean canAdvance,
            List<String> missingPrerequisites,
            double completenessScore
    ) {
        if (canAdvance) {
            return 0;
        }
        int blockerHours = Math.max(1, missingPrerequisites.size()) * 6;
        int readinessPenaltyHours = (int) Math.ceil(Math.max(0.0, 0.90 - completenessScore) * 24.0);
        return Math.max(1, blockerHours + readinessPenaltyHours);
    }

    private String humanizeReadyInHours(int hours) {
        if (hours <= 0) {
            return "Ready now";
        }
        if (hours < 24) {
            return "~" + hours + " hours";
        }
        int days = Math.max(1, (int) Math.round(hours / 24.0));
        return "~" + days + (days == 1 ? " day" : " days");
    }

    private double predictionConfidence(
            double completenessScore,
            int missingPrerequisiteCount,
            boolean evidenceAvailable,
            boolean healthReady
    ) {
        double signalPenalty = (evidenceAvailable ? 0.0 : 0.12) + (healthReady ? 0.0 : 0.10);
        double blockerPenalty = Math.min(0.25, missingPrerequisiteCount * 0.04);
        return roundScore(Math.max(0.35, Math.min(0.95, completenessScore - signalPenalty - blockerPenalty)));
    }

    private boolean isArtifactComplete(
            PhasePacket.RequiredArtifact required,
            List<PhasePacket.CompletedArtifact> completedArtifacts
    ) {
        return required.isComplete()
                || completedArtifacts.stream().anyMatch(completed ->
                        equalsCanonical(completed.artifactId(), required.artifactId())
                                || equalsCanonical(completed.artifactType(), required.artifactType()));
    }

    private boolean equalsCanonical(String actual, String expected) {
        return actual != null
                && expected != null
                && actual.equalsIgnoreCase(expected);
    }

    private double roundScore(double score) {
        return Math.max(0.0, Math.min(1.0, Math.round(score * 100.0) / 100.0));
    }

    private boolean isPolicyDenied(PhasePacket.GovernanceRecord record) {
        return "DENIED".equalsIgnoreCase(record.outcome())
                || "POLICY_DENIAL".equalsIgnoreCase(record.type());
    }

    private boolean isEvidenceDegraded(PhasePacket.PhaseEvidence evidence) {
        return "SYSTEM_DEGRADED".equalsIgnoreCase(evidence.type())
                || "EVIDENCE_QUERY_FAILED".equalsIgnoreCase(evidence.id());
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
            // Query completed artifacts from artifact repository
            com.ghatana.yappc.domain.PhaseType phaseType;
            try {
                phaseType = com.ghatana.yappc.domain.PhaseType.valueOf(phase.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid phase type: {}, defaulting to INTENT", phase);
                phaseType = com.ghatana.yappc.domain.PhaseType.INTENT;
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
                    .toList());
        } catch (Exception e) {
            log.error("Error querying completed artifacts: phase={}, projectId={}, tenantId={}", phase, projectId, tenantId, e);
            return Promise.of(List.of());
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
        try {
            // Query recent audit events for this project and phase using the platform AuditService
            Instant endDate = Instant.now();
            Instant startDate = endDate.minus(java.time.Duration.ofDays(30)); // Last 30 days

            return auditService.queryByPhase(projectId, phase, startDate, endDate)
                .map(auditEvents -> {
                    if (auditEvents == null || auditEvents.isEmpty()) {
                        log.debug("No audit events found for phase={}, projectId={}", phase, projectId);
                        return List.of();
                    }

                    // Convert AuditEvent to ActivityFeedEntry
                    return auditEvents.stream()
                        .limit(50) // Limit to most recent 50 events
                        .map(event -> {
                            Map<String, Object> details = event.getDetails();
                            String outcome = activityOutcome(event.getSuccess(), details);
                            Boolean success = activitySuccess(event.getSuccess(), outcome);
                            return new PhasePacket.ActivityFeedEntry(
                                    event.getId(),
                                    event.getEventType(),
                                    activityAction(event.getEventType(), details),
                                    activitySummary(details),
                                    event.getPrincipal() != null ? event.getPrincipal() : "System",
                                    event.getTimestamp(),
                                    activitySeverity(success, details),
                                    event.getEventType(),
                                    success,
                                    outcome,
                                    activityCorrelationId(details)
                            );
                        })
                        .toList();
                });

        } catch (Exception e) {
            log.error("Error querying activity feed: phase={}, projectId={}, tenantId={}", phase, projectId, tenantId, e);
            return Promise.of(List.of());
        }
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
     * Builds health signals for the phase.
     */
    private PhasePacket.HealthSignals buildHealthSignals(
            String phase,
            String projectId,
            Map<String, Object> projectState
    ) {
        try {
            String previewId = stringState(projectState, "previewId")
                    .orElse(projectId + "-" + phase.toLowerCase());
            String generationId = stringState(projectState, "generationId")
                    .orElse(previewId + "-gen");
            String runtimeId = stringState(projectState, "runtimeId")
                    .orElse(previewId + "-runtime");

            PreviewRuntimeService.PreviewHealthStatus previewHealth = previewRuntimeService.getHealth(previewId);
            PreviewRuntimeService.GenerationHealthStatus generationHealth = previewRuntimeService.getGenerationHealth(generationId);
            PreviewRuntimeService.RuntimeHealthStatus runtimeHealth = previewRuntimeService.getRuntimeHealth(runtimeId);
            PhasePacket.PreviewSecurity previewSecurity = buildPreviewSecurity(projectState);
            List<String> previewIssues = new java.util.ArrayList<>(previewHealth.issues());
            previewIssues.addAll(previewSecurity.issues());

            return new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(
                    previewHealth.healthy() && previewSecurity.safe(),
                    previewSecurity.safe() ? previewHealth.status() : "unsafe",
                    List.copyOf(previewIssues),
                    previewSecurity
                ),
                new PhasePacket.GenerationHealth(
                    generationHealth.healthy(),
                    generationHealth.status(),
                    generationHealth.generationId(),
                    generationHealth.issues()
                ),
                new PhasePacket.RuntimeHealth(
                    runtimeHealth.healthy(),
                    runtimeHealth.status(),
                    runtimeHealth.runtimeId(),
                    runtimeHealth.issues()
                ),
                buildAgentGovernanceHealth(projectState)
            );

        } catch (Exception e) {
            log.error("Error building health signals: phase={}, projectId={}", phase, projectId, e);
            return new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(false, "error", List.of("Health check failed")),
                new PhasePacket.GenerationHealth(false, "error", null, List.of("Health check failed")),
                new PhasePacket.RuntimeHealth(false, "error", null, List.of("Health check failed")),
                new PhasePacket.AgentGovernanceHealth(
                        false,
                        "error",
                        "unknown",
                        "none",
                        List.of(),
                        List.of("Health check failed"))
            );
        }
    }

    @SuppressWarnings("unchecked")
    private PhasePacket.AgentGovernanceHealth buildAgentGovernanceHealth(Map<String, Object> projectState) {
        Object rawGovernance = projectState.get("agentGovernance");
        if (!(rawGovernance instanceof Map<?, ?> governance)) {
            return PhasePacket.AgentGovernanceHealth.unknown();
        }

        String status = stringValue(governance.get("status")).orElse("unknown");
        String governanceState = stringValue(governance.get("governanceState")).orElse(status);
        String learningLevel = stringValue(governance.get("learningLevel")).orElse("none");
        List<String> evidenceIds = stringList(governance.get("evidenceIds"));
        if (evidenceIds.isEmpty()) {
            evidenceIds = stringList(governance.get("learningEvidenceIds"));
        }
        List<String> issues = stringList(governance.get("issues"));
        boolean isHealthy = Boolean.TRUE.equals(governance.get("healthy"))
                || ("healthy".equalsIgnoreCase(status) && issues.isEmpty());
        return new PhasePacket.AgentGovernanceHealth(
                isHealthy,
                status,
                governanceState,
                learningLevel,
                evidenceIds,
                issues);
    }

    @SuppressWarnings("unchecked")
    private PhasePacket.PreviewSecurity buildPreviewSecurity(Map<String, Object> projectState) {
        Object rawSecurity = projectState.get("previewSecurity");
        if (!(rawSecurity instanceof Map<?, ?> raw)) {
            return PhasePacket.PreviewSecurity.safeDefault();
        }

        Object rawTrustLevel = raw.get("trustLevel");
        String trustLevel = rawTrustLevel instanceof String value && !value.isBlank()
                ? value.toLowerCase(java.util.Locale.ROOT)
                : "trusted";
        String expiresAt = raw.get("expiresAt") instanceof String value && !value.isBlank() ? value : null;
        boolean expired = Boolean.TRUE.equals(raw.get("expired")) || isExpired(expiresAt);
        List<PhasePacket.TokenScope> tokenScopes = new java.util.ArrayList<>();
        Object scopesObject = raw.get("tokenScopes");
        if (scopesObject == null) {
            scopesObject = raw.get("tokenScope");
        }
        if (scopesObject instanceof List<?> scopes) {
            for (Object scopeObject : scopes) {
                if (scopeObject instanceof Map<?, ?> scope) {
                    Object rawId = scope.get("id");
                    String id = rawId instanceof String value && !value.isBlank() ? value : "preview-scope";
                    Object rawName = scope.get("name");
                    String name = rawName instanceof String value && !value.isBlank() ? value : id;
                    boolean required = Boolean.TRUE.equals(scope.get("required"));
                    boolean granted = Boolean.TRUE.equals(scope.get("granted"));
                    tokenScopes.add(new PhasePacket.TokenScope(id, name, required, granted));
                }
            }
        }

        List<String> issues = new java.util.ArrayList<>();
        Object rawIssues = raw.get("issues");
        if (rawIssues instanceof List<?> issueList) {
            issueList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(issue -> !issue.isBlank())
                    .forEach(issues::add);
        }
        Object rawMismatches = raw.get("scopeMismatches");
        if (rawMismatches instanceof List<?> mismatches) {
            mismatches.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(issue -> !issue.isBlank())
                    .forEach(issue -> issues.add("Scope mismatch: " + issue));
        }

        long missingRequiredScopes = tokenScopes.stream()
                .filter(scope -> scope.required() && !scope.granted())
                .count();
        if (missingRequiredScopes > 0) {
            issues.add(missingRequiredScopes + " required preview token scope(s) are not granted");
        }
        if (expired) {
            issues.add("Preview token is expired");
        }
        if ("untrusted".equals(trustLevel)) {
            issues.add("Preview trust level is untrusted");
        }

        boolean explicitSafe = raw.get("safe") instanceof Boolean value ? value : true;
        boolean safe = explicitSafe && !"untrusted".equals(trustLevel) && !expired && missingRequiredScopes == 0 && issues.isEmpty();
        return new PhasePacket.PreviewSecurity(
                trustLevel,
                List.copyOf(tokenScopes),
                expiresAt,
                expired,
                safe,
                List.copyOf(issues)
        );
    }

    private boolean isExpired(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return false;
        }
        try {
            return Instant.parse(expiresAt).isBefore(Instant.now());
        } catch (Exception ignored) {
            return true;
        }
    }

    private Optional<String> stringState(Map<String, Object> projectState, String key) {
        Object value = projectState.get(key);
        return stringValue(value);
    }

    private Optional<String> stringValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(text -> !text.isBlank())
                .toList();
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

    /**
     * Determines the next phase in the lifecycle using TransitionConfigLoader.
     */
    private String getNextPhase(String currentPhase) {
        return transitionConfigLoader.getNextPhase(currentPhase);
    }
}
