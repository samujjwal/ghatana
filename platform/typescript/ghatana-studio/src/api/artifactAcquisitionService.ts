/**
 * Artifact Acquisition Service - backend job tracking and provenance for Studio artifact workflow.
 *
 * This service manages artifact acquisition jobs, tracks their status, and maintains
 * provenance information for the complete artifact lifecycle from import to deployment.
 *
 * @doc.type module
 * @doc.purpose Backend acquisition jobs and provenance tracking
 * @doc.layer platform
 * @doc.pattern Service
 */

import { z } from 'zod';

/**
 * Artifact acquisition job status.
 */
export type AcquisitionJobStatus = 
  | 'pending'
  | 'running'
  | 'succeeded'
  | 'failed'
  | 'cancelled'
  | 'timeout';

/**
 * Artifact acquisition job type.
 */
export type AcquisitionJobType =
  | 'import'
  | 'decompile'
  | 'analyze'
  | 'validate'
  | 'index';

/**
 * Artifact acquisition job priority.
 */
export type AcquisitionJobPriority = 'low' | 'medium' | 'high' | 'critical';

/**
 * Source linkage - connects an artifact to its source code origin.
 */
export interface ArtifactSourceLinkage {
  readonly gitCommit: string;
  readonly gitBranch: string;
  readonly gitRepository: string;
  readonly sourceManifestRef?: string;
  readonly committedAt: string;
  readonly author?: string;
  readonly message?: string;
}

/**
 * Deployment linkage - connects an artifact to its deployment history.
 */
export interface ArtifactDeploymentLinkage {
  readonly deploymentId: string;
  readonly environment: string;
  readonly artifactRef: string;
  readonly previousArtifactRef?: string;
  readonly status: 'pending' | 'deployed' | 'failed' | 'rolled-back';
  readonly deployedAt: string;
  readonly completedAt?: string;
  readonly deployedBy?: string;
}

/**
 * Artifact acquisition job.
 */
export interface AcquisitionJob {
  /**
   * Unique job identifier.
   */
  readonly jobId: string;

  /**
   * Job type.
   */
  readonly jobType: AcquisitionJobType;

  /**
   * Job status.
   */
  readonly status: AcquisitionJobStatus;

  /**
   * Job priority.
   */
  readonly priority: AcquisitionJobPriority;

  /**
   * Artifact reference.
   */
  readonly artifactRef: string;

  /**
   * Artifact manifest reference (if available).
   */
  readonly artifactManifestRef?: string;

  /**
   * Source reference (for import jobs).
   */
  readonly sourceRef?: string;

  /**
   * Product unit ID.
   */
  readonly productUnitId: string;

  /**
   * Phase where this artifact was produced.
   */
  readonly phase: string;

  /**
   * Job creation timestamp.
   */
  readonly createdAt: string;

  /**
   * Job started timestamp.
   */
  readonly startedAt?: string;

  /**
   * Job completed timestamp.
   */
  readonly completedAt?: string;

  /**
   * Error message (if failed).
   */
  readonly error?: string;

  /**
   * Progress percentage (0-100).
   */
  readonly progress: number;

  /**
   * Job metadata.
   */
  readonly metadata?: Record<string, unknown>;

  /**
   * Provenance information (if available).
   */
  readonly provenance?: ArtifactProvenance;
}

/**
 * Artifact provenance - tracks the complete lineage of an artifact.
 */
export interface ArtifactProvenance {
  /**
   * Source linkage.
   */
  readonly source: ArtifactSourceLinkage;

  /**
   * Build information.
   */
  readonly build: {
    readonly buildNumber: string;
    readonly buildId: string;
    readonly buildTool: string;
    readonly buildTimestamp: string;
  };

  /**
   * Deployment linkage (if deployed).
   */
  readonly deployment?: ArtifactDeploymentLinkage;

  /**
   * Acquisition job references.
   */
  readonly acquisitionJobs: readonly string[];

  /**
   * Provenance verification status.
   */
  readonly verificationStatus: 'verified' | 'unverified' | 'verification-failed';

  /**
   * Verification timestamp.
   */
  readonly verifiedAt?: string;
}

/**
 * Zod schema for acquisition job.
 */
