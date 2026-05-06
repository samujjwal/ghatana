import type {
  PhaseConfig,
  MountedPhase,
  PhaseIconId,
  PhaseCockpitContext,
  PhaseFeatureFlag,
} from './types';

const PHASE_CONFIG: Record<MountedPhase, PhaseConfig> = {
  intent: {
    name: 'Intent',
    description: 'Clarify the goal, the problem to solve, and the evidence that supports why this work matters.',
    primaryTitle: 'Define Requirements',
    primaryDescription: 'Capture the user outcome and problem framing so later phases inherit a clear intent.',
    primaryLabel: 'Define Requirements',
    primaryTestId: 'define-requirements',
    secondaryLabel: 'Review Evidence',
    secondaryTestId: 'review-evidence',
    icon: 'target' satisfies PhaseIconId,
    supportingTitle: 'Project overview',
    actionFeedback: 'Project details below are ready for goal and evidence review.',
  },
  shape: {
    name: 'Shape',
    description: 'Turn intent into structure by shaping the UI, information flow, and component composition.',
    primaryTitle: 'Add Components',
    primaryDescription: 'Open the builder surface below and shape the page structure with real components.',
    primaryLabel: 'Add Components',
    primaryTestId: 'add-components',
    secondaryLabel: 'Review Requirements',
    secondaryTestId: 'review-requirements',
    icon: 'layers' satisfies PhaseIconId,
    supportingTitle: 'Canvas and page builder',
    actionFeedback: 'Builder details below are ready for component and layout work.',
  },
  validate: {
    name: 'Validate',
    description: 'Review whether the shaped solution is consistent, ready, and safe to approve for generation.',
    primaryTitle: 'Approve Changes',
    primaryDescription: 'Use the gate and blocker evidence below to decide whether this packet is ready to move forward.',
    primaryLabel: 'Approve Changes',
    primaryTestId: 'approve-changes',
    secondaryLabel: 'Request Changes',
    secondaryTestId: 'request-changes',
    icon: 'check-circle' satisfies PhaseIconId,
    supportingTitle: 'Validation and lifecycle review',
    actionFeedback: 'Validation details below reflect the latest gate summary for this phase.',
  },
  generate: {
    name: 'Generate',
    description: 'Prepare the implementation handoff from approved design into generated output and reviewable diffs.',
    primaryTitle: 'Generate Code',
    primaryDescription: 'Review the supporting builder context below before kicking off implementation work.',
    primaryLabel: 'Generate Code',
    primaryTestId: 'generate-code',
    secondaryLabel: 'Preview Codegen Plan',
    secondaryTestId: 'view-codegen-preview',
    icon: 'code-2' satisfies PhaseIconId,
    supportingTitle: 'Implementation context',
    actionFeedback: 'Implementation details below are ready for code generation review.',
  },
  run: {
    name: 'Run',
    description: 'Check readiness and execute the safest available run or deployment path for this project.',
    primaryTitle: 'Check Readiness',
    primaryDescription: 'Review the deployment posture and capability gates before attempting any release action.',
    primaryLabel: 'Check Readiness',
    primaryTestId: 'check-readiness',
    secondaryLabel: 'View Run Plan',
    secondaryTestId: 'view-run-plan',
    icon: 'play-circle' satisfies PhaseIconId,
    supportingTitle: 'Deployment and run plan',
    actionFeedback: 'Run readiness and deployment planning are available below.',
  },
  observe: {
    name: 'Observe',
    description: 'Watch preview health, activity, and operator signals to understand how the current build behaves.',
    primaryTitle: 'View Metrics',
    primaryDescription: 'Use the preview and observability evidence below to decide what needs attention first.',
    primaryLabel: 'View Metrics',
    primaryTestId: 'view-metrics',
    secondaryLabel: 'View Project Preview',
    secondaryTestId: 'view-project-preview',
    icon: 'eye' satisfies PhaseIconId,
    supportingTitle: 'Preview and observation',
    actionFeedback: 'Preview and operational signals are ready for review below.',
  },
  learn: {
    name: 'Learn',
    description: 'Capture what worked, what failed, and which reusable patterns should inform the next cycle.',
    primaryTitle: 'Capture Learnings',
    primaryDescription: 'Review the latest evidence below and record the most useful lessons from this iteration.',
    primaryLabel: 'Capture Learnings',
    primaryTestId: 'capture-learnings',
    secondaryLabel: 'View Retrospective',
    secondaryTestId: 'view-retrospective',
    icon: 'lightbulb' satisfies PhaseIconId,
    supportingTitle: 'Retrospective and insights',
    actionFeedback: 'Retrospective details below are ready for learnings capture.',
  },
  evolve: {
    name: 'Evolve',
    description: 'Convert validated learnings into the next cycle plan, roadmap changes, and backlog priorities.',
    primaryTitle: 'Plan Next Cycle',
    primaryDescription: 'Use the backed evidence below to decide what the next improvement cycle should prioritize.',
    primaryLabel: 'Plan Next Cycle',
    primaryTestId: 'plan-next-cycle',
    secondaryLabel: 'Review Backlog',
    secondaryTestId: 'view-roadmap',
    icon: 'arrow-up-right' satisfies PhaseIconId,
    supportingTitle: 'Roadmap and backlog',
    actionFeedback: 'Roadmap and backlog details are ready for next-cycle planning.',
  },
};

