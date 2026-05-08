/**
 * Registry candidate routes.
 *
 * @doc.type router
 * @doc.purpose Promote residual islands to reviewed registry candidates with durable audit evidence
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyInstance, FastifyRequest } from 'fastify';
import { randomUUID } from 'node:crypto';
import { requirePermission } from '../middleware/rbac.middleware';
import { getAuditService } from '../services/audit/audit.service';

type RegistryCandidateSource = 'decompiled-import';
type RegistryCandidateStatus = 'NEEDS_REVIEW';

interface RegistryCandidatePromotionRequest {
  proposedContractName?: string;
  source?: RegistryCandidateSource;
  notes?: string;
}

interface RegistryCandidatePromotionResponse {
  candidateId: string;
  artifactId: string;
  residualIslandId: string;
  proposedContractName: string;
  status: RegistryCandidateStatus;
  auditRecordId: string;
  auditRecorded: boolean;
  createdAt: string;
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

function sanitizeIdentifierSegment(value: string): string {
  const normalized = value.trim().replace(/[^a-zA-Z0-9_-]+/g, '-');
  return normalized.length > 0 ? normalized : randomUUID();
}

function buildCandidateId(artifactId: string, residualIslandId: string): string {
  return `registry-candidate-${sanitizeIdentifierSegment(artifactId)}-${sanitizeIdentifierSegment(residualIslandId)}`;
}

function isAuditLogEntrySummary(value: unknown): value is AuditLogEntrySummary {
  return typeof value === 'object' && value !== null;
}

async function writeRegistryCandidateAudit(params: {
  request: FastifyRequest;
  artifactId: string;
  residualIslandId: string;
  proposedContractName: string;
  source: RegistryCandidateSource;
  notes?: string;
  candidateId: string;
}): Promise<{ auditRecordId: string; createdAt: string }> {
  const { request, artifactId, residualIslandId, proposedContractName, source, notes, candidateId } = params;
  const authenticatedUser = request.user;
  const actor = authenticatedUser?.userId ?? getHeaderValue(request.headers['x-user-id']) ?? 'system';
  const actorRole = authenticatedUser?.role ?? getHeaderValue(request.headers['x-user-role']) ?? 'SYSTEM';
  const tenantId = authenticatedUser?.tenantId ?? getHeaderValue(request.headers['x-tenant-id']) ?? undefined;
  const workspaceId = authenticatedUser?.workspaceId ?? getHeaderValue(request.headers['x-workspace-id']);
  const projectId = getHeaderValue(request.headers['x-project-id']);
  const correlationId = request.correlationId ?? getHeaderValue(request.headers['x-correlation-id']) ?? undefined;

  const auditEntry = await getAuditService().log({
    action: 'YAPPC_REGISTRY_CANDIDATE_PROMOTED',
    actor,
    actorRole,
    resource: `artifact/${artifactId}/residual-island/${residualIslandId}/registry-candidate`,
    severity: 'info',
    details: `Residual island ${residualIslandId} promoted to registry candidate ${proposedContractName}`,
    ipAddress: request.ip,
    userAgent: getHeaderValue(request.headers['user-agent']) ?? undefined,
    method: request.method,
    status: 201,
    tenantId,
    success: true,
    metadata: {
      workspaceId,
      projectId,
      artifactId,
      residualIslandId,
      candidateId,
      proposedContractName,
      source,
      status: 'NEEDS_REVIEW',
      correlationId,
      route: request.url,
      ...(notes ? { notes } : {}),
    },
  });

  if (!isAuditLogEntrySummary(auditEntry) || typeof auditEntry.id !== 'string') {
    throw new Error('Registry candidate audit persistence did not return an audit record id');
  }

  const timestamp = auditEntry.timestamp instanceof Date
    ? auditEntry.timestamp.toISOString()
    : typeof auditEntry.timestamp === 'string'
      ? auditEntry.timestamp
      : new Date().toISOString();

  return {
    auditRecordId: auditEntry.id,
    createdAt: timestamp,
  };
}

export default async function registryCandidateRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{
    Params: { artifactId: string; residualIslandId: string };
    Body: RegistryCandidatePromotionRequest;
  }>(
    '/yappc/artifacts/:artifactId/residual-islands/:residualIslandId/registry-candidates',
    { preHandler: requirePermission('workflow', 'create') },
    async (request, reply) => {
      const { artifactId, residualIslandId } = request.params;
      const body = request.body;
      const proposedContractName = body?.proposedContractName?.trim();

      if (!artifactId.trim() || !residualIslandId.trim() || !proposedContractName) {
        return reply.status(400).send({
          error: 'artifactId, residualIslandId, and proposedContractName are required',
          auditRecorded: false,
        });
      }

      if (body?.source !== 'decompiled-import') {
        return reply.status(400).send({
          error: 'source must be decompiled-import',
          auditRecorded: false,
        });
      }

      const candidateId = buildCandidateId(artifactId, residualIslandId);

      try {
        const audit = await writeRegistryCandidateAudit({
          request,
          artifactId,
          residualIslandId,
          proposedContractName,
          source: body.source,
          notes: body.notes,
          candidateId,
        });

        const response: RegistryCandidatePromotionResponse = {
          candidateId,
          artifactId,
          residualIslandId,
          proposedContractName,
          status: 'NEEDS_REVIEW',
          auditRecordId: audit.auditRecordId,
          auditRecorded: true,
          createdAt: audit.createdAt,
        };

        return reply.status(201).send(response);
      } catch (error) {
        request.log.error(
          {
            error,
            artifactId,
            residualIslandId,
            candidateId,
          },
          'Failed to persist registry candidate promotion audit record'
        );
        return reply.status(503).send({
          error: 'Registry candidate promotion audit persistence failed',
          auditRecorded: false,
        });
      }
    }
  );
}
