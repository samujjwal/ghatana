/**
 * Lifecycle Hub REST API Routes
 *
 * Provides REST endpoints for the Lifecycle Hub feature.
 * Uses Prisma to interact with seeded database.
 *
 * @doc.type route
 * @doc.purpose Lifecycle REST API
 * @doc.layer product
 * @doc.pattern REST Controller
 */

import { FastifyPluginAsync, FastifyRequest } from 'fastify';
import { type PrismaClient, type Prisma, LifecyclePhase } from '@prisma/client';
import { getPrismaClient } from '../database/client.js';
import { requirePermission, requireRole } from '../middleware/rbac.middleware';
import { getAuditService } from '../services/audit/audit.service';
import { AIService } from '../services/ai/ai.service';
import {
  type LifecyclePhaseId,
  type LegacyLifecyclePhaseId,
  LIFECYCLE_PHASES,
  LIFECYCLE_PHASE_ORDER,
  LIFECYCLE_PHASES_BY_ID,
  normalizeLifecyclePhaseId,
  isCanonicalLifecyclePhaseId,
  isValidLifecyclePhaseId,
  getNextPhase,
  getPreviousPhase,
  isValidPhaseTransition,
  getPhaseMetadata,
  getPhaseExitRequirements,
} from '../domain/lifecycle/lifecycle-taxonomy';

interface LifecyclePhaseDefinition {
  id: LifecyclePhaseId;
  name: string;
  description: string;
  stage: number;
  color: string;
  icon: string;
  gates: string[];
  personas: string[];
  keyArtifacts: string[];
}

interface TransitionTimingPrediction {
  estimatedReadyIn: string | null;
  estimatedReadyInHours: number | null;
  predictionConfidence: number | null;
}

type ApprovalMode = 'auto_with_audit' | 'manual_review';
type RiskTolerance = 'low' | 'medium' | 'high';
type ValidationDepth = 'standard' | 'deep';

interface DecisionSupportDefaults {
  approvalMode: ApprovalMode;
  riskTolerance: RiskTolerance;
  validationDepth: ValidationDepth;
  targetEnvironment: 'staging' | 'production';
  ownerRole: string;
}

interface DecisionSupportSuggestion {
  id: string;
  title: string;
  reasoning: string;
  impact: 'low' | 'medium' | 'high';
}

interface ProgressiveDisclosureModel {
  primaryActions: string[];
  secondaryActions: string[];
}

// Use canonical taxonomy from domain module
// LIFECYCLE_PHASES, LIFECYCLE_PHASE_ORDER, LIFECYCLE_PHASES_BY_ID imported from '../domain/lifecycle/lifecycle-taxonomy'

// Stage-specific gate requirements derived from lifecycle phases
const STAGE_GATE_REQUIREMENTS: Record<number, string[]> = LIFECYCLE_PHASES.reduce(
  (accumulator, phase) => ({
    ...accumulator,
    [phase.stage]: phase.keyArtifacts,
  }),
  {} as Record<number, string[]>
);

// ============================================================================
// Utilities
// ============================================================================

/**
 * Validate that projectId is not empty
 */
function validateProjectId(projectId: string | undefined): projectId is string {
  return !!(projectId && projectId.trim().length > 0);
}

/**
 * Log structured audit event for stage transitions
 */
async function logStageTransitionAuditEvent(params: {
  request: FastifyRequest;
  projectId: string;
  fromStage: number;
  toStage: number;
  forced: boolean;
  gateEvaluation?: any;
}): Promise<void> {
  const { request, projectId, fromStage, toStage, forced, gateEvaluation } = params;

  if (!request.user?.userId) {
    return;
  }

  try {
    await getAuditService().log({
      action: forced ? 'STAGE_TRANSITION_FORCED' : 'STAGE_TRANSITIONED',
      actor: request.user.userId,
      actorRole: request.user.role,
      resource: `/lifecycle/projects/${projectId}/stages/transition`,
      severity: forced ? 'warn' : 'info',
      details: forced
        ? `Force transitioned project ${projectId} from stage ${fromStage} to ${toStage} by ${request.user.role}`
        : `Transitioned project ${projectId} from stage ${fromStage} to ${toStage}`,
      ipAddress: request.ip,
      userAgent: request.headers['user-agent'],
      method: request.method,
      status: 200,
      tenantId: request.user.tenantId,
      success: true,
      metadata: {
        projectId,
        fromStage,
        toStage,
        forced,
        gateEvaluation,
        actorRole: request.user.role,
      },
    });
  } catch (error) {
    console.error('Failed to log stage transition audit event:', error);
  }
}

// Use getNextPhase from canonical taxonomy module instead of getNextLifecyclePhase
function getNextLifecyclePhase(
  currentPhase: LifecyclePhaseId
): LifecyclePhaseId | null {
  const currentIndex = LIFECYCLE_PHASE_ORDER.indexOf(currentPhase);
  if (currentIndex === -1 || currentIndex >= LIFECYCLE_PHASE_ORDER.length - 1) {
    return null;
  }

  return LIFECYCLE_PHASE_ORDER[currentIndex + 1] ?? null;
}

function buildNextPhasePreview(
  currentPhase: LifecyclePhaseId,
  approvedArtifacts: string[]
): {
  nextPhase: LifecyclePhaseId | null;
  canAdvance: boolean;
  blockers: string[];
  readiness: number;
  requiredArtifacts: string[];
} {
  const nextPhase = getNextLifecyclePhase(currentPhase);
  const phaseDefinition = LIFECYCLE_PHASES_BY_ID[currentPhase];
  const requiredArtifacts = phaseDefinition.keyArtifacts;
  const missingArtifacts = requiredArtifacts.filter(
    (artifact) => !approvedArtifacts.includes(artifact)
  );
  const blockers = missingArtifacts.map(
    (artifact) => `Missing approved artifact: ${artifact}`
  );

  if (nextPhase === null) {
    blockers.unshift('Project is already at the final lifecycle phase.');
  }

  if (nextPhase !== null && approvedArtifacts.length < 2) {
    blockers.push(
      `At least 2 approved artifacts are required before advancing from ${currentPhase} to ${nextPhase}.`
    );
  }

  const readiness =
    requiredArtifacts.length === 0
      ? 100
      : Math.max(
          0,
          Math.round(
            ((requiredArtifacts.length - missingArtifacts.length) /
              requiredArtifacts.length) *
              100
          )
        );

  return {
    nextPhase,
    canAdvance: blockers.length === 0,
    blockers,
    readiness,
    requiredArtifacts,
  };
}

function formatEstimatedReadyIn(estimatedHours: number): string {
  if (estimatedHours <= 0) {
    return 'Ready now';
  }

  if (estimatedHours < 24) {
    return `~${Math.max(1, Math.round(estimatedHours))} hours`;
  }

  const estimatedDays = Math.max(1, Math.round(estimatedHours / 24));
  return `~${estimatedDays} day${estimatedDays === 1 ? '' : 's'}`;
}

