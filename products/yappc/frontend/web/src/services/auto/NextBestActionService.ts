/**
 * Next Best Action Service
 *
 * Ranks next best actions by phase, state, blockers, role, permissions,
 * activity, capabilities, and validation.
 *
 * @doc.type service
 * @doc.purpose Next best action ranking
 * @doc.layer product
 */

import { LifecyclePhase } from '../../types/lifecycle';

export interface ActionContext {
  /** Current lifecycle phase */
  phase: LifecyclePhase;
  /** Current state */
  state: Record<string, unknown>;
  /** Blockers */
  blockers: Blocker[];
  /** User role */
  role: string;
  /** User permissions */
  permissions: string[];
  /** Recent activity */
  activity: Activity[];
  /** Available capabilities */
  capabilities: string[];
}

export interface Blocker {
  /** Blocker ID */
  id: string;
  /** Blocker type */
  type: 'validation' | 'permission' | 'dependency' | 'resource';
  /** Blocker severity */
  severity: 'critical' | 'high' | 'medium' | 'low';
  /** Blocker description */
  description: string;
  /** Resolvable flag */
  resolvable: boolean;
}

export interface Activity {
  /** Activity type */
  type: string;
  /** Timestamp */
  timestamp: string;
  /** User ID */
  userId?: string;
}

export interface SuggestedAction {
  /** Action ID */
  id: string;
  /** Action title */
  title: string;
  /** Action description */
  description: string;
  /** Action type */
  type: 'navigation' | 'validation' | 'configuration' | 'generation' | 'review';
  /** Priority score */
  priority: number;
  /** Confidence */
  confidence: number;
  /** Required permissions */
  requiredPermissions: string[];
  /** Required capabilities */
  requiredCapabilities: string[];
  /** Target phase */
  targetPhase?: LifecyclePhase;
  /** Reasoning */
  reasoning: string[];
}

export interface ActionRankingResult {
  /** Ranked actions */
  actions: SuggestedAction[];
  /** Total score */
  totalScore: number;
  /** Top action */
  topAction?: SuggestedAction;
}

/**
 * Calculate next best actions
 */
export function calculateNextBestActions(context: ActionContext): ActionRankingResult {
  const actions: SuggestedAction[] = [];
  const reasoning: string[] = [];

  // Analyze blockers
  const criticalBlockers = context.blockers.filter(b => b.severity === 'critical');
  if (criticalBlockers.length > 0) {
    reasoning.push(`Critical blockers present: ${criticalBlockers.length}`);
    actions.push(...generateBlockerActions(criticalBlockers, context));
  }

  // Analyze phase-specific actions
  const phaseActions = generatePhaseActions(context.phase, context);
  actions.push(...phaseActions);

  // Analyze state-specific actions
  const stateActions = generateStateActions(context.state, context);
  actions.push(...stateActions);

  // Analyze activity-based suggestions
  const activityActions = generateActivityActions(context.activity, context);
  actions.push(...activityActions);

  // Rank actions by priority and confidence
  const rankedActions = rankActions(actions, context);

  const topAction = rankedActions.length > 0 ? rankedActions[0] : undefined;
  const totalScore = rankedActions.reduce((sum, action) => sum + action.priority, 0);

  return {
    actions: rankedActions,
    totalScore,
    topAction,
  };
}

/**
 * Generate actions for blockers
 */
function generateBlockerActions(blockers: Blocker[], context: ActionContext): SuggestedAction[] {
  const actions: SuggestedAction[] = [];

  for (const blocker of blockers) {
    if (blocker.resolvable) {
      actions.push({
        id: `resolve-blocker-${blocker.id}`,
        title: `Resolve ${blocker.type} issue`,
        description: blocker.description,
        type: 'validation',
        priority: blocker.severity === 'critical' ? 100 : 50,
        confidence: 0.9,
        requiredPermissions: [],
        requiredCapabilities: [],
        reasoning: [`Blocker of ${blocker.severity} severity needs resolution`],
      });
    }
  }

  return actions;
}

/**
 * Generate phase-specific actions
 */
