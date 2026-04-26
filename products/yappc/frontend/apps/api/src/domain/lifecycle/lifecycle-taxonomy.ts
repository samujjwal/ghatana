/**
 * Lifecycle Phase Taxonomy
 *
 * Canonical lifecycle phase definitions and legacy migration mappings.
 * This is the single source of truth for lifecycle phases across the system.
 *
 * Phases follow the OctoSpan pattern: 8 phases from initial intent
 * through institutionalized practice.
 *
 * @doc.type module
 * @doc.purpose Canonical lifecycle phase taxonomy
 * @doc.layer domain
 * @doc.pattern Taxonomy, Value Object
 */

// ============================================================================
// Canonical Phase Definitions
// ============================================================================

export type LifecyclePhaseId =
  | 'INTENT'
  | 'CONTEXT'
  | 'PLAN'
  | 'EXECUTE'
  | 'VERIFY'
  | 'OBSERVE'
  | 'LEARN'
  | 'INSTITUTIONALIZE';

/**
 * Legacy phase IDs that have been replaced by canonical names.
 * These are kept for backward compatibility during migration.
 * @deprecated Use canonical LifecyclePhaseId values instead
 */
export type LegacyLifecyclePhaseId =
  | 'SHAPE' // Replaced by CONTEXT
  | 'VALIDATE' // Replaced by PLAN
  | 'GENERATE' // Replaced by EXECUTE
  | 'RUN' // Replaced by VERIFY
  | 'IMPROVE'; // Replaced by LEARN

/**
 * All valid phase identifiers (canonical + legacy for input validation)
 */
export type AllLifecyclePhaseId = LifecyclePhaseId | LegacyLifecyclePhaseId;

// ============================================================================
// Legacy to Canonical Mapping
// ============================================================================

/**
 * Maps legacy phase names to their canonical equivalents.
 * This mapping is used during data migration and API input normalization.
 */
export const LEGACY_TO_CANONICAL_MAP: Record<
  LegacyLifecyclePhaseId,
  LifecyclePhaseId
> = {
  SHAPE: 'CONTEXT',
  VALIDATE: 'PLAN',
  GENERATE: 'EXECUTE',
  RUN: 'VERIFY',
  IMPROVE: 'LEARN',
};

/**
 * Reverse mapping for reference (canonical to legacy)
 */
export const CANONICAL_TO_LEGACY_MAP: Partial<
  Record<LifecyclePhaseId, LegacyLifecyclePhaseId>
> = {
  CONTEXT: 'SHAPE',
  PLAN: 'VALIDATE',
  EXECUTE: 'GENERATE',
  VERIFY: 'RUN',
  LEARN: 'IMPROVE',
};

// ============================================================================
// Phase Metadata
// ============================================================================

export interface LifecyclePhaseMetadata {
  id: LifecyclePhaseId;
  name: string;
  description: string;
  stage: number;
  color: string;
  icon: string;
  gates: string[];
  personas: string[];
  keyArtifacts: string[];
  /**
   * Whether this phase allows AI-assisted transitions
   */
  aiAssistEnabled: boolean;
  /**
   * Required approvals for transition out of this phase
   */
  exitRequirements: {
    requiresApproval: boolean;
    approverRoles: string[];
    minimumEvidenceCount: number;
  };
}

/**
 * Complete phase definitions in order.
 * This array defines the canonical progression through the lifecycle.
 */
