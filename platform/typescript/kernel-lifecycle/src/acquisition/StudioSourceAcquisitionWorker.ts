/**
 * @fileoverview Server-side Studio source acquisition worker.
 *
 * Executes scoped source acquisition jobs behind the Kernel API boundary. The
 * worker intentionally receives repository/archive bytes through injected
 * fetcher/writer adapters so credentials, queues, and durable storage can be
 * provided by platform infrastructure without leaking into Studio.
 */

import { gunzipSync, inflateRawSync } from 'node:zlib';
import { mkdir, readFile, readdir, rename, rm, writeFile } from 'node:fs/promises';
import { dirname, join, relative, resolve } from 'node:path';

import type {
  StudioSourceAcquisitionArchivePayload,
  StudioSourceAcquisitionJob,
  StudioSourceAcquisitionJobStore,
  StudioSourceAcquisitionPayloadStore,
  StudioWorkflowStoreScope,
} from '../api/KernelLifecycleApiHandlers.js';

export interface StudioSourceAcquisitionWorkerOptions {
  readonly maxArchiveBytes?: number;
  readonly maxTotalUncompressedBytes?: number;
  readonly maxFileBytes?: number;
  readonly maxEntryCount?: number;
  readonly allowedExtensions?: readonly string[];
  readonly includeHidden?: boolean;
}

export interface StudioArchiveAcquisitionRequest {
  readonly scope: StudioWorkflowStoreScope;
  readonly jobId: string;
  readonly fileName: string;
  readonly bytes: Uint8Array;
  readonly options?: StudioSourceAcquisitionWorkerOptions;
}

export interface StudioStoredArchiveAcquisitionRequest {
  readonly scope: StudioWorkflowStoreScope;
  readonly jobId: string;
  readonly payloadStore: StudioSourceAcquisitionPayloadStore;
  readonly options?: StudioSourceAcquisitionWorkerOptions;
}

export interface StudioRunningStoredArchiveAcquisitionRequest extends StudioStoredArchiveAcquisitionRequest {}

export interface StudioRepositoryAcquisitionRequest {
  readonly scope: StudioWorkflowStoreScope;
  readonly jobId: string;
  readonly repositoryUrl: string;
  readonly ref?: string;
  readonly fetcher: StudioRepositoryArchiveFetcher;
  readonly options?: StudioSourceAcquisitionWorkerOptions;
}

export interface StudioRunningRepositoryAcquisitionRequest extends StudioRepositoryAcquisitionRequest {}

export interface StudioRepositoryArchiveFetcher {
  fetchArchive(request: {
    readonly repositoryUrl: string;
    readonly ref?: string;
    readonly maxBytes: number;
  }): Promise<{
    readonly fileName: string;
    readonly bytes: Uint8Array;
  }>;
}

export interface StudioSourceAcquisitionQueueClaim {
  readonly workerId: string;
  readonly now: string;
  readonly leaseExpiresAt: string;
  readonly maxAttempts?: number;
}

export interface StudioSourceAcquisitionQueueSnapshot {
  readonly total: number;
  readonly pending: number;
  readonly running: number;
  readonly expiredRunning: number;
  readonly complete: number;
  readonly failed: number;
  readonly cancelled: number;
  readonly oldestPendingCreatedAt?: string;
  readonly oldestExpiredLeaseAt?: string;
}

export interface StudioSourceAcquisitionQueueStore extends StudioSourceAcquisitionJobStore {
  claimNextPendingJob(claim: StudioSourceAcquisitionQueueClaim): Promise<StudioSourceAcquisitionJob | null>;
  getQueueSnapshot(now: string): Promise<StudioSourceAcquisitionQueueSnapshot>;
}

export interface StudioSourceAcquisitionQueueRunnerOptions {
  readonly workerId: string;
  readonly leaseMs?: number;
  readonly maxAttempts?: number;
  readonly archivePayloadStore: StudioSourceAcquisitionPayloadStore;
  readonly repositoryFetcher: StudioRepositoryArchiveFetcher;
  readonly workerOptions?: StudioSourceAcquisitionWorkerOptions;
}

export interface StudioRepositoryArchiveTokenProvider {
  getToken(request: {
    readonly provider: 'github' | 'gitlab';
    readonly repositoryUrl: string;
  }): Promise<string | null>;
}

export interface HttpStudioRepositoryArchiveFetcherOptions {
  readonly githubApiUrl?: string;
  readonly gitlabApiUrl?: string;
  readonly tokenProvider?: StudioRepositoryArchiveTokenProvider;
  readonly fetchFn?: typeof fetch;
}

export interface StudioSourceWorkspaceWriter {
  writeFiles(request: {
    readonly scope: StudioWorkflowStoreScope;
    readonly jobId: string;
    readonly files: readonly StudioMaterializedSourceFile[];
  }): Promise<{
    readonly localWorkspacePath: string;
  }>;
}

