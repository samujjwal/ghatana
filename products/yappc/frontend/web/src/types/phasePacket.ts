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
  readonly evidenceIds: readonly string[];
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
 * Health signals for preview/generation/runtime.
 */
export interface HealthSignals {
  readonly preview: PreviewHealth;
  readonly generation: GenerationHealth;
  readonly runtime: RuntimeHealth;
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