export const LIFECYCLE_PHASES: LifecyclePhaseMetadata[] = [
  {
    id: 'INTENT',
    name: 'Intent',
    description: 'Define the problem and strategic intent',
    stage: 0,
    color: '#3B82F6',
    icon: '💡',
    gates: ['problem-defined', 'stakeholders-aligned'],
    personas: ['Product Owner', 'Product Manager'],
    keyArtifacts: ['Idea Brief', 'Problem Statement', 'Success Criteria'],
    aiAssistEnabled: true,
    exitRequirements: {
      requiresApproval: false,
      approverRoles: ['PRODUCT_MANAGER', 'PRODUCT_OWNER'],
      minimumEvidenceCount: 0,
    },
  },
  {
    id: 'CONTEXT',
    name: 'Context',
    description: 'Capture the requirements, architecture, and delivery context',
    stage: 1,
    color: '#8B5CF6',
    icon: '🧭',
    gates: ['architecture-approved', 'tech-stack-selected'],
    personas: ['Architect', 'Tech Lead'],
    keyArtifacts: ['Architecture Diagram', 'Tech Stack', 'API Design'],
    aiAssistEnabled: true,
    exitRequirements: {
      requiresApproval: true,
      approverRoles: ['ARCHITECT', 'TECH_LEAD'],
      minimumEvidenceCount: 1,
    },
  },
  {
    id: 'PLAN',
    name: 'Plan',
    description: 'Produce a concrete, risk-aware delivery plan with estimates',
    stage: 2,
    color: '#F59E0B',
    icon: '📋',
    gates: ['plan-approved', 'risks-mitigated'],
    personas: ['Engineering Manager', 'Tech Lead'],
    keyArtifacts: ['Delivery Plan', 'Risk Register', 'Estimates'],
    aiAssistEnabled: true,
    exitRequirements: {
      requiresApproval: true,
      approverRoles: ['ENGINEERING_MANAGER', 'TECH_LEAD'],
      minimumEvidenceCount: 2,
    },
  },
  {
    id: 'EXECUTE',
    name: 'Execute',
    description: 'Build, test, and integrate the solution',
    stage: 3,
    color: '#10B981',
    icon: '🔨',
    gates: ['feature-complete', 'tests-passing'],
    personas: ['Developer', 'QA Engineer'],
    keyArtifacts: ['Code', 'Tests', 'Documentation'],
    aiAssistEnabled: true,
    exitRequirements: {
      requiresApproval: false,
      approverRoles: ['TECH_LEAD'],
      minimumEvidenceCount: 0,
    },
  },
  {
    id: 'VERIFY',
    name: 'Verify',
    description: 'Validate the solution meets requirements and quality standards',
    stage: 4,
    color: '#06B6D4',
    icon: '✅',
    gates: ['qa-passed', 'acceptance-criteria-met'],
    personas: ['QA Engineer', 'Product Manager'],
    keyArtifacts: ['Test Reports', 'Acceptance Sign-off', 'Quality Metrics'],
    aiAssistEnabled: true,
    exitRequirements: {
      requiresApproval: true,
      approverRoles: ['QA_LEAD', 'PRODUCT_MANAGER'],
      minimumEvidenceCount: 2,
    },
  },
  {
    id: 'OBSERVE',
    name: 'Observe',
    description: 'Monitor production behavior, incidents, and user feedback',
    stage: 5,
    color: '#6366F1',
    icon: '👁️',
    gates: ['monitoring-active', 'alerts-configured'],
    personas: ['SRE', 'Support Engineer'],
    keyArtifacts: ['Monitoring Dashboard', 'Incident Response Plan', 'SLAs'],
    aiAssistEnabled: true,
    exitRequirements: {
      requiresApproval: false,
      approverRoles: ['SRE'],
      minimumEvidenceCount: 0,
    },
  },
  {
    id: 'LEARN',
    name: 'Learn',
    description: 'Capture lessons, follow-ups, and improvement signals',
    stage: 6,
    color: '#EC4899',
    icon: '📚',
    gates: ['improvements-identified', 'next-iteration-planned'],
    personas: ['Product Manager', 'All'],
    keyArtifacts: ['Improvement Backlog', 'Metrics Analysis', 'Lessons Learned'],
    aiAssistEnabled: true,
    exitRequirements: {
      requiresApproval: false,
      approverRoles: ['PRODUCT_MANAGER'],
      minimumEvidenceCount: 1,
    },
  },
  {
    id: 'INSTITUTIONALIZE',
    name: 'Institutionalize',
    description: 'Roll validated practices back into the operating model',
    stage: 7,
    color: '#14B8A6',
    icon: '🏛️',
    gates: ['changes-adopted', 'standards-updated'],
    personas: ['Platform Lead', 'Engineering Manager'],
    keyArtifacts: ['Standards Update', 'Reusable Playbook', 'Adoption Plan'],
    aiAssistEnabled: false, // Human approval required for institutionalization
    exitRequirements: {
      requiresApproval: true,
      approverRoles: ['PLATFORM_LEAD', 'ENGINEERING_MANAGER'],
      minimumEvidenceCount: 3,
    },
  },
];

// ============================================================================
// Lookup Utilities
// ============================================================================

/**
 * Ordered list of canonical phase IDs
 */
export const LIFECYCLE_PHASE_ORDER: LifecyclePhaseId[] = LIFECYCLE_PHASES.map(
  (phase) => phase.id
);

/**
 * Map of phase IDs to their metadata
 */
export const LIFECYCLE_PHASES_BY_ID: Record<
  LifecyclePhaseId,
  LifecyclePhaseMetadata
