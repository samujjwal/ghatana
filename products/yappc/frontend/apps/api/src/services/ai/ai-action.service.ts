/**
 * AI Action Service
 *
 * Centralized service for creating, tracking, and managing AI-suggested actions
 * with evidence tracking and override trail.
 *
 * @doc.type service
 * @doc.purpose AI Action management with evidence
 * @doc.layer service
 * @doc.pattern Service Pattern
 */

import {
  type AIAction,
  type AIActionType,
  type Evidence,
  type EvidenceType,
  type ConfidenceScore,
  type RiskLevel,
  type ActionOverride,
  createAIAction,
  calculateConfidenceLevel,
  determineRiskLevel,
  recordOverride,
  validateAIAction,
  DEFAULT_CONFIDENCE_THRESHOLDS,
} from '../../domain/ai/ai-action.model';

// In-memory store for AI actions (use database in production)
const aiActionStore = new Map<string, AIAction>();

/**
 * Create a new AI action with evidence
 */
export async function createAction(
  params: {
    type: AIActionType;
    projectId: string;
    workspaceId?: string;
    title: string;
    description: string;
    reasoning: string;
    evidence: Evidence[];
    confidenceFactors: Array<{
      name: string;
      weight: number;
      score: number;
      explanation: string;
    }>;
    suggestedSteps: string[];
    estimatedEffort: string;
    estimatedImpact: string;
    category: string;
    priority: number;
    tags: string[];
    suggestedBy: string;
    expiresAt?: Date;
  }
): Promise<{ success: boolean; action?: AIAction; errors?: string[] }> {
  // Calculate confidence score
  const overallConfidence =
    params.confidenceFactors.reduce((sum, f) => sum + f.score * f.weight, 0) /
    params.confidenceFactors.reduce((sum, f) => sum + f.weight, 0);

  const confidence: ConfidenceScore = {
    overall: overallConfidence,
    level: calculateConfidenceLevel(overallConfidence),
    factors: params.confidenceFactors,
  };

  // Determine risk level
  const criticalFactors = params.evidence.filter(
    (e) => e.credibilityScore < 0.3 || e.type === 'user_feedback'
  );
  const riskLevel = determineRiskLevel(
    overallConfidence,
    params.evidence.length,
    criticalFactors.map((e) => e.title)
  );

  // Create action (status, createdAt, and overrides are set by createAIAction)
  const action = createAIAction({
    type: params.type,
    projectId: params.projectId,
    workspaceId: params.workspaceId,
    title: params.title,
    description: params.description,
    reasoning: params.reasoning,
    evidence: params.evidence,
    confidence,
    riskLevel,
    riskFactors: criticalFactors.map((e) => e.title),
    suggestedSteps: params.suggestedSteps,
    estimatedEffort: params.estimatedEffort,
    estimatedImpact: params.estimatedImpact,
    expiresAt: params.expiresAt,
    suggestedBy: params.suggestedBy,
    category: params.category,
    priority: params.priority,
    tags: params.tags,
  });

  // Validate
  const validation = validateAIAction(action);
  if (!validation.valid) {
    return { success: false, errors: validation.errors };
  }

  // Store action
  aiActionStore.set(action.id, action);

  return { success: true, action };
}

/**
 * Get an AI action by ID
 */
export async function getAction(actionId: string): Promise<AIAction | null> {
  return aiActionStore.get(actionId) || null;
}

/**
 * Get all actions for a project
 */
export async function getProjectActions(
  projectId: string,
  filters?: {
    status?: AIAction['status'];
    type?: AIActionType;
    minConfidence?: number;
  }
): Promise<AIAction[]> {
  let actions = Array.from(aiActionStore.values()).filter(
    (a) => a.projectId === projectId
  );

  if (filters?.status) {
    actions = actions.filter((a) => a.status === filters.status);
  }

  if (filters?.type) {
    actions = actions.filter((a) => a.type === filters.type);
  }

  if (filters?.minConfidence !== undefined) {
    actions = actions.filter((a) => a.confidence.overall >= filters.minConfidence!);
  }

  // Sort by priority (descending) and createdAt (descending)
  return actions.sort((a, b) => {
    if (b.priority !== a.priority) {
      return b.priority - a.priority;
    }
    return b.createdAt.getTime() - a.createdAt.getTime();
  });
}