export interface StudioMaterializedSourceFile {
  readonly relativePath: string;
  readonly bytes: Uint8Array;
}

export interface StudioSourceAcquisitionWorkerResult {
  readonly job: StudioSourceAcquisitionJob;
  readonly files: readonly StudioMaterializedSourceFile[];
}

const DEFAULT_OPTIONS: Required<StudioSourceAcquisitionWorkerOptions> = {
  maxArchiveBytes: 50 * 1024 * 1024,
  maxTotalUncompressedBytes: 100 * 1024 * 1024,
  maxFileBytes: 5 * 1024 * 1024,
  maxEntryCount: 5_000,
  allowedExtensions: ['.ts', '.tsx', '.js', '.jsx', '.css', '.json'],
  includeHidden: false,
};

export class StudioSourceAcquisitionWorker {
  constructor(
    private readonly jobStore: StudioSourceAcquisitionJobStore,
    private readonly writer: StudioSourceWorkspaceWriter,
    private readonly clock: () => string = () => new Date().toISOString(),
  ) {}

  async executeArchive(request: StudioArchiveAcquisitionRequest): Promise<StudioSourceAcquisitionWorkerResult> {
    const options = resolveOptions(request.options);
    await this.markRunning(request.scope, request.jobId);
    try {
      const files = materializeArchive(request.fileName, request.bytes, options);
      const totalBytes = files.reduce((sum, file) => sum + file.bytes.byteLength, 0);
      const writeResult = await this.writer.writeFiles({
        scope: request.scope,
        jobId: request.jobId,
        files,
      });
      const job = await this.updateRequired(request.scope, request.jobId, {
        status: 'complete',
        completedAt: this.clock(),
        totalBytes,
        fileCount: files.length,
        localWorkspacePath: writeResult.localWorkspacePath,
      });
      return { job, files };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const job = await this.updateRequired(request.scope, request.jobId, {
        status: 'failed',
        completedAt: this.clock(),
        errorMessage: message,
      });
      return { job, files: [] };
    }
  }

  async executeStoredArchive(request: StudioStoredArchiveAcquisitionRequest): Promise<StudioSourceAcquisitionWorkerResult> {
    const options = resolveOptions(request.options);
    await this.markRunning(request.scope, request.jobId);
    return await this.executeStoredArchiveFromRunningJob({
      scope: request.scope,
      jobId: request.jobId,
      payloadStore: request.payloadStore,
      options,
    });
  }

  async executeRunningStoredArchive(request: StudioRunningStoredArchiveAcquisitionRequest): Promise<StudioSourceAcquisitionWorkerResult> {
    return await this.executeStoredArchiveFromRunningJob({
      scope: request.scope,
      jobId: request.jobId,
      payloadStore: request.payloadStore,
      options: resolveOptions(request.options),
    });
  }

