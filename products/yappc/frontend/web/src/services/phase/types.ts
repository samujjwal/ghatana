import type { ProjectAccessFields } from '@/services/workspace/accessControl';

export type MountedPhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'learn'
  | 'evolve';

/**
 * Typed identifier for a phase icon from the lucide-react icon registry.
 * Extend this union as new phase icon needs arise; prefer descriptive, accessible names.
 */
export type PhaseIconId =
  | 'target'
  | 'layers'
  | 'check-circle'
  | 'code-2'
  | 'play-circle'
  | 'eye'
  | 'lightbulb'
  | 'arrow-up-right';

export interface PhaseConfig {
  name: string;
  description: string;
  primaryTitle: string;
  primaryDescription: string;
  primaryLabel: string;
  primaryTestId: string;
  secondaryLabel: string;
  secondaryTestId: string;
  /** Typed icon identifier — rendered via resolvePhaseIcon(), never a raw emoji. */
  icon: PhaseIconId;
  supportingTitle: string;
  actionFeedback: string;
  /** When true, the primary action CTA is locked for the current context. */
  primaryLocked?: boolean;
  /** Reason the primary action is locked; shown as tooltip or helper text. */
  primaryLockedReason?: string;
}

// ─── Adaptive config context types ────────────────────────────────────────────

/**
 * Canonical user roles within the YAPPC phase cockpit.
 * Controls which CTAs are enabled and what descriptions are shown.
 */
export type PhaseUserRole =
  | 'owner'
  | 'approver'
  | 'contributor'
  | 'viewer'
  | 'guest';

/**
 * Tenant subscription tier. Controls which phases and capabilities are accessible.
 */
export type TenantTier = 'free' | 'starter' | 'pro' | 'enterprise';

/**
 * Feature flag key union. Extend this as new flags are introduced.
 */
export type PhaseFeatureFlag =
  | 'phase.generate.enabled'
  | 'phase.run.preview.enabled'
  | 'phase.run.production.enabled'
  | 'phase.observe.enabled'
  | 'phase.learn.patterns.enabled'
  | 'phase.evolve.enabled';

/**
 * Context passed to getAdaptivePhaseCockpitConfig().
 * Represents the runtime constraints for the current user/tenant/project.
 */
export interface PhaseCockpitContext {
  /** Role of the current user in this project */
  readonly role: PhaseUserRole;
  /** Tenant subscription tier */
  readonly tier: TenantTier;
  /** Active feature flags (as a Set for O(1) lookup) */
  readonly enabledFlags: ReadonlySet<PhaseFeatureFlag>;
  /** Whether the project has unresolved blockers in the current phase */
  readonly hasBlockers: boolean;
  /** Whether the phase transition gate is currently passing */
  readonly gatesPassed: boolean;
  /** Current lifecycle phase of the project */
  readonly currentLifecyclePhase: MountedPhase;
}

export interface PhaseProjectSnapshot extends ProjectAccessFields {
  name?: string;
  description?: string | null;
  lifecyclePhase?: string;
  updatedAt?: string;
  healthScore?: number | null;
  nextActionHints?: string[];
  tenantTier?: TenantTier;
  enabledPhaseFlags?: PhaseFeatureFlag[];
}

export interface PhaseActivityEvent {
  id: string;
  source: 'lifecycle' | 'audit';
  action: string;
  summary: string;
  timestamp: string;
  actor: string | null;
  severity?: string | null;
  success?: boolean | null;
  eventType?: string | null;
  outcome?: string | null;
  correlationId?: string | null;
}

export interface PhaseActivityResponse {
  projectId: string;
  activity: PhaseActivityEvent[];
}

export interface PhaseTransitionPreviewSnapshot {
  projectId: string;
  currentPhase: string;
  nextPhase: string | null;
  canAdvance: boolean;
  readiness: number;
  blockers: string[];
  requiredArtifacts: string[];
  completedArtifacts: string[];
  estimatedReadyIn: string | null;
  estimatedReadyInHours: number | null;
  predictionConfidence: number | null;
  checkedAt: string;
}
