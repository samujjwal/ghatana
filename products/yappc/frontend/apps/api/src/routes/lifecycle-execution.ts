/**
 * Lifecycle Execution API Routes
 *
 * Provides endpoints for persisting and retrieving lifecycle execution results
 * from the Java backend execution engine.
 *
 * Security features:
 * - Signed service token authentication
 * - Idempotency key support
 * - Project binding verification
 *
 * @doc.type route
 * @doc.purpose Lifecycle execution persistence
 * @doc.layer product
 * @doc.pattern REST Controller
 * @doc.security Service token auth, idempotency keys
 */

import { FastifyPluginAsync, type FastifyRequest } from 'fastify';
import { type PrismaClient } from '@prisma/client';
import { getPrismaClient } from '../database/client.js';
import { requirePermission } from '../middleware/rbac.middleware';
import { createHash, timingSafeEqual } from 'crypto';
import { getAuditService } from '../services/audit/audit.service';

// In-memory idempotency store (use Redis in production)
const idempotencyStore = new Map<string, { processedAt: Date; response: unknown }>();

// Service token secret (from environment)
function getServiceTokenSecret(): string {
  return process.env.JAVA_BACKEND_API_KEY || '';
}

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

type LifecycleExecutionAuditOutcome = 'SUCCESS' | 'REJECTED' | 'FAILED';

interface LifecycleExecutionAuditContext {
  action: 'YAPPC_LIFECYCLE_EXECUTION_RESULT' | 'YAPPC_LIFECYCLE_EXECUTION_PHASE_RESULT';
  outcome: LifecycleExecutionAuditOutcome;
  status: number;
  projectId?: string;
  executionId?: string;
  phase?: string;
  executionStatus?: string;
  durationMs?: number;
  reason?: string;
  operation: 'create' | 'update' | 'phase-update' | 'reject' | 'failure';
  metadata?: Record<string, unknown>;
}

interface LifecycleExecutionAuditResult {
  auditRecorded: boolean;
}

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

/**
 * Verify service token from request headers
 */
function verifyServiceToken(request: { headers: Record<string, string | string[] | undefined> }): { valid: boolean; error?: string } {
  const authHeader = request.headers.authorization;
  const secret = getServiceTokenSecret();

  if (!secret) {
    return { valid: false, error: 'Service authentication not configured' };
  }

  if (!authHeader || typeof authHeader !== 'string') {
    return { valid: false, error: 'Missing authorization header' };
  }

  // Expect: "Bearer <service-token>"
  const parts = authHeader.split(' ');
  if (parts.length !== 2 || parts[0] !== 'Bearer') {
    return { valid: false, error: 'Invalid authorization format' };
  }

  const token = parts[1];

  // Simple HMAC verification (use proper JWT or signed tokens in production)
  const expectedHash = createHash('sha256').update(secret).digest('hex');
  const providedHash = createHash('sha256').update(token).digest('hex');

  try {
    const expected = Buffer.from(expectedHash);
    const provided = Buffer.from(providedHash);

    if (expected.length !== provided.length) {
      return { valid: false, error: 'Invalid token' };
    }

    if (!timingSafeEqual(expected, provided)) {
      return { valid: false, error: 'Invalid service token' };
    }

    return { valid: true };
  } catch {
    return { valid: false, error: 'Token verification failed' };
  }
}

/**
 * Get or check idempotency key
 */
function checkIdempotency(idempotencyKey: string): { processed: boolean; response?: unknown } {
  const stored = idempotencyStore.get(idempotencyKey);
  if (stored) {
    // Clean up old entries (older than 24 hours)
    const now = new Date();
    const age = now.getTime() - stored.processedAt.getTime();
    if (age > 24 * 60 * 60 * 1000) {
      idempotencyStore.delete(idempotencyKey);
      return { processed: false };
    }
    return { processed: true, response: stored.response };
  }
  return { processed: false };
}

/**
 * Store idempotency key response
 */
function storeIdempotencyResponse(idempotencyKey: string, response: unknown): void {
  idempotencyStore.set(idempotencyKey, {
    processedAt: new Date(),
    response,
  });
}

function getHeaderValue(value: string | string[] | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return value ?? null;
}

