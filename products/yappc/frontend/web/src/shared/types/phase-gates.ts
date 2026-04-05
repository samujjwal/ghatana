/**
 * Phase Gate Types and Definitions
 *
 * Complete type system for YAPPC lifecycle phase gates.
 * Gates enforce artifact-completion requirements before phase transitions.
 * The catalog of required artifacts per gate is derived from
 * LIFECYCLE_ARTIFACT_CATALOG — there is no separate hard-coded list.
 *
 * @doc.type types
 * @doc.purpose Phase gate validation definitions and utilities
 * @doc.layer product
 * @doc.pattern Domain Types
 */

import { LifecyclePhase } from '@/types/lifecycle';
import {
  type LifecycleArtifactKind,
  LIFECYCLE_ARTIFACT_CATALOG,
  getArtifactsForPhase,
} from './lifecycle-artifacts';

// ============================================================================
// Core Types
// ============================================================================

/**
 * Per-rule validation result collected when a gate is evaluated.
 */
export interface ValidationResult {
  ruleId: string;
  valid: boolean;
  errors: string[];
  warnings: string[];
}

/**
 * Overall gate evaluation result for a specific project.
 */
export interface GateStatus {
  gateId: string;
  status: 'pending' | 'passed' | 'failed' | 'blocked' | 'bypassed';
  /** Human-readable reason when status is "blocked" or "failed". */
  blockedReason?: string;
  validationResults: ValidationResult[];
}

/**
 * Snapshot of a single lifecycle artifact used during gate evaluation.
 */
export interface ItemSummary {
  id: string;
  title: string;
  artifactKind: LifecycleArtifactKind;
  status: 'draft' | 'complete' | 'validated';
  lastUpdated: string;
}

/**
 * Runtime context passed to gate-validation functions.
 */
export interface GateContext {
  projectId: string;
  currentPhase: LifecyclePhase;
  targetPhase: LifecyclePhase;
  /** Artifacts that exist in the project — keyed by their kind. */
  lifecycleArtifactItemsByKind: Partial<
    Record<LifecycleArtifactKind, ItemSummary>
  >;
}

/**
 * Static definition of a single phase gate.
 */
export interface PhaseGate {
  id: string;
  name: string;
  description: string;
  fromPhase: LifecyclePhase;
  toPhase: LifecyclePhase;
  /** Whether a user can bypass this gate with an explicit reason. */
  canBypass: boolean;
  /**
   * Artifact kinds that must be "complete" or "validated" before the gate
   * passes. Derived automatically from LIFECYCLE_ARTIFACT_CATALOG.
   */
  requiredArtifactKinds: LifecycleArtifactKind[];
}

/**
 * Shape returned by validatePhaseTransition.
 */
export interface PhaseTransitionValidation {
  canTransition: boolean;
  gate?: PhaseGate;
  gateStatus?: GateStatus;
}

// ============================================================================
// Gate Catalog — derived from LIFECYCLE_ARTIFACT_CATALOG
// ============================================================================

/**
 * All 6 consecutive phase gates for the 7-phase lifecycle:
 * INTENT → SHAPE → VALIDATE → GENERATE → RUN → OBSERVE → IMPROVE
 *
 * Required artifacts per gate are taken directly from the catalog so
 * that this file never drifts out of sync with lifecycle-artifacts.ts.
 */
export const PHASE_GATES: PhaseGate[] = [
  {
    id: 'gate:intent-to-shape',
    name: 'Intent → Shape Gate',
    description:
      'All Intent-phase artifacts (Idea Brief, Research Pack, Problem Statement) must be complete before shaping can begin.',
    fromPhase: LifecyclePhase.INTENT,
    toPhase: LifecyclePhase.SHAPE,
    canBypass: true,
    requiredArtifactKinds: getArtifactsForPhase(LifecyclePhase.INTENT),
  },
  {
    id: 'gate:shape-to-validate',
    name: 'Shape → Validate Gate',
    description:
      'All Shape-phase artifacts (Requirements, ADR, UX Spec) must be complete before validation can begin.',
    fromPhase: LifecyclePhase.SHAPE,
    toPhase: LifecyclePhase.VALIDATE,
    canBypass: true,
    requiredArtifactKinds: getArtifactsForPhase(LifecyclePhase.SHAPE),
  },
  {
    id: 'gate:validate-to-generate',
    name: 'Validate → Generate Gate',
    description:
      'All Validate-phase artifacts (Threat Model, Validation Report, Simulation Results) must be complete before code generation can begin.',
    fromPhase: LifecyclePhase.VALIDATE,
    toPhase: LifecyclePhase.GENERATE,
    canBypass: true,
    requiredArtifactKinds: getArtifactsForPhase(LifecyclePhase.VALIDATE),
  },
  {
    id: 'gate:generate-to-run',
    name: 'Generate → Run Gate',
    description:
      'All Generate-phase artifacts (Delivery Plan, Release Strategy) must be complete before execution can begin.',
    fromPhase: LifecyclePhase.GENERATE,
    toPhase: LifecyclePhase.RUN,
    canBypass: true,
    requiredArtifactKinds: getArtifactsForPhase(LifecyclePhase.GENERATE),
  },
  {
    id: 'gate:run-to-observe',
    name: 'Run → Observe Gate',
    description:
      'All Run-phase artifacts (Evidence Pack, Release Packet) must be complete before the observation phase can begin.',
    fromPhase: LifecyclePhase.RUN,
    toPhase: LifecyclePhase.OBSERVE,
    canBypass: true,
    requiredArtifactKinds: getArtifactsForPhase(LifecyclePhase.RUN),
  },
  {
    id: 'gate:observe-to-improve',
    name: 'Observe → Improve Gate',
    description:
      'All Observe-phase artifacts (Ops Baseline, Incident Report) must be complete before the improvement cycle can begin.',
    fromPhase: LifecyclePhase.OBSERVE,
    toPhase: LifecyclePhase.IMPROVE,
    canBypass: true,
    requiredArtifactKinds: getArtifactsForPhase(LifecyclePhase.OBSERVE),
  },
];

