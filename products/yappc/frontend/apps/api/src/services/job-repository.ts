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
import { readFile, writeFile, mkdir } from 'fs/promises';
import { join } from 'path';
import { homedir } from 'os';

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

class FileJobRepository implements JobRepository {
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
      const files = await require('fs').promises.readdir(this.jobsDir);
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
      await require('fs').promises.unlink(join(this.jobsDir, `${id}.json`));
      return true;
    } catch {
      return false;
    }
  }

  async deleteExpired(ttlMs: number): Promise<number> {
    const now = Date.now();
    let deleted = 0;
    const files = await require('fs').promises.readdir(this.jobsDir);
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

// Singleton instance
let repositoryInstance: JobRepository | null = null;

export function getJobRepository(): JobRepository {
  if (!repositoryInstance) {
    repositoryInstance = new FileJobRepository();
  }
  return repositoryInstance;
}

export function setJobRepository(repo: JobRepository): void {
  repositoryInstance = repo;
}