function buildTransitionTimingPrediction(
  currentPhase: LifecyclePhaseId,
  preview: {
    nextPhase: LifecyclePhaseId | null;
    canAdvance: boolean;
    blockers: string[];
    readiness: number;
    requiredArtifacts: string[];
  },
  completedArtifacts: string[]
): TransitionTimingPrediction {
  if (preview.nextPhase === null) {
    return {
      estimatedReadyIn: null,
      estimatedReadyInHours: null,
      predictionConfidence: null,
    };
  }

  if (preview.canAdvance) {
    return {
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.95,
    };
  }

  const baseHoursByPhase: Record<LifecyclePhaseId, number> = {
    INTENT: 16,
    CONTEXT: 24,
    PLAN: 12,
    EXECUTE: 20,
    VERIFY: 8,
    OBSERVE: 12,
    LEARN: 10,
    INSTITUTIONALIZE: 8,
  };

  const requiredCount = preview.requiredArtifacts.length;
  const completedCount = completedArtifacts.length;
  const missingArtifacts = Math.max(requiredCount - completedCount, 0);
  const readinessGap = Math.max(100 - preview.readiness, 0);
  const extraBlockers = Math.max(preview.blockers.length - missingArtifacts, 0);
  const estimatedReadyInHours =
    baseHoursByPhase[currentPhase] +
    missingArtifacts * 8 +
    Math.ceil(readinessGap / 10) * 2 +
    extraBlockers * 4;

  const completionRatio =
    requiredCount === 0 ? 1 : completedCount / requiredCount;
  const predictionConfidence = Math.max(
    0.3,
    Math.min(
      0.95,
      0.45 +
        completionRatio * 0.35 -
        preview.blockers.length * 0.04 +
        (preview.readiness / 100) * 0.15
    )
  );

  return {
    estimatedReadyIn: formatEstimatedReadyIn(estimatedReadyInHours),
    estimatedReadyInHours,
    predictionConfidence: Number(predictionConfidence.toFixed(2)),
  };
}

function buildDecisionSupportDefaults(
  phase: LifecyclePhaseId,
  readiness: number,
  blockerCount: number
): DecisionSupportDefaults {
  const isLateLifecycle = phase === 'VERIFY' || phase === 'OBSERVE' || phase === 'LEARN' || phase === 'INSTITUTIONALIZE';
  const riskTolerance: RiskTolerance = blockerCount > 0
    ? 'low'
    : readiness >= 85
      ? 'high'
      : 'medium';

  return {
    approvalMode: blockerCount === 0 ? 'auto_with_audit' : 'manual_review',
    riskTolerance,
    validationDepth: readiness < 75 || blockerCount > 0 ? 'deep' : 'standard',
    targetEnvironment: isLateLifecycle ? 'production' : 'staging',
    ownerRole: isLateLifecycle ? 'SRE' : 'Tech Lead',
  };
}

function buildDecisionSupportSuggestions(
  phase: LifecyclePhaseId,
  readiness: number,
  blockers: string[]
): DecisionSupportSuggestion[] {
  const suggestions: DecisionSupportSuggestion[] = [];

  if (blockers.length > 0) {
    suggestions.push({
      id: 'resolve-blockers',
      title: 'Resolve lifecycle blockers before transition',
      reasoning: blockers[0],
      impact: 'high',
    });
  }

  if (readiness < 80) {
    suggestions.push({
      id: 'raise-readiness',
      title: 'Raise phase readiness to >= 80',
      reasoning: `Current readiness is ${readiness}. Completing missing artifacts reduces promotion risk.`,
      impact: 'high',
    });
  }

  if (phase === 'PLAN') {
    suggestions.push({
      id: 'promote-test-evidence',
      title: 'Attach passing test evidence to approval packet',
      reasoning: 'Validation outcomes should be traceable for one-click approvals.',
      impact: 'medium',
    });
  }

  if (phase === 'VERIFY' || phase === 'OBSERVE') {
    suggestions.push({
      id: 'confirm-alert-routes',
      title: 'Confirm on-call alert routing before promotion',
      reasoning: 'Operational readiness is a precondition for late lifecycle transitions.',
      impact: 'high',
    });
  }

  if (suggestions.length === 0) {
    suggestions.push({
      id: 'proceed',
      title: 'Proceed with one-click promotion',
      reasoning: 'No blocking risks detected in current lifecycle context.',
      impact: 'medium',
    });
  }

  return suggestions;
}

function buildProgressiveDisclosureModel(
  suggestions: DecisionSupportSuggestion[]
): ProgressiveDisclosureModel {
  const primaryActions = suggestions.slice(0, 2).map((item) => item.id);
  const secondaryActions = suggestions.slice(2).map((item) => item.id);
  return {
    primaryActions,
    secondaryActions,
  };
}

/**
 * Calculate task risk based on priority and status
 * TODO: Replace with real security scan results when available
 */
function calculateTaskRisk(priority: string, status: string): number {
  // Base risk by priority
  let riskScore = 0;
  switch (priority.toUpperCase()) {
    case 'HIGH':
      riskScore = 75;
      break;
    case 'MEDIUM':
      riskScore = 50;
      break;
    case 'LOW':
      riskScore = 25;
      break;
    default:
      riskScore = 40;
  }

  // Adjust risk based on status
  switch (status.toUpperCase()) {
    case 'TODO':
    case 'PENDING':
      riskScore += 10; // Higher risk for incomplete tasks
      break;
    case 'IN_PROGRESS':
      riskScore += 5;
      break;
    case 'DONE':
    case 'COMPLETED':
      riskScore -= 15; // Lower risk for completed tasks
      break;
    case 'BLOCKED':
      riskScore += 20; // Higher risk for blocked tasks
      break;
  }

  // Ensure risk is within 0-100 range
  return Math.max(0, Math.min(100, riskScore));
}

/**
 * Evaluate stage gate requirements for transition
 */
