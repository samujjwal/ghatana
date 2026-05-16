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
import { getJobRepository, type SourceImportJob } from '../services/job-repository';

type SourceImportType = 'tsx' | 'route' | 'storybook' | 'artifact' | 'zip';
type SourceImportFileType = 'component' | 'style' | 'test' | 'documentation' | 'other' | 'route';
type SourceImportJobStatus = 'VALIDATING' | 'FETCHING_SOURCE' | 'REVIEW_REQUIRED' | 'REJECTED' | 'FAILED';
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

const allowedSourceTypes: readonly SourceImportType[] = ['tsx', 'route', 'storybook', 'artifact', 'zip'];
const maxSourceLocatorLength = 4096;
const maxFetchedBytes = 512 * 1024;
const sourceImportJobTtlMs = 24 * 60 * 60 * 1000;

const sourceImportStepTemplates: readonly Omit<SourceImportProgressStep, 'status'>[] = [
  { id: 'validate_scope', label: 'Validate tenant workspace and project scope', percent: 20 },
  { id: 'validate_source', label: 'Validate source locator and import type', percent: 35 },
  { id: 'fetch_source', label: 'Fetch governed source content', percent: 65 },
  { id: 'prepare_review', label: 'Prepare review-required import payload', percent: 90 },
  { id: 'audit', label: 'Record governed import audit event', percent: 100 },
];

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
          job: completeSourceImportJob(sourceValidatedJob, {
            status: 'REJECTED',
            reason: 'unsupported_source_type',
            auditRecorded,
            failedStepId: 'validate_source',
          }),
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
        const content = await readRemoteSource(body.source);
        const componentName = body.targetComponentName ?? inferComponentName(body.source, 'ImportedSource');
        const file: SourceImportFile = {
          path: body.sourceType === 'artifact' ? `${componentName}.json` : `${componentName}.tsx`,
          content,
          type: inferFileType(body.sourceType),
          source: body.source,
        };
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
          componentName,
          totalSize: content.length,
        });
        const completedJob = await completeSourceImportJob(
          await updateSourceImportJobStep(reviewJob, 'prepare_review', 'completed'),
          {
            status: 'REVIEW_REQUIRED',
            auditRecorded,
            componentName,
          },
        );

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
          job: completeSourceImportJob(activeJob, {
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
