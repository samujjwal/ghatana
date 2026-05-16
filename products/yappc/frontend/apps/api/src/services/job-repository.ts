/**
 * @fileoverview Durable job repository for source import jobs.
 *
 * Provides persistent storage for import jobs with CRUD operations.
 * Initially file-based for simplicity, designed to be replaceable with database.
 *
 * @doc.type service
 * @doc.purpose Durable job persistence for source imports
 * @doc.layer product
 * @doc.pattern Repository
 */

import { randomUUID } from 'crypto';
import { mkdir, readFile, readdir, unlink, writeFile } from 'fs/promises';
import { join } from 'path';
import { homedir } from 'os';
import { getPrismaClient, type PrismaClient } from '../database/client';

export type SourceImportType = 'tsx' | 'route' | 'storybook' | 'artifact' | 'zip' | 'github' | 'gitlab' | 'local-folder';
export type SourceImportJobStatus = 'VALIDATING' | 'FETCHING_SOURCE' | 'REVIEW_REQUIRED' | 'REJECTED' | 'FAILED';
export type SourceImportProgressStepStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';

export interface SourceImportProgressStep {
  id: string;
  label: string;
  status: SourceImportProgressStepStatus;
  percent: number;
  message?: string;
  startedAt?: string;
  completedAt?: string;
}

export interface SourceImportJob {
  id: string;
  status: SourceImportJobStatus;
  tenantId: string | null;
  workspaceId: string | null;
  projectId: string | null;
  sourceType: SourceImportType;
  source: string;
  ref?: string; // Branch, tag, or commit SHA for GitHub/GitLab
  componentName?: string;
  reason?: string;
  auditRecorded?: boolean;
  percentComplete: number;
  currentStep: string;
  steps: readonly SourceImportProgressStep[];
  createdAt: string;
  updatedAt: string;
  /** Snapshot reference from source provider */
  snapshotRef?: {
    provider: string;
    repoId: string;
    commitSha?: string;
    branch?: string;
  };
  /** Summary statistics */
  summary?: {
    totalFiles: number;
    skippedFiles: number;
    totalSize: number;
    confidence: number;
  };
  /** Residual islands detected during import */
  residualIslandIds?: string[];
  /** Skipped artifacts with reasons */
  skippedArtifacts?: Array<{
    path: string;
    reason: string;
  }>;
}

export interface JobRepository {
  create(job: Omit<SourceImportJob, 'id' | 'createdAt' | 'updatedAt'>): Promise<SourceImportJob>;
  findById(id: string): Promise<SourceImportJob | null>;
  findByProject(projectId: string): Promise<SourceImportJob[]>;
  update(id: string, updates: Partial<SourceImportJob>): Promise<SourceImportJob | null>;
  delete(id: string): Promise<boolean>;
  deleteExpired(ttlMs: number): Promise<number>;
}

interface SourceImportJobRow {
  id: string;
  job_data: SourceImportJob | string;
}

function parseStoredJob(jobData: SourceImportJob | string): SourceImportJob {
  return typeof jobData === 'string' ? JSON.parse(jobData) as SourceImportJob : jobData;
}

export class FileJobRepository implements JobRepository {
  private readonly jobsDir: string;
  private readonly cache = new Map<string, SourceImportJob>();

  constructor(jobsDir?: string) {
    this.jobsDir = jobsDir ?? join(homedir(), '.yappc', 'jobs');
  }

  async create(job: Omit<SourceImportJob, 'id' | 'createdAt' | 'updatedAt'>): Promise<SourceImportJob> {
    await mkdir(this.jobsDir, { recursive: true });
    const now = new Date().toISOString();
    const newJob: SourceImportJob = {
      ...job,
      id: `source-import-${randomUUID()}`,
      createdAt: now,
      updatedAt: now,
    };
    this.cache.set(newJob.id, newJob);
    await this.persist(newJob);
    return newJob;
  }

