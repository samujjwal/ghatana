/**
 * AI Action & Evidence Model
 *
 * Defines the contract for AI-suggested actions with evidence tracking,
 * confidence scoring, and override trail.
 *
 * @doc.type module
 * @doc.purpose AI Action contract with evidence tracking
 * @doc.layer domain
 * @doc.pattern Domain Model
 */

// ============================================================================
// Evidence Types
// ============================================================================

export type EvidenceType =
  | 'document'
  | 'test_result'
  | 'code_analysis'
  | 'metric'
  | 'user_feedback'
  | 'approval'
  | 'review'
  | 'artifact'
  | 'external_source';

export interface Evidence {
  id: string;
  type: EvidenceType;
  title: string;
  description?: string;
  source: string;
  sourceUrl?: string;
  timestamp: Date;
  credibilityScore: number; // 0-1
  metadata?: Record<string, unknown>;
}

// ============================================================================
// Confidence & Risk Levels
// ============================================================================

export type ConfidenceLevel = 'low' | 'medium' | 'high' | 'very_high';

export interface ConfidenceScore {
  overall: number; // 0-1
  level: ConfidenceLevel;
  factors: ConfidenceFactor[];
}

export interface ConfidenceFactor {
  name: string;
  weight: number;
  score: number;
  explanation: string;
}

export type RiskLevel = 'critical' | 'high' | 'medium' | 'low' | 'minimal';

// ============================================================================
// AI Action Types
// ============================================================================

export type AIActionType =
  | 'lifecycle_transition'
  | 'artifact_creation'
  | 'review_request'
  | 'test_recommendation'
  | 'security_alert'
  | 'performance_optimization'
  | 'documentation_update'
  | 'team_assignment'
  | 'dependency_update'
  | 'custom';

export interface AIAction {
  id: string;
  type: AIActionType;
  projectId: string;
  workspaceId?: string;

  // Action details
  title: string;
  description: string;
  reasoning: string;

  // Evidence and confidence
  evidence: Evidence[];
  confidence: ConfidenceScore;

  // Risk assessment
  riskLevel: RiskLevel;
  riskFactors: string[];

  // Suggested implementation
  suggestedSteps: string[];
  estimatedEffort: string; // e.g., "2 hours", "1 day"
  estimatedImpact: string; // e.g., "High", "Medium", "Low"

  // Override trail
  status: 'pending' | 'accepted' | 'rejected' | 'implemented' | 'superseded';
  overrides: ActionOverride[];

  // Timestamps
  createdAt: Date;
  expiresAt?: Date;
  implementedAt?: Date;

  // Actor tracking
  suggestedBy: string; // AI model identifier
  acceptedBy?: string;
  rejectedBy?: string;

  // UI metadata
  category: string;
  priority: number; // 1-100
  tags: string[];
}

export interface ActionOverride {
  id: string;
  actionId: string;
  overriddenBy: string;
  overriddenByRole: string;
  overriddenAt: Date;
  reason: string;
  previousStatus: AIAction['status'];
  newStatus: AIAction['status'];
  signature?: string; // Digital signature/hash for audit
}

// ============================================================================
// Evidence Collection
// ============================================================================

export interface EvidenceCollectionRequest {
  projectId: string;
  phase?: string;
  evidenceTypes: EvidenceType[];
  minCredibilityScore?: number;
  maxResults?: number;
}

export interface EvidenceCollectionResult {
  projectId: string;
  collectedAt: Date;
  evidence: Evidence[];
  gaps: string[]; // Missing evidence types
  recommendations: string[];
}

// ============================================================================
// Confidence Thresholds
// ============================================================================

export interface ConfidenceThresholds {
  autoSuggest: number; // e.g., 0.7 - auto-suggest above this
  requireApproval: number; // e.g., 0.5 - require approval below this
  highConfidence: number; // e.g., 0.85 - high confidence above this
}

export const DEFAULT_CONFIDENCE_THRESHOLDS: ConfidenceThresholds = {
  autoSuggest: 0.7,
  requireApproval: 0.5,
  highConfidence: 0.85,
};

// ============================================================================
// Factory Functions
// ============================================================================

export function createAIAction(
  params: Omit<AIAction, 'id' | 'createdAt' | 'status' | 'overrides'>
): AIAction {
  return {
    id: `ai-action-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    createdAt: new Date(),
    status: 'pending',
    overrides: [],
    ...params,
  };
}

export function calculateConfidenceLevel(score: number): ConfidenceLevel {
  if (score >= 0.85) return 'very_high';
  if (score >= 0.7) return 'high';
  if (score >= 0.5) return 'medium';
  return 'low';
}

export function determineRiskLevel(
  confidence: number,
  evidenceCount: number,
  criticalFactors: string[]
): RiskLevel {
  if (criticalFactors.length > 0) return 'critical';
  if (confidence < 0.5 && evidenceCount < 2) return 'high';
  if (confidence < 0.7) return 'medium';
  if (confidence < 0.85) return 'low';
  return 'minimal';
}

export function recordOverride(
  action: AIAction,
  params: Omit<ActionOverride, 'id' | 'actionId' | 'overriddenAt'>
): AIAction {
  const override: ActionOverride = {
    id: `override-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    actionId: action.id,
    overriddenAt: new Date(),
    ...params,
  };

  return {
    ...action,
    overrides: [...action.overrides, override],
    status: params.newStatus,
  };
}

// ============================================================================
// Validation
// ============================================================================

export function validateAIAction(action: AIAction): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!action.title || action.title.trim().length === 0) {
    errors.push('Title is required');
  }

  if (!action.description || action.description.trim().length === 0) {
    errors.push('Description is required');
  }

  if (!action.projectId || action.projectId.trim().length === 0) {
    errors.push('Project ID is required');
  }

  if (action.confidence.overall < 0 || action.confidence.overall > 1) {
    errors.push('Confidence score must be between 0 and 1');
  }

  if (action.evidence.length === 0) {
    errors.push('At least one evidence item is required');
  }

  if (action.suggestedSteps.length === 0) {
    errors.push('At least one suggested step is required');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