async function evaluateStageGate(
  prisma: PrismaClient,
  projectId: string,
  fromStage: number,
  toStage: number
): Promise<{
  canProceed: boolean;
  readiness: number;
  requiredArtifacts: Array<{ type: string; required: number; current: number }>;
  unmetCriteria: string[];
  gateStatus: 'OPEN' | 'CLOSED';
}> {
  // Define required artifacts by stage
  const stageRequirements: Record<number, string[]> = {
    0: ['Idea Brief', 'Problem Statement', 'Success Criteria'],
    1: ['Architecture Diagram', 'Tech Stack', 'API Design'],
    2: ['Test Plan', 'Test Cases', 'Test Results'],
    3: ['Source Code', 'Documentation', 'Build Artifacts'],
    4: ['Deployment Script', 'Environment Config', 'Smoke Test Results'],
    5: ['Dashboards', 'Alerts', 'SLOs'],
    6: ['Improvement Backlog', 'Performance Metrics', 'Next Iteration Plan'],
    7: ['Standards Update', 'Reusable Playbook', 'Adoption Plan'],
  };

  const requiredArtifacts = stageRequirements[fromStage] || [];
  const artifacts = await prisma.lifecycleArtifact.findMany({
    where: {
      projectId,
      flowStage: fromStage,
    },
  });

  const completedArtifacts = artifacts.filter((a) => a.status === 'approved');
  const readiness = requiredArtifacts.length === 0
    ? 100
    : Math.min(100, Math.round((completedArtifacts.length / requiredArtifacts.length) * 100));

  const artifactRequirements = requiredArtifacts.map((type) => ({
    type,
    required: 1,
    current: artifacts.filter((a) => a.type === type && a.status === 'approved').length,
  }));

  const unmetCriteria = artifactRequirements
    .filter((req) => req.current < req.required)
    .map((req) => `Missing or incomplete: ${req.type}`);

  const canProceed = readiness >= 80 && unmetCriteria.length === 0;
  const gateStatus = canProceed ? 'OPEN' : 'CLOSED';

  return {
    canProceed,
    readiness,
    requiredArtifacts: artifactRequirements,
    unmetCriteria,
    gateStatus,
  };
}

/**
 * Safely extract a numeric field from Prisma Json metadata.
 */
function safeMetadataNumber(metadata: unknown, key: string): number | undefined {
  if (metadata !== null && typeof metadata === 'object' && !Array.isArray(metadata)) {
    const val = (metadata as Record<string, unknown>)[key];
    return typeof val === 'number' ? val : undefined;
  }
  return undefined;
}

function getPhaseFromStage(stage: number): LifecyclePhaseId {
  const phaseMap: Record<number, LifecyclePhaseId> = {
    0: 'INTENT',
    1: 'CONTEXT',
    2: 'PLAN',
    3: 'EXECUTE',
    4: 'VERIFY',
    5: 'OBSERVE',
    6: 'LEARN',
    7: 'INSTITUTIONALIZE',
  };
  
  return phaseMap[stage] || 'INTENT';
}