  async findById(id: string): Promise<SourceImportJob | null> {
    const cached = this.cache.get(id);
    if (cached) return cached;

    try {
      const data = await readFile(join(this.jobsDir, `${id}.json`), 'utf-8');
      const job = JSON.parse(data) as SourceImportJob;
      this.cache.set(id, job);
      return job;
    } catch {
      return null;
    }
  }

  async findByProject(projectId: string): Promise<SourceImportJob[]> {
    const jobs: SourceImportJob[] = [];
    try {
      const files = await readdir(this.jobsDir);
      for (const file of files) {
        if (!file.endsWith('.json')) continue;
        try {
          const data = await readFile(join(this.jobsDir, file), 'utf-8');
          const job = JSON.parse(data) as SourceImportJob;
          if (job.projectId === projectId) {
            jobs.push(job);
            this.cache.set(job.id, job);
          }
        } catch {
          // Skip corrupted files
        }
      }
    } catch {
      // Directory doesn't exist yet
    }
    return jobs.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
  }

  async update(id: string, updates: Partial<SourceImportJob>): Promise<SourceImportJob | null> {
    const existing = await this.findById(id);
    if (!existing) return null;

    const updated: SourceImportJob = {
      ...existing,
      ...updates,
      updatedAt: new Date().toISOString(),
    };
    this.cache.set(id, updated);
    await this.persist(updated);
    return updated;
  }

  async delete(id: string): Promise<boolean> {
    const exists = await this.findById(id);
    if (!exists) return false;

    this.cache.delete(id);
    try {
      await unlink(join(this.jobsDir, `${id}.json`));
      return true;
    } catch {
      return false;
    }
  }

  async deleteExpired(ttlMs: number): Promise<number> {
    const now = Date.now();
    let deleted = 0;
    const files = await readdir(this.jobsDir);
    for (const file of files) {
      if (!file.endsWith('.json')) continue;
      try {
        const data = await readFile(join(this.jobsDir, file), 'utf-8');
        const job = JSON.parse(data) as SourceImportJob;
        if (now - Date.parse(job.updatedAt) > ttlMs) {
          await this.delete(job.id);
          deleted++;
        }
      } catch {
        // Skip corrupted files
      }
    }
    return deleted;
  }

  private async persist(job: SourceImportJob): Promise<void> {
    await writeFile(join(this.jobsDir, `${job.id}.json`), JSON.stringify(job, null, 2), 'utf-8');
  }
}

export class DatabaseJobRepository implements JobRepository {
  private readonly prisma: PrismaClient;
  private ensureTablePromise: Promise<void> | null = null;

  constructor(prisma?: PrismaClient) {
    this.prisma = prisma ?? getPrismaClient();
  }

  async create(job: Omit<SourceImportJob, 'id' | 'createdAt' | 'updatedAt'>): Promise<SourceImportJob> {
    await this.ensureTable();
    const now = new Date().toISOString();
    const newJob: SourceImportJob = {
      ...job,
      id: `source-import-${randomUUID()}`,
      createdAt: now,
      updatedAt: now,
    };

    await this.upsertJob(newJob);
    return newJob;
  }

  async findById(id: string): Promise<SourceImportJob | null> {
    await this.ensureTable();
    const rows = await this.prisma.$queryRaw<SourceImportJobRow[]>`
      SELECT id, job_data
      FROM source_import_jobs
      WHERE id = ${id}
      LIMIT 1
    `;
    const row = rows[0];
    return row ? parseStoredJob(row.job_data) : null;
  }

  async findByProject(projectId: string): Promise<SourceImportJob[]> {
    await this.ensureTable();
    const rows = await this.prisma.$queryRaw<SourceImportJobRow[]>`
      SELECT id, job_data
      FROM source_import_jobs
      WHERE project_id = ${projectId}
      ORDER BY updated_at DESC
    `;
    return rows.map((row) => parseStoredJob(row.job_data));
  }

