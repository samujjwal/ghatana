/**
 * Artifact Lineage Tracking
 *
 * Traces generated artifacts to their origin including:
 * - Requirement
 * - Phase
 * - Canvas node
 * - Source artifact
 * - Confidence
 * - Review actor
 * - Approval time
 *
 * @doc.type domain
 * @doc.purpose Track artifact lineage and provenance
 * @doc.layer domain
 */

import type { LifecyclePhaseId } from '../lifecycle/lifecycle-taxonomy';

/**
 * Artifact lineage information
 */
export interface ArtifactLineage {
  artifactId: string;
  artifactType: string;
  artifactName: string;
  projectId: string;
  generationRunId: string;
  
  // Origin information
  requirement: string;
  phase: LifecyclePhaseId;
  canvasNodeId?: string;
  canvasNodeName?: string;
  sourceArtifactIds: string[];
  sourceArtifacts: ArtifactSource[];
  
  // Generation metadata
  generatedAt: Date;
  generatedBy: string; // AI model or user
  confidence: number; // 0-100
  generationMethod: 'ai_generated' | 'user_created' | 'hybrid';
  
  // Review information
  reviewedAt?: Date;
  reviewedBy?: string;
  reviewDecision?: 'approved' | 'rejected' | 'changes_requested' | 'pending';
  reviewComments?: string;
  
  // Approval information
  approvedAt?: Date;
  approvedBy?: string;
  approvalComments?: string;
  
  // Correlation and audit
  correlationId?: string;
  auditTrail: AuditEvent[];
}

/**
 * Source artifact reference
 */
export interface ArtifactSource {
  artifactId: string;
  artifactType: string;
  artifactName: string;
  contribution: 'direct' | 'indirect' | 'reference';
  confidence: number;
}

/**
 * Audit event for lineage tracking
 */
export interface AuditEvent {
  timestamp: Date;
  actor: string;
  action: string;
  details: Record<string, unknown>;
}

/**
 * Create a new artifact lineage record
 */
export function createArtifactLineage(
  artifactId: string,
  artifactType: string,
  artifactName: string,
  projectId: string,
  generationRunId: string,
  requirement: string,
  phase: LifecyclePhaseId,
  generatedBy: string,
  confidence: number,
  generationMethod: 'ai_generated' | 'user_created' | 'hybrid',
): ArtifactLineage {
  return {
    artifactId,
    artifactType,
    artifactName,
    projectId,
    generationRunId,
    requirement,
    phase,
    canvasNodeId: undefined,
    canvasNodeName: undefined,
    sourceArtifactIds: [],
    sourceArtifacts: [],
    generatedAt: new Date(),
    generatedBy,
    confidence,
    generationMethod,
    correlationId: generateCorrelationId(),
    auditTrail: [
      {
        timestamp: new Date(),
        actor: generatedBy,
        action: 'ARTIFACT_CREATED',
        details: {
          artifactId,
          artifactType,
          artifactName,
          phase,
          confidence,
          generationMethod,
        },
      },
    ],
  };
}

/**
 * Add source artifacts to lineage
 */
export function addSourceArtifacts(
  lineage: ArtifactLineage,
  sources: ArtifactSource[],
): ArtifactLineage {
  return {
    ...lineage,
    sourceArtifactIds: [...lineage.sourceArtifactIds, ...sources.map(s => s.artifactId)],
    sourceArtifacts: [...lineage.sourceArtifacts, ...sources],
    auditTrail: [
      ...lineage.auditTrail,
      {
        timestamp: new Date(),
        actor: 'system',
        action: 'SOURCE_ARTIFACTS_ADDED',
        details: {
          artifactId: lineage.artifactId,
          sourceArtifactIds: sources.map(s => s.artifactId),
        },
      },
    ],
  };
}

/**
 * Set canvas node information
 */
export function setCanvasNode(
  lineage: ArtifactLineage,
  canvasNodeId: string,
  canvasNodeName: string,
): ArtifactLineage {
  return {
    ...lineage,
    canvasNodeId,
    canvasNodeName,
    auditTrail: [
      ...lineage.auditTrail,
      {
        timestamp: new Date(),
        actor: 'system',
        action: 'CANVAS_NODE_ASSOCIATED',
        details: {
          artifactId: lineage.artifactId,
          canvasNodeId,
          canvasNodeName,
        },
      },
    ],
  };
}

/**
 * Record review
 */
export function recordReview(
  lineage: ArtifactLineage,
  reviewedBy: string,
  decision: 'approved' | 'rejected' | 'changes_requested' | 'pending',
  comments?: string,
): ArtifactLineage {
  return {
    ...lineage,
    reviewedAt: new Date(),
    reviewedBy,
    reviewDecision: decision,
    reviewComments: comments,
    auditTrail: [
      ...lineage.auditTrail,
      {
        timestamp: new Date(),
        actor: reviewedBy,
        action: 'ARTIFACT_REVIEWED',
        details: {
          artifactId: lineage.artifactId,
          decision,
          comments,
        },
      },
    ],
  };
}

