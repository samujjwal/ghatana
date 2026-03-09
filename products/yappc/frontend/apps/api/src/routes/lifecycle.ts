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

import { FastifyPluginAsync } from 'fastify';
import { getPrismaClient } from '../database/client.js';

// ============================================================================
// Utilities
// ============================================================================

/**
 * Validate that projectId is not empty
 */
function validateProjectId(projectId: string | undefined): projectId is string {
  return !!(projectId && projectId.trim().length > 0);
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
    const phases = [
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
      },
      {
        id: 'SHAPE',
        name: 'Shape',
        description: 'Design the solution architecture',
        stage: 1,
        color: '#8B5CF6',
        icon: '🎨',
        gates: ['architecture-approved', 'tech-stack-selected'],
        personas: ['Architect', 'Tech Lead'],
        keyArtifacts: ['Architecture Diagram', 'Tech Stack', 'API Design'],
      },
      {
        id: 'VALIDATE',
        name: 'Validate',
        description: 'Test and validate the solution',
        stage: 2,
        color: '#10B981',
        icon: '✅',
        gates: ['tests-passed', 'quality-gates-met'],
        personas: ['QA Engineer', 'Test Lead'],
        keyArtifacts: ['Test Plan', 'Test Cases', 'Test Results'],
      },
      {
        id: 'GENERATE',
        name: 'Generate',
        description: 'Build and implement the solution',
        stage: 3,
        color: '#F59E0B',
        icon: '⚙️',
        gates: ['code-complete', 'code-reviewed'],
        personas: ['Developer', 'Engineer'],
        keyArtifacts: ['Source Code', 'Documentation', 'Build Artifacts'],
      },
      {
        id: 'RUN',
        name: 'Run',
        description: 'Deploy and run the solution',
        stage: 4,
        color: '#EF4444',
        icon: '🚀',
        gates: ['deployment-successful', 'smoke-tests-passed'],
        personas: ['DevOps Engineer', 'SRE'],
        keyArtifacts: [
          'Deployment Scripts',
          'Infrastructure Code',
          'Monitoring Setup',
        ],
      },
      {
        id: 'OBSERVE',
        name: 'Observe',
        description: 'Monitor and observe solution performance',
        stage: 5,
        color: '#6366F1',
        icon: '👁️',
        gates: ['metrics-stable', 'alerts-configured'],
        personas: ['SRE', 'Operations'],
        keyArtifacts: ['Dashboards', 'Alerts', 'SLOs'],
      },
      {
        id: 'IMPROVE',
        name: 'Improve',
        description: 'Continuous improvement and optimization',
        stage: 6,
        color: '#EC4899',
        icon: '📈',
        gates: ['improvements-identified', 'next-iteration-planned'],
        personas: ['Product Manager', 'All'],
        keyArtifacts: [
          'Improvement Backlog',
          'Metrics Analysis',
          'Lessons Learned',
        ],
      },
    ];

    return { phases, total: phases.length };
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
    const phaseMap: Record<string, unknown> = {
      INTENT: { stage: 0, name: 'Intent', color: '#3B82F6' },
      SHAPE: { stage: 1, name: 'Shape', color: '#8B5CF6' },
      VALIDATE: { stage: 2, name: 'Validate', color: '#10B981' },
      GENERATE: { stage: 3, name: 'Generate', color: '#F59E0B' },
      RUN: { stage: 4, name: 'Run', color: '#EF4444' },
      OBSERVE: { stage: 5, name: 'Observe', color: '#6366F1' },
      IMPROVE: { stage: 6, name: 'Improve', color: '#EC4899' },
    };

    const currentPhase = project.lifecyclePhase || 'INTENT';
    const phaseInfo = phaseMap[currentPhase] || phaseMap['INTENT'];

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
   */
  fastify.post('/projects/:id/transition', async (request, reply) => {
    const { id: projectId } = request.params as { id: string };
    const body = request.body as unknown;

    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const prisma = getPrismaClient();

    // Get current project state
    const project = await prisma.project.findUnique({
      where: { id: projectId },
    });

    if (!project) {
      return reply.status(404).send({ error: 'Project not found' });
    }

    const currentPhase = project.lifecyclePhase || 'INTENT';
    const targetPhase = body.targetPhase;

    // Validate transition
    const phaseOrder = [
      'INTENT',
      'SHAPE',
      'VALIDATE',
      'GENERATE',
      'RUN',
      'OBSERVE',
      'IMPROVE',
    ];
    const currentIndex = phaseOrder.indexOf(currentPhase);
    const targetIndex = phaseOrder.indexOf(targetPhase);

    if (targetIndex === -1) {
      return reply.status(400).send({
        error: 'Invalid target phase',
        validPhases: phaseOrder,
      });
    }

    // Update project phase
    const updatedProject = await prisma.project.update({
      where: { id: projectId },
      data: { lifecyclePhase: targetPhase },
    });

    // Log the transition
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
          reason: body.reason || 'Manual transition',
        },
      },
    });

    return {
      success: true,
      projectId: updatedProject.id,
      previousPhase: currentPhase,
      currentPhase: targetPhase,
      transitionedAt: new Date(),
    };
  });

  /**
   * POST /lifecycle/gates/validate
   * Validate if a project can pass through a gate
   */
  fastify.post('/gates/validate', async (request, reply) => {
    const body = request.body as unknown;
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
        phase: phase || 'INTENT',
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

    const requirement = gateRequirements[gate] || {
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

  fastify.post('/artifacts', async (request, reply) => {
    const prisma = getPrismaClient();
    const body = request.body as unknown;

    const artifact = await prisma.lifecycleArtifact.create({
      data: {
        projectId: body.projectId,
        title: body.title,
        type: body.type,
        description: body.description,
        content: body.content,
        status: body.status || 'draft',
        phase: body.phase || 'INTENT',
        fowStage: body.fowStage || 1,
        createdBy: body.createdBy || 'system',
        linkedArtifacts: body.linkedArtifacts || [],
      },
    });

    return reply.status(201).send(artifact);
  });

  fastify.patch('/artifacts/:artifactId', async (request, reply) => {
    const { artifactId } = request.params as { artifactId: string };
    const body = request.body as unknown;
    const prisma = getPrismaClient();
    const artifact = await prisma.lifecycleArtifact.update({
      where: { id: artifactId },
      data: {
        title: body.title,
        description: body.description,
        content: body.content,
        status: body.status,
      },
    });

    return artifact;
  });

  fastify.delete('/artifacts/:artifactId', async (request, reply) => {
    const { artifactId } = request.params as { artifactId: string };

    const prisma = getPrismaClient();
    await prisma.lifecycleArtifact.delete({
      where: { id: artifactId },
    });

    return reply.status(204).send();
  });

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
    const evidence = logs.map((log) => ({
      id: log.id,
      type: 'audit',
      title: log.action,
      description: log.description,
      timestamp: log.timestamp,
      phase: 'INTENT',
      fowStage: 1,
      status: 'approved',
      metadata: log.metadata,
    }));

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

    const artifacts = await prisma.lifecycleArtifact.findMany({
      where: {
        projectId,
        fowStage: parsedStage,
      },
    });

    const requiredArtifacts = [
      'Idea Brief',
      'Problem Statement',
      'Requirements',
    ];
    const completedArtifacts = artifacts.filter((a) => a.status === 'approved');
    const readiness = Math.min(
      100,
      Math.round((completedArtifacts.length / requiredArtifacts.length) * 100)
    );

    return {
      stage: parseInt(stage),
      readiness,
      canProceed: readiness >= 80,
      requiredArtifacts: requiredArtifacts.map((type) => ({
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
    async (request, reply) => {
      const { projectId } = request.params as { projectId: string };
      const body = request.body as unknown;

      // Validate projectId
      if (!validateProjectId(projectId)) {
        return reply.status(400).send({
          error: 'Invalid projectId. Must be a non-empty string.',
          received: projectId,
        });
      }

      const prisma = getPrismaClient();
      await prisma.lifecycleActivityLog.create({
        data: {
          projectId,
          userId: body.userId || 'system',
          action: 'STAGE_TRANSITIONED',
          description: `Transitioned from stage ${body.fromStage} to ${body.toStage}`,
          metadata: body,
        },
      });

      return { success: true, currentStage: body.toStage };
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
        fowStage: 1,
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
      fowStage: 1,
      persona: 'Developer',
      priority: item.priority.toLowerCase(),
      estimatedEffort: item.estimatedEffort || 0,
      status: item.status.toLowerCase(),
    };
  });

  fastify.post('/tasks/:taskId/execute', async (request, reply) => {
    const { taskId } = request.params as { taskId: string };
    const body = request.body as unknown;

    // Simulate task execution
    await new Promise((resolve) => setTimeout(resolve, 1000));

    return {
      taskId,
      status: 'completed',
      steps: [
        { id: '1', status: 'completed', output: 'Context analyzed' },
        { id: '2', status: 'completed', output: 'Content generated' },
        { id: '3', status: 'completed', output: 'Validation passed' },
      ],
      artifacts: [
        { id: 'new-art', title: 'Generated Artifact', type: 'Document' },
      ],
      logs: [
        'Task execution started',
        'Processing inputs...',
        'Task completed successfully',
      ],
    };
  });

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
      const insights = await prisma.lifecycleAIInsight.findMany({
        where: { projectId },
        orderBy: { createdAt: 'desc' },
        take: 5,
      });

      return insights.map((insight) => ({
        id: insight.id,
        type: 'insight',
        title: insight.title,
        description: insight.description || '',
        confidence: insight.confidence,
        phase: phase || 'INTENT',
        fowStage: 1,
        persona: 'AI Assistant',
        priority: insight.severity.toLowerCase(),
        actionable: insight.status === 'PENDING',
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

    return logs.map((log) => ({
      id: log.id,
      type: log.action,
      timestamp: log.timestamp,
      userId: log.userId,
      projectId: log.projectId,
      fowStage: 1,
      phase: 'INTENT',
      metadata: log.metadata,
      description: log.description || '',
    }));
  });

  fastify.post('/projects/:projectId/audit', async (request, reply) => {
    const { projectId } = request.params as { projectId: string };
    const body = request.body as unknown;

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
        userId: body.userId || 'system',
        action: body.type || body.action,
        description: body.description,
        metadata: body.metadata || {},
      },
    });

    return reply.status(201).send(log);
  });

  // ========================================================================
  // Persona Derivation (Universal Endpoint)
  // ========================================================================

  fastify.post('/personas/derive', async (request, reply) => {
    const body = request.body as unknown;
    const { projectId, phase, fowStage } = body;

    // Validate projectId
    if (!validateProjectId(projectId)) {
      return reply.status(400).send({
        error: 'Invalid projectId. Must be a non-empty string.',
        received: projectId,
      });
    }

    const personas: Record<number, unknown> = {
      0: {
        persona: 'Product Owner',
        confidence: 0.85,
        reason: 'Intent phase requires strategic thinking',
      },
      1: {
        persona: 'Architect',
        confidence: 0.9,
        reason: 'Shape phase requires technical design',
      },
      2: {
        persona: 'QA Engineer',
        confidence: 0.88,
        reason: 'Validate phase requires testing',
      },
      3: {
        persona: 'Developer',
        confidence: 0.92,
        reason: 'Generate phase requires coding',
      },
      4: {
        persona: 'DevOps Engineer',
        confidence: 0.89,
        reason: 'Run phase requires deployment',
      },
      5: {
        persona: 'SRE',
        confidence: 0.87,
        reason: 'Observe phase requires monitoring',
      },
      6: {
        persona: 'Product Manager',
        confidence: 0.84,
        reason: 'Improve phase requires planning',
      },
    };

    return personas[phase] || personas[0];
  });

  // ========================================================================
  // DevSecOps Items
  // ========================================================================

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
      risk: Math.floor(Math.random() * 100), // Placeholder
    }));
  });

  // ========================================================================
  // Health Check
  // ========================================================================

  fastify.get('/health', async (request, reply) => {
    return { status: 'ok', timestamp: new Date(), service: 'lifecycle-api' };
  });
};

export default lifecycleRoutes;
