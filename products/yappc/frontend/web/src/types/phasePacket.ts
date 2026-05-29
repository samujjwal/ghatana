/**
 * Canonical PhaseCockpitPacket Types — YAPPC Web.
 *
 * TypeScript types for the canonical PhaseCockpitPacket contract.
 * These types mirror the backend PhasePacket.java contract exactly.
 *
 * @doc.type module
 * @doc.purpose TypeScript types for PhaseCockpitPacket
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

// ============================================================================
// Context Types
// ============================================================================

/**
 * Actor context information.
 */
export interface ActorContext {
  readonly actorId: string;
  readonly actorName: string;
  readonly role: string;
  readonly isOwner: boolean;
  readonly isAdmin: boolean;
}

/**
 * Tenant tier enumeration.
 */
export type TenantTier = 'FREE' | 'PRO' | 'ENTERPRISE';

/**
 * Capability model for the current actor.
 */
export interface CapabilityModel {
  readonly canRead: boolean;
  readonly canCreate: boolean;
  readonly canUpdate: boolean;
  readonly canDelete: boolean;
  readonly canApprove: boolean;
  readonly canReject: boolean;
  readonly canRollback: boolean;
}

// ============================================================================
// State Types
// ============================================================================

/**
 * Phase blocker record.
 */
export interface PhaseBlocker {
  readonly id: string;
  readonly type: string;
  readonly title: string;
  readonly description: string;
  readonly severity: string;
  readonly resourceId: string;
  readonly resolvable: boolean;
}

/**
 * Phase readiness information.
 */
export interface PhaseReadiness {
  readonly canAdvance: boolean;
  readonly nextPhase: string;
  readonly missingPrerequisites: readonly string[];
  readonly completenessScore: number;
  readonly isDegraded: boolean;
  readonly estimatedReadyIn?: string | null;
  readonly estimatedReadyInHours?: number | null;
  readonly predictionConfidence?: number | null;
}

/**
 * Required artifact for the current phase.
 */
export interface RequiredArtifact {
  readonly artifactId: string;
  readonly artifactType: string;
  readonly title: string;
  readonly description: string;
  readonly isComplete: boolean;
}

/**
 * Completed artifact for the current phase.
 */
export interface CompletedArtifact {
  readonly artifactId: string;
  readonly artifactType: string;
  readonly version: string;
  readonly title: string;
  readonly completedAt: string;
  readonly completedBy: string;
  readonly evidenceId: string | null;
}

/**
 * Activity feed entry.
 */
export interface ActivityFeedEntry {
  readonly id: string;
  readonly type: string;
  readonly action: string;
  readonly summary: string;
  readonly actor: string;
  readonly timestamp: string;
  readonly severity: string;
  readonly eventType: string;
  readonly success: boolean | null;
  readonly outcome: string;
  readonly correlationId?: string | null;
}

// ============================================================================
// Platform Integration Types
// ============================================================================

/**
 * Phase evidence record from Data Cloud+AEP.
 */
export interface PhaseEvidence {
  readonly id: string;
  readonly type: string;
  readonly title: string;
  readonly description: string;
  readonly timestamp: string;
  readonly metadata: Record<string, unknown>;
  readonly evidenceId: string;
}

/**
 * Governance record.
 */
export interface GovernanceRecord {
  readonly id: string;
  readonly type: string;
  readonly outcome: string;
  readonly actor: string;
  readonly timestamp: string;
  readonly metadata: Record<string, unknown>;
  readonly policyDecisionId: string;
}

/**
 * Data Cloud+AEP platform run status.
 */
export interface PlatformRunStatus {
  readonly runId: string;
  readonly status: string;
  readonly platform: string;
  readonly startedAt: string;
  readonly completedAt?: string;
  readonly traceId: string;
  readonly evidenceIds: readonly string[];`r`n  readonly rollbackTarget?: string;`r`n  readonly promoteTarget?: string;`r`n  readonly releaseCandidate?: string;`r`n  readonly riskLevel?: string;`r`n  readonly remediationHint?: string;`r`n  readonly rollbackSupported?: boolean;
}

// ============================================================================
// Action Types
// ============================================================================

/**
 * Phase action contract with capability gating.
 */
export interface PhaseAction {
  readonly actionId: string;
  readonly label: string;
  readonly description: string;
  readonly enabled: boolean;
  readonly disabledReason?: string;
  readonly requiredPermission: string;
  readonly category: string;
  readonly severity: string;
  readonly confirmationRequired: boolean;
  readonly idempotencyKey: string;
  readonly auditType: string;
  readonly targetType?: string;
  readonly targetRoute?: string | null;
  readonly targetDrawer?: string | null;
  readonly requiresPreview?: boolean;
  readonly serverOperation?: string;
  readonly postSuccessBehavior?: string;
  readonly parameters: Record<string, unknown>;
}

/**
 * Dashboard action classification.
 */
export interface DashboardActionClassification {
  readonly primaryAction: string;
  readonly blockedActions: readonly string[];
  readonly reviewRequiredActions: readonly string[];
  readonly safeToContinueActions: readonly string[];
}

/**
 * Backend-owned card within a phase status panel.
 */
export interface PhasePanelCard {
  readonly id: string;
  readonly title: string;
  readonly detail: string;
  readonly status: string;
  readonly trace: string;
  readonly metadata: Record<string, unknown>;
}