  private async executeStoredArchiveFromRunningJob(request: {
    readonly scope: StudioWorkflowStoreScope;
    readonly jobId: string;
    readonly payloadStore: StudioSourceAcquisitionPayloadStore;
    readonly options: Required<StudioSourceAcquisitionWorkerOptions>;
  }): Promise<StudioSourceAcquisitionWorkerResult> {
    let payload: StudioSourceAcquisitionArchivePayload;
    let bytes: Uint8Array;
    try {
      const storedPayload = await request.payloadStore.getArchivePayload(request.scope, request.jobId);
      if (storedPayload === null) {
        throw new Error(`Studio source acquisition archive payload not found: ${request.jobId}`);
      }
      if (!sameScope(storedPayload.scope, request.scope) || storedPayload.jobId !== request.jobId) {
        throw new Error('Studio source acquisition archive payload scope mismatch');
      }
      payload = storedPayload;
      bytes = Uint8Array.from(Buffer.from(payload.contentBase64, 'base64'));
      if (bytes.byteLength !== payload.size) {
        throw new Error(`Stored archive payload size mismatch for job ${request.jobId}`);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const job = await this.updateRequired(request.scope, request.jobId, {
        status: 'failed',
        completedAt: this.clock(),
        errorMessage: message,
      });
      return { job, files: [] };
    }
    const result = await this.executeArchiveFromRunningJob({
      scope: request.scope,
      jobId: request.jobId,
      fileName: payload.fileName,
      bytes,
      options: request.options,
    });
    if (result.job.status === 'complete') {
      await request.payloadStore.deleteArchivePayload?.(request.scope, request.jobId);
    }
    return result;
  }

  async executeRepository(request: StudioRepositoryAcquisitionRequest): Promise<StudioSourceAcquisitionWorkerResult> {
    const options = resolveOptions(request.options);
    await this.markRunning(request.scope, request.jobId);
    return await this.executeRepositoryFromRunningJob({
      scope: request.scope,
      jobId: request.jobId,
      repositoryUrl: request.repositoryUrl,
      ...(request.ref === undefined ? {} : { ref: request.ref }),
      fetcher: request.fetcher,
      options,
    });
  }

  async executeRunningRepository(request: StudioRunningRepositoryAcquisitionRequest): Promise<StudioSourceAcquisitionWorkerResult> {
    return await this.executeRepositoryFromRunningJob({
      scope: request.scope,
      jobId: request.jobId,
      repositoryUrl: request.repositoryUrl,
      ...(request.ref === undefined ? {} : { ref: request.ref }),
      fetcher: request.fetcher,
      options: resolveOptions(request.options),
    });
  }

  private async executeRepositoryFromRunningJob(request: {
    readonly scope: StudioWorkflowStoreScope;
    readonly jobId: string;
    readonly repositoryUrl: string;
    readonly ref?: string;
    readonly fetcher: StudioRepositoryArchiveFetcher;
    readonly options: Required<StudioSourceAcquisitionWorkerOptions>;
  }): Promise<StudioSourceAcquisitionWorkerResult> {
    try {
      const archive = await request.fetcher.fetchArchive({
        repositoryUrl: request.repositoryUrl,
        ...(request.ref === undefined ? {} : { ref: request.ref }),
        maxBytes: request.options.maxArchiveBytes,
      });
      return await this.executeArchiveFromRunningJob({
        scope: request.scope,
        jobId: request.jobId,
        fileName: archive.fileName,
        bytes: archive.bytes,
        options: request.options,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const job = await this.updateRequired(request.scope, request.jobId, {
        status: 'failed',
        completedAt: this.clock(),
        errorMessage: message,
      });
      return { job, files: [] };
    }
  }

  private async executeArchiveFromRunningJob(request: {
    readonly scope: StudioWorkflowStoreScope;
    readonly jobId: string;
    readonly fileName: string;
    readonly bytes: Uint8Array;
    readonly options: Required<StudioSourceAcquisitionWorkerOptions>;
  }): Promise<StudioSourceAcquisitionWorkerResult> {
    try {
      const files = materializeArchive(request.fileName, request.bytes, request.options);
      const totalBytes = files.reduce((sum, file) => sum + file.bytes.byteLength, 0);
      const writeResult = await this.writer.writeFiles({
        scope: request.scope,
        jobId: request.jobId,
        files,
      });
      const job = await this.updateRequired(request.scope, request.jobId, {
        status: 'complete',
        completedAt: this.clock(),
        totalBytes,
        fileCount: files.length,
        localWorkspacePath: writeResult.localWorkspacePath,
      });
      return { job, files };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const job = await this.updateRequired(request.scope, request.jobId, {
        status: 'failed',
        completedAt: this.clock(),
        errorMessage: message,
      });
      return { job, files: [] };
    }
  }

  private async markRunning(scope: StudioWorkflowStoreScope, jobId: string): Promise<void> {
    await this.updateRequired(scope, jobId, {
      status: 'running',
      startedAt: this.clock(),
    });
  }

  private async updateRequired(
    scope: StudioWorkflowStoreScope,
    jobId: string,
    patch: Parameters<StudioSourceAcquisitionJobStore['updateJob']>[2],
  ): Promise<StudioSourceAcquisitionJob> {
    const job = await this.jobStore.updateJob(scope, jobId, patch);
    if (job === null) {
      throw new Error(`Studio source acquisition job not found: ${jobId}`);
    }
    return job;
  }
}

export class StudioSourceAcquisitionQueueRunner {
  constructor(
    private readonly queueStore: StudioSourceAcquisitionQueueStore,
    private readonly sourceWorker: StudioSourceAcquisitionWorker,
    private readonly clock: () => string = () => new Date().toISOString(),
  ) {}

  async runNext(options: StudioSourceAcquisitionQueueRunnerOptions): Promise<StudioSourceAcquisitionWorkerResult | null> {
    const now = this.clock();
    const leaseExpiresAt = new Date(Date.parse(now) + (options.leaseMs ?? 5 * 60 * 1000)).toISOString();
    const job = await this.queueStore.claimNextPendingJob({
      workerId: options.workerId,
      now,
      leaseExpiresAt,
      ...(options.maxAttempts === undefined ? {} : { maxAttempts: options.maxAttempts }),
    });
    if (job === null) {
      return null;
    }
    if (job.descriptor.kind === 'archive') {
      return await this.sourceWorker.executeRunningStoredArchive({
        scope: job.scope,
        jobId: job.jobId,
        payloadStore: options.archivePayloadStore,
        ...(options.workerOptions === undefined ? {} : { options: options.workerOptions }),
      });
    }
    return await this.sourceWorker.executeRunningRepository({
      scope: job.scope,
      jobId: job.jobId,
      repositoryUrl: job.descriptor.uri,
      ...(job.descriptor.ref === undefined ? {} : { ref: job.descriptor.ref }),
      fetcher: options.repositoryFetcher,
      ...(options.workerOptions === undefined ? {} : { options: options.workerOptions }),
    });
  }
}

export class HttpStudioRepositoryArchiveFetcher implements StudioRepositoryArchiveFetcher {
  constructor(private readonly options: HttpStudioRepositoryArchiveFetcherOptions = {}) {}

  async fetchArchive(request: {
    readonly repositoryUrl: string;
    readonly ref?: string;
    readonly maxBytes: number;
  }): Promise<{ readonly fileName: string; readonly bytes: Uint8Array }> {
    const parsed = parseRepositoryArchiveRequest(request.repositoryUrl, request.ref);
    const token = await this.options.tokenProvider?.getToken({
      provider: parsed.provider,
      repositoryUrl: request.repositoryUrl,
    }) ?? null;
    const response = await (this.options.fetchFn ?? fetch)(this.archiveUrl(parsed), {
      method: 'GET',
      headers: {
        Accept: 'application/octet-stream',
        ...(token === null ? {} : { Authorization: `Bearer ${token}` }),
      },
    });

    if (!response.ok) {
      throw new Error(`${parsed.provider} archive fetch failed (${response.status})`);
    }

    const contentLength = response.headers.get('content-length');
    if (contentLength !== null && Number.parseInt(contentLength, 10) > request.maxBytes) {
      throw new Error(`Repository archive exceeds maximum size (${request.maxBytes} bytes)`);
    }

    const buffer = await response.arrayBuffer();
    if (buffer.byteLength > request.maxBytes) {
      throw new Error(`Repository archive exceeds maximum size (${request.maxBytes} bytes)`);
    }

    return {
      fileName: `${parsed.owner}-${parsed.repo}-${parsed.ref}.tar.gz`,
      bytes: new Uint8Array(buffer),
    };
  }

  private archiveUrl(parsed: ParsedRepositoryArchiveRequest): string {
    if (parsed.provider === 'github') {
      const apiUrl = this.options.githubApiUrl ?? 'https://api.github.com';
      return `${apiUrl}/repos/${encodeURIComponent(parsed.owner)}/${encodeURIComponent(parsed.repo)}/tarball/${encodeURIComponent(parsed.ref)}`;
    }
    const apiUrl = this.options.gitlabApiUrl ?? 'https://gitlab.com/api/v4';
    const projectPath = encodeURIComponent(`${parsed.owner}/${parsed.repo}`);
    return `${apiUrl}/projects/${projectPath}/repository/archive.tar.gz?sha=${encodeURIComponent(parsed.ref)}`;
  }
}

export class InMemoryStudioSourceWorkspaceWriter implements StudioSourceWorkspaceWriter {
  readonly filesByJob = new Map<string, readonly StudioMaterializedSourceFile[]>();

  async writeFiles(request: {
    readonly scope: StudioWorkflowStoreScope;
    readonly jobId: string;
    readonly files: readonly StudioMaterializedSourceFile[];
  }): Promise<{ readonly localWorkspacePath: string }> {
    this.filesByJob.set(request.jobId, request.files);
    return {
      localWorkspacePath: `.kernel/source-acquisition/${request.scope.tenantId}/${request.scope.workspaceId}/${request.scope.projectId}/${request.jobId}`,
    };
  }
}

export class FileSystemStudioSourceWorkspaceWriter implements StudioSourceWorkspaceWriter {
  constructor(private readonly rootDirectory: string) {}

  async writeFiles(request: {
    readonly scope: StudioWorkflowStoreScope;
    readonly jobId: string;
    readonly files: readonly StudioMaterializedSourceFile[];
  }): Promise<{ readonly localWorkspacePath: string }> {
    const workspaceRoot = scopedJobDirectory(this.rootDirectory, request.scope, request.jobId);
    await mkdir(workspaceRoot, { recursive: true });
    for (const file of request.files) {
      const targetPath = containedPath(workspaceRoot, file.relativePath);
      await mkdir(dirname(targetPath), { recursive: true });
      await writeFile(targetPath, file.bytes);
    }
    return { localWorkspacePath: workspaceRoot };
  }
}

export class FileSystemStudioSourceAcquisitionJobStore implements StudioSourceAcquisitionQueueStore {
  constructor(private readonly rootDirectory: string) {}

  async putJob(job: StudioSourceAcquisitionJob): Promise<StudioSourceAcquisitionJob> {
    await this.writeJob(job);
    return job;
  }

  async getJob(scope: StudioWorkflowStoreScope, jobId: string): Promise<StudioSourceAcquisitionJob | null> {
    try {
      const content = await readFile(this.jobPath(scope, jobId), 'utf8');
      return JSON.parse(content) as StudioSourceAcquisitionJob;
    } catch (error) {
      if (isFileNotFound(error)) {
        return null;
      }
      throw error;
    }
  }

  async updateJob(
    scope: StudioWorkflowStoreScope,
    jobId: string,
    patch: Parameters<StudioSourceAcquisitionJobStore['updateJob']>[2],
  ): Promise<StudioSourceAcquisitionJob | null> {
    const existing = await this.getJob(scope, jobId);
    if (existing === null) {
      return null;
    }
    assertWorkerAllowedAcquisitionJobTransition(existing.status, patch.status);
    const updated: StudioSourceAcquisitionJob = {
      ...existing,
      status: patch.status,
      ...(patch.startedAt === undefined ? {} : { startedAt: patch.startedAt }),
      ...(patch.completedAt === undefined ? {} : { completedAt: patch.completedAt }),
      ...(patch.totalBytes === undefined ? {} : { totalBytes: patch.totalBytes }),
      ...(patch.fileCount === undefined ? {} : { fileCount: patch.fileCount }),
      ...(patch.localWorkspacePath === undefined ? {} : { localWorkspacePath: patch.localWorkspacePath }),
      ...(patch.errorMessage === undefined ? {} : { errorMessage: patch.errorMessage }),
    };
    await this.writeJob(updated);
    return updated;
  }

  async claimNextPendingJob(claim: StudioSourceAcquisitionQueueClaim): Promise<StudioSourceAcquisitionJob | null> {
    const jobs = await this.readAllJobs();
    const claimableJobs = jobs
      .filter((job) => job.status === 'pending' || isExpiredRunningJob(job, claim.now))
      .sort((left, right) => left.createdAt.localeCompare(right.createdAt));
    for (const job of claimableJobs) {
      const attemptCount = (job.attemptCount ?? 0) + 1;
      if (attemptCount > (claim.maxAttempts ?? 3)) {
        await this.writeJob({
          ...job,
          status: 'failed',
          completedAt: claim.now,
          errorMessage: `Studio source acquisition exceeded maximum retry attempts (${claim.maxAttempts ?? 3})`,
        });
        continue;
      }
      const claimed: StudioSourceAcquisitionJob = {
        ...job,
        status: 'running',
        startedAt: claim.now,
        leasedBy: claim.workerId,
        leaseExpiresAt: claim.leaseExpiresAt,
        attemptCount,
      };
      await this.writeJob(claimed);
      return claimed;
    }
    return null;
  }

  async getQueueSnapshot(now: string): Promise<StudioSourceAcquisitionQueueSnapshot> {
    const jobs = await this.readAllJobs();
    const pendingJobs = jobs.filter((job) => job.status === 'pending');
    const expiredRunningJobs = jobs.filter((job) => isExpiredRunningJob(job, now));
    const oldestPendingCreatedAt = pendingJobs
      .map((job) => job.createdAt)
      .sort()[0];
    const oldestExpiredLeaseAt = expiredRunningJobs
      .map((job) => job.leaseExpiresAt)
      .filter((leaseExpiresAt): leaseExpiresAt is string => leaseExpiresAt !== undefined)
      .sort()[0];
    return {
      total: jobs.length,
      pending: pendingJobs.length,
      running: jobs.filter((job) => job.status === 'running').length,
      expiredRunning: expiredRunningJobs.length,
      complete: jobs.filter((job) => job.status === 'complete').length,
      failed: jobs.filter((job) => job.status === 'failed').length,
      cancelled: jobs.filter((job) => job.status === 'cancelled').length,
      ...(oldestPendingCreatedAt === undefined ? {} : { oldestPendingCreatedAt }),
      ...(oldestExpiredLeaseAt === undefined ? {} : { oldestExpiredLeaseAt }),
    };
  }

  private async readAllJobs(): Promise<readonly StudioSourceAcquisitionJob[]> {
    const paths = await findJobFiles(this.rootDirectory);
    const jobs: StudioSourceAcquisitionJob[] = [];
    for (const path of paths) {
      const content = await readFile(path, 'utf8');
      jobs.push(JSON.parse(content) as StudioSourceAcquisitionJob);
    }
    return jobs;
  }

  private async writeJob(job: StudioSourceAcquisitionJob): Promise<void> {
    const targetPath = this.jobPath(job.scope, job.jobId);
    await mkdir(dirname(targetPath), { recursive: true });
    const tempPath = `${targetPath}.${process.pid}.${Date.now()}.tmp`;
    await writeFile(tempPath, `${JSON.stringify(job, null, 2)}\n`, 'utf8');
    await rename(tempPath, targetPath);
  }

  private jobPath(scope: StudioWorkflowStoreScope, jobId: string): string {
    return join(scopedJobDirectory(this.rootDirectory, scope, jobId), 'job.json');
  }
}

export class FileSystemStudioSourceAcquisitionPayloadStore implements StudioSourceAcquisitionPayloadStore {
  constructor(private readonly rootDirectory: string) {}

  async putArchivePayload(payload: StudioSourceAcquisitionArchivePayload): Promise<void> {
    const targetPath = this.payloadPath(payload.scope, payload.jobId);
    await mkdir(dirname(targetPath), { recursive: true });
    const tempPath = `${targetPath}.${process.pid}.${Date.now()}.tmp`;
    await writeFile(tempPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
    await rename(tempPath, targetPath);
  }

  async getArchivePayload(scope: StudioWorkflowStoreScope, jobId: string): Promise<StudioSourceAcquisitionArchivePayload | null> {
    try {
      const content = await readFile(this.payloadPath(scope, jobId), 'utf8');
      return JSON.parse(content) as StudioSourceAcquisitionArchivePayload;
    } catch (error) {
      if (isFileNotFound(error)) {
        return null;
      }
      throw error;
    }
  }

  async deleteArchivePayload(scope: StudioWorkflowStoreScope, jobId: string): Promise<void> {
    await rm(this.payloadPath(scope, jobId), { force: true });
  }

  private payloadPath(scope: StudioWorkflowStoreScope, jobId: string): string {
    return join(scopedJobDirectory(this.rootDirectory, scope, jobId), 'archive-payload.json');
  }
}

function assertWorkerAllowedAcquisitionJobTransition(
  currentStatus: StudioSourceAcquisitionJob['status'],
  nextStatus: Parameters<StudioSourceAcquisitionJobStore['updateJob']>[2]['status'],
): void {
  const allowed: Record<StudioSourceAcquisitionJob['status'], readonly Parameters<StudioSourceAcquisitionJobStore['updateJob']>[2]['status'][]> = {
    pending: ['running', 'failed', 'cancelled'],
    running: ['complete', 'failed', 'cancelled'],
    complete: [],
    failed: [],
    cancelled: [],
  };
  if (!allowed[currentStatus].includes(nextStatus)) {
    throw new Error(`Cannot transition Studio source acquisition job from ${currentStatus} to ${nextStatus}`);
  }
}

function resolveOptions(options: StudioSourceAcquisitionWorkerOptions | undefined): Required<StudioSourceAcquisitionWorkerOptions> {
  return {
    ...DEFAULT_OPTIONS,
    ...(options ?? {}),
    allowedExtensions: options?.allowedExtensions ?? DEFAULT_OPTIONS.allowedExtensions,
  };
}

function sameScope(left: StudioWorkflowStoreScope, right: StudioWorkflowStoreScope): boolean {
  return left.tenantId === right.tenantId
    && left.workspaceId === right.workspaceId
    && left.projectId === right.projectId;
}

function isExpiredRunningJob(job: StudioSourceAcquisitionJob, now: string): boolean {
  return job.status === 'running'
    && job.leaseExpiresAt !== undefined
    && Date.parse(job.leaseExpiresAt) <= Date.parse(now);
}

interface ParsedRepositoryArchiveRequest {
  readonly provider: 'github' | 'gitlab';
  readonly owner: string;
  readonly repo: string;
  readonly ref: string;
}

function parseRepositoryArchiveRequest(repositoryUrl: string, ref: string | undefined): ParsedRepositoryArchiveRequest {
  let url: URL;
  try {
    url = new URL(repositoryUrl);
  } catch {
    throw new Error('Repository archive fetch requires a valid HTTPS URL');
  }
  if (url.protocol !== 'https:' || url.username !== '' || url.password !== '') {
    throw new Error('Repository archive fetch requires an HTTPS URL without embedded credentials');
  }
  const provider = url.hostname.toLowerCase() === 'github.com'
    ? 'github'
    : url.hostname.toLowerCase() === 'gitlab.com'
      ? 'gitlab'
      : null;
  if (provider === null) {
    throw new Error('Repository archive fetch only supports github.com and gitlab.com');
  }
  const [owner, rawRepo] = url.pathname.split('/').filter(Boolean);
  if (owner === undefined || rawRepo === undefined) {
    throw new Error('Repository archive fetch requires owner/project path segments');
  }
  const repo = rawRepo.replace(/\.git$/i, '');
  return {
    provider,
    owner,
    repo,
    ref: ref ?? 'main',
  };
}

function scopedJobDirectory(rootDirectory: string, scope: StudioWorkflowStoreScope, jobId: string): string {
  const root = resolve(rootDirectory);
  return containedPath(
    root,
    join(
      safePathSegment(scope.tenantId),
      safePathSegment(scope.workspaceId),
      safePathSegment(scope.projectId),
      safePathSegment(jobId),
    ),
  );
}

async function findJobFiles(rootDirectory: string): Promise<readonly string[]> {
  const root = resolve(rootDirectory);
  const files: string[] = [];
  await walk(root);
  return files;

  async function walk(directory: string): Promise<void> {
    let entries: import('node:fs').Dirent[];
    try {
      entries = await readdir(directory, { withFileTypes: true });
    } catch (error) {
      if (isFileNotFound(error)) {
        return;
      }
      throw error;
    }
    for (const entry of entries) {
      const entryPath = containedPath(root, relative(root, join(directory, entry.name)));
      if (entry.isDirectory()) {
        await walk(entryPath);
      } else if (entry.isFile() && entry.name === 'job.json') {
        files.push(entryPath);
      }
    }
  }
}

function containedPath(rootDirectory: string, relativePath: string): string {
  const root = resolve(rootDirectory);
  const target = resolve(root, relativePath);
  const relativePathFromRoot = relative(root, target);
  if (
    relativePathFromRoot === '' ||
    relativePathFromRoot.startsWith('..') ||
    relativePathFromRoot.includes(`..${sep()}`)
  ) {
    throw new Error(`Resolved path escapes source acquisition workspace: ${relativePath}`);
  }
  return target;
}

function safePathSegment(value: string): string {
  return encodeURIComponent(value).replace(/[!'().*]/g, (character) => `%${character.charCodeAt(0).toString(16).toUpperCase()}`);
}

function sep(): string {
  return process.platform === 'win32' ? '\\' : '/';
}

function isFileNotFound(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'code' in error && (error as { readonly code?: unknown }).code === 'ENOENT';
}

function materializeArchive(
  fileName: string,
  bytes: Uint8Array,
  options: Required<StudioSourceAcquisitionWorkerOptions>,
): readonly StudioMaterializedSourceFile[] {
  if (bytes.byteLength > options.maxArchiveBytes) {
    throw new Error(`Archive exceeds maximum size (${options.maxArchiveBytes} bytes)`);
  }
  const normalizedName = fileName.toLowerCase();
  if (isZip(bytes)) {
    return unpackZip(bytes, options);
  }
  if (normalizedName.endsWith('.tar.gz') || normalizedName.endsWith('.tgz') || isGzip(bytes)) {
    const decompressed = gunzipSync(bytes);
    if (decompressed.byteLength > options.maxTotalUncompressedBytes) {
      throw new Error(`Archive uncompressed size exceeds maximum (${options.maxTotalUncompressedBytes} bytes)`);
    }
    return unpackTar(decompressed, options);
  }
  if (normalizedName.endsWith('.tar') || isTar(bytes)) {
    return unpackTar(bytes, options);
  }
  throw new Error('Unsupported archive format (supported: ZIP, TAR, TAR.GZ)');
}

function unpackZip(
  bytes: Uint8Array,
  options: Required<StudioSourceAcquisitionWorkerOptions>,
): readonly StudioMaterializedSourceFile[] {
  const files: StudioMaterializedSourceFile[] = [];
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let offset = 0;
  let entryCount = 0;
  let totalUncompressedBytes = 0;

  while (offset + 30 <= view.byteLength) {
    const signature = view.getUint32(offset, true);
    if (signature !== 0x04034b50) break;

    const flags = view.getUint16(offset + 6, true);
    const compressionMethod = view.getUint16(offset + 8, true);
    const compressedSize = view.getUint32(offset + 18, true);
    const uncompressedSize = view.getUint32(offset + 22, true);
    const fileNameLength = view.getUint16(offset + 26, true);
    const extraFieldLength = view.getUint16(offset + 28, true);
    if ((flags & 0x0008) !== 0) {
      throw new Error('ZIP archives with data descriptors are not supported');
    }

    const fileNameStart = offset + 30;
    const fileDataStart = fileNameStart + fileNameLength + extraFieldLength;
    const fileDataEnd = fileDataStart + compressedSize;
    if (fileDataEnd > bytes.byteLength) {
      throw new Error('Invalid ZIP archive: entry exceeds archive size');
    }

    const relativePath = normalizeSourceEntryPath(new TextDecoder().decode(bytes.slice(fileNameStart, fileNameStart + fileNameLength)));
    if (!relativePath.endsWith('/')) {
      entryCount += 1;
      assertArchiveLimits(relativePath, uncompressedSize, entryCount, totalUncompressedBytes + uncompressedSize, options);
      totalUncompressedBytes += uncompressedSize;
      const compressed = bytes.slice(fileDataStart, fileDataEnd);
      const fileBytes = compressionMethod === 0
        ? compressed
        : compressionMethod === 8
          ? inflateRawSync(compressed)
          : undefined;
      if (fileBytes === undefined) {
        throw new Error(`Unsupported ZIP compression method: ${compressionMethod}`);
      }
      if (shouldIncludeFile(relativePath, options)) {
        assertTextLike(relativePath, fileBytes);
        files.push({ relativePath, bytes: fileBytes });
      }
    }

    offset = fileDataEnd;
  }

  return files;
}

function unpackTar(
  bytes: Uint8Array,
  options: Required<StudioSourceAcquisitionWorkerOptions>,
): readonly StudioMaterializedSourceFile[] {
  const files: StudioMaterializedSourceFile[] = [];
  let offset = 0;
  let entryCount = 0;
  let totalUncompressedBytes = 0;

  while (offset + 512 <= bytes.byteLength) {
    const header = bytes.slice(offset, offset + 512);
    if (header.every((byte) => byte === 0)) break;

    const name = readTarString(header, 0, 100);
    const prefix = readTarString(header, 345, 155);
    const typeFlag = readTarString(header, 156, 1);
    const sizeOctal = readTarString(header, 124, 12);
    const fileSize = Number.parseInt(sizeOctal.trim() || '0', 8);
    const relativePath = normalizeSourceEntryPath(prefix.length > 0 ? `${prefix}/${name}` : name);
    const dataStart = offset + 512;
    const dataEnd = dataStart + fileSize;
    if (dataEnd > bytes.byteLength) {
      throw new Error('Invalid TAR archive: entry exceeds archive size');
    }

    const isRegularFile = typeFlag === '' || typeFlag === '0';
    if (isRegularFile && fileSize > 0 && !relativePath.endsWith('/')) {
      entryCount += 1;
      totalUncompressedBytes += fileSize;
      assertArchiveLimits(relativePath, fileSize, entryCount, totalUncompressedBytes, options);
      if (shouldIncludeFile(relativePath, options)) {
        const fileBytes = bytes.slice(dataStart, dataEnd);
        assertTextLike(relativePath, fileBytes);
        files.push({ relativePath, bytes: fileBytes });
      }
    }

    offset = dataStart + Math.ceil(fileSize / 512) * 512;
  }

  return files;
}

function assertArchiveLimits(
  relativePath: string,
  fileBytes: number,
  entryCount: number,
  totalUncompressedBytes: number,
  options: Required<StudioSourceAcquisitionWorkerOptions>,
): void {
  if (entryCount > options.maxEntryCount) {
    throw new Error(`Archive entry count exceeds maximum (${options.maxEntryCount})`);
  }
  if (fileBytes > options.maxFileBytes) {
    throw new Error(`Archive entry "${relativePath}" exceeds file size limit (${fileBytes} bytes)`);
  }
  if (totalUncompressedBytes > options.maxTotalUncompressedBytes) {
    throw new Error(`Archive uncompressed size exceeds maximum (${options.maxTotalUncompressedBytes} bytes)`);
  }
}

function shouldIncludeFile(relativePath: string, options: Required<StudioSourceAcquisitionWorkerOptions>): boolean {
  const hasAllowedExtension = options.allowedExtensions.some((extension) => relativePath.endsWith(extension));
  const isHidden = relativePath.split('/').some((segment) => segment.startsWith('.'));
  return hasAllowedExtension && (options.includeHidden || !isHidden);
}

function normalizeSourceEntryPath(path: string): string {
  const normalized = path.replace(/\\/g, '/').replace(/^\.\/+/, '');
  const segments = normalized.split('/');
  if (
    normalized.trim().length === 0 ||
    normalized.includes('\0') ||
    normalized.startsWith('/') ||
    /^[a-z]:\//i.test(normalized) ||
    segments.some((segment) => segment === '' || segment === '.' || segment === '..')
  ) {
    throw new Error(`Unsafe source path rejected: ${path}`);
  }
  return normalized;
}

function assertTextLike(relativePath: string, bytes: Uint8Array): void {
  if (bytes.includes(0)) {
    throw new Error(`Source file "${relativePath}" appears to be binary`);
  }
}

function readTarString(bytes: Uint8Array, start: number, length: number): string {
  return new TextDecoder().decode(bytes.slice(start, start + length)).replace(/\0.*$/, '').trim();
}

function isZip(bytes: Uint8Array): boolean {
  return bytes[0] === 0x50 && bytes[1] === 0x4b;
}

function isGzip(bytes: Uint8Array): boolean {
  return bytes[0] === 0x1f && bytes[1] === 0x8b;
}

function isTar(bytes: Uint8Array): boolean {
  return bytes.byteLength >= 512 && readTarString(bytes, 257, 5) === 'ustar';
}
