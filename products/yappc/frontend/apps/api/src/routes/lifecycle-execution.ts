/**
 * Lifecycle Execution API Routes
 * 
 * Provides endpoints for persisting and retrieving lifecycle execution results
 * from the Java backend execution engine.
 *
 * @doc.type route
 * @doc.purpose Lifecycle execution persistence
 * @doc.layer product
 * @doc.pattern REST Controller
 */

import { FastifyPluginAsync } from 'fastify';
import { type PrismaClient } from '@prisma/client';
import { getPrismaClient } from '../database/client.js';
import { requirePermission } from '../middleware/rbac.middleware';

/**
 * Extended PrismaClient type that includes lifecycleExecutionResult.
 * This model is defined in the Prisma schema but the client may not yet be
 * regenerated in all environments. Run `prisma generate` to remove this cast.
 */
type PrismaClientWithExecution = PrismaClient & {
  lifecycleExecutionResult: {
    findUnique(args: { where: { executionId: string } }): Promise<ExecutionRecord | null>;
    create(args: { data: ExecutionCreateInput }): Promise<ExecutionRecord>;
    update(args: { where: { executionId: string }; data: Partial<ExecutionCreateInput> & { updatedAt?: Date } }): Promise<ExecutionRecord>;
    findMany(args: { where: { projectId: string }; orderBy: { startedAt: 'asc' | 'desc' }; take: number; skip: number }): Promise<ExecutionRecord[]>;
    count(args: { where: { projectId: string } }): Promise<number>;
  };
};

interface ExecutionRecord {
  id: string;
  projectId: string;
  executionId: string;
  status: string;
  startedAt: Date;
  completedAt: Date | null;
  [key: string]: unknown;
}

type ExecutionCreateInput = Omit<ExecutionRecord, 'id'> & {
  totalDurationMs?: number;
  executedPhases?: string[];
  phaseDurationsMs?: Record<string, number>;
  intentResult?: Record<string, unknown>;
  shapeResult?: Record<string, unknown>;
  validationResult?: Record<string, unknown>;
  generationResult?: Record<string, unknown>;
  runResult?: Record<string, unknown>;
  observationResult?: Record<string, unknown>;
  learningResult?: Record<string, unknown>;
  evolutionResult?: Record<string, unknown>;
  success?: boolean;
  errorMessage?: string;
  errorPhase?: string;
  aiConfidence?: number;
  tokenUsage?: Record<string, number>;
  fallbacksUsed?: string[];
};

type ExecutionPhaseResult = Record<string, unknown>;

interface LifecycleExecutionRequest {
  projectId: string;
  executionId: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'CANCELLED';
  startedAt: string;
  completedAt?: string;
  totalDurationMs?: number;
  executedPhases: string[];
  phaseDurationsMs: Record<string, number>;
  intentResult?: ExecutionPhaseResult;
  shapeResult?: ExecutionPhaseResult;
  validationResult?: ExecutionPhaseResult;
  generationResult?: ExecutionPhaseResult;
  runResult?: ExecutionPhaseResult;
  observationResult?: ExecutionPhaseResult;
  learningResult?: ExecutionPhaseResult;
  evolutionResult?: ExecutionPhaseResult;
  success: boolean;
  errorMessage?: string;
  errorPhase?: string;
  aiConfidence?: number;
  tokenUsage?: Record<string, number>;
  fallbacksUsed?: string[];
}