export interface LearningInsightPanel {
  readonly learnedSignal: string;
  readonly sourceEvent: string;
  readonly confidence: number;
  readonly recommendation: string;
  readonly approvalRequired: boolean;
  readonly rollbackPath: string;
}

export interface EvolutionPlanPanel {
  readonly proposal: string;
  readonly impactSummary: string;
  readonly diffSummary: string;
  readonly validationRequirements: string;
  readonly approvalState: string;
  readonly rollbackPath: string;
  readonly rerunTarget: string;
}

/**
 * Backend-owned phase panel view model.
 */
export interface PhasePanelView {
  readonly phase: string;
  readonly status: string;
  readonly summary: string;
  readonly recommendation: string;
  readonly owner: string;
  readonly confidence: number;
  readonly supportTrace: string;
  readonly cards: readonly PhasePanelCard[];
  readonly learningInsight?: LearningInsightPanel;
  readonly evolutionPlan?: EvolutionPlanPanel;
}

// ============================================================================
// Health Signal Types
// ============================================================================

/**
 * Preview health status.
 */
export interface PreviewHealth {
  readonly isHealthy: boolean;
  readonly status: string;
  readonly issues: readonly string[];
  readonly security?: PreviewSecurity;
}

/**
 * Preview token scope grant status.
 */
export interface PreviewTokenScope {
  readonly id: string;
  readonly name: string;
  readonly required: boolean;
  readonly granted: boolean;
}

/**
 * Preview token/trust security status.
 */
export interface PreviewSecurity {
  readonly trustLevel: string;
  readonly tokenScopes: readonly PreviewTokenScope[];
  readonly expiresAt?: string | null;
  readonly expired: boolean;
  readonly safe: boolean;
  readonly issues: readonly string[];
}

/**
 * Generation health status.
 */
export interface GenerationHealth {
  readonly isHealthy: boolean;
  readonly status: string;
  readonly lastGeneratedAt?: string;
  readonly issues: readonly string[];
}

/**
 * Runtime health status.
 */
export interface RuntimeHealth {
  readonly isHealthy: boolean;
  readonly status: string;
  readonly lastDeployedAt?: string;
  readonly issues: readonly string[];
}

/**
 * Agent governance and learning evidence health status.
 */
export interface AgentGovernanceHealth {
  readonly isHealthy: boolean;
  readonly status: string;
  readonly governanceState: string;
  readonly learningLevel: string;
  readonly evidenceIds: readonly string[];`r`n  readonly rollbackTarget?: string;`r`n  readonly promoteTarget?: string;`r`n  readonly releaseCandidate?: string;`r`n  readonly riskLevel?: string;`r`n  readonly remediationHint?: string;`r`n  readonly rollbackSupported?: boolean;
  readonly issues: readonly string[];
}

/**
 * Health signals for preview/generation/runtime.
 */
export interface HealthSignals {
  readonly preview: PreviewHealth;
  readonly generation: GenerationHealth;
  readonly runtime: RuntimeHealth;
  readonly agentGovernance?: AgentGovernanceHealth;
}

/**
 * Dependency-specific details for a degraded packet.
 */
export interface DegradedPacketDetails {
  readonly dependency: string;
  readonly reason: string;
  readonly truthSource: string;
  readonly recoveryAction: string;
  readonly impactedFeatures: readonly string[];
}

// ============================================================================
// Main Packet Type
// ============================================================================

/**
 * Canonical PhaseCockpitPacket contract.
 *
 * This is the canonical contract for phase cockpit data provided by the backend.
 * The frontend should consume this packet directly without reconstructing lifecycle rules.
 */
export interface PhaseCockpitPacket {
  // Context fields
  readonly phase: string;
  readonly projectId: string;
  readonly projectName?: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly workspaceName?: string;
  readonly actor: ActorContext;
  readonly lifecyclePhase?: string;
  
  // Tenant and capability fields
  readonly tenantTier: TenantTier;
  readonly enabledPhaseFlags: readonly string[];
  readonly capabilities: CapabilityModel;
  
  // State fields
  readonly blockers: readonly PhaseBlocker[];
  readonly readiness: PhaseReadiness;
  readonly requiredArtifacts: readonly RequiredArtifact[];
  readonly completedArtifacts: readonly CompletedArtifact[];
  readonly activityFeed: readonly ActivityFeedEntry[];
  
  // Platform integration fields
  readonly evidence: readonly PhaseEvidence[];
  readonly governance: readonly GovernanceRecord[];
  readonly platformRunStatus?: PlatformRunStatus;
  
  // Action fields
  readonly availableActions: readonly PhaseAction[];
  readonly dashboardActions: DashboardActionClassification;
  readonly phasePanels: readonly PhasePanelView[];
  
  // Health signals
  readonly healthSignals: HealthSignals;
  readonly degradedDetails?: DegradedPacketDetails;
  
  // Metadata
  readonly timestamp: number;
  readonly correlationId?: string;
}

// ============================================================================
// Request Types
// ============================================================================

/**
 * Request for fetching a phase packet.
 */
export interface PhasePacketRequest {
  readonly phase: string;
  readonly tenantId?: string;
  readonly projectId: string;
  readonly workspaceId?: string;
  readonly correlationId?: string;
}