export function getPhaseCockpitConfig(phase: MountedPhase): PhaseConfig {
  return PHASE_CONFIG[phase];
}

export function getAllPhaseCockpitConfig(): Record<MountedPhase, PhaseConfig> {
  return PHASE_CONFIG;
}

// ─── Adaptive config ──────────────────────────────────────────────────────────

/**
 * Per-phase flag requirements. If the flag is listed and NOT in enabledFlags,
 * the primary action is locked for that phase.
 */
const PHASE_REQUIRED_FLAG: Partial<Record<MountedPhase, PhaseFeatureFlag>> = {
  generate: 'phase.generate.enabled',
  run: 'phase.run.preview.enabled',
  observe: 'phase.observe.enabled',
  learn: 'phase.learn.patterns.enabled',
  evolve: 'phase.evolve.enabled',
};

/**
 * Minimum tier required to access each phase's primary action.
 * Phases not listed here are available on all tiers.
 */
const PHASE_MIN_TIER: Partial<Record<MountedPhase, 'starter' | 'pro' | 'enterprise'>> = {
  run: 'starter',
  observe: 'starter',
  learn: 'pro',
  evolve: 'pro',
};

const TIER_RANK: Record<string, number> = {
  free: 0,
  starter: 1,
  pro: 2,
  enterprise: 3,
};

/** Roles that may trigger the primary action CTA. */
const PRIMARY_ACTION_ROLES = new Set(['owner', 'approver', 'contributor']);

/**
 * Returns an adapted PhaseConfig based on runtime context (role, tier, flags,
 * project state). The static base config is never mutated.
 *
 * Rules applied in order of precedence:
 * 1. Role check — viewer/guest may not trigger primary actions.
 * 2. Tier check — lower-tier tenants are locked out of advanced phases.
 * 3. Feature flag check — disabled flags lock the primary action.
 * 4. Blocker check — unresolved blockers lock the primary action.
 * 5. Gate check — failing gates lock the primary action for Validate/Generate.
 */
export function getAdaptivePhaseCockpitConfig(
  phase: MountedPhase,
  context: PhaseCockpitContext,
): PhaseConfig {
  const base = PHASE_CONFIG[phase];

  const lock = (reason: string): PhaseConfig => ({
    ...base,
    primaryLocked: true,
    primaryLockedReason: reason,
  });

  // 1. Role check
  if (!PRIMARY_ACTION_ROLES.has(context.role)) {
    return lock('You have view-only access to this project.');
  }

  // 2. Tier check
  const minTier = PHASE_MIN_TIER[phase];
  if (minTier !== undefined && (TIER_RANK[context.tier] ?? 0) < (TIER_RANK[minTier] ?? 0)) {
    return lock(`This action requires the ${minTier} plan or higher.`);
  }

  // 3. Feature flag check
  const requiredFlag = PHASE_REQUIRED_FLAG[phase];
  if (requiredFlag !== undefined && !context.enabledFlags.has(requiredFlag)) {
    return lock('This feature is not enabled for your workspace.');
  }

  // 4. Blocker check — blockers lock validate, generate, run primary actions
  const blockerGatedPhases: ReadonlySet<MountedPhase> = new Set([
    'validate',
    'generate',
    'run',
  ]);
  if (context.hasBlockers && blockerGatedPhases.has(phase)) {
    return lock('Resolve all blockers before proceeding.');
  }

  // 5. Gate check — validate and generate require gates to pass
  const gateGatedPhases: ReadonlySet<MountedPhase> = new Set(['validate', 'generate']);
  if (!context.gatesPassed && gateGatedPhases.has(phase)) {
    return lock('All required gates must pass before this action is available.');
  }

  return base;
}
