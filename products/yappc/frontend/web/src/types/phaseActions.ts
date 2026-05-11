/**
 * Phase-Specific Action Contracts — YAPPC Web.
 *
 * Defines typed action contracts for each lifecycle phase.
 * Each phase has specific actions that can be performed based on user capabilities.
 *
 * @doc.type module
 * @doc.purpose Phase-specific action contracts
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

import type { PhaseAction } from './phasePacket';

// ============================================================================
// Lifecycle Phase Types
// ============================================================================

/**
 * Canonical lifecycle phases.
 */
export type LifecyclePhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'learn'
  | 'evolve';

// ============================================================================
// Intent Phase Actions
// ============================================================================

/**
 * Intent phase action IDs.
 */
export type IntentActionId =
  | 'intent.capture'
  | 'intent.update'
  | 'intent.delete'
  | 'intent.analyze'
  | 'intent.approve'
  | 'intent.reject';

/**
 * Intent phase actions.
 */
export const INTENT_PHASE_ACTIONS: readonly PhaseAction[] = [
  {
    actionId: 'intent.capture',
    label: 'Capture Intent',
    description: 'Capture and document project intent requirements',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'intent.update',
    label: 'Update Intent',
    description: 'Update existing intent requirements',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'intent.delete',
    label: 'Delete Intent',
    description: 'Delete intent requirements',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_DELETE',
    parameters: {},
  },
  {
    actionId: 'intent.analyze',
    label: 'Analyze Intent',
    description: 'Analyze intent with AI for clarity and completeness',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
  {
    actionId: 'intent.approve',
    label: 'Approve Intent',
    description: 'Approve intent requirements for next phase',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'intent.reject',
    label: 'Reject Intent',
    description: 'Reject intent requirements and request changes',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
] as const;

// ============================================================================
// Shape Phase Actions
// ============================================================================

/**
 * Shape phase action IDs.
 */
export type ShapeActionId =
  | 'shape.create'
  | 'shape.update'
  | 'shape.delete'
  | 'shape.analyze'
  | 'shape.approve'
  | 'shape.reject'
  | 'shape.preview';

/**
 * Shape phase actions.
 */
export const SHAPE_PHASE_ACTIONS: readonly PhaseAction[] = [
  {
    actionId: 'shape.create',
    label: 'Create Shape Model',
    description: 'Create architectural shape model',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'shape.update',
    label: 'Update Shape Model',
    description: 'Update existing architectural shape model',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'shape.delete',
    label: 'Delete Shape Model',
    description: 'Delete architectural shape model',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_DELETE',
    parameters: {},
  },
  {
    actionId: 'shape.analyze',
    label: 'Analyze Shape',
    description: 'Analyze shape model for consistency and best practices',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
  {
    actionId: 'shape.approve',
    label: 'Approve Shape',
    description: 'Approve shape model for next phase',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'shape.reject',
    label: 'Reject Shape',
    description: 'Reject shape model and request changes',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'shape.preview',
    label: 'Preview Shape',
    description: 'Preview architectural shape model visualization',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
] as const;

// ============================================================================
// Validate Phase Actions
// ============================================================================

/**
 * Validate phase action IDs.
 */
export type ValidateActionId =
  | 'validate.run'
  | 'validate.approve'
  | 'validate.reject'
  | 'validate.view-results';

/**
 * Validate phase actions.
 */
export const VALIDATE_PHASE_ACTIONS: readonly PhaseAction[] = [
  {
    actionId: 'validate.run',
    label: 'Run Validation',
    description: 'Run validation checks on project artifacts',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'validate.approve',
    label: 'Approve Validation',
    description: 'Approve validation results for next phase',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'validate.reject',
    label: 'Reject Validation',
    description: 'Reject validation results and request fixes',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'validate.view-results',
    label: 'View Validation Results',
    description: 'View detailed validation results and issues',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
] as const;

// ============================================================================
// Generate Phase Actions
// ============================================================================

/**
 * Generate phase action IDs.
 */
export type GenerateActionId =
  | 'generate.run'
  | 'generate.apply'
  | 'generate.rollback'
  | 'generate.approve'
  | 'generate.reject'
  | 'generate.preview';

/**
 * Generate phase actions.
 */
export const GENERATE_PHASE_ACTIONS: readonly PhaseAction[] = [
  {
    actionId: 'generate.run',
    label: 'Generate Artifacts',
    description: 'Generate code and documentation artifacts',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'generate.apply',
    label: 'Apply Artifacts',
    description: 'Apply generated artifacts to project',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'generate.rollback',
    label: 'Rollback Generation',
    description: 'Rollback to previous artifact version',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_DELETE',
    parameters: {},
  },
  {
    actionId: 'generate.approve',
    label: 'Approve Artifacts',
    description: 'Approve generated artifacts for deployment',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'generate.reject',
    label: 'Reject Artifacts',
    description: 'Reject generated artifacts and regenerate',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'generate.preview',
    label: 'Preview Artifacts',
    description: 'Preview generated artifacts before applying',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
] as const;

// ============================================================================
// Run Phase Actions
// ============================================================================

/**
 * Run phase action IDs.
 */
export type RunActionId =
  | 'run.build'
  | 'run.test'
  | 'run.deploy'
  | 'run.rollback'
  | 'run.view-logs'
  | 'run.view-metrics';

/**
 * Run phase actions.
 */
export const RUN_PHASE_ACTIONS: readonly PhaseAction[] = [
  {
    actionId: 'run.build',
    label: 'Build Project',
    description: 'Build project artifacts',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'run.test',
    label: 'Run Tests',
    description: 'Run project tests',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'run.deploy',
    label: 'Deploy Project',
    description: 'Deploy project to environment',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'run.rollback',
    label: 'Rollback Deployment',
    description: 'Rollback to previous deployment',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_DELETE',
    parameters: {},
  },
  {
    actionId: 'run.view-logs',
    label: 'View Logs',
    description: 'View build and deployment logs',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
  {
    actionId: 'run.view-metrics',
    label: 'View Metrics',
    description: 'View deployment metrics and performance',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
] as const;

// ============================================================================
// Observe Phase Actions
// ============================================================================

/**
 * Observe phase action IDs.
 */
export type ObserveActionId =
  | 'observe.collect'
  | 'observe.analyze'
  | 'observe.view-metrics'
  | 'observe.view-logs'
  | 'observe.set-alerts';

/**
 * Observe phase actions.
 */
export const OBSERVE_PHASE_ACTIONS: readonly PhaseAction[] = [
  {
    actionId: 'observe.collect',
    label: 'Collect Observability Data',
    description: 'Collect metrics, logs, and traces',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'observe.analyze',
    label: 'Analyze Observability',
    description: 'Analyze observability data for insights',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
  {
    actionId: 'observe.view-metrics',
    label: 'View Metrics',
    description: 'View application metrics',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
  {
    actionId: 'observe.view-logs',
    label: 'View Logs',
    description: 'View application logs',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
  {
    actionId: 'observe.set-alerts',
    label: 'Set Alerts',
    description: 'Configure observability alerts',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
] as const;

// ============================================================================
// Learn Phase Actions
// ============================================================================

/**
 * Learn phase action IDs.
 */
export type LearnActionId =
  | 'learn.collect-feedback'
  | 'learn.analyze-feedback'
  | 'learn.generate-insights'
  | 'learn.update-models'
  | 'learn.view-insights';

/**
 * Learn phase actions.
 */
export const LEARN_PHASE_ACTIONS: readonly PhaseAction[] = [
  {
    actionId: 'learn.collect-feedback',
    label: 'Collect Feedback',
    description: 'Collect user feedback on project outcomes',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'learn.analyze-feedback',
    label: 'Analyze Feedback',
    description: 'Analyze collected feedback for patterns',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
  {
    actionId: 'learn.generate-insights',
    label: 'Generate Insights',
    description: 'Generate insights from feedback using AI',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'learn.update-models',
    label: 'Update Models',
    description: 'Update AI models with new learnings',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'learn.view-insights',
    label: 'View Insights',
    description: 'View generated insights and learnings',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
] as const;

// ============================================================================
// Evolve Phase Actions
// ============================================================================

/**
 * Evolve phase action IDs.
 */
export type EvolveActionId =
  | 'evolve.propose'
  | 'evolve.approve'
  | 'evolve.reject'
  | 'evolve.apply'
  | 'evolve.view-proposals';

/**
 * Evolve phase actions.
 */
export const EVOLVE_PHASE_ACTIONS: readonly PhaseAction[] = [
  {
    actionId: 'evolve.propose',
    label: 'Propose Evolution',
    description: 'Propose project evolution changes',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'evolve.approve',
    label: 'Approve Evolution',
    description: 'Approve evolution proposal',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'evolve.reject',
    label: 'Reject Evolution',
    description: 'Reject evolution proposal',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'evolve.apply',
    label: 'Apply Evolution',
    description: 'Apply approved evolution changes',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_UPDATE',
    parameters: {},
  },
  {
    actionId: 'evolve.view-proposals',
    label: 'View Proposals',
    description: 'View evolution proposals',
    enabled: true,
    disabledReason: undefined,
    requiredPermission: 'PROJECT_READ',
    parameters: {},
  },
] as const;

// ============================================================================
// Phase Action Registry
// ============================================================================

/**
 * Registry of phase-specific actions.
 */
export const PHASE_ACTION_REGISTRY: Readonly<
  Record<LifecyclePhase, readonly PhaseAction[]>
> = {
  intent: INTENT_PHASE_ACTIONS,
  shape: SHAPE_PHASE_ACTIONS,
  validate: VALIDATE_PHASE_ACTIONS,
  generate: GENERATE_PHASE_ACTIONS,
  run: RUN_PHASE_ACTIONS,
  observe: OBSERVE_PHASE_ACTIONS,
  learn: LEARN_PHASE_ACTIONS,
  evolve: EVOLVE_PHASE_ACTIONS,
} as const;

/**
 * Gets actions for a specific phase.
 */
export function getPhaseActions(phase: LifecyclePhase): readonly PhaseAction[] {
  return PHASE_ACTION_REGISTRY[phase];
}

/**
 * Gets an action by ID for a specific phase.
 */
export function getPhaseActionById(
  phase: LifecyclePhase,
  actionId: string
): PhaseAction | undefined {
  return PHASE_ACTION_REGISTRY[phase].find((a) => a.actionId === actionId);
}