/**
 * Accept an AI action
 */
export async function acceptAction(
  actionId: string,
  acceptedBy: string,
  acceptedByRole: string,
  reason?: string
): Promise<{ success: boolean; action?: AIAction; error?: string }> {
  const action = aiActionStore.get(actionId);
  if (!action) {
    return { success: false, error: 'Action not found' };
  }

  if (action.status !== 'pending') {
    return { success: false, error: `Action already ${action.status}` };
  }

  const updatedAction = recordOverride(action, {
    overriddenBy: acceptedBy,
    overriddenByRole: acceptedByRole,
    reason: reason || 'Accepted by user',
    previousStatus: action.status,
    newStatus: 'accepted',
  });

  updatedAction.acceptedBy = acceptedBy;
  aiActionStore.set(actionId, updatedAction);

  return { success: true, action: updatedAction };
}

/**
 * Reject an AI action
 */
export async function rejectAction(
  actionId: string,
  rejectedBy: string,
  rejectedByRole: string,
  reason: string
): Promise<{ success: boolean; action?: AIAction; error?: string }> {
  const action = aiActionStore.get(actionId);
  if (!action) {
    return { success: false, error: 'Action not found' };
  }

  if (action.status !== 'pending') {
    return { success: false, error: `Action already ${action.status}` };
  }

  const updatedAction = recordOverride(action, {
    overriddenBy: rejectedBy,
    overriddenByRole: rejectedByRole,
    reason,
    previousStatus: action.status,
    newStatus: 'rejected',
  });

  updatedAction.rejectedBy = rejectedBy;
  aiActionStore.set(actionId, updatedAction);

  return { success: true, action: updatedAction };
}

/**
 * Mark an AI action as implemented
 */
export async function implementAction(
  actionId: string,
  implementedBy: string
): Promise<{ success: boolean; action?: AIAction; error?: string }> {
  const action = aiActionStore.get(actionId);
  if (!action) {
    return { success: false, error: 'Action not found' };
  }

  if (action.status !== 'accepted') {
    return { success: false, error: 'Action must be accepted before implementation' };
  }

  const updatedAction: AIAction = {
    ...action,
    status: 'implemented',
    implementedAt: new Date(),
  };

  aiActionStore.set(actionId, updatedAction);

  return { success: true, action: updatedAction };
}

/**
 * Get override trail for an action
 */
export async function getOverrideTrail(
  actionId: string
): Promise<{ success: boolean; overrides?: ActionOverride[]; error?: string }> {
  const action = aiActionStore.get(actionId);
  if (!action) {
    return { success: false, error: 'Action not found' };
  }

  return { success: true, overrides: action.overrides };
}

/**
 * Get confidence thresholds
 */
export function getConfidenceThresholds(): typeof DEFAULT_CONFIDENCE_THRESHOLDS {
  // In production, these could be loaded from config/database
  return DEFAULT_CONFIDENCE_THRESHOLDS;
}

/**
 * Collect evidence for a project
 */
export async function collectEvidence(
  projectId: string,
  evidenceTypes: EvidenceType[],
  minCredibilityScore: number = 0.5
): Promise<{ success: boolean; evidence?: Evidence[]; gaps?: string[]; error?: string }> {
  // In production, this would query various sources (documents, tests, etc.)
  // For now, return mock evidence
  const mockEvidence: Evidence[] = [
    {
      id: `ev-${Date.now()}-1`,
      type: 'document',
      title: 'Project Requirements Document',
      source: 'project_files',
      timestamp: new Date(),
      credibilityScore: 0.9,
    },
  ];

  const filtered = mockEvidence.filter((e) =>
    evidenceTypes.includes(e.type) && e.credibilityScore >= minCredibilityScore
  );

  const foundTypes = new Set(filtered.map((e) => e.type));
  const gaps = evidenceTypes.filter((t) => !foundTypes.has(t));

  return { success: true, evidence: filtered, gaps };
}