/**
 * Record approval
 */
export function recordApproval(
  lineage: ArtifactLineage,
  approvedBy: string,
  comments?: string,
): ArtifactLineage {
  return {
    ...lineage,
    approvedAt: new Date(),
    approvedBy,
    approvalComments: comments,
    reviewDecision: 'approved',
    auditTrail: [
      ...lineage.auditTrail,
      {
        timestamp: new Date(),
        actor: approvedBy,
        action: 'ARTIFACT_APPROVED',
        details: {
          artifactId: lineage.artifactId,
          comments,
        },
      },
    ],
  };
}

/**
 * Trace lineage back to intent
 */
export function traceToIntent(lineage: ArtifactLineage): LineageTraceResult {
  const trace: LineageTraceStep[] = [
    {
      step: 'artifact',
      id: lineage.artifactId,
      name: lineage.artifactName,
      type: lineage.artifactType,
      timestamp: lineage.generatedAt,
    },
  ];

  // Add source artifacts
  for (const source of lineage.sourceArtifacts) {
    trace.push({
      step: 'source_artifact',
      id: source.artifactId,
      name: source.artifactName,
      type: source.artifactType,
      contribution: source.contribution,
      confidence: source.confidence,
    });
  }

  // Add canvas node if available
  if (lineage.canvasNodeId && lineage.canvasNodeName) {
    trace.push({
      step: 'canvas_node',
      id: lineage.canvasNodeId,
      name: lineage.canvasNodeName,
      type: 'canvas_node',
    });
  }

  // Add phase information
  trace.push({
    step: 'phase',
    id: lineage.phase,
    name: lineage.phase,
    type: 'lifecycle_phase',
  });

  // Add requirement
  trace.push({
    step: 'intent',
    id: lineage.generationRunId,
    name: lineage.requirement,
    type: 'intent',
  });

  return {
    artifactId: lineage.artifactId,
    trace,
    confidence: lineage.confidence,
    reviewed: !!lineage.reviewedAt,
    approved: !!lineage.approvedAt,
    reviewedBy: lineage.reviewedBy,
    approvedBy: lineage.approvedBy,
    reviewDecision: lineage.reviewDecision,
  };
}

/**
 * Get lineage summary
 */
export function getLineageSummary(lineage: ArtifactLineage): LineageSummary {
  return {
    artifactId: lineage.artifactId,
    artifactName: lineage.artifactName,
    artifactType: lineage.artifactType,
    requirement: lineage.requirement,
    phase: lineage.phase,
    confidence: lineage.confidence,
    generatedAt: lineage.generatedAt,
    generatedBy: lineage.generatedBy,
    sourceCount: lineage.sourceArtifacts.length,
    hasCanvasNode: !!lineage.canvasNodeId,
    reviewed: !!lineage.reviewedAt,
    approved: !!lineage.approvedAt,
    reviewDecision: lineage.reviewDecision,
  };
}

/**
 * Check if artifact is ready for export/apply
 */
export function isReadyForExport(lineage: ArtifactLineage): boolean {
  return (
    lineage.reviewDecision === 'approved' &&
    !!lineage.approvedAt &&
    !!lineage.approvedBy
  );
}

/**
 * Get confidence threshold check
 */
export function meetsConfidenceThreshold(
  lineage: ArtifactLineage,
  threshold: number,
): boolean {
  return lineage.confidence >= threshold;
}

/**
 * Generate correlation ID
 */
function generateCorrelationId(): string {
  return `lineage-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

/**
 * Lineage trace step
 */
export interface LineageTraceStep {
  step: 'artifact' | 'source_artifact' | 'canvas_node' | 'phase' | 'intent';
  id: string;
  name: string;
  type: string;
  timestamp?: Date;
  contribution?: 'direct' | 'indirect' | 'reference';
  confidence?: number;
}

/**
 * Lineage trace result
 */
export interface LineageTraceResult {
  artifactId: string;
  trace: LineageTraceStep[];
  confidence: number;
  reviewed: boolean;
  approved: boolean;
  reviewedBy?: string;
  approvedBy?: string;
  reviewDecision?: 'approved' | 'rejected' | 'changes_requested' | 'pending';
}

/**
 * Lineage summary
 */
export interface LineageSummary {
  artifactId: string;
  artifactName: string;
  artifactType: string;
  requirement: string;
  phase: LifecyclePhaseId;
  confidence: number;
  generatedAt: Date;
  generatedBy: string;
  sourceCount: number;
  hasCanvasNode: boolean;
  reviewed: boolean;
  approved: boolean;
  reviewDecision?: 'approved' | 'rejected' | 'changes_requested' | 'pending';
}
