/**
 * Governed source import routes.
 *
 * @doc.type router
 * @doc.purpose Proxy to Java-owned source import API
 * @doc.layer product
 * @doc.pattern REST API
 * 
 * P1-15: CONSOLIDATED - Legacy TS implementation removed.
 * Java now owns production source acquisition via ImportController.
 * This route only proxies to the Java API for all requests.
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';

interface SourceImportRequest {
  sourceType: string;
  source: string;
  projectId: string;
  targetComponentName?: string;
  options?: Record<string, unknown>;
}

const javaImportApiBaseUrl =
  process.env.YAPPC_ARTIFACT_COMPILER_IMPORT_API_BASE_URL ?? 'http://localhost:8080';

function getHeaderValue(value: string | string[] | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return value ?? null;
}

async function proxyImportToJava(
  request: FastifyRequest<{ Body: SourceImportRequest }>,
  reply: FastifyReply,
): Promise<unknown> {
  const tenantId = getHeaderValue(request.headers['x-tenant-id']);
  const workspaceId = getHeaderValue(request.headers['x-workspace-id']);
  const projectId = getHeaderValue(request.headers['x-project-id']);

  const response = await fetch(`${javaImportApiBaseUrl}/api/v1/yappc/artifact/import-source`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(tenantId ? { 'x-tenant-id': tenantId } : {}),
      ...(workspaceId ? { 'x-workspace-id': workspaceId } : {}),
      ...(projectId ? { 'x-project-id': projectId } : {}),
    },
    body: JSON.stringify(request.body ?? {}),
  });

  const contentType = response.headers.get('content-type') ?? '';
  const payload = contentType.includes('application/json')
    ? await response.json()
    : { success: response.ok, message: await response.text() };

  if (!response.ok) {
    return reply.status(response.status).send(payload);
  }

  return payload;
}

async function proxyJobStatusToJava(
  request: FastifyRequest<{ Params: { jobId: string } }>,
  reply: FastifyReply,
): Promise<unknown> {
  const tenantId = getHeaderValue(request.headers['x-tenant-id']);
  const workspaceId = getHeaderValue(request.headers['x-workspace-id']);
  const projectId = getHeaderValue(request.headers['x-project-id']);

  const response = await fetch(
    `${javaImportApiBaseUrl}/api/v1/yappc/artifact/import-source/${request.params.jobId}`,
    {
      method: 'GET',
      headers: {
        ...(tenantId ? { 'x-tenant-id': tenantId } : {}),
        ...(workspaceId ? { 'x-workspace-id': workspaceId } : {}),
        ...(projectId ? { 'x-project-id': projectId } : {}),
      },
    },
  );

  const contentType = response.headers.get('content-type') ?? '';
  const payload = contentType.includes('application/json')
    ? await response.json()
    : { success: response.ok, message: await response.text() };

  if (!response.ok) {
    return reply.status(response.status).send(payload);
  }

  return payload;
}

export default async function sourceImportRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{ Body: SourceImportRequest }>(
    '/yappc/artifact/import-source',
    async (request, reply) => {
      try {
        return await proxyImportToJava(request, reply);
      } catch (error) {
        request.log.error({ error }, 'Java artifact import API proxy failed');
        return reply.status(502).send({
          success: false,
          files: [],
          warnings: [],
          errors: ['Java artifact import API unavailable.'],
          metadata: {
            sourceType: request.body?.sourceType ?? 'unknown',
            source: request.body?.source ?? '',
            importedAt: new Date().toISOString(),
            dependencies: [],
            fileCount: 0,
            totalSize: 0,
          },
        });
      }
    },
  );

  fastify.get<{ Params: { jobId: string } }>(
    '/yappc/artifact/import-source/:jobId',
    async (request, reply) => {
      try {
        return await proxyJobStatusToJava(request, reply);
      } catch (error) {
        request.log.error({ error }, 'Java artifact import job status proxy failed');
        return reply.status(502).send({
          error: 'java_api_unavailable',
          message: 'Java artifact import API unavailable.',
        });
      }
    },
  );
}
