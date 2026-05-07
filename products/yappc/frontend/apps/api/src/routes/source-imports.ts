/**
 * Governed source import routes.
 *
 * @doc.type router
 * @doc.purpose Server-side source import orchestration with tenant/project scope
 * @doc.layer product
 * @doc.pattern REST API
 */

import { randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { getAuditService } from '../services/audit/audit.service';

type SourceImportType = 'tsx' | 'route' | 'storybook' | 'artifact' | 'zip';
type SourceImportFileType = 'component' | 'style' | 'test' | 'documentation' | 'other' | 'route';

interface SourceImportRequest {
  sourceType: SourceImportType;
  source: string;
  projectId: string;
  targetComponentName?: string;
  options?: {
    includeDependencies?: boolean;
    includeStyles?: boolean;
    includeTests?: boolean;
    includeDocumentation?: boolean;
    preserveStructure?: boolean;
    allowUnsafeComponents?: boolean;
  };
}

interface SourceImportFile {
  path: string;
  content: string;
  type: SourceImportFileType;
  source: string;
}

interface SourceImportAuditContext {
  tenantId: string | null;
  workspaceId: string | null;
  projectId: string | null;
  sourceType: string;
  source: string;
  outcome: 'REVIEW_REQUIRED' | 'REJECTED' | 'FAILED';
  reason?: string;
  status: number;
  jobId?: string;
  componentName?: string;
  totalSize?: number;
}

const allowedSourceTypes: readonly SourceImportType[] = ['tsx', 'route', 'storybook', 'artifact', 'zip'];
const maxSourceLocatorLength = 4096;
const maxFetchedBytes = 512 * 1024;

function getHeaderValue(value: string | string[] | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return value ?? null;
}

function isAllowedSourceType(value: string): value is SourceImportType {
  return allowedSourceTypes.includes(value as SourceImportType);
}

function isTrustedLocator(source: string): boolean {
  if (source.startsWith('artifact:')) {
    return true;
  }
  try {
    const url = new URL(source);
    return url.protocol === 'https:';
  } catch {
    return false;
  }
}

function inferFileType(sourceType: SourceImportType): SourceImportFileType {
  if (sourceType === 'route') {
    return 'route';
  }
  if (sourceType === 'storybook') {
    return 'documentation';
  }
  return 'component';
}

function inferComponentName(source: string, fallback: string): string {
  const clean = source.split('?')[0]?.split('#')[0] ?? source;
  const fileName = clean.split('/').pop()?.replace(/\.(tsx|jsx|ts|js|json|zip)$/i, '') ?? '';
  const normalized = fileName
    .replace(/[^a-zA-Z0-9]+(.)/g, (_, letter: string) => letter.toUpperCase())
    .replace(/^[^a-zA-Z]+/, '');
  return normalized.length > 0
    ? normalized.charAt(0).toUpperCase() + normalized.slice(1)
    : fallback;
}

async function readRemoteSource(source: string): Promise<string> {
  if (source.startsWith('artifact:')) {
    return JSON.stringify({ artifactRef: source }, null, 2);
  }

  const response = await fetch(source, { signal: AbortSignal.timeout(10_000) });
  if (!response.ok) {
    throw new Error(`Source fetch failed with ${response.status}`);
  }

  const contentLength = response.headers.get('content-length');
  if (contentLength && Number(contentLength) > maxFetchedBytes) {
    throw new Error(`Source exceeds ${maxFetchedBytes} byte import limit`);
  }

  const text = await response.text();
  if (text.length > maxFetchedBytes) {
    throw new Error(`Source exceeds ${maxFetchedBytes} byte import limit`);
  }
  return text;
}

async function logSourceImportAudit(
  request: FastifyRequest,
  context: SourceImportAuditContext,
): Promise<boolean> {
  const authenticatedUser = request.user;
  const actor = authenticatedUser?.userId ?? getHeaderValue(request.headers['x-user-id']) ?? 'system';
  const actorRole = authenticatedUser?.role ?? getHeaderValue(request.headers['x-user-role']) ?? 'SYSTEM';
  const tenantId = authenticatedUser?.tenantId ?? context.tenantId ?? undefined;

  try {
    await getAuditService().log({
      action: 'YAPPC_SOURCE_IMPORT',
      actor,
      actorRole,
      resource: context.projectId ? `project/${context.projectId}/source-import` : 'source-import',
      severity: context.outcome === 'REVIEW_REQUIRED' ? 'info' : 'warn',
      details: `Source import ${context.outcome.toLowerCase()} for ${context.sourceType}`,
      ipAddress: request.ip,
      userAgent: getHeaderValue(request.headers['user-agent']) ?? undefined,
      method: request.method,
      status: context.status,
      tenantId,
      success: context.outcome === 'REVIEW_REQUIRED',
      error: context.reason,
      metadata: {
        workspaceId: context.workspaceId,
        projectId: context.projectId,
        sourceType: context.sourceType,
        source: context.source,
        outcome: context.outcome,
        reason: context.reason,
        jobId: context.jobId,
        componentName: context.componentName,
        totalSize: context.totalSize,
      },
    });
    return true;
  } catch (error) {
    request.log.warn(
      {
        error,
        projectId: context.projectId,
        sourceType: context.sourceType,
        outcome: context.outcome,
      },
      'Failed to write source import audit event',
    );
    return false;
  }
}

export default async function sourceImportRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{ Body: SourceImportRequest }>(
    '/yappc/artifact/import-source',
    async (request, reply) => {
      const tenantId = getHeaderValue(request.headers['x-tenant-id']);
      const workspaceId = getHeaderValue(request.headers['x-workspace-id']);
      const projectScopeId = getHeaderValue(request.headers['x-project-id']);
      const body = request.body;

      if (!tenantId || !workspaceId || !projectScopeId) {
        const auditRecorded = await logSourceImportAudit(request, {
          tenantId,
          workspaceId,
          projectId: projectScopeId ?? body?.projectId ?? null,
          sourceType: body?.sourceType ?? 'unknown',
          source: body?.source ?? '',
          outcome: 'REJECTED',
          reason: 'missing_scope',
          status: 400,
        });
        return reply.status(400).send({
          success: false,
          files: [],
          warnings: [],
          errors: ['Governed source imports require tenant, workspace, and project headers.'],
          metadata: {
            sourceType: body?.sourceType ?? 'unknown',
            source: body?.source ?? '',
            importedAt: new Date().toISOString(),
            dependencies: [],
            fileCount: 0,
            totalSize: 0,
          },
          job: {
            status: 'REJECTED',
            reason: 'missing_scope',
            auditRecorded,
          },
        });
      }

      if (!body || !body.projectId || body.projectId !== projectScopeId) {
        const auditRecorded = await logSourceImportAudit(request, {
          tenantId,
          workspaceId,
          projectId: projectScopeId ?? body?.projectId ?? null,
          sourceType: body?.sourceType ?? 'unknown',
          source: body?.source ?? '',
          outcome: 'REJECTED',
          reason: 'project_scope_mismatch',
          status: 400,
        });
        return reply.status(400).send({
          success: false,
          files: [],
          warnings: [],
          errors: ['Request projectId must match X-Project-ID scope.'],
          metadata: {
            sourceType: body?.sourceType ?? 'unknown',
            source: body?.source ?? '',
            importedAt: new Date().toISOString(),
            dependencies: [],
            fileCount: 0,
            totalSize: 0,
          },
          job: {
            status: 'REJECTED',
            reason: 'project_scope_mismatch',
            auditRecorded,
          },
        });
      }

      if (!isAllowedSourceType(body.sourceType)) {
        const auditRecorded = await logSourceImportAudit(request, {
          tenantId,
          workspaceId,
          projectId: projectScopeId,
          sourceType: body.sourceType,
          source: body.source,
          outcome: 'REJECTED',
          reason: 'unsupported_source_type',
          status: 400,
        });
        return reply.status(400).send({
          success: false,
          files: [],
          warnings: [],
          errors: ['Unsupported source import type.'],
          metadata: {
            sourceType: body.sourceType,
            source: body.source,
            importedAt: new Date().toISOString(),
            dependencies: [],
            fileCount: 0,
            totalSize: 0,
          },
          job: {
            status: 'REJECTED',
            reason: 'unsupported_source_type',
            auditRecorded,
          },
        });
      }

      if (!body.source || body.source.length > maxSourceLocatorLength || !isTrustedLocator(body.source)) {
        const auditRecorded = await logSourceImportAudit(request, {
          tenantId,
          workspaceId,
          projectId: projectScopeId,
          sourceType: body.sourceType,
          source: body.source ?? '',
          outcome: 'REJECTED',
          reason: 'untrusted_source_locator',
          status: 400,
        });
        return reply.status(400).send({
          success: false,
          files: [],
          warnings: [],
          errors: ['Source locator must be an HTTPS URL or artifact reference within the allowed size limit.'],
          metadata: {
            sourceType: body.sourceType,
            source: body.source ?? '',
            importedAt: new Date().toISOString(),
            dependencies: [],
            fileCount: 0,
            totalSize: 0,
          },
          job: {
            status: 'REJECTED',
            reason: 'untrusted_source_locator',
            auditRecorded,
          },
        });
      }

      try {
        const importedAt = new Date().toISOString();
        const content = await readRemoteSource(body.source);
        const componentName = body.targetComponentName ?? inferComponentName(body.source, 'ImportedSource');
        const file: SourceImportFile = {
          path: body.sourceType === 'artifact' ? `${componentName}.json` : `${componentName}.tsx`,
          content,
          type: inferFileType(body.sourceType),
          source: body.source,
        };
        const jobId = `source-import-${randomUUID()}`;
        const auditRecorded = await logSourceImportAudit(request, {
          tenantId,
          workspaceId,
          projectId: body.projectId,
          sourceType: body.sourceType,
          source: body.source,
          outcome: 'REVIEW_REQUIRED',
          status: 200,
          jobId,
          componentName,
          totalSize: content.length,
        });

        return {
          success: true,
          componentId: `${body.projectId}/${componentName}`,
          files: [file],
          warnings: ['Imported source requires operator review before applying generated page artifacts.'],
          errors: [],
          metadata: {
            sourceType: body.sourceType,
            source: body.source,
            importedAt,
            componentName,
            dependencies: [],
            fileCount: 1,
            totalSize: content.length,
          },
          job: {
            id: jobId,
            status: 'REVIEW_REQUIRED',
            tenantId,
            workspaceId,
            projectId: body.projectId,
            createdAt: importedAt,
            auditRecorded,
          },
        };
      } catch (error) {
        const auditRecorded = await logSourceImportAudit(request, {
          tenantId,
          workspaceId,
          projectId: body.projectId,
          sourceType: body.sourceType,
          source: body.source,
          outcome: 'FAILED',
          reason: 'source_fetch_failed',
          status: 502,
        });
        return reply.status(502).send({
          success: false,
          files: [],
          warnings: [],
          errors: [error instanceof Error ? error.message : 'Source import failed.'],
          metadata: {
            sourceType: body.sourceType,
            source: body.source,
            importedAt: new Date().toISOString(),
            dependencies: [],
            fileCount: 0,
            totalSize: 0,
          },
          job: {
            status: 'FAILED',
            reason: 'source_fetch_failed',
            auditRecorded,
          },
        });
      }
    },
  );
}