function generatePhaseActions(phase: LifecyclePhase, context: ActionContext): SuggestedAction[] {
  const actions: SuggestedAction[] = [];

  switch (phase) {
    case LifecyclePhase.INTENT:
      actions.push({
        id: 'define-requirements',
        title: 'Define requirements',
        description: 'Specify what you want to build',
        type: 'configuration',
        priority: 80,
        confidence: 0.95,
        requiredPermissions: ['edit:project'],
        requiredCapabilities: [],
        targetPhase: LifecyclePhase.SHAPE,
        reasoning: ['Intent phase requires clear requirements'],
      });
      break;

    case LifecyclePhase.SHAPE:
      actions.push({
        id: 'add-components',
        title: 'Add components',
        description: 'Drag components to the canvas',
        type: 'navigation',
        priority: 75,
        confidence: 0.9,
        requiredPermissions: ['edit:canvas'],
        requiredCapabilities: ['canvas:edit'],
        targetPhase: LifecyclePhase.VALIDATE,
        reasoning: ['Shape phase requires component composition'],
      });
      break;

    case LifecyclePhase.VALIDATE:
      actions.push({
        id: 'run-validation',
        title: 'Run validation',
        description: 'Check for errors and warnings',
        type: 'validation',
        priority: 85,
        confidence: 0.95,
        requiredPermissions: ['validate:artifact'],
        requiredCapabilities: [],
        targetPhase: LifecyclePhase.GENERATE,
        reasoning: ['Validation phase requires artifact validation'],
      });
      break;

    case LifecyclePhase.GENERATE:
      actions.push({
        id: 'generate-code',
        title: 'Generate code',
        description: 'Generate reviewable implementation artifacts',
        type: 'generation',
        priority: 90,
        confidence: 0.9,
        requiredPermissions: ['generate:code'],
        requiredCapabilities: ['codegen:execute'],
        targetPhase: LifecyclePhase.RUN,
        reasoning: ['Generate phase requires code generation'],
      });
      break;

    case LifecyclePhase.RUN:
      actions.push({
        id: 'preview-deployment',
        title: 'Preview deployment',
        description: 'Preview your deployed application',
        type: 'navigation',
        priority: 70,
        confidence: 0.85,
        requiredPermissions: ['preview:artifact'],
        requiredCapabilities: ['preview:execute'],
        targetPhase: LifecyclePhase.OBSERVE,
        reasoning: ['Run phase requires deployment preview'],
      });
      break;

    case LifecyclePhase.OBSERVE:
      actions.push({
        id: 'review-metrics',
        title: 'Review metrics',
        description: 'Analyze performance and usage metrics',
        type: 'review',
        priority: 65,
        confidence: 0.8,
        requiredPermissions: ['view:metrics'],
        requiredCapabilities: [],
        targetPhase: LifecyclePhase.LEARN,
        reasoning: ['Observe phase requires metric analysis'],
      });
      break;

    case LifecyclePhase.LEARN:
      actions.push({
        id: 'apply-improvements',
        title: 'Apply improvements',
        description: 'Apply learned improvements to the design',
        type: 'configuration',
        priority: 75,
        confidence: 0.85,
        requiredPermissions: ['edit:artifact'],
        requiredCapabilities: [],
        targetPhase: LifecyclePhase.INSTITUTIONALIZE,
        reasoning: ['Learn phase requires improvement application'],
      });
      break;

    case LifecyclePhase.INSTITUTIONALIZE:
      actions.push({
        id: 'iterate-design',
        title: 'Iterate on design',
        description: 'Start a new iteration based on learnings',
        type: 'navigation',
        priority: 80,
        confidence: 0.9,
        requiredPermissions: ['edit:project'],
        requiredCapabilities: [],
        targetPhase: LifecyclePhase.INTENT,
        reasoning: ['Evolve phase requires design iteration'],
      });
      break;
  }

  return actions;
}

/**
 * Generate state-specific actions
 */