async function logLifecycleExecutionAudit(
  request: FastifyRequest,
  context: LifecycleExecutionAuditContext
): Promise<LifecycleExecutionAuditResult> {
  const authenticatedUser = request.user;
  const actor = authenticatedUser?.userId ?? getHeaderValue(request.headers['x-user-id']) ?? 'java-backend';
  const actorRole = authenticatedUser?.role ?? getHeaderValue(request.headers['x-user-role']) ?? 'SERVICE';
  const tenantId = authenticatedUser?.tenantId ?? getHeaderValue(request.headers['x-tenant-id']) ?? undefined;
  const workspaceId = authenticatedUser?.workspaceId ?? getHeaderValue(request.headers['x-workspace-id']);
  const correlationId = request.correlationId ?? getHeaderValue(request.headers['x-correlation-id']) ?? undefined;

  try {
    await getAuditService().log({
      action: context.action,
      actor,
      actorRole,
      resource: context.executionId
        ? `lifecycle-execution/${context.executionId}`
        : 'lifecycle-execution',
      severity: context.outcome === 'SUCCESS' ? 'info' : 'warn',
      details: `Lifecycle execution ${context.operation} ${context.outcome.toLowerCase()}`,
      ipAddress: request.ip,
      userAgent: getHeaderValue(request.headers['user-agent']) ?? undefined,
      method: request.method,
      status: context.status,
      tenantId,
      success: context.outcome === 'SUCCESS',
      error: context.reason,
      metadata: {
        projectId: context.projectId,
        workspaceId,
        executionId: context.executionId,
        phase: context.phase,
        executionStatus: context.executionStatus,
        durationMs: context.durationMs,
        outcome: context.outcome,
        operation: context.operation,
        correlationId,
        route: request.url,
        ...context.metadata,
      },
    });
    return { auditRecorded: true };
  } catch (error) {
    request.log.warn(
      {
        error,
        projectId: context.projectId,
        executionId: context.executionId,
        phase: context.phase,
        outcome: context.outcome,
      },
      'Failed to write lifecycle execution audit event'
    );
    return { auditRecorded: false };
  }
}

/**
 * Verify project binding - ensure execution belongs to claimed project
 */
async function verifyProjectBinding(
  prisma: PrismaClientWithExecution,
  executionId: string,
  claimedProjectId: string
): Promise<{ valid: boolean; error?: string }> {
  // Check if execution already exists with different project
  const existing = await prisma.lifecycleExecutionResult.findUnique({
    where: { executionId },
  });

  if (existing && existing.projectId !== claimedProjectId) {
    return {
      valid: false,
      error: `Project binding mismatch: execution ${executionId} belongs to project ${existing.projectId}, not ${claimedProjectId}`,
    };
  }

  return { valid: true };
}