const lifecycleExecutionRoutes: FastifyPluginAsync = async (fastify) => {
  // ========================================================================
  // Lifecycle Execution Results
  // ========================================================================

  /**
   * POST /lifecycle-execution/results
   * Persists lifecycle execution results from Java backend
   */
  fastify.post(
    '/results',
    { preHandler: requirePermission('workflow', 'create') },
    async (request, reply) => {
      const body = request.body as LifecycleExecutionRequest;

      // Validate required fields
      if (!body.projectId || !body.executionId || !body.status) {
        return reply.status(400).send({
          error: 'Missing required fields: projectId, executionId, status',
        });
      }

      const prisma = getPrismaClient() as unknown as PrismaClientWithExecution;

      try {
        // Check if execution already exists
        const existingExecution = await prisma.lifecycleExecutionResult.findUnique({
          where: { executionId: body.executionId },
        });

        if (existingExecution) {
          // Update existing execution
          const updatedExecution = await prisma.lifecycleExecutionResult.update({
            where: { executionId: body.executionId },
            data: {
              status: body.status,
              completedAt: body.completedAt ? new Date(body.completedAt) : null,
              totalDurationMs: body.totalDurationMs,
              executedPhases: body.executedPhases,
              phaseDurationsMs: body.phaseDurationsMs,
              intentResult: body.intentResult,
              shapeResult: body.shapeResult,
              validationResult: body.validationResult,
              generationResult: body.generationResult,
              runResult: body.runResult,
              observationResult: body.observationResult,
              learningResult: body.learningResult,
              evolutionResult: body.evolutionResult,
              success: body.success,
              errorMessage: body.errorMessage,
              errorPhase: body.errorPhase,
              aiConfidence: body.aiConfidence,
              tokenUsage: body.tokenUsage,
              fallbacksUsed: body.fallbacksUsed,
              updatedAt: new Date(),
            },
          });

          return { success: true, execution: updatedExecution };
        } else {
          // Create new execution record
          const newExecution = await prisma.lifecycleExecutionResult.create({
            data: {
              projectId: body.projectId,
              executionId: body.executionId,
              status: body.status,
              startedAt: new Date(body.startedAt),
              completedAt: body.completedAt ? new Date(body.completedAt) : null,
              totalDurationMs: body.totalDurationMs,
              executedPhases: body.executedPhases,
              phaseDurationsMs: body.phaseDurationsMs,
              intentResult: body.intentResult,
              shapeResult: body.shapeResult,
              validationResult: body.validationResult,
              generationResult: body.generationResult,
              runResult: body.runResult,
              observationResult: body.observationResult,
              learningResult: body.learningResult,
              evolutionResult: body.evolutionResult,
              success: body.success,
              errorMessage: body.errorMessage,
              errorPhase: body.errorPhase,
              aiConfidence: body.aiConfidence,
              tokenUsage: body.tokenUsage,
              fallbacksUsed: body.fallbacksUsed,
            },
          });

          return { success: true, execution: newExecution };
        }
      } catch (error) {
        fastify.log.error({ err: error }, 'Failed to persist lifecycle execution result');
        return reply.status(500).send({
          error: 'Failed to persist execution result',
          details: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }
  );

  /**
   * GET /lifecycle-execution/projects/:projectId/results
   * Retrieves lifecycle execution results for a project
   */
  fastify.get(
    '/projects/:projectId/results',
    { preHandler: requirePermission('workflow', 'read') },
    async (request, reply) => {
      const { projectId } = request.params as { projectId: string };
      const query = request.query as { limit?: string; offset?: string };
      const limitVal = parseInt(query.limit ?? '10', 10);
      const offsetVal = parseInt(query.offset ?? '0', 10);

      const prisma = getPrismaClient() as unknown as PrismaClientWithExecution;

      try {
        const executions = await prisma.lifecycleExecutionResult.findMany({
          where: { projectId },
          orderBy: { startedAt: 'desc' },
          take: limitVal,
          skip: offsetVal,
        });

        const total = await prisma.lifecycleExecutionResult.count({
          where: { projectId },
        });

        return {
          executions,
          pagination: {
            total,
            limit: limitVal,
            offset: offsetVal,
          },
        };
      } catch (error) {
        fastify.log.error({ err: error }, 'Failed to retrieve lifecycle execution results');
        return reply.status(500).send({
          error: 'Failed to retrieve execution results',
          details: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }
  );

  /**
   * GET /lifecycle-execution/results/:executionId
   * Retrieves a specific lifecycle execution result
   */
  fastify.get(
    '/results/:executionId',
    { preHandler: requirePermission('workflow', 'read') },
    async (request, reply) => {
      const { executionId } = request.params as { executionId: string };

      const prisma = getPrismaClient() as unknown as PrismaClientWithExecution;

      try {
        const execution = await prisma.lifecycleExecutionResult.findUnique({
          where: { executionId },
        });

        if (!execution) {
          return reply.status(404).send({
            error: 'Execution result not found',
            executionId,
          });
        }

        return execution;
      } catch (error) {
        fastify.log.error({ err: error }, 'Failed to retrieve lifecycle execution result');
        return reply.status(500).send({
          error: 'Failed to retrieve execution result',
          details: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }
  );

  /**
   * POST /lifecycle-execution/results/:executionId/phase
   * Updates phase-specific results during execution
   */
  fastify.post(
    '/results/:executionId/phase',
    { preHandler: requirePermission('workflow', 'update') },
    async (request, reply) => {
      const { executionId } = request.params as { executionId: string };
      const body = request.body as {
        phase: string;
        result: ExecutionPhaseResult;
        durationMs: number;
        status: 'SUCCESS' | 'FAILED';
      };

      const prisma = getPrismaClient() as unknown as PrismaClientWithExecution;

      try {
        const execution = await prisma.lifecycleExecutionResult.findUnique({
          where: { executionId },
        });

        if (!execution) {
          return reply.status(404).send({
            error: 'Execution result not found',
            executionId,
          });
        }

        // Update phase-specific result
        const updateData: Record<string, unknown> = {
          phaseDurationsMs: {
            ...(execution.phaseDurationsMs as Record<string, number>),
            [body.phase]: body.durationMs,
          },
          updatedAt: new Date(),
        };

        // Update the specific phase result field
        switch (body.phase.toLowerCase()) {
          case 'intent':
            updateData.intentResult = body.result;
            break;
          case 'shape':
            updateData.shapeResult = body.result;
            break;
          case 'validate':
            updateData.validationResult = body.result;
            break;
          case 'generate':
            updateData.generationResult = body.result;
            break;
          case 'run':
            updateData.runResult = body.result;
            break;
          case 'observe':
            updateData.observationResult = body.result;
            break;
          case 'learn':
            updateData.learningResult = body.result;
            break;
          case 'evolve':
            updateData.evolutionResult = body.result;
            break;
        }

        const updatedExecution = await prisma.lifecycleExecutionResult.update({
          where: { executionId },
          data: updateData,
        });

        return { success: true, execution: updatedExecution };
      } catch (error) {
        fastify.log.error({ err: error }, 'Failed to update phase result');
        return reply.status(500).send({
          error: 'Failed to update phase result',
          details: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }
  );
};

export default lifecycleExecutionRoutes;
