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
    @Nullable
    private final com.ghatana.audit.AuditLogger auditLogger;

    public PhasePacketServiceImpl(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull YappcArtifactRepository artifactRepository,
            @NotNull PhaseGateValidator phaseGateValidator,
            @NotNull PolicyEngine policyEngine,
            @NotNull CapabilityEvaluationService capabilityEvaluationService,
            @NotNull TransitionConfigLoader transitionConfigLoader,
            @NotNull PlatformIntegrationClient platformIntegrationClient,
            @Nullable BusinessMetrics metrics
    ) {
        this(dataCloudClient, artifactRepository, phaseGateValidator, policyEngine, capabilityEvaluationService, transitionConfigLoader, platformIntegrationClient, metrics, null, null, null);
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
            @Nullable com.ghatana.audit.AuditLogger auditLogger
    ) {
        this(dataCloudClient, artifactRepository, phaseGateValidator, policyEngine, capabilityEvaluationService, transitionConfigLoader, platformIntegrationClient, metrics, null, null, auditLogger);
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
            @Nullable AuditService auditService,
            @Nullable com.ghatana.audit.AuditLogger auditLogger
    ) {
        this(dataCloudClient, artifactRepository, phaseGateValidator, policyEngine, capabilityEvaluationService, transitionConfigLoader, platformIntegrationClient, metrics, auditService, null, auditLogger);
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
            @Nullable AuditService auditService,
            @Nullable PreviewRuntimeService previewRuntimeService,
            @Nullable com.ghatana.audit.AuditLogger auditLogger
    ) {
        this.dataCloudClient = dataCloudClient;
        this.artifactRepository = artifactRepository;
        this.phaseGateValidator = phaseGateValidator;
        this.capabilityEvaluationService = capabilityEvaluationService;
        this.transitionConfigLoader = transitionConfigLoader;
        this.platformIntegrationClient = platformIntegrationClient;
        this.metrics = metrics;
        this.auditService = auditService != null
            ? auditService
            : new DegradedAuditService("PLATFORM_AUDIT_SERVICE_UNAVAILABLE");
        this.previewRuntimeService = previewRuntimeService != null
            ? previewRuntimeService
            : new DegradedPreviewRuntimeService("PREVIEW_RUNTIME_SERVICE_UNAVAILABLE");
        this.platformRunStatusService = new DataCloudPlatformRunStatusService(dataCloudClient);
        this.phaseActionAuthorizationService = new PhaseActionAuthorizationService();
        this.auditLogger = auditLogger;
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
            @Nullable AuditService auditService,
            @Nullable PreviewRuntimeService previewRuntimeService,
            @NotNull PlatformRunStatusService platformRunStatusService,
            @Nullable com.ghatana.audit.AuditLogger auditLogger
    ) {
        this.dataCloudClient = dataCloudClient;
        this.artifactRepository = artifactRepository;
        this.phaseGateValidator = phaseGateValidator;
        this.capabilityEvaluationService = capabilityEvaluationService;
        this.transitionConfigLoader = transitionConfigLoader;
        this.platformIntegrationClient = platformIntegrationClient;
        this.metrics = metrics;
        this.auditService = auditService != null
            ? auditService
            : new DegradedAuditService("PLATFORM_AUDIT_SERVICE_UNAVAILABLE");
        this.previewRuntimeService = previewRuntimeService != null
            ? previewRuntimeService
            : new DegradedPreviewRuntimeService("PREVIEW_RUNTIME_SERVICE_UNAVAILABLE");
        this.platformRunStatusService = platformRunStatusService;
        this.phaseActionAuthorizationService = new PhaseActionAuthorizationService();
        this.auditLogger = auditLogger;
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

        // Query project state from DataCloud
        return queryProjectState(projectId, workspaceId, principal.getTenantId())
            .then(projectState -> {
                // Check if project is in degraded state
                boolean isDegraded = Boolean.TRUE.equals(projectState.get("degraded"));
                if (isDegraded) {
                    String degradedReason = (String) projectState.get("degradedReason");
                    log.warn("Project in degraded state: projectId={}, reason={}", projectId, degradedReason);
                    
                    // Return degraded packet with explicit degradation reason
                    return Promise.of(buildDegradedPhasePacket(
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
                List<PhasePacket.RequiredArtifact> requiredArtifacts = queryRequiredArtifacts(phase, projectId);
                List<PhasePacket.PhaseEvidence> evidence =
                        queryPhaseEvidence(phase, projectId, workspaceId, principal.getTenantId());
                List<PhasePacket.GovernanceRecord> governance =
                        queryGovernanceRecords(phase, projectId, workspaceId, principal.getTenantId());
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
                            enabledFlags
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
                                    principal.getTenantId(),
                                    principal.getName(),
                                     workspaceId,
                                     projectId,
                                     null,
                                     "phase:read",
                                     phase
                                 )
                             ).then(capabilities -> platformRunStatusService.findLatest(
                                     principal.getTenantId(),
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
                    principal.getTenantId(),
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
                    Instant.now().toEpochMilli(),
                    effectiveCorrelationId
                );

                // Record metrics if available
                if (metrics != null) {
                    metrics.recordPhaseGateValidation(principal.getTenantId(), phase, "BUILT", System.currentTimeMillis() - startTime);
                }

                log.debug("Built phase packet successfully: phase={}, projectId={}, correlationId={}",
                    phase, projectId, effectiveCorrelationId);

                 return Promise.of(packet);
                             })));
                    }));
            });
    }

    /**
     * Queries project state from DataCloud.
     */
    private Promise<Map<String, Object>> queryProjectState(
            String projectId,
            String workspaceId,
            String tenantId
    ) {
        try {
            // Query project metadata from DataCloud; handle async failures explicitly
            return dataCloudClient.findById(tenantId, "projects", projectId)
                .then((entityOpt, e) -> {
                    if (e != null) {
                        log.error("DataCloud query failed for project: projectId={}, tenantId={}", projectId, tenantId, e);
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
                        log.warn("Project state not found: projectId={}, tenantId={}", projectId, tenantId);
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
                    return Promise.of(Map.copyOf(state));
                });
        } catch (Exception e) {
            log.error("Unexpected error in queryProjectState", e);
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
            Set<String> enabledFlags
    ) {
        try {
            com.ghatana.yappc.domain.PhaseType phaseType;
            try {
                phaseType = com.ghatana.yappc.domain.PhaseType.valueOf(phase.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid phase type: {}, defaulting to INTENT", phase);
                phaseType = com.ghatana.yappc.domain.PhaseType.INTENT;
            }
            
            Map<String, Boolean> gateContext = buildPhaseGateContext(
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
                .whenException(e -> log.error("Error querying phase blockers: phase={}, projectId={}", phase, projectId, e));
        } catch (Exception e) {
            log.error("Error querying phase blockers: phase={}, projectId={}", phase, projectId, e);
            return Promise.of(List.<PhasePacket.PhaseBlocker>of());
        }
    }

    private Map<String, Boolean> buildPhaseGateContext(
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
        return Map.copyOf(context);
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
            String tenantId
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
                    "Error querying phase evidence: phase={}, projectId={}, workspaceId={}, tenantId={}",
                    phase,
                    projectId,
                    workspaceId,
                    tenantId,
                    e
            );
            return List.of();
        }
    }

    /**
     * Queries governance records from PolicyEngine.
     */
    private List<PhasePacket.GovernanceRecord> queryGovernanceRecords(
            String phase,
            String projectId,
            String workspaceId,
            String tenantId
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
                        "DENIED",
                        reason,
                        policyDecision.policyId(),
                        Instant.now(),
                        Map.of("phase", phase, "projectId", projectId),
                        policyDecision.policyId()
                    ));
                }
            } else {
                records.add(new PhasePacket.GovernanceRecord(
                    "POLICY_APPROVAL",
                    "APPROVED",
                    "Phase governance check passed",
                    policyDecision.policyId(),
                    Instant.now(),
                    Map.of("phase", phase, "projectId", projectId),
                    policyDecision.policyId()
                ));
            }
            
            return records;
        } catch (Exception e) {
            log.error(
                    "Error querying governance records: phase={}, projectId={}, workspaceId={}, tenantId={}",
                    phase,
                    projectId,
                    workspaceId,
                    tenantId,
                    e
            );
            return List.of();
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
            double evidenceScore = evidence.isEmpty() ? 0.0 : 1.0;
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
                    && completenessScore >= 0.90
                    && "active".equalsIgnoreCase(String.valueOf(projectState.getOrDefault("status", "active")));
            
            return new PhasePacket.PhaseReadiness(
                canAdvance,
                getNextPhase(phase),
                missingPrerequisites.stream().distinct().toList(),
                completenessScore,
                false
            );
        } catch (Exception e) {
            log.error("Error calculating phase readiness: phase={}, projectId={}", phase, projectId, e);
            return new PhasePacket.PhaseReadiness(
                false,
                getNextPhase(phase),
                List.of("Error calculating readiness"),
                0.0,
                true
            );
        }
    }

    private boolean isArtifactComplete(
            PhasePacket.RequiredArtifact required,
            List<PhasePacket.CompletedArtifact> completedArtifacts
    ) {
        return required.isComplete()
                || completedArtifacts.stream().anyMatch(completed ->
                        completed.artifactId().equals(required.artifactId())
                                || completed.artifactType().equals(required.artifactType()));
    }

    private double roundScore(double score) {
        return Math.max(0.0, Math.min(1.0, Math.round(score * 100.0) / 100.0));
    }

    private boolean isPolicyDenied(PhasePacket.GovernanceRecord record) {
        return "DENIED".equalsIgnoreCase(record.outcome())
                || "POLICY_DENIAL".equalsIgnoreCase(record.type());
    }

    /**
     * Queries required artifacts for the phase.
     */
    private List<PhasePacket.RequiredArtifact> queryRequiredArtifacts(
            String phase,
            String projectId
    ) {
        try {
            // Use StageConfigLoader to get required artifacts for the phase
            com.ghatana.yappc.domain.PhaseType phaseType;
            try {
                phaseType = com.ghatana.yappc.domain.PhaseType.valueOf(phase.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid phase type: {}, defaulting to INTENT", phase);
                phaseType = com.ghatana.yappc.domain.PhaseType.INTENT;
            }
            
            // Get stage spec from StageConfigLoader
            var stageConfig = new com.ghatana.yappc.services.lifecycle.StageConfigLoader();
            var stageOpt = stageConfig.findById(phaseType.name().toLowerCase());
            
            if (stageOpt.isEmpty()) {
                log.warn("No stage spec found for phase: {}", phase);
                return List.of();
            }
            
            var stage = stageOpt.get();
            List<String> requiredArtifactKeys = stage.getArtifacts() != null ? stage.getArtifacts() : List.of();
            
            // Convert to RequiredArtifact records
            return requiredArtifactKeys.stream()
                .<PhasePacket.RequiredArtifact>map(artifactKey -> new PhasePacket.RequiredArtifact(
                    artifactKey,
                    artifactKey,
                    "REQUIRED",
                    null,
                    false
                ))
                .toList();
        } catch (Exception e) {
            log.error("Error querying required artifacts: phase={}, projectId={}", phase, projectId, e);
            return List.of();
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
            // Query completed artifacts from artifact repository
            com.ghatana.yappc.domain.PhaseType phaseType;
            try {
                phaseType = com.ghatana.yappc.domain.PhaseType.valueOf(phase.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid phase type: {}, defaulting to INTENT", phase);
                phaseType = com.ghatana.yappc.domain.PhaseType.INTENT;
            }
            
            // List artifact versions for this project and phase
            return artifactRepository.listVersions(projectId, phaseType)
                .map(versions -> {
                    if (versions == null || versions.isEmpty()) {
                        return List.of();
                    }

                    // Convert to CompletedArtifact records
                    return versions.stream()
                        .<PhasePacket.CompletedArtifact>map(version -> new PhasePacket.CompletedArtifact(
                            phase + "-" + version,
                            phase + "-" + version,
                            phase,
                            Instant.now(),
                            null
                        ))
                        .toList();
                });
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
                        .map(event -> new PhasePacket.ActivityFeedEntry(
                            event.getId(),
                            event.getEventType(),
                            phase,
                            event.getDetails().getOrDefault("description", "Audit event").toString(),
                            event.getPrincipal() != null ? event.getPrincipal() : "System",
                            event.getTimestamp(),
                            event.getSuccess() != null && event.getSuccess() ? "INFO" : "ERROR"
                        ))
                        .toList();
                });

        } catch (Exception e) {
            log.error("Error querying activity feed: phase={}, projectId={}, tenantId={}", phase, projectId, tenantId, e);
            return Promise.of(List.of());
        }
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

            return new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(
                    previewHealth.healthy(),
                    previewHealth.status(),
                    previewHealth.issues()
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
                )
            );

        } catch (Exception e) {
            log.error("Error building health signals: phase={}, projectId={}", phase, projectId, e);
            return new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(false, "error", List.of("Health check failed")),
                new PhasePacket.GenerationHealth(false, "error", null, List.of("Health check failed")),
                new PhasePacket.RuntimeHealth(false, "error", null, List.of("Health check failed"))
            );
        }
    }

    private Optional<String> stringState(Map<String, Object> projectState, String key) {
        Object value = projectState.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        return Optional.empty();
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
     * Builds a degraded phase packet when Data Cloud is unavailable.
     *
     * @param phase the phase name
     * @param projectId the project ID
     * @param workspaceId the workspace ID
     * @param principal the authenticated principal
     * @param correlationId the correlation ID for tracing
     * @param degradedReason the reason for degradation
     * @return a degraded phase packet
     */
    private PhasePacket buildDegradedPhasePacket(
            String phase,
            String projectId,
            String workspaceId,
            Principal principal,
            String correlationId,
            String degradedReason
    ) {
        // Build actor context
        PhasePacket.ActorContext actor = buildActorContext(principal, Map.of());
        
        // Build degraded capability model (read-only)
        PhasePacket.CapabilityModel capabilities = new PhasePacket.CapabilityModel(
            true,  // canRead - allow read even in degraded state
            false, // canCreate - no create in degraded state
            false, // canUpdate - no update in degraded state
            false, // canDelete - no delete in degraded state
            false, // canApprove - no approve in degraded state
            false, // canReject - no reject in degraded state
            false  // canRollback - no rollback in degraded state
        );
        
        // Build degraded blocker
        List<PhasePacket.PhaseBlocker> blockers = List.of(
            new PhasePacket.PhaseBlocker(
                "data-cloud-degraded",
                "SYSTEM",
                "Data Cloud Service Unavailable",
                degradedReason,
                "CRITICAL",
                projectId,
                false
            )
        );
        
        // Build degraded readiness
        PhasePacket.PhaseReadiness readiness = new PhasePacket.PhaseReadiness(
            false, // canAdvance - cannot advance in degraded state
            null,  // nextPhase - unknown in degraded state
            List.of("Data Cloud service unavailable"),
            0.0,   // completeness score - 0 in degraded state
            true   // isDegraded - explicitly marked as degraded
        );
        
        // Build degraded health signals
        PhasePacket.HealthSignals healthSignals = new PhasePacket.HealthSignals(
            new PhasePacket.PreviewHealth(false, "degraded", List.of(degradedReason)),
            new PhasePacket.GenerationHealth(false, "degraded", null, List.of(degradedReason)),
            new PhasePacket.RuntimeHealth(false, "degraded", null, List.of(degradedReason))
        );
        
        // Record metrics if available
        if (metrics != null) {
            metrics.recordPhaseGateValidation(principal.getTenantId(), phase, "DEGRADED", System.currentTimeMillis() - 0);
        }
        
        return new PhasePacket(
            phase,
            projectId,
            "degraded-project", // explicit degraded sentinel
            principal.getTenantId(),
            workspaceId,
            "degraded-workspace", // explicit degraded sentinel
            actor,
            phase,
            PhasePacket.TenantTier.FREE, // fail-closed default in degraded state
            Set.of(), // no feature flags in degraded state
            capabilities,
            blockers,
            readiness,
            List.of(), // no required artifacts in degraded state
            List.of(), // no completed artifacts in degraded state
            List.of(), // no activity feed in degraded state
            List.of(), // no evidence in degraded state
            List.of(), // no governance records in degraded state
            new PhasePacket.PlatformRunStatus(
                "unavailable-" + projectId,
                "NOT_READY",
                "data-cloud-aep",
                Instant.now(),
                null,
                correlationId,
                List.of()
            ),
            List.of(), // no actions in degraded state
            new PhasePacket.DashboardActionClassification(null, List.of("all"), List.of(), List.of()),
            healthSignals,
            Instant.now().toEpochMilli(),
            correlationId
        );
    }

    /**
     * Determines the next phase in the lifecycle using TransitionConfigLoader.
     */
    private String getNextPhase(String currentPhase) {
        return transitionConfigLoader.getNextPhase(currentPhase);
    }
}