  async update(id: string, updates: Partial<SourceImportJob>): Promise<SourceImportJob | null> {
    const existing = await this.findById(id);
    if (!existing) {
      return null;
    }

    const updated: SourceImportJob = {
      ...existing,
      ...updates,
      updatedAt: new Date().toISOString(),
    };

    await this.upsertJob(updated);
    return updated;
  }

  async delete(id: string): Promise<boolean> {
    await this.ensureTable();
    const deletedRows = await this.prisma.$queryRaw<Array<{ id: string }>>`
      DELETE FROM source_import_jobs
      WHERE id = ${id}
      RETURNING id
    `;
    return deletedRows.length > 0;
  }

  async deleteExpired(ttlMs: number): Promise<number> {
    await this.ensureTable();
    const cutoff = new Date(Date.now() - ttlMs);
    const deletedRows = await this.prisma.$queryRaw<Array<{ id: string }>>`
      DELETE FROM source_import_jobs
      WHERE updated_at < ${cutoff}
      RETURNING id
    `;
    return deletedRows.length;
  }

  private async upsertJob(job: SourceImportJob): Promise<void> {
    const jobData = JSON.stringify(job);
    const createdAt = new Date(job.createdAt);
    const updatedAt = new Date(job.updatedAt);

    await this.prisma.$executeRaw`
      INSERT INTO source_import_jobs (
        id,
        tenant_id,
        workspace_id,
        project_id,
        source_type,
        source_locator,
        created_at,
        updated_at,
        job_data
      )
      VALUES (
        ${job.id},
        ${job.tenantId},
        ${job.workspaceId},
        ${job.projectId},
        ${job.sourceType},
        ${job.source},
        ${createdAt},
        ${updatedAt},
        CAST(${jobData} AS JSONB)
      )
      ON CONFLICT (id) DO UPDATE SET
        tenant_id = EXCLUDED.tenant_id,
        workspace_id = EXCLUDED.workspace_id,
        project_id = EXCLUDED.project_id,
        source_type = EXCLUDED.source_type,
        source_locator = EXCLUDED.source_locator,
        updated_at = EXCLUDED.updated_at,
        job_data = EXCLUDED.job_data
    `;
  }

  private async ensureTable(): Promise<void> {
    if (!this.ensureTablePromise) {
      this.ensureTablePromise = this.createTable();
    }
    await this.ensureTablePromise;
  }

  private async createTable(): Promise<void> {
    await this.prisma.$executeRaw`
      CREATE TABLE IF NOT EXISTS source_import_jobs (
        id TEXT PRIMARY KEY,
        tenant_id TEXT,
        workspace_id TEXT,
        project_id TEXT,
        source_type TEXT NOT NULL,
        source_locator TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        job_data JSONB NOT NULL
      )
    `;
    await this.prisma.$executeRaw`
      CREATE INDEX IF NOT EXISTS idx_source_import_jobs_project_updated
      ON source_import_jobs (project_id, updated_at DESC)
    `;
    await this.prisma.$executeRaw`
      CREATE INDEX IF NOT EXISTS idx_source_import_jobs_updated_at
      ON source_import_jobs (updated_at)
    `;
  }
}

export function shouldUseDatabaseJobRepository(): boolean {
  const backend = process.env.YAPPC_SOURCE_IMPORT_JOB_BACKEND ?? process.env.SOURCE_IMPORT_JOB_BACKEND;
  if (backend === 'file') {
    return false;
  }
  if (backend === 'database') {
    return true;
  }

  return process.env.NODE_ENV === 'production' && Boolean(process.env.DATABASE_URL);
}

// Singleton instance
let repositoryInstance: JobRepository | null = null;

export function getJobRepository(): JobRepository {
  if (!repositoryInstance) {
    repositoryInstance = shouldUseDatabaseJobRepository()
      ? new DatabaseJobRepository()
      : new FileJobRepository();
  }
  return repositoryInstance;
}

export function setJobRepository(repo: JobRepository): void {
  repositoryInstance = repo;
}