> = LIFECYCLE_PHASES.reduce(
  (accumulator, phase) => ({
    ...accumulator,
    [phase.id]: phase,
  }),
  {} as Record<LifecyclePhaseId, LifecyclePhaseMetadata>
);

/**
 * All valid phase IDs including legacy (for input validation)
 */
export const ALL_VALID_PHASE_IDS: string[] = [
  ...LIFECYCLE_PHASE_ORDER,
  ...Object.keys(LEGACY_TO_CANONICAL_MAP),
];

// ============================================================================
// Validation Functions
// ============================================================================

/**
 * Check if a value is a valid canonical phase ID
 */
export function isCanonicalLifecyclePhaseId(
  value: string | undefined | null
): value is LifecyclePhaseId {
  if (!value) return false;
  return value in LIFECYCLE_PHASES_BY_ID;
}

/**
 * Check if a value is a valid legacy phase ID
 */
export function isLegacyLifecyclePhaseId(
  value: string | undefined | null
): value is LegacyLifecyclePhaseId {
  if (!value) return false;
  return value in LEGACY_TO_CANONICAL_MAP;
}

/**
 * Check if a value is any valid phase ID (canonical or legacy)
 */
export function isValidLifecyclePhaseId(
  value: string | undefined | null
): value is AllLifecyclePhaseId {
  return isCanonicalLifecyclePhaseId(value) || isLegacyLifecyclePhaseId(value);
}

// ============================================================================
// Normalization Functions
// ============================================================================

/**
 * Normalize a phase ID to its canonical form.
 * Legacy phase names are mapped to their canonical equivalents.
 * Returns null for invalid/empty input.
 */
export function normalizeLifecyclePhaseId(
  value: string | undefined | null
): LifecyclePhaseId | null {
  if (!value) {
    return null;
  }

  // Already canonical
  if (isCanonicalLifecyclePhaseId(value)) {
    return value;
  }

  // Legacy name - convert to canonical
  if (isLegacyLifecyclePhaseId(value)) {
    return LEGACY_TO_CANONICAL_MAP[value];
  }

  // Invalid value
  return null;
}

/**
 * Normalize with fallback to default phase
 */
export function normalizeLifecyclePhaseIdWithFallback(
  value: string | undefined | null,
  fallback: LifecyclePhaseId = 'INTENT'
): LifecyclePhaseId {
  return normalizeLifecyclePhaseId(value) ?? fallback;
}

// ============================================================================
// Transition Rules
// ============================================================================

/**
 * Get the next phase in the sequence
 */
export function getNextPhase(
  currentPhase: LifecyclePhaseId
): LifecyclePhaseId | null {
  const currentIndex = LIFECYCLE_PHASE_ORDER.indexOf(currentPhase);
  if (currentIndex === -1 || currentIndex >= LIFECYCLE_PHASE_ORDER.length - 1) {
    return null;
  }
  return LIFECYCLE_PHASE_ORDER[currentIndex + 1];
}

/**
 * Get the previous phase in the sequence
 */
export function getPreviousPhase(
  currentPhase: LifecyclePhaseId
): LifecyclePhaseId | null {
  const currentIndex = LIFECYCLE_PHASE_ORDER.indexOf(currentPhase);
  if (currentIndex <= 0) {
    return null;
  }
  return LIFECYCLE_PHASE_ORDER[currentIndex - 1];
}

/**
 * Check if transitioning from one phase to another is valid
 */
export function isValidPhaseTransition(
  fromPhase: LifecyclePhaseId,
  toPhase: LifecyclePhaseId
): boolean {
  const fromIndex = LIFECYCLE_PHASE_ORDER.indexOf(fromPhase);
  const toIndex = LIFECYCLE_PHASE_ORDER.indexOf(toPhase);

  if (fromIndex === -1 || toIndex === -1) {
    return false;
  }

  // Can only move forward one step at a time, or backward any amount
  const diff = toIndex - fromIndex;
  return diff === 1 || diff <= 0;
}

/**
 * Get phase metadata by ID
 */
export function getPhaseMetadata(
  phaseId: LifecyclePhaseId
): LifecyclePhaseMetadata | null {
  return LIFECYCLE_PHASES_BY_ID[phaseId] ?? null;
}

/**
 * Get exit requirements for a phase
 */
export function getPhaseExitRequirements(phaseId: LifecyclePhaseId): {
  requiresApproval: boolean;
  approverRoles: string[];
  minimumEvidenceCount: number;
} {
  const phase = getPhaseMetadata(phaseId);
  return (
    phase?.exitRequirements ?? {
      requiresApproval: false,
      approverRoles: [],
      minimumEvidenceCount: 0,
    }
  );
}