/** Quick O(1) lookup of a gate by its ID. */
export const PHASE_GATES_BY_ID: Readonly<Record<string, PhaseGate>> =
  Object.fromEntries(PHASE_GATES.map((g) => [g.id, g]));

// ============================================================================
// Validation Functions
// ============================================================================

/**
 * Look up the gate that guards the transition from `from` to `to`.
 * Returns undefined when no gate is defined for that pair (e.g. non-adjacent
 * phases or same-phase).
 */
export function getGateForTransition(
  from: LifecyclePhase,
  to: LifecyclePhase
): PhaseGate | undefined {
  return PHASE_GATES.find((g) => g.fromPhase === from && g.toPhase === to);
}

/**
 * Evaluate a single gate against the provided project context.
 *
 * The gate passes when every required artifact exists and is in
 * `complete` or `validated` status.  A `draft` artifact or a missing
 * artifact causes a `blocked` status.
 */
export function validateGate(
  gate: PhaseGate,
  context: GateContext
): GateStatus {
  const validationResults: ValidationResult[] = [];
  const blockedReasons: string[] = [];

  for (const kind of gate.requiredArtifactKinds) {
    const item = context.lifecycleArtifactItemsByKind[kind];
    const meta = LIFECYCLE_ARTIFACT_CATALOG[kind];
    const ruleId = `artifact:${kind}`;

    if (!item) {
      blockedReasons.push(`Missing required artifact: ${meta.label}`);
      validationResults.push({
        ruleId,
        valid: false,
        errors: [`Missing required artifact: ${meta.label}`],
        warnings: [],
      });
    } else if (item.status === 'draft') {
      blockedReasons.push(`Artifact not yet complete: ${meta.label}`);
      validationResults.push({
        ruleId,
        valid: false,
        errors: [`Artifact not yet complete: ${meta.label} (status: draft)`],
        warnings: [],
      });
    } else {
      validationResults.push({
        ruleId,
        valid: true,
        errors: [],
        warnings: [],
      });
    }
  }

  if (blockedReasons.length > 0) {
    return {
      gateId: gate.id,
      status: 'blocked',
      blockedReason: blockedReasons[0],
      validationResults,
    };
  }

  // Gate with no required artifacts stays "pending" (no rules to pass)
  const status = gate.requiredArtifactKinds.length === 0 ? 'pending' : 'passed';
  return {
    gateId: gate.id,
    status,
    validationResults,
  };
}

/**
 * Evaluate whether a phase transition is allowed for the given context.
 *
 * - If no gate is defined for the (`currentPhase` → `targetPhase`) pair and
 *   they are the same phase, the transition is a no-op and is always allowed.
 * - If no gate is defined for a different pair, the transition is blocked
 *   (only consecutive phases are permitted).
 * - Otherwise the gate is evaluated and the result drives `canTransition`.
 */
export function validatePhaseTransition(
  context: GateContext
): PhaseTransitionValidation {
  // Same-phase is a no-op — always allowed, no gate needed
  if (context.currentPhase === context.targetPhase) {
    return { canTransition: true };
  }

  const gate = getGateForTransition(context.currentPhase, context.targetPhase);

  if (!gate) {
    return {
      canTransition: false,
      gateStatus: {
        gateId: '',
        status: 'blocked',
        blockedReason: `No gate defined for transition from ${context.currentPhase} to ${context.targetPhase}. Only consecutive-phase transitions are permitted.`,
        validationResults: [],
      },
    };
  }

  const gateStatus = validateGate(gate, context);
  return {
    canTransition:
      gateStatus.status !== 'blocked' && gateStatus.status !== 'failed',
    gate,
    gateStatus,
  };
}