export const AcquisitionJobSchema = z.object({
  jobId: z.string().min(1),
  jobType: z.enum(['import', 'decompile', 'analyze', 'validate', 'index']),
  status: z.enum(['pending', 'running', 'succeeded', 'failed', 'cancelled', 'timeout']),
  priority: z.enum(['low', 'medium', 'high', 'critical']),
  artifactRef: z.string().min(1),
  artifactManifestRef: z.string().min(1).optional(),
  sourceRef: z.string().min(1).optional(),
  productUnitId: z.string().min(1),
  phase: z.string().min(1),
  createdAt: z.string().datetime(),
  startedAt: z.string().datetime().optional(),
  completedAt: z.string().datetime().optional(),
  error: z.string().min(1).optional(),
  progress: z.number().int().min(0).max(100),
  metadata: z.record(z.string(), z.unknown()).optional(),
  provenance: z.object({
    source: z.object({
      gitCommit: z.string().min(1),
      gitBranch: z.string().min(1),
      gitRepository: z.string().url(),
      committedAt: z.string().datetime(),
    }),
    build: z.object({
      buildNumber: z.string().min(1),
      buildId: z.string().min(1),
      buildTool: z.string().min(1),
      buildTimestamp: z.string().datetime(),
    }),
    deployment: z.object({
      deploymentId: z.string().min(1),
      environment: z.string().min(1),
      artifactRef: z.string().min(1),
      status: z.enum(['pending', 'deployed', 'failed', 'rolled-back']),
      deployedAt: z.string().datetime(),
    }).optional(),
    trustChain: z.object({
      source: z.object({
        gitCommit: z.string().min(1),
        gitBranch: z.string().min(1),
        gitRepository: z.string().url(),
        committedAt: z.string().datetime(),
      }),
      artifactRef: z.string().min(1),
      artifactFingerprint: z.object({
        algorithm: z.enum(['sha256', 'sha512']),
        hash: z.string().min(1),
      }),
      trustState: z.enum(['unverified', 'verified', 'signed', 'attested', 'policy-compliant', 'policy-rejected']),
    }).optional(),
    acquisitionJobs: z.array(z.string().min(1)),
    verificationStatus: z.enum(['verified', 'unverified', 'verification-failed']),
    verifiedAt: z.string().datetime().optional(),
  }).optional(),
}).strict();

/**
 * Create acquisition job request.
 */
export interface CreateAcquisitionJobRequest {
  jobType: AcquisitionJobType;
  priority: AcquisitionJobPriority;
  artifactRef: string;
  artifactManifestRef?: string;
  sourceRef?: string;
  productUnitId: string;
  phase: string;
  metadata?: Record<string, unknown>;
}

/**
 * Update acquisition job request.
 */
export interface UpdateAcquisitionJobRequest {
  status: AcquisitionJobStatus;
  progress?: number;
  error?: string;
  completedAt?: string;
  provenance?: ArtifactProvenance;
}

/**
 * Artifact acquisition service - manages artifact acquisition jobs and provenance.
 */
export class ArtifactAcquisitionService {
  private jobs: Map<string, AcquisitionJob> = new Map();

