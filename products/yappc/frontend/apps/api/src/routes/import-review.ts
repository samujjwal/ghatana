/**
 * Import review routes.
 *
 * @doc.type router
 * @doc.purpose Persist import review and residual island decisions with durable audit evidence
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyInstance, FastifyRequest } from 'fastify';
import { randomUUID } from 'node:crypto';
import { requirePermission } from '../middleware/rbac.middleware';
import { getAuditService } from '../services/audit/audit.service';

type ResidualIslandDecision = 'ACCEPTED' | 'REJECTED';
type ImportReviewDecision = 'applied' | 'skipped' | 'promoted';
type ImportReviewItemKind = 'loss-point' | 'residual-island';

interface ResidualIslandReviewRequest {
  decision?: ResidualIslandDecision;
  notes?: string;
}

interface ImportReviewDecisionRequest {
  reviewItemId?: string;
  kind?: ImportReviewItemKind;
  decision?: ImportReviewDecision;
  label?: string;
  details?: string;
  notes?: string;
}

interface PersistedReviewResponse {
  artifactId: string;
  auditRecordId: string;
  auditRecorded: boolean;
  reviewedAt: string;
}

interface ResidualIslandReviewResponse extends PersistedReviewResponse {
  residualIslandId: string;
  decision: ResidualIslandDecision;
}

interface ImportReviewDecisionResponse extends PersistedReviewResponse {
  reviewItemId: string;
  kind: ImportReviewItemKind;
  decision: ImportReviewDecision;
}

interface AuditLogEntrySummary {
  id?: string;
  timestamp?: Date | string;
}

function getHeaderValue(value: string | string[] | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return value ?? null;
}

function isAuditLogEntrySummary(value: unknown): value is AuditLogEntrySummary {
  return typeof value === 'object' && value !== null;
}

function normalizeAuditTimestamp(timestamp: Date | string | undefined): string {
  if (timestamp instanceof Date) {
    return timestamp.toISOString();
  }
  if (typeof timestamp === 'string' && timestamp.trim().length > 0) {
    return timestamp;
  }
  return new Date().toISOString();
}

function sanitizeSegment(value: string): string {
  const normalized = value.trim().replace(/[^a-zA-Z0-9_-]+/g, '-');
  return normalized.length > 0 ? normalized : randomUUID();
}

async function writeImportReviewAudit(params: {
  request: FastifyRequest;
  action: string;
  artifactId: string;
  resourceSuffix: string;
  details: string;
  status: number;
  metadata: Record<string, unknown>;
}): Promise<{ auditRecordId: string; reviewedAt: string }> {
  const { request, action, artifactId, resourceSuffix, details, status, metadata } = params;
  const authenticatedUser = request.user;
  const actor = authenticatedUser?.userId ?? getHeaderValue(request.headers['x-user-id']) ?? 'system';
  const actorRole = authenticatedUser?.role ?? getHeaderValue(request.headers['x-user-role']) ?? 'SYSTEM';
  const tenantId = authenticatedUser?.tenantId ?? getHeaderValue(request.headers['x-tenant-id']) ?? undefined;
  const workspaceId = authenticatedUser?.workspaceId ?? getHeaderValue(request.headers['x-workspace-id']);
  const projectId = getHeaderValue(request.headers['x-project-id']);
  const correlationId = request.correlationId ?? getHeaderValue(request.headers['x-correlation-id']) ?? undefined;

  const auditEntry = await getAuditService().log({
    action,
    actor,
    actorRole,
    resource: `artifact/${artifactId}/${resourceSuffix}`,
    severity: 'info',
    details,
    ipAddress: request.ip,
    userAgent: getHeaderValue(request.headers['user-agent']) ?? undefined,
    method: request.method,
    status,
    tenantId,
    success: true,
    metadata: {
      workspaceId,
      projectId,
      artifactId,
      correlationId,
      route: request.url,
      ...metadata,
    },
  });

  if (!isAuditLogEntrySummary(auditEntry) || typeof auditEntry.id !== 'string') {
    throw new Error('Import review audit persistence did not return an audit record id');
  }

  return {
    auditRecordId: auditEntry.id,
    reviewedAt: normalizeAuditTimestamp(auditEntry.timestamp),
  };
}

export default async function importReviewRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{
    Params: { artifactId: string; residualIslandId: string };
    Body: ResidualIslandReviewRequest;
  }>(
    '/yappc/artifacts/:artifactId/residual-islands/:residualIslandId/review',
    { preHandler: requirePermission('workflow', 'create') },
    async (request, reply) => {
      const { artifactId, residualIslandId } = request.params;
      const decision = request.body?.decision;
      const notes = request.body?.notes?.trim();

      if (!artifactId.trim() || !residualIslandId.trim() || (decision !== 'ACCEPTED' && decision !== 'REJECTED')) {
        return reply.status(400).send({
          error: 'artifactId, residualIslandId, and decision are required',
          auditRecorded: false,
        });
      }

      try {
        const audit = await writeImportReviewAudit({
          request,
          action: 'YAPPC_RESIDUAL_ISLAND_REVIEWED',
          artifactId,
          resourceSuffix: `residual-island/${residualIslandId}/review`,
          details: `Residual island ${residualIslandId} reviewed as ${decision}`,
          status: 200,
          metadata: {
            residualIslandId,
            decision,
            ...(notes ? { notes } : {}),
          },
        });

        const response: ResidualIslandReviewResponse = {
          artifactId,
          residualIslandId,
          decision,
          auditRecordId: audit.auditRecordId,
          auditRecorded: true,
          reviewedAt: audit.reviewedAt,
        };
        return reply.status(200).send(response);
      } catch (error) {
        request.log.error({ error, artifactId, residualIslandId }, 'Failed to persist residual island review audit record');
        return reply.status(503).send({
          error: 'Residual island review audit persistence failed',
          auditRecorded: false,
        });
      }
    }
  );

  fastify.post<{
    Params: { artifactId: string };
    Body: ImportReviewDecisionRequest;
  }>(
    '/yappc/artifacts/:artifactId/import-review-decisions',
    { preHandler: requirePermission('workflow', 'create') },
    async (request, reply) => {
      const { artifactId } = request.params;
      const reviewItemId = request.body?.reviewItemId?.trim();
      const kind = request.body?.kind;
      const decision = request.body?.decision;
      const label = request.body?.label?.trim();
      const details = request.body?.details?.trim();
      const notes = request.body?.notes?.trim();

      const validKind = kind === 'loss-point' || kind === 'residual-island';
      const validDecision = decision === 'applied' || decision === 'skipped' || decision === 'promoted';
      if (!artifactId.trim() || !reviewItemId || !validKind || !validDecision) {
        return reply.status(400).send({
          error: 'artifactId, reviewItemId, kind, and decision are required',
          auditRecorded: false,
        });
      }

      const decisionId = `import-review-${sanitizeSegment(artifactId)}-${sanitizeSegment(reviewItemId)}`;
      try {
        const audit = await writeImportReviewAudit({
          request,
          action: 'YAPPC_IMPORT_REVIEW_DECISION_RECORDED',
          artifactId,
          resourceSuffix: `import-review/${reviewItemId}`,
          details: `Import review item ${reviewItemId} recorded as ${decision}`,
          status: 201,
          metadata: {
            reviewItemId,
            decisionId,
            kind,
            decision,
            ...(label ? { label } : {}),
            ...(details ? { details } : {}),
            ...(notes ? { notes } : {}),
          },
        });

        const response: ImportReviewDecisionResponse = {
          artifactId,
          reviewItemId,
          kind,
          decision,
          auditRecordId: audit.auditRecordId,
          auditRecorded: true,
          reviewedAt: audit.reviewedAt,
        };
        return reply.status(201).send(response);
      } catch (error) {
        request.log.error({ error, artifactId, reviewItemId }, 'Failed to persist import review decision audit record');
        return reply.status(503).send({
          error: 'Import review decision audit persistence failed',
          auditRecorded: false,
        });
      }
    }
  );
}