function generateStateActions(state: Record<string, unknown>, context: ActionContext): SuggestedAction[] {
  const actions: SuggestedAction[] = [];

  // Check for empty state
  if (Object.keys(state).length === 0) {
    actions.push({
      id: 'initialize-state',
      title: 'Initialize project',
      description: 'Set up your project configuration',
      type: 'configuration',
      priority: 70,
      confidence: 0.9,
      requiredPermissions: ['edit:project'],
      requiredCapabilities: [],
      reasoning: ['Project state is empty'],
    });
  }

  // Check for validation state
  if (state.validationErrors && Array.isArray(state.validationErrors) && state.validationErrors.length > 0) {
    actions.push({
      id: 'fix-validation-errors',
      title: 'Fix validation errors',
      description: `Resolve ${state.validationErrors.length} validation error(s)`,
      type: 'validation',
      priority: 85,
      confidence: 0.95,
      requiredPermissions: ['edit:artifact'],
      requiredCapabilities: [],
      reasoning: [`Validation errors present: ${state.validationErrors.length}`],
    });
  }

  return actions;
}

/**
 * Generate activity-based actions
 */
function generateActivityActions(activity: Activity[], context: ActionContext): SuggestedAction[] {
  const actions: SuggestedAction[] = [];

  if (activity.length === 0) {
    actions.push({
      id: 'get-started',
      title: 'Get started',
      description: 'Begin by defining your requirements',
      type: 'navigation',
      priority: 60,
      confidence: 0.8,
      requiredPermissions: [],
      requiredCapabilities: [],
      reasoning: ['No recent activity detected'],
    });
  }

  // Check for inactivity
  const lastActivity = activity[activity.length - 1];
  if (lastActivity) {
    const lastActivityTime = new Date(lastActivity.timestamp);
    const now = new Date();
    const hoursSinceActivity = (now.getTime() - lastActivityTime.getTime()) / (1000 * 60 * 60);

    if (hoursSinceActivity > 24) {
      actions.push({
        id: 'resume-work',
        title: 'Resume work',
        description: 'Continue where you left off',
        type: 'navigation',
        priority: 55,
        confidence: 0.7,
        requiredPermissions: [],
        requiredCapabilities: [],
        reasoning: [`Last activity was ${Math.floor(hoursSinceActivity)} hours ago`],
      });
    }
  }

  return actions;
}

/**
 * Rank actions by priority and confidence
 */
function rankActions(actions: SuggestedAction[], context: ActionContext): SuggestedAction[] {
  return actions
    .filter(action => {
      // Filter by permissions
      const hasPermissions = action.requiredPermissions.every(perm =>
        context.permissions.includes(perm)
      );
      
      // Filter by capabilities
      const hasCapabilities = action.requiredCapabilities.every(cap =>
        context.capabilities.includes(cap)
      );

      return hasPermissions && hasCapabilities;
    })
    .map(action => ({
      ...action,
      priority: calculateActionPriority(action, context),
    }))
    .sort((a, b) => b.priority - a.priority);
}

/**
 * Calculate action priority based on context
 */
function calculateActionPriority(action: SuggestedAction, context: ActionContext): number {
  let priority = action.priority;

  // Boost priority for critical blockers
  const criticalBlockers = context.blockers.filter(b => b.severity === 'critical');
  if (criticalBlockers.length > 0 && action.type === 'validation') {
    priority += 30;
  }

  // Reduce priority if user lacks some capabilities
  const missingCapabilities = action.requiredCapabilities.filter(
    cap => !context.capabilities.includes(cap)
  );
  if (missingCapabilities.length > 0) {
    priority -= 20;
  }

  return Math.max(0, Math.min(100, priority));
}

/**
 * Get top recommended action
 */
export function getTopRecommendedAction(context: ActionContext): SuggestedAction | null {
  const result = calculateNextBestActions(context);
  return result.topAction || null;
}

/**
 * Get action by ID
 */
export function getActionById(actionId: string, context: ActionContext): SuggestedAction | null {
  const result = calculateNextBestActions(context);
  return result.actions.find(a => a.id === actionId) || null;
}

export default {
  calculateNextBestActions,
  getTopRecommendedAction,
  getActionById,
};