  /**
   * Create a new acquisition job.
   */
  createJob(request: CreateAcquisitionJobRequest): AcquisitionJob {
    const job: AcquisitionJob = {
      jobId: `job-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      jobType: request.jobType,
      status: 'pending',
      priority: request.priority,
      artifactRef: request.artifactRef,
      artifactManifestRef: request.artifactManifestRef,
      sourceRef: request.sourceRef,
      productUnitId: request.productUnitId,
      phase: request.phase,
      createdAt: new Date().toISOString(),
      progress: 0,
      metadata: request.metadata,
    };

    this.jobs.set(job.jobId, job);
    return job;
  }

  /**
   * Get an acquisition job by ID.
   */
  getJob(jobId: string): AcquisitionJob | undefined {
    return this.jobs.get(jobId);
  }

  /**
   * Get all jobs for a specific artifact.
   */
  getJobsForArtifact(artifactRef: string): AcquisitionJob[] {
    return Array.from(this.jobs.values()).filter(
      (job) => job.artifactRef === artifactRef,
    );
  }

  /**
   * Get all jobs for a specific product unit.
   */
  getJobsForProductUnit(productUnitId: string): AcquisitionJob[] {
    return Array.from(this.jobs.values()).filter(
      (job) => job.productUnitId === productUnitId,
    );
  }

  /**
   * Get pending jobs by priority.
   */
  getPendingJobs(priority?: AcquisitionJobPriority): AcquisitionJob[] {
    let jobs = Array.from(this.jobs.values()).filter((job) => job.status === 'pending');
    
    if (priority) {
      jobs = jobs.filter((job) => job.priority === priority);
    }

    // Sort by priority (critical > high > medium > low) and creation time
    const priorityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
    return jobs.sort((a, b) => {
      const priorityDiff = priorityOrder[a.priority] - priorityOrder[b.priority];
      if (priorityDiff !== 0) return priorityDiff;
      return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
    });
  }

  /**
   * Update an acquisition job.
   */
  updateJob(jobId: string, update: UpdateAcquisitionJobRequest): AcquisitionJob | undefined {
    const job = this.jobs.get(jobId);
    if (!job) {
      return undefined;
    }

    const updatedJob: AcquisitionJob = {
      ...job,
      status: update.status,
      progress: update.progress ?? job.progress,
      error: update.error,
      completedAt: update.completedAt,
      provenance: update.provenance,
      ...(job.status === 'pending' && update.status === 'running' ? { startedAt: new Date().toISOString() } : {}),
    };

    this.jobs.set(jobId, updatedJob);
    return updatedJob;
  }

  /**
   * Cancel an acquisition job.
   */
  cancelJob(jobId: string): AcquisitionJob | undefined {
    const job = this.jobs.get(jobId);
    if (!job || job.status === 'succeeded' || job.status === 'failed') {
      return undefined;
    }

    return this.updateJob(jobId, { status: 'cancelled', completedAt: new Date().toISOString() });
  }

  /**
   * Delete an acquisition job.
   */
  deleteJob(jobId: string): boolean {
    return this.jobs.delete(jobId);
  }

  /**
   * Verify artifact provenance.
   */
  verifyProvenance(provenance: ArtifactProvenance): {
    valid: boolean;
    errors: string[];
    warnings: string[];
  } {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Verify source linkage
    if (!provenance.source.gitCommit || provenance.source.gitCommit.length === 0) {
      errors.push('Git commit is required');
    }

    if (!provenance.source.gitBranch || provenance.source.gitBranch.length === 0) {
      errors.push('Git branch is required');
    }

    try {
      new URL(provenance.source.gitRepository);
    } catch {
      errors.push('Invalid git repository URL');
    }

    const committedDate = new Date(provenance.source.committedAt);
    if (isNaN(committedDate.getTime())) {
      errors.push('Invalid committedAt timestamp');
    }

    // Verify build information
    if (!provenance.build.buildNumber || provenance.build.buildNumber.length === 0) {
      errors.push('Build number is required');
    }

    if (!provenance.build.buildId || provenance.build.buildId.length === 0) {
      errors.push('Build ID is required');
    }

    // Verify deployment linkage if present
    if (provenance.deployment) {
      if (!provenance.deployment.deploymentId || provenance.deployment.deploymentId.length === 0) {
        errors.push('Deployment ID is required');
      }

      if (!provenance.deployment.environment || provenance.deployment.environment.length === 0) {
        errors.push('Environment is required');
      }

      const deployedDate = new Date(provenance.deployment.deployedAt);
      if (isNaN(deployedDate.getTime())) {
        errors.push('Invalid deployedAt timestamp');
      }
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Get provenance for an artifact.
   */
  getProvenance(artifactRef: string): ArtifactProvenance | undefined {
    const jobs = this.getJobsForArtifact(artifactRef);
    const completedJob = jobs.find((job) => job.status === 'succeeded' && job.provenance);
    return completedJob?.provenance;
  }

  /**
   * Build provenance from artifact manifest and build information.
   */
  buildProvenance(params: {
    sourceLinkage: ArtifactSourceLinkage;
    artifactRef: string;
    buildNumber: string;
    buildId: string;
    buildTool: string;
    buildTimestamp: string;
    deploymentLinkage?: ArtifactDeploymentLinkage;
    acquisitionJobIds: string[];
  }): ArtifactProvenance {
    const baseProvenance: Omit<ArtifactProvenance, 'deployment'> = {
      source: params.sourceLinkage,
      build: {
        buildNumber: params.buildNumber,
        buildId: params.buildId,
        buildTool: params.buildTool,
        buildTimestamp: params.buildTimestamp,
      },
      acquisitionJobs: params.acquisitionJobIds,
      verificationStatus: 'unverified',
    };

    return params.deploymentLinkage
      ? { ...baseProvenance, deployment: params.deploymentLinkage }
      : baseProvenance;
  }

  /**
   * Validate acquisition job.
   */
  validateJob(job: unknown): AcquisitionJob {
    return AcquisitionJobSchema.parse(job);
  }

  /**
   * Get job statistics.
   */
  getJobStats(): {
    total: number;
    pending: number;
    running: number;
    succeeded: number;
    failed: number;
    cancelled: number;
    timeout: number;
  } {
    const jobs = Array.from(this.jobs.values());
    return {
      total: jobs.length,
      pending: jobs.filter((j) => j.status === 'pending').length,
      running: jobs.filter((j) => j.status === 'running').length,
      succeeded: jobs.filter((j) => j.status === 'succeeded').length,
      failed: jobs.filter((j) => j.status === 'failed').length,
      cancelled: jobs.filter((j) => j.status === 'cancelled').length,
      timeout: jobs.filter((j) => j.status === 'timeout').length,
    };
  }

  /**
   * Clear all jobs (for testing purposes).
   */
  clearAllJobs(): void {
    this.jobs.clear();
  }
}

/**
 * Singleton instance of the artifact acquisition service.
 */
export const artifactAcquisitionService = new ArtifactAcquisitionService();