const lifecycleRoutes: FastifyPluginAsync = async (fastify) => {
  // ========================================================================
  // Phases (P0 - Critical)
  // ========================================================================

  /**
   * GET /lifecycle/phases
   * Returns the list of all lifecycle phases in the Framework of Work
   */
  fastify.get('/phases', async (request, reply) => {
    return { phases: LIFECYCLE_PHASES, total: LIFECYCLE_PHASES.length };
  });

  /**
   * GET /lifecycle/phases/:phase/next?projectId=:projectId
   * Returns the next phase and readiness blockers for the current phase.
   */
  fastify.get('/phases/:phase/next', async (request, reply) => {
    const { phase } = request.params as { phase: string };
    const { projectId } = request.query as { projectId?: string };

    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const normalizedPhase = normalizeLifecyclePhaseId(phase);
    if (!normalizedPhase || !isCanonicalLifecyclePhaseId(normalizedPhase)) {
      return reply.status(400).send({
        error: 'Invalid phase',
        validPhases: LIFECYCLE_PHASE_ORDER,
        received: phase,
      });
    }

    const prisma = getPrismaClient();
    const project = await prisma.project.findUnique({
      where: { id: projectId },
      select: {
        id: true,
      },
    });

    if (!project) {
      return reply.status(404).send({ error: 'Project not found' });
    }

    const approvedArtifacts = await prisma.lifecycleArtifact.findMany({
      where: {
        projectId,
        phase: normalizedPhase,
        status: 'approved',
      },
      select: {
        type: true,
      },
    });

    const completedArtifacts = approvedArtifacts.map(
      (artifact) => artifact.type
    );
    const preview = buildNextPhasePreview(normalizedPhase, completedArtifacts);
    const timingPrediction = buildTransitionTimingPrediction(
      normalizedPhase,
      preview,
      completedArtifacts
    );

    return {
      projectId,
      currentPhase: normalizedPhase,
      nextPhase: preview.nextPhase,
      canAdvance: preview.canAdvance,
      readiness: preview.readiness,
      blockers: preview.blockers,
      requiredArtifacts: preview.requiredArtifacts,
      completedArtifacts,
      estimatedReadyIn: timingPrediction.estimatedReadyIn,
      estimatedReadyInHours: timingPrediction.estimatedReadyInHours,
      predictionConfidence: timingPrediction.predictionConfidence,
      checkedAt: new Date().toISOString(),
    };
  });

  /**
   * GET /lifecycle/projects/:id/current
   * Get the current lifecycle phase for a project
   */
  fastify.get('/projects/:id/current', async (request, reply) => {
    const { id: projectId } = request.params as { id: string };

    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();

    // Get project with lifecycle phase
    const project = await prisma.project.findUnique({
      where: { id: projectId },
      select: {
        id: true,
        name: true,
        lifecyclePhase: true,
        status: true,
      },
    });

    if (!project) {
      return reply.status(404).send({ error: 'Project not found' });
    }

    // Get phase details
    const currentPhase = normalizeLifecyclePhaseId(project.lifecyclePhase || 'INTENT')
      ? normalizeLifecyclePhaseId(project.lifecyclePhase || 'INTENT')
      : 'INTENT';
    const phaseInfo = LIFECYCLE_PHASES_BY_ID[currentPhase];

    // Get readiness for next phase
    const artifacts = await prisma.lifecycleArtifact.findMany({
      where: {
        projectId,
        phase: currentPhase,
        status: 'approved',
      },
    });

    return {
      projectId: project.id,
      projectName: project.name,
      currentPhase: {
        id: currentPhase,
        name: phaseInfo.name,
        stage: phaseInfo.stage,
        color: phaseInfo.color,
      },
      readiness: Math.min(100, artifacts.length * 25),
      canProgress: artifacts.length >= 2,
      completedArtifacts: artifacts.length,
      status: project.status,
    };
  });

  /**
   * POST /lifecycle/projects/:id/transition
   * Transition a project to the next lifecycle phase
   * Enforces gate requirements before allowing transition
   */
  fastify.post(
    '/projects/:id/transition',
    { preHandler: requirePermission('workflow', 'update') },
    async (request, reply) => {
      const { id: projectId } = request.params as { id: string };
      const body = request.body as { targetPhase?: string; userId?: string; reason?: string };

      if (!validateProjectId(projectId)) {
        return reply.status(400).send({
          error: 'Invalid projectId. Must be a non-empty string.',
          received: projectId,
        });
      }

      const prisma = getPrismaClient();
      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      const currentPhase = normalizeLifecyclePhaseId(project.lifecyclePhase ?? 'INTENT') ?? 'INTENT';
      const targetPhase = normalizeLifecyclePhaseId(body.targetPhase ?? '');
      const phaseOrder = [
        'INTENT',
        'CONTEXT',
        'PLAN',
        'EXECUTE',
        'VERIFY',
        'OBSERVE',
        'LEARN',
        'INSTITUTIONALIZE',
      ] as const;
      const currentIndex = phaseOrder.indexOf(currentPhase);
      const targetIndex = targetPhase ? phaseOrder.indexOf(targetPhase) : -1;

      if (targetIndex === -1 || targetPhase === null) {
        return reply.status(400).send({
          error: 'Invalid target phase',
          validPhases: phaseOrder,
        });
      }

      // Enforce gate requirements: check required artifacts for current phase
      const phaseInfo = LIFECYCLE_PHASES_BY_ID[currentPhase];
      const requiredArtifacts = phaseInfo.keyArtifacts;
      const approvedArtifacts = await prisma.lifecycleArtifact.findMany({
        where: {
          projectId,
          phase: currentPhase,
          status: 'approved',
        },
        select: { type: true },
      });

      const completedArtifactTypes = new Set(approvedArtifacts.map((a) => a.type));
      const missingArtifacts = requiredArtifacts.filter((artifact) => !completedArtifactTypes.has(artifact));

      // Block transition if required artifacts are missing
      if (missingArtifacts.length > 0) {
        return reply.status(403).send({
          error: 'Phase gate requirements not met',
          blocked: true,
          blockReason: 'Missing required artifacts for phase transition',
          currentPhase,
          targetPhase,
          missingArtifacts,
          requiredArtifacts,
          message: `Cannot transition from ${currentPhase} to ${targetPhase}. Complete the following required artifacts: ${missingArtifacts.join(', ')}`,
        });
      }

      const updatedProject = await prisma.project.update({
        where: { id: projectId },
          data: { lifecyclePhase: targetPhase as LifecyclePhase },
      });

      await prisma.lifecycleActivityLog.create({
        data: {
          projectId,
          userId: body.userId || 'system',
          action: 'PHASE_TRANSITIONED',
          description: `Transitioned from ${currentPhase} to ${targetPhase}`,
          metadata: {
            fromPhase: currentPhase,
            toPhase: targetPhase,
            fromStage: currentIndex,
            toStage: targetIndex,
            reason: body.reason ?? 'Manual transition',
            gateValidated: true,
            requiredArtifacts,
            completedArtifacts: approvedArtifacts.length,
          } as Prisma.InputJsonValue,
        },
      });

      return {
        success: true,
        projectId: updatedProject.id,
        previousPhase: currentPhase,
        currentPhase: targetPhase,
        transitionedAt: new Date(),
      };
    }
  );

  /**
   * POST /projects/:projectId/automation/plan
   * Build AI-driven workflow automation plan and optionally apply one-click transition approval.
   */
  fastify.post(
    '/projects/:projectId/automation/plan',
    { preHandler: requirePermission('workflow', 'update') },
    async (request, reply) => {
      const { projectId } = request.params as { projectId: string };
      const body = (request.body as {
        phase?: string;
        oneClickApprove?: boolean;
        userId?: string;
        reason?: string;
      }) || {};

      if (!validateProjectId(projectId)) {
        return reply.status(400).send({
          error: 'Invalid projectId. Must be a non-empty string.',
          received: projectId,
        });
      }

      const prisma = getPrismaClient();
      const project = await prisma.project.findUnique({
        where: { id: projectId },
        select: {
          id: true,
          lifecyclePhase: true,
          name: true,
        },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      const currentPhase = normalizeLifecyclePhaseId(body.phase || '')
        ?? normalizeLifecyclePhaseId(project.lifecyclePhase || 'INTENT')
        ?? 'INTENT';

      const approvedArtifacts = await prisma.lifecycleArtifact.findMany({
        where: {
          projectId,
          phase: currentPhase,
          status: 'approved',
        },
        select: {
          type: true,
        },
      });

      const completedArtifacts = approvedArtifacts.map((artifact) => artifact.type);
      const preview = buildNextPhasePreview(currentPhase, completedArtifacts);
      const timingPrediction = buildTransitionTimingPrediction(
        currentPhase,
        preview,
        completedArtifacts
      );

      const defaults = buildDecisionSupportDefaults(
        currentPhase,
        preview.readiness,
        preview.blockers.length
      );
      const suggestions = buildDecisionSupportSuggestions(
        currentPhase,
        preview.readiness,
        preview.blockers
      );
      const progressiveDisclosure = buildProgressiveDisclosureModel(suggestions);

      let execution: {
        transitioned: boolean;
        previousPhase: LifecyclePhaseId;
        currentPhase: LifecyclePhaseId;
        activityLogId?: string;
      } | null = null;

      if (body.oneClickApprove && preview.canAdvance && preview.nextPhase) {
        const updatedProject = await prisma.project.update({
          where: { id: projectId },
          data: { lifecyclePhase: preview.nextPhase },
          select: { lifecyclePhase: true },
        });

        const activity = await prisma.lifecycleActivityLog.create({
          data: {
            projectId,
            userId: body.userId || 'system',
            action: 'AI_ONE_CLICK_TRANSITION_APPROVED',
            description: `AI-approved lifecycle transition from ${currentPhase} to ${preview.nextPhase}`,
            metadata: {
              projectName: project.name,
              fromPhase: currentPhase,
              toPhase: preview.nextPhase,
              readiness: preview.readiness,
              reason: body.reason || 'AI-driven one-click approval',
            },
          },
          select: { id: true },
        });

        execution = {
          transitioned: true,
          previousPhase: currentPhase,
          currentPhase: updatedProject.lifecyclePhase as LifecyclePhaseId,
          activityLogId: activity.id,
        };
      }

      return {
        projectId,
        currentPhase,
        nextPhase: preview.nextPhase,
        canAutoAdvance: preview.canAdvance,
        readiness: preview.readiness,
        blockers: preview.blockers,
        estimatedReadyIn: timingPrediction.estimatedReadyIn,
        estimatedReadyInHours: timingPrediction.estimatedReadyInHours,
        predictionConfidence: timingPrediction.predictionConfidence,
        decisionSupport: {
          defaults,
          suggestions,
          progressiveDisclosure,
        },
        execution,
        generatedAt: new Date().toISOString(),
      };
    }
  );

  /**
   * POST /lifecycle/gates/validate
   * Validate if a project can pass through a gate
   */
  fastify.post('/gates/validate', async (request, reply) => {
    const body = request.body as { projectId?: string; phase?: string; gate?: string };
    const { projectId, phase, gate } = body;

    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();

    // Get artifacts for this phase
    const artifacts = await prisma.lifecycleArtifact.findMany({
      where: {
        projectId,
        phase: (phase ?? 'INTENT') as LifecyclePhase,
        status: 'approved',
      },
    });

    // Define gate requirements
    const gateRequirements: Record<string, unknown> = {
      'problem-defined': {
        requiredArtifacts: ['Problem Statement'],
        minCount: 1,
      },
      'stakeholders-aligned': {
        requiredArtifacts: ['Idea Brief'],
        minCount: 1,
      },
      'architecture-approved': {
        requiredArtifacts: ['Architecture Diagram'],
        minCount: 1,
      },
      'tech-stack-selected': { requiredArtifacts: ['Tech Stack'], minCount: 1 },
      'tests-passed': { requiredArtifacts: ['Test Results'], minCount: 1 },
      'quality-gates-met': {
        requiredArtifacts: ['Test Plan', 'Test Cases'],
        minCount: 2,
      },
      'code-complete': { requiredArtifacts: ['Source Code'], minCount: 1 },
      'code-reviewed': { requiredArtifacts: ['Code Review'], minCount: 1 },
      'deployment-successful': {
        requiredArtifacts: ['Deployment Scripts'],
        minCount: 1,
      },
      'smoke-tests-passed': {
        requiredArtifacts: ['Test Results'],
        minCount: 1,
      },
      'metrics-stable': { requiredArtifacts: ['Dashboards'], minCount: 1 },
      'alerts-configured': { requiredArtifacts: ['Alerts'], minCount: 1 },
    };

    type GateRequirement = { requiredArtifacts: string[]; minCount: number };
    const requirement: GateRequirement = (gate ? (gateRequirements[gate] as GateRequirement) : undefined) ?? {
      requiredArtifacts: [],
      minCount: 0,
    };
    const passed = artifacts.length >= requirement.minCount;

    return {
      gate,
      phase,
      projectId,
      passed,
      readiness: Math.min(
        100,
        Math.round((artifacts.length / requirement.minCount) * 100)
      ),
      requiredArtifacts: requirement.requiredArtifacts,
      completedArtifacts: artifacts.map((a) => a.type),
      missingArtifacts: requirement.requiredArtifacts.filter(
        (req: string) => !artifacts.some((a) => a.type === req)
      ),
      validatedAt: new Date(),
    };
  });

  // ========================================================================
  // Artifacts
  // ========================================================================

  fastify.get('/projects/:projectId/artifacts', async (request, reply) => {
    const { projectId } = request.params as { projectId: string };

    // Validate projectId
    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();

    const artifacts = await prisma.lifecycleArtifact.findMany({
      where: { projectId },
      orderBy: { createdAt: 'desc' },
    });

    return artifacts;
  });

  fastify.get('/artifacts/:artifactId', async (request, reply) => {
    const { artifactId } = request.params as { artifactId: string };
    const prisma = getPrismaClient();

    const artifact = await prisma.lifecycleArtifact.findUnique({
      where: { id: artifactId },
    });

    if (!artifact) {
      return reply.status(404).send({ error: 'Artifact not found' });
    }

    return artifact;
  });

  fastify.post(
    '/artifacts',
    { preHandler: requirePermission('workflow', 'create') },
    async (request, reply) => {
      const prisma = getPrismaClient();
      const userId = request.user?.userId ?? 'system';
      const body = request.body as unknown;

      const artifactBody = body as {
        projectId?: string;
        title?: string;
        type?: string;
        description?: string;
        content?: string;
        status?: string;
        phase?: string;
        flowStage?: number;
        createdBy?: string;
        linkedArtifacts?: string[];
        metadata?: Record<string, unknown>;
      };

      // P2-5: Validate projectId - reject empty or missing projectId
      const projectId = artifactBody.projectId?.trim();
      if (!projectId) {
        return reply.status(400).send({
          error: 'Bad Request',
          message: 'projectId is required and cannot be empty',
        });
      }

      // P2-5: Verify project exists and user has access
      const project = await prisma.project.findUnique({
        where: { id: projectId },
        include: {
          ownerWorkspace: { select: { id: true } },
          workspaceProjects: { select: { workspaceId: true } },
        },
      });

      if (!project) {
        return reply.status(404).send({
          error: 'Not Found',
          message: `Project not found: ${projectId}`,
        });
      }

      // Check if user is member of owning workspace or any workspace that includes this project
      const userWorkspaceIds = await prisma.workspaceMember
        .findMany({
          where: { userId },
          select: { workspaceId: true },
        })
        .then((members) => members.map((m) => m.workspaceId));

      const projectWorkspaceIds = [
        project.ownerWorkspaceId,
        ...project.workspaceProjects.map((wp) => wp.workspaceId),
      ];

      const hasAccess = projectWorkspaceIds.some((id) => userWorkspaceIds.includes(id));
      if (!hasAccess) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to create artifacts in this project',
        });
      }

      // P2-5: Validate title and type are not empty
      const title = artifactBody.title?.trim();
      const type = artifactBody.type?.trim();

      if (!title) {
        return reply.status(400).send({
          error: 'Bad Request',
          message: 'title is required and cannot be empty',
        });
      }

      if (!type) {
        return reply.status(400).send({
          error: 'Bad Request',
          message: 'type is required and cannot be empty',
        });
      }

      const artifact = await prisma.lifecycleArtifact.create({
        data: {
          projectId,
          title,
          type,
          description: artifactBody.description,
          content: artifactBody.content,
          status: artifactBody.status ?? 'draft',
          phase: (artifactBody.phase ?? 'INTENT') as LifecyclePhase,
          flowStage: artifactBody.flowStage ?? 0,
          createdBy: artifactBody.createdBy ?? userId,
          linkedArtifacts: artifactBody.linkedArtifacts ?? [],
          metadata: (artifactBody.metadata ?? {}) as Prisma.InputJsonValue,
        },
      });

      return reply.status(201).send(artifact);
    }
  );

  fastify.patch(
    '/artifacts/:artifactId',
    { preHandler: requirePermission('workflow', 'update') },
    async (request, reply) => {
      const { artifactId } = request.params as { artifactId: string };
      const body = request.body as {
        title?: string;
        description?: string;
        content?: string;
        status?: string;
        phase?: string;
        metadata?: Record<string, unknown>;
      };
      const prisma = getPrismaClient();
      const artifact = await prisma.lifecycleArtifact.update({
        where: { id: artifactId },
        data: {
          title: body.title,
          description: body.description,
          content: body.content,
          status: body.status,
          ...(body.phase !== undefined && { phase: body.phase as LifecyclePhase }),
          ...(body.metadata !== undefined && { metadata: body.metadata as Prisma.InputJsonValue }),
        },
      });

      return artifact;
    }
  );

  fastify.delete(
    '/artifacts/:artifactId',
    { preHandler: requirePermission('workflow', 'delete') },
    async (request, reply) => {
      const { artifactId } = request.params as { artifactId: string };

      const prisma = getPrismaClient();
      await prisma.lifecycleArtifact.delete({
        where: { id: artifactId },
      });

      return reply.status(204).send();
    }
  );

  // ========================================================================
  // Evidence (using ActivityLog as a proxy)
  // ========================================================================

  fastify.get('/projects/:projectId/evidence', async (request, reply) => {
    const { projectId } = request.params as { projectId: string };

    // Validate projectId
    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();

    const logs = await prisma.lifecycleActivityLog.findMany({
      where: { projectId },
      orderBy: { timestamp: 'desc' },
      take: 50,
    });

    // Transform to evidence format
    const evidence = logs.map((log) => {
      // Derive phase from stage or metadata
      const flowStage = safeMetadataNumber(log.metadata, 'stage') ?? safeMetadataNumber(log.metadata, 'flowStage') ?? 1;
      const phase = getPhaseFromStage(flowStage);

      return {
        id: log.id,
        type: 'audit',
        title: log.action,
        description: log.description,
        timestamp: log.timestamp,
        phase,
        flowStage,
        status: 'approved',
        metadata: log.metadata,
      };
    });

    return evidence;
  });

  // ========================================================================
  // Gates
  // ========================================================================

  fastify.get('/projects/:projectId/gates/:stage', async (request, reply) => {
    const { projectId, stage } = request.params as {
      projectId: string;
      stage: string;
    };

    // Validate projectId
    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();

    const parsedStage = Number.isFinite(Number(stage))
      ? parseInt(stage, 10)
      : NaN;
    if (Number.isNaN(parsedStage)) {
      return reply.status(400).send({
        error: 'Invalid stage parameter. Must be a valid integer stage index.',
        received: stage,
      });
    }

    // Get stage-specific requirements
    const requiredArtifactTypes = STAGE_GATE_REQUIREMENTS[parsedStage];
    if (!requiredArtifactTypes) {
      return reply.status(400).send({
        error: 'Invalid stage. No gate requirements defined for this stage.',
        received: parsedStage,
      });
    }

    const artifacts = await prisma.lifecycleArtifact.findMany({
      where: {
        projectId,
        flowStage: parsedStage,
      },
    });

    const completedArtifacts = artifacts.filter((a) => a.status === 'approved');
    const readiness = Math.min(
      100,
      Math.round((completedArtifacts.length / requiredArtifactTypes.length) * 100)
    );

    return {
      stage: parseInt(stage),
      readiness,
      canProceed: readiness >= 80,
      requiredArtifactTypes,
      requiredArtifacts: requiredArtifactTypes.map((type) => ({
        type,
        required: 1,
        current: artifacts.filter(
          (a) => a.type === type && a.status === 'approved'
        ).length,
      })),
      completedArtifacts,
      lastChecked: new Date(),
    };
  });

  fastify.post(
    '/projects/:projectId/stages/transition',
    { preHandler: requirePermission('workflow', 'update') },
    async (request, reply) => {
      const { projectId } = request.params as { projectId: string };
      const body = request.body as {
        fromStage: number;
        toStage: number;
        userId?: string;
        force?: boolean;
      };

      // Validate projectId
      if (!validateProjectId(projectId)) {
        return reply.status(400).send({
          error: 'Invalid projectId. Must be a non-empty string.',
          received: projectId,
        });
      }

      // Validate request body
      if (typeof body.fromStage !== 'number' || typeof body.toStage !== 'number') {
        return reply.status(400).send({
          error: 'Invalid request body. fromStage and toStage must be numbers.',
        });
      }

      if (body.toStage <= body.fromStage) {
        return reply.status(400).send({
          error: 'Invalid transition. toStage must be greater than fromStage.',
        });
      }

      // Guard force transitions to ADMIN/OWNER roles only
      if (body.force === true) {
        const userRole = (request.user as { role?: string })?.role;
        if (userRole !== 'ADMIN' && userRole !== 'OWNER') {
          return reply.status(403).send({
            error: 'Forbidden',
            message: 'Force stage transitions require ADMIN or OWNER role.',
          });
        }
      }

      const prisma = getPrismaClient();

      // Validate that fromStage matches the project's actual current lifecycle stage
      const project = await prisma.project.findUnique({
        where: { id: projectId },
        select: { lifecyclePhase: true },
      });

      if (!project) {
        return reply.status(404).send({
          error: 'Project not found',
          projectId,
        });
      }

      const currentPhase = project.lifecyclePhase || 'INTENT';
      const currentPhaseIndex = LIFECYCLE_PHASE_ORDER.indexOf(currentPhase);
      
      if (currentPhaseIndex !== body.fromStage) {
        return reply.status(400).send({
          error: 'Invalid fromStage. Does not match project current lifecycle stage.',
          fromStage: body.fromStage,
          currentStage: currentPhaseIndex,
          currentPhase,
          message: `Project is currently at stage ${currentPhaseIndex} (${currentPhase}), but request specified fromStage ${body.fromStage}.`,
        });
      }

      // Check gate requirements before allowing transition
      const gateEvaluation = await evaluateStageGate(
        prisma,
        projectId,
        body.fromStage,
        body.toStage
      );

      // Allow force transition if explicitly requested (for admin override)
      if (!gateEvaluation.canProceed && !body.force) {
        return reply.status(422).send({
          error: 'Stage transition blocked by gate requirements',
          fromStage: body.fromStage,
          toStage: body.toStage,
          gateEvaluation,
          message: 'Complete required artifacts and criteria before advancing',
        });
      }

      // Record the transition
      const userRole = (request.user as { role?: string; userId?: string })?.role;
      const userId = (request.user as { userId?: string })?.userId || body.userId || 'system';

      await prisma.lifecycleActivityLog.create({
        data: {
          projectId,
          userId,
          action: body.force ? 'STAGE_TRANSITION_FORCED' : 'STAGE_TRANSITIONED',
          description: body.force
            ? `Force transitioned from stage ${body.fromStage} to ${body.toStage} by ${userRole}`
            : `Transitioned from stage ${body.fromStage} to ${body.toStage}`,
          metadata: {
            ...body,
            gateEvaluation,
            forced: body.force ?? false,
            actorRole: userRole,
          } as Prisma.InputJsonValue,
        },
      });

      // Log structured audit event
      await logStageTransitionAuditEvent({
        request,
        projectId,
        fromStage: body.fromStage,
        toStage: body.toStage,
        forced: body.force ?? false,
        gateEvaluation,
      });

      return { 
        success: true, 
        currentStage: body.toStage,
        gateEvaluation,
        forced: body.force || false,
      };
    }
  );

  // ========================================================================
  // Tasks
  // ========================================================================

  fastify.get('/projects/:projectId/tasks/next', async (request, reply) => {
    const { projectId } = request.params as { projectId: string };
    const { phase } = request.query as { phase?: string };

    // Validate projectId
    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();
    const items = await prisma.lifecycleItem.findMany({
      where: {
        projectId,
        status: 'TODO',
      },
      orderBy: { priority: 'asc' },
      take: 1,
    });

    if (items.length === 0) {
      return {
        id: 'default-task',
        title: 'Define Problem Statement',
        description: 'Create a clear problem statement',
        phase: phase || 'INTENT',
        flowStage: 1,
        persona: 'Product Manager',
        priority: 'high',
        status: 'pending',
      };
    }

    const item = items[0];
    return {
      id: item.id,
      title: item.title,
      description: item.description || '',
      phase: phase || 'INTENT',
      flowStage: 1,
      persona: 'Developer',
      priority: item.priority.toLowerCase(),
      estimatedEffort: item.estimatedEffort || 0,
      status: item.status.toLowerCase(),
    };
  });

  fastify.post(
    '/tasks/:taskId/execute',
    { preHandler: requirePermission('workflow', 'update') },
    async (request, reply) => {
      const { taskId } = request.params as { taskId: string };
      const body = request.body as unknown;

      const enableTaskExecution = process.env.ENABLE_TASK_EXECUTION === 'true';

      if (!enableTaskExecution) {
        // Task execution is not yet implemented - queue for future CI/CD integration
        return reply.status(202).send({
          taskId,
          status: 'queued',
          message: 'Task queued for execution. CI/CD adapter not yet connected.',
          recommendation: 'Configure ENABLE_TASK_EXECUTION=true when CI/CD integration is available.',
        });
      }

      // Use CI/CD adapter for task execution
      const { createCICDAdapter } = await import('../services/cicd/CICDAdapter');
      const cicdAdapter = createCICDAdapter();

      try {
        // Determine task type from body or taskId
        const taskType = (body as { type?: string })?.type || 'build';
        const projectId = (body as { projectId?: string })?.projectId;

        let result;
        switch (taskType) {
          case 'build':
            result = await cicdAdapter.executeBuildTask(taskId, {
              projectId: projectId || 'unknown',
              branch: (body as { branch?: string })?.branch,
              commitSha: (body as { commitSha?: string })?.commitSha,
              environment: (body as { environment?: string })?.environment,
              buildCommand: (body as { buildCommand?: string })?.buildCommand,
              variables: (body as { variables?: Record<string, string> })?.variables,
            });
            break;
          case 'test':
            result = await cicdAdapter.executeTestTask(taskId, {
              projectId: projectId || 'unknown',
              branch: (body as { branch?: string })?.branch,
              commitSha: (body as { commitSha?: string })?.commitSha,
              testCommand: (body as { testCommand?: string })?.testCommand,
              coverageThreshold: (body as { coverageThreshold?: number })?.coverageThreshold,
              variables: (body as { variables?: Record<string, string> })?.variables,
            });
            break;
          case 'deploy':
            result = await cicdAdapter.executeDeployTask(taskId, {
              projectId: projectId || 'unknown',
              branch: (body as { branch?: string })?.branch,
              commitSha: (body as { commitSha?: string })?.commitSha,
              environment: (body as { environment?: string })?.environment || 'production',
              deployCommand: (body as { deployCommand?: string })?.deployCommand,
              variables: (body as { variables?: Record<string, string> })?.variables,
            });
            break;
          default:
            return reply.status(400).send({
              error: 'Invalid task type',
              taskId,
              taskType,
              message: 'Task type must be one of: build, test, deploy',
            });
        }

        return reply.status(202).send({
          taskId,
          status: result.status,
          executionId: result.executionId,
          message: `Task ${taskType} execution started`,
          logs: result.logs,
          metadata: result.metadata,
        });
      } catch (error) {
        console.error(`Error executing task ${taskId}:`, error);
        return reply.status(500).send({
          error: 'Task execution failed',
          taskId,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    }
  );

  // ========================================================================
  // AI Recommendations
  // ========================================================================

  fastify.get(
    '/projects/:projectId/ai/recommendations',
    async (request, reply) => {
      const { projectId } = request.params as { projectId: string };
      const { phase } = request.query as { phase?: string };

      // Validate projectId
      if (!validateProjectId(projectId)) {
        return reply.status(400).send({
          error: 'Invalid projectId. Must be a non-empty string.',
          received: projectId,
        });
      }

      const prisma = getPrismaClient();
      const javaBackendUrl = process.env.JAVA_BACKEND_URL ?? 'http://localhost:7003';
      const currentPhase = phase ?? 'INTENT';

      // Attempt dynamic generation from Java AI backend using current project state
      try {
        const [artifacts, recentLogs] = await Promise.all([
          prisma.lifecycleArtifact.findMany({
            where: { projectId },
            select: { type: true, status: true, phase: true },
            take: 20,
          }),
          prisma.lifecycleActivityLog.findMany({
            where: { projectId },
            orderBy: { timestamp: 'desc' },
            take: 5,
            select: { action: true, description: true },
          }),
        ]);

        const contextPayload = {
          projectId,
          phase: currentPhase,
          artifactSummary: artifacts,
          recentActivity: recentLogs,
        };

        const aiResponse = await fetch(`${javaBackendUrl}/api/v1/yappc/intent/recommendations`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(contextPayload),
          signal: AbortSignal.timeout(5000),
        });

        if (aiResponse.ok) {
          const aiData = await aiResponse.json() as { recommendations?: unknown[] };
          if (Array.isArray(aiData.recommendations) && aiData.recommendations.length > 0) {
            return aiData.recommendations;
          }
        }
      } catch (err) {
        fastify.log.warn({ projectId, phase: currentPhase, err }, 'AI recommendations endpoint unavailable; falling back to stored insights');
      }

      // Fallback: read pre-stored insights from DB
      const insights = await prisma.lifecycleAIInsight.findMany({
        where: { projectId },
        orderBy: { createdAt: 'desc' },
        take: 5,
      });

      const phaseDefinition = LIFECYCLE_PHASES_BY_ID[currentPhase as LifecyclePhaseId];
      const flowStage = phaseDefinition?.stage ?? 0;

      return insights.map((insight) => ({
        id: insight.id,
        type: 'insight',
        title: insight.title,
        description: insight.description ?? '',
        confidence: insight.confidence,
        phase: currentPhase,
        flowStage,
        persona: 'AI Assistant',
        priority: insight.severity.toLowerCase(),
        actionable: insight.status === 'PENDING',
        source: 'stored_insight',
      }));
    }
  );

  // ========================================================================
  // Audit Events
  // ========================================================================

  fastify.get('/projects/:projectId/audit', async (request, reply) => {
    const { projectId } = request.params as { projectId: string };

    // Validate projectId
    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();

    const logs = await prisma.lifecycleActivityLog.findMany({
      where: { projectId },
      orderBy: { timestamp: 'desc' },
      take: 100,
    });

    return logs.map((log) => {
      const flowStage = safeMetadataNumber(log.metadata, 'stage') ?? safeMetadataNumber(log.metadata, 'flowStage') ?? 1;
      const phase = getPhaseFromStage(flowStage);

      return {
        id: log.id,
        type: log.action,
        timestamp: log.timestamp,
        userId: log.userId,
        projectId: log.projectId,
        flowStage,
        phase,
        metadata: log.metadata,
        description: log.description ?? '',
      };
    });
  });

  fastify.post(
    '/projects/:projectId/audit',
    { preHandler: requireRole('ADMIN') },
    async (request, reply) => {
      const { projectId } = request.params as { projectId: string };
      const body = request.body as {
        userId?: string;
        type?: string;
        action?: string;
        description?: string;
        metadata?: Record<string, unknown>;
      };

      // Validate projectId
      if (!validateProjectId(projectId)) {
        return reply.status(400).send({
          error: 'Invalid projectId. Must be a non-empty string.',
          received: projectId,
        });
      }

      const prisma = getPrismaClient();
      const log = await prisma.lifecycleActivityLog.create({
        data: {
          projectId,
          userId: body.userId ?? 'system',
          action: body.type ?? body.action ?? 'UNKNOWN',
          description: body.description,
          metadata: (body.metadata ?? {}) as Prisma.InputJsonValue,
        },
      });

      return reply.status(201).send(log);
    }
  );


  fastify.get('/projects/:projectId/devsecops', async (request, reply) => {
    const { projectId } = request.params as { projectId: string };

    // Validate projectId
    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();

    const items = await prisma.lifecycleItem.findMany({
      where: { projectId },
      orderBy: { createdAt: 'desc' },
    });

    return items.map((item) => ({
      id: item.id,
      type: 'task',
      title: item.title,
      priority: item.priority.toLowerCase(),
      status: item.status.toLowerCase().replace('_', '-'),
      assignee: item.assignedPersona || null,
      dueDate: null,
      // Calculate basic risk based on priority and status
      // TODO: Replace with real security scan results when available
      risk: calculateTaskRisk(item.priority, item.status),
    }));
  });

  // ========================================================================
  // Persona Derivation (AI-backed with fallback)
  // ========================================================================

  fastify.post('/personas/derive', async (request, reply) => {
    const body = request.body as { projectId?: unknown; phase?: unknown; useAI?: unknown; userId?: unknown };
    const projectId = typeof body.projectId === 'string' ? body.projectId : undefined;
    const phase = typeof body.phase === 'string' ? body.phase : 'INTENT';
    const useAI = typeof body.useAI === 'boolean' ? body.useAI : true;
    const userId = typeof body.userId === 'string' ? body.userId : 'system';

    // Validate projectId
    if (!projectId) {
      return reply.status(400).send({
        error: 'projectId is required',
      });
    }

    const prisma = getPrismaClient();
    const phaseDefinition = LIFECYCLE_PHASES.find((p) => p.id === phase);

    // Get project context for AI recommendation
    const project = await prisma.project.findUnique({
      where: { id: projectId },
      select: { name: true, description: true },
    });

    const artifacts = await prisma.lifecycleArtifact.findMany({
      where: { projectId, phase: phase as LifecyclePhase },
      take: 10,
    });

    // Try AI-backed persona recommendation if enabled
    if (useAI) {
      try {
        const aiService = new AIService({
          prisma,
          javaBackendUrl: process.env.JAVA_BACKEND_URL || 'http://localhost:8080',
          enableCaching: true,
          cacheTTL: 300000,
        });
        const artifactSummary = artifacts.map(a => `${a.title}: ${a.status}`).join(', ');
        const personaList = phaseDefinition?.personas?.join(', ') || 'Product Owner, Product Manager';
        
        const prompt = `Given a project in the ${phaseDefinition?.name || phase} phase, recommend the most appropriate persona from this list: ${personaList}.
        
Project: ${project?.name || 'Unknown'}
Description: ${project?.description || 'No description'}
Phase: ${phaseDefinition?.name || phase} - ${phaseDefinition?.description || ''}
Key Artifacts (${artifacts.length}): ${artifactSummary || 'None'}

Respond with JSON format:
{
  "persona": "recommended persona name",
  "reasoning": "brief explanation",
  "confidence": 0.0-1.0
}`;

        const aiResponse = await aiService.sendCopilotMessage({
          sessionId: `persona-${projectId}-${phase}`,
          userId,
          message: prompt,
        });

        // Parse AI response
        const personaMatch = aiResponse.response.match(/"persona"\s*:\s*"([^"]+)"/);
        const reasoningMatch = aiResponse.response.match(/"reasoning"\s*:\s*"([^"]+)"/);
        const confidenceMatch = aiResponse.response.match(/"confidence"\s*:\s*([\d.]+)/);

        if (personaMatch) {
          return {
            persona: personaMatch[1],
            confidence: confidenceMatch ? parseFloat(confidenceMatch[1]) : 0.8,
            reasoning: reasoningMatch ? reasoningMatch[1] : 'AI recommendation based on project context',
            phase,
            source: 'AI',
            artifactCount: artifacts.length,
          };
        }
      } catch (error) {
        // Fallback to phase definition if AI fails
        console.error('AI persona recommendation failed, falling back to phase definition:', error);
      }
    }

    // Fallback: Derive persona from phase definition's canonical persona list
    const primaryPersona = phaseDefinition?.personas[0] ?? 'Product Owner';

    const approvedCount = await prisma.lifecycleArtifact.count({
      where: { projectId, phase: phase as LifecyclePhase, status: 'APPROVED' },
    });

    const baseConfidence = 0.75;
    const completionBonus = artifacts.length > 0 ? (approvedCount / artifacts.length) * 0.2 : 0;
    const confidence = Math.round((baseConfidence + completionBonus) * 100) / 100;

    return {
      persona: primaryPersona,
      confidence,
      phase,
      reasoning: phaseDefinition
        ? `${phaseDefinition.name} phase: ${phaseDefinition.description}`
        : 'Default phase context',
      source: 'phase_definition',
      artifactCount: artifacts.length,
      approvedCount,
    };
  });

  // ========================================================================
  // Health Check
  // ========================================================================

  fastify.get('/health', async (request, reply) => {
    return { status: 'ok', timestamp: new Date(), service: 'lifecycle-api' };
  });
};

export default lifecycleRoutes;
