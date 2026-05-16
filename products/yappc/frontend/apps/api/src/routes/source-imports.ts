/**
 * Governed source import routes.
 *
 * @doc.type router
 * @doc.purpose Server-side source import orchestration with tenant/project scope
 * @doc.layer product
 * @doc.pattern REST API
 */

import { randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { createDefaultProviderRegistry, SynthesisPipeline, type GraphNode, getCanonicalExtractors } from 'yappc-artifact-compiler';
import { getAuditService } from '../services/audit/audit.service';
import {
  getJobRepository,
  type SourceImportJob,
  type SourceImportJobStatus,
  type SourceImportType,
} from '../services/job-repository';

type SourceImportFileType = 'component' | 'style' | 'test' | 'documentation' | 'other' | 'route';
type SourceImportProgressStepStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';

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

interface SourceImportResolution {
  files: SourceImportFile[];
  componentName: string;
  totalSize: number;
  warnings: string[];
  summary?: SourceImportJob['summary'];
  snapshotRef?: SourceImportJob['snapshotRef'];
  residualIslandIds?: SourceImportJob['residualIslandIds'];
  skippedArtifacts?: SourceImportJob['skippedArtifacts'];
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

interface SourceImportProgressStep {
  id: string;
  label: string;
  status: SourceImportProgressStepStatus;
  percent: number;
  message?: string;
  startedAt?: string;
  completedAt?: string;
}

interface SourceImportJobSnapshot {
  id: string;
  status: SourceImportJobStatus;
  tenantId: string | null;
  workspaceId: string | null;
  projectId: string | null;
  sourceType: string;
  source: string;
  componentName?: string;
  reason?: string;
  auditRecorded?: boolean;
  percentComplete: number;
  currentStep: string;
  steps: readonly SourceImportProgressStep[];
  createdAt: string;
  updatedAt: string;
}

const allowedSourceTypes = [
  'tsx',
  'route',
  'storybook',
  'artifact',
  'zip',
  'github',
  'gitlab',
  'local-folder',
] as const satisfies readonly SourceImportType[];
const maxSourceLocatorLength = 4096;
const maxFetchedBytes = 512 * 1024;
const sourceImportJobTtlMs = 24 * 60 * 60 * 1000;
const repoSlugPattern = /^[\w.-]+\/[\w.-]+(?:@.+)?$/;

const sourceImportStepTemplates: readonly Omit<SourceImportProgressStep, 'status'>[] = [
  { id: 'validate_scope', label: 'Validate tenant workspace and project scope', percent: 20 },
  { id: 'validate_source', label: 'Validate source locator and import type', percent: 35 },
  { id: 'fetch_source', label: 'Fetch governed source content', percent: 65 },
  { id: 'prepare_review', label: 'Prepare review-required import payload', percent: 90 },
  { id: 'audit', label: 'Record governed import audit event', percent: 100 },
];

const legacyTsImportApiEnabled =
  process.env.YAPPC_ARTIFACT_COMPILER_LEGACY_TS_IMPORT_API === 'true';

const javaImportApiBaseUrl =
  process.env.YAPPC_ARTIFACT_COMPILER_IMPORT_API_BASE_URL ?? 'http://localhost:8080';

function getHeaderValue(value: string | string[] | undefined): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return value ?? null;
}

function isAllowedSourceType(value: string): value is SourceImportType {
  return allowedSourceTypes.includes(value as SourceImportType);
}

function isRepositorySourceType(sourceType: SourceImportType): sourceType is Extract<SourceImportType, 'github' | 'gitlab' | 'local-folder'> {
  return sourceType === 'github' || sourceType === 'gitlab' || sourceType === 'local-folder';
}

function isTrustedLocator(sourceType: SourceImportType, source: string): boolean {
  if (sourceType === 'artifact' && source.startsWith('artifact:')) {
    return true;
  }

  if (sourceType === 'github') {
    return source.includes('github.com') || source.startsWith('github:') || repoSlugPattern.test(source);
  }

  if (sourceType === 'gitlab') {
    return source.includes('gitlab.com') || source.startsWith('gitlab:') || repoSlugPattern.test(source);
  }

  if (sourceType === 'local-folder') {
    return source.startsWith('file://');
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

async function resolveRepositoryImport(request: SourceImportRequest): Promise<SourceImportResolution> {
  const registry = createDefaultProviderRegistry();
  const locator = request.sourceType === 'gitlab' && !request.source.includes('gitlab.com')
    ? `https://gitlab.com/${request.source}`
    : request.source;
  const snapshot = await registry.resolve(locator, {
    maxFiles: 10_000,
    maxFileSizeBytes: maxFetchedBytes,
  });

  // P0-1: Use canonical extractors instead of empty array
  // This ensures repository import produces meaningful extraction results
  const canonicalExtractors = getCanonicalExtractors();
  if (canonicalExtractors.length === 0) {
    throw new Error('UNSUPPORTED_EXTRACTION_PIPELINE: No extractors registered. Cannot perform repository import without extraction capabilities.');
  }

  const pipeline = new SynthesisPipeline({
    extractors: canonicalExtractors,
    residualConfidenceThreshold: 0.5,
  });
  const result = await pipeline.runFromSnapshot(snapshot);
  const unrecoverableErrors = result.errors
    .filter((error: { recoverable: boolean }) => !error.recoverable)
    .map((error: { message: string }) => error.message);

  if (unrecoverableErrors.length > 0) {
    throw new Error(unrecoverableErrors.join('; '));
  }

  // P0-1: Validate that extraction produced meaningful results
  // If no nodes were extracted, this indicates the extractors could not handle the repository
  if (result.stats.extractedNodes === 0 && result.stats.modelElementsGenerated === 0) {
    throw new Error('UNSUPPORTED_EXTRACTION_PIPELINE: Repository import produced no extracted artifacts. The repository may contain unsupported file types or require additional extractors.');
  }

  const componentNodes = result.graph.nodes.filter(
    (node: GraphNode) => node.kind === 'component',
  );
  const totalSize = snapshot.files.reduce((sum: number, file: { sizeBytes: number }) => sum + file.sizeBytes, 0);
  const skippedArtifacts = snapshot.files
    .filter((file: { materialized: boolean }) => !file.materialized)
    .map((file: { relativePath: string }) => ({
      path: file.relativePath,
      reason: 'not_materialized',
    }));
  const confidenceDenominator = result.stats.modelElementsGenerated + result.stats.residualIslandsGenerated;
  const fallbackName = snapshot.snapshotRef.repoId.split('/').at(-1) ?? 'ImportedRepository';

  return {
    files: componentNodes.map((node: GraphNode) => ({
      path: node.sourceLocation.filePath,
      content: '',
      type: 'component',
      source: request.source,
    })),
    componentName: request.targetComponentName ?? inferComponentName(request.source, fallbackName),
    totalSize,
    warnings: [
      ...result.warnings.map((warning: { message: string }) => warning.message),
      ...result.errors.filter((error: { recoverable: boolean }) => error.recoverable).map((error: { message: string }) => error.message),
    ],
    snapshotRef: snapshot.snapshotRef,
    summary: {
      totalFiles: snapshot.files.length,
      skippedFiles: skippedArtifacts.length,
      totalSize,
      confidence: confidenceDenominator > 0
        ? result.stats.modelElementsGenerated / confidenceDenominator
        : 0,
    },
    residualIslandIds: result.residualIslands.map((island: { id: string }) => island.id),
    skippedArtifacts,
  };
}

async function resolveSourceImport(request: SourceImportRequest): Promise<SourceImportResolution> {
  if (isRepositorySourceType(request.sourceType)) {
    return resolveRepositoryImport(request);
  }

  const content = await readRemoteSource(request.source);
  const componentName = request.targetComponentName ?? inferComponentName(request.source, 'ImportedSource');

  return {
    files: [
      {
        path: request.sourceType === 'artifact' ? `${componentName}.json` : `${componentName}.tsx`,
        content,
        type: inferFileType(request.sourceType),
        source: request.source,
      },
    ],
    componentName,
    totalSize: content.length,
    warnings: [],
  };
}

async function createSourceImportJob(input: {
  tenantId: string | null;
  workspaceId: string | null;
  projectId: string | null;
  sourceType: string;
  source: string;
  componentName?: string;
}): Promise<SourceImportJob> {
  const now = new Date().toISOString();
  const repo = getJobRepository();
  return repo.create({
    status: 'VALIDATING',
    tenantId: input.tenantId,
    workspaceId: input.workspaceId,
    projectId: input.projectId,
    sourceType: input.sourceType as any,
    source: input.source,
    componentName: input.componentName,
    percentComplete: 0,
    currentStep: 'validate_scope',
    steps: sourceImportStepTemplates.map((step, index) => ({
      ...step,
      status: index === 0 ? 'running' : 'pending',
      startedAt: index === 0 ? now : undefined,
    })),
  });
}

async function updateSourceImportJobStep(
  job: SourceImportJob,
  stepId: string,
  status: SourceImportProgressStepStatus,
  message?: string,
): Promise<SourceImportJob> {
  const now = new Date().toISOString();
  const steps = job.steps.map((step) => {
    if (step.id !== stepId) {
      return step;
    }

    return {
      ...step,
      status,
      message,
      startedAt: step.startedAt ?? now,
      completedAt: status === 'completed' || status === 'failed' || status === 'skipped' ? now : step.completedAt,
    };
  });
  const currentStep = steps.find((step) => step.status === 'running')?.id ?? stepId;
  const percentComplete = Math.max(
    0,
    ...steps
      .filter((step) => step.status === 'completed')
      .map((step) => step.percent),
  );

  const repo = getJobRepository();
  const updated = await repo.update(job.id, {
    currentStep,
    percentComplete,
    steps,
    updatedAt: now,
  });
  return updated ?? job;
}

async function startSourceImportJobStep(job: SourceImportJob, stepId: string): Promise<SourceImportJob> {
  const now = new Date().toISOString();
  const repo = getJobRepository();
  const updated = await repo.update(job.id, {
    status: stepId === 'fetch_source' ? 'FETCHING_SOURCE' : job.status,
    currentStep: stepId,
    steps: job.steps.map((step) =>
      step.id === stepId
        ? {
            ...step,
            status: 'running',
            startedAt: step.startedAt ?? now,
          }
        : step,
    ),
    updatedAt: now,
  });
  return updated ?? job;
}

async function completeSourceImportJob(
  job: SourceImportJob,
  input: {
    status: Extract<SourceImportJobStatus, 'REVIEW_REQUIRED' | 'REJECTED' | 'FAILED'>;
    reason?: string;
    auditRecorded: boolean;
    failedStepId?: string;
    componentName?: string;
    summary?: SourceImportJob['summary'];
    snapshotRef?: SourceImportJob['snapshotRef'];
    residualIslandIds?: SourceImportJob['residualIslandIds'];
    skippedArtifacts?: SourceImportJob['skippedArtifacts'];
  },
): Promise<SourceImportJob> {
  const now = new Date().toISOString();
  const failureStepId = input.failedStepId ?? job.currentStep;
  const steps = job.steps.map((step) => {
    if (input.status === 'REVIEW_REQUIRED') {
      return {
        ...step,
        status: 'completed' as const,
        startedAt: step.startedAt ?? now,
        completedAt: step.completedAt ?? now,
      };
    }
    if (step.id === failureStepId) {
      return {
        ...step,
        status: 'failed' as const,
        message: input.reason,
        startedAt: step.startedAt ?? now,
        completedAt: now,
      };
    }
    if (step.status === 'pending') {
      return {
        ...step,
        status: 'skipped' as const,
        completedAt: now,
      };
    }
    return step;
  });

  const percentComplete =
    input.status === 'REVIEW_REQUIRED'
      ? 100
      : Math.max(0, ...steps.filter((step) => step.status === 'completed').map((step) => step.percent));
  const currentStep = input.status === 'REVIEW_REQUIRED' ? 'audit' : failureStepId;

  const repo = getJobRepository();
  const updated = await repo.update(job.id, {
    status: input.status,
    reason: input.reason,
    auditRecorded: input.auditRecorded,
    componentName: input.componentName ?? job.componentName,
    summary: input.summary ?? job.summary,
    snapshotRef: input.snapshotRef ?? job.snapshotRef,
    residualIslandIds: input.residualIslandIds ?? job.residualIslandIds,
    skippedArtifacts: input.skippedArtifacts ?? job.skippedArtifacts,
    percentComplete,
    currentStep,
    steps,
    updatedAt: now,
  });
  return updated ?? job;
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
      if (!legacyTsImportApiEnabled) {
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
      }

      const tenantId = getHeaderValue(request.headers['x-tenant-id']);
      const workspaceId = getHeaderValue(request.headers['x-workspace-id']);
      const projectScopeId = getHeaderValue(request.headers['x-project-id']);
      const body = request.body;
      const importJob = await createSourceImportJob({
        tenantId,
        workspaceId,
        projectId: projectScopeId ?? body?.projectId ?? null,
        sourceType: body?.sourceType ?? 'unknown',
        source: body?.source ?? '',
        componentName: body?.targetComponentName,
      });

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
          job: await completeSourceImportJob(importJob, {
            status: 'REJECTED',
            reason: 'missing_scope',
            auditRecorded,
            failedStepId: 'validate_scope',
          }),
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
          job: await completeSourceImportJob(importJob, {
            status: 'REJECTED',
            reason: 'project_scope_mismatch',
            auditRecorded,
            failedStepId: 'validate_scope',
          }),
        });
      }

      const sourceValidatedJob = await updateSourceImportJobStep(
        await updateSourceImportJobStep(importJob, 'validate_scope', 'completed'),
        'validate_source',
        'running',
      );

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
          job: await completeSourceImportJob(sourceValidatedJob, {
            status: 'REJECTED',
            reason: 'unsupported_source_type',
            auditRecorded,
            failedStepId: 'validate_source',
          }),
        });
      }

      if (!body.source || body.source.length > maxSourceLocatorLength || !isTrustedLocator(body.sourceType, body.source)) {
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
          job: await completeSourceImportJob(sourceValidatedJob, {
            status: 'REJECTED',
            reason: 'untrusted_source_locator',
            auditRecorded,
            failedStepId: 'validate_source',
          }),
        });
      }

      let activeJob = sourceValidatedJob;
      try {
        const importedAt = new Date().toISOString();
        const fetchingJob = await startSourceImportJobStep(
          await updateSourceImportJobStep(sourceValidatedJob, 'validate_source', 'completed'),
          'fetch_source',
        );
        activeJob = fetchingJob;
        const resolution = await resolveSourceImport(body);
        const reviewJob = await startSourceImportJobStep(
          await updateSourceImportJobStep(fetchingJob, 'fetch_source', 'completed'),
          'prepare_review',
        );
        activeJob = reviewJob;
        const auditRecorded = await logSourceImportAudit(request, {
          tenantId,
          workspaceId,
          projectId: body.projectId,
          sourceType: body.sourceType,
          source: body.source,
          outcome: 'REVIEW_REQUIRED',
          status: 200,
          jobId: reviewJob.id,
          componentName: resolution.componentName,
          totalSize: resolution.totalSize,
        });
        const completedJob = await completeSourceImportJob(
          await updateSourceImportJobStep(reviewJob, 'prepare_review', 'completed'),
          {
            status: 'REVIEW_REQUIRED',
            auditRecorded,
            componentName: resolution.componentName,
            summary: resolution.summary,
            snapshotRef: resolution.snapshotRef,
            residualIslandIds: resolution.residualIslandIds,
            skippedArtifacts: resolution.skippedArtifacts,
          },
        );

        return {
          success: true,
          componentId: `${body.projectId}/${resolution.componentName}`,
          files: resolution.files,
          warnings: [
            ...resolution.warnings,
            'Imported source requires operator review before applying generated page artifacts.',
          ],
          errors: [],
          metadata: {
            sourceType: body.sourceType,
            source: body.source,
            importedAt,
            componentName: resolution.componentName,
            dependencies: [],
            fileCount: resolution.files.length,
            totalSize: resolution.totalSize,
          },
          job: completedJob,
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
          job: await completeSourceImportJob(activeJob, {
            status: 'FAILED',
            reason: 'source_fetch_failed',
            auditRecorded,
            failedStepId: 'fetch_source',
          }),
        });
      }
    },
  );

  fastify.get<{ Params: { jobId: string } }>(
    '/yappc/artifact/import-source/:jobId',
    async (request, reply) => {
      const tenantId = getHeaderValue(request.headers['x-tenant-id']);
      const workspaceId = getHeaderValue(request.headers['x-workspace-id']);
      const projectScopeId = getHeaderValue(request.headers['x-project-id']);

      if (!tenantId || !workspaceId || !projectScopeId) {
        return reply.status(400).send({
          error: 'missing_scope',
          message: 'Governed source import job polling requires tenant, workspace, and project headers.',
        });
      }

      const repo = getJobRepository();
      await repo.deleteExpired(sourceImportJobTtlMs);
      const job = await repo.findById(request.params.jobId);
      if (!job) {
        return reply.status(404).send({
          error: 'source_import_job_not_found',
          message: 'Source import job was not found or has expired.',
        });
      }

      if (job.tenantId !== tenantId || job.workspaceId !== workspaceId || job.projectId !== projectScopeId) {
        return reply.status(403).send({
          error: 'source_import_job_scope_mismatch',
          message: 'Source import job scope does not match the request scope.',
        });
      }

      return { job };
    },
  );
}