const lifecycleExecutionRoutes: FastifyPluginAsync = async (fastify) => {
  // ========================================================================
  // Lifecycle Execution Results
  // ========================================================================

  /**
   * POST /lifecycle-execution/results
   * Persists lifecycle execution results from Java backend
   *
   * Security:
   * - Requires valid service token
   * - Supports idempotency key for retries
   * - Verifies project binding
   */
  fastify.post(
    '/results',
    { preHandler: requirePermission('workflow', 'create') },
    async (request, reply) => {
      // Verify service token
      const tokenResult = verifyServiceToken(request);
      if (!tokenResult.valid) {
        return reply.status(401).send({
          error: 'Unauthorized',
          message: tokenResult.error,
        });
      }

      const body = request.body as LifecycleExecutionRequest;
      const idempotencyKey = (request.headers['idempotency-key'] as string) || body.executionId;

      // Check idempotency
      const idempotencyCheck = checkIdempotency(idempotencyKey);
      if (idempotencyCheck.processed) {
        return reply.status(200).send(idempotencyCheck.response);
      }

      // Validate required fields
      if (!body.projectId || !body.executionId || !body.status) {
        const audit = await logLifecycleExecutionAudit(request, {
          action: 'YAPPC_LIFECYCLE_EXECUTION_RESULT',
          outcome: 'REJECTED',
          status: 400,
          projectId: body.projectId,
          executionId: body.executionId,
          executionStatus: body.status,
          operation: 'reject',
          reason: 'missing_required_fields',
        });
        return reply.status(400).send({
          error: 'Missing required fields: projectId, executionId, status',
          auditRecorded: audit.auditRecorded,
        });
      }

      // Validate projectId is not empty
      if (!body.projectId.trim()) {
        const audit = await logLifecycleExecutionAudit(request, {
          action: 'YAPPC_LIFECYCLE_EXECUTION_RESULT',
          outcome: 'REJECTED',
          status: 400,
          projectId: body.projectId,
          executionId: body.executionId,
          executionStatus: body.status,
          operation: 'reject',
          reason: 'empty_project_id',
        });
        return reply.status(400).send({
          error: 'Invalid projectId: cannot be empty',
          auditRecorded: audit.auditRecorded,
        });
      }

      const prisma = getPrismaClient() as unknown as PrismaClientWithExecution;

      // Verify project binding
      const bindingCheck = await verifyProjectBinding(prisma, body.executionId, body.projectId);
      if (!bindingCheck.valid) {
        const audit = await logLifecycleExecutionAudit(request, {
          action: 'YAPPC_LIFECYCLE_EXECUTION_RESULT',
          outcome: 'REJECTED',
          status: 403,
          projectId: body.projectId,
          executionId: body.executionId,
          executionStatus: body.status,
          operation: 'reject',
          reason: bindingCheck.error,
        });
        return reply.status(403).send({
          error: 'Forbidden',
          message: bindingCheck.error,
          auditRecorded: audit.auditRecorded,
        });
      }

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

          const audit = await logLifecycleExecutionAudit(request, {
            action: 'YAPPC_LIFECYCLE_EXECUTION_RESULT',
            outcome: 'SUCCESS',
            status: 200,
            projectId: body.projectId,
            executionId: body.executionId,
            executionStatus: body.status,
            durationMs: body.totalDurationMs,
            operation: 'update',
            metadata: {
              executedPhases: body.executedPhases,
              success: body.success,
              errorPhase: body.errorPhase,
              fallbacksUsed: body.fallbacksUsed,
            },
          });
          const response = { success: true, execution: updatedExecution, auditRecorded: audit.auditRecorded };
          storeIdempotencyResponse(idempotencyKey, response);
          return response;
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

          const audit = await logLifecycleExecutionAudit(request, {
            action: 'YAPPC_LIFECYCLE_EXECUTION_RESULT',
            outcome: 'SUCCESS',
            status: 201,
            projectId: body.projectId,
            executionId: body.executionId,
            executionStatus: body.status,
            durationMs: body.totalDurationMs,
            operation: 'create',
            metadata: {
              executedPhases: body.executedPhases,
              success: body.success,
              errorPhase: body.errorPhase,
              fallbacksUsed: body.fallbacksUsed,
            },
          });
          const response = { success: true, execution: newExecution, auditRecorded: audit.auditRecorded };
          storeIdempotencyResponse(idempotencyKey, response);
          return reply.status(201).send(response);
        }
      } catch (error) {
        const audit = await logLifecycleExecutionAudit(request, {
          action: 'YAPPC_LIFECYCLE_EXECUTION_RESULT',
          outcome: 'FAILED',
          status: 500,
          projectId: body.projectId,
          executionId: body.executionId,
          executionStatus: body.status,
          operation: 'failure',
          reason: error instanceof Error ? error.message : 'Unknown error',
        });
        fastify.log.error({ err: error }, 'Failed to persist lifecycle execution result');
        return reply.status(500).send({
          error: 'Failed to persist execution result',
          details: error instanceof Error ? error.message : 'Unknown error',
          auditRecorded: audit.auditRecorded,
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

        const audit = await logLifecycleExecutionAudit(request, {
          action: 'YAPPC_LIFECYCLE_EXECUTION_PHASE_RESULT',
          outcome: body.status === 'SUCCESS' ? 'SUCCESS' : 'FAILED',
          status: 200,
          projectId: execution.projectId,
          executionId,
          phase: body.phase,
          executionStatus: body.status,
          durationMs: body.durationMs,
          operation: 'phase-update',
        });
        return { success: true, execution: updatedExecution, auditRecorded: audit.auditRecorded };
      } catch (error) {
        const audit = await logLifecycleExecutionAudit(request, {
          action: 'YAPPC_LIFECYCLE_EXECUTION_PHASE_RESULT',
          outcome: 'FAILED',
          status: 500,
          executionId,
          phase: body.phase,
          executionStatus: body.status,
          durationMs: body.durationMs,
          operation: 'failure',
          reason: error instanceof Error ? error.message : 'Unknown error',
        });
        fastify.log.error({ err: error }, 'Failed to update phase result');
        return reply.status(500).send({
          error: 'Failed to update phase result',
          details: error instanceof Error ? error.message : 'Unknown error',
          auditRecorded: audit.auditRecorded,
        });
      }
    }
  );
};

export default lifecycleExecutionRoutes;
