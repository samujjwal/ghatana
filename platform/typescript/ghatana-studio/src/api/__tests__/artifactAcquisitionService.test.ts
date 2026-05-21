/**
 * Tests for Artifact Acquisition Service.
 */

import { describe, it, expect, beforeEach } from "vitest";
import {
  ArtifactAcquisitionService,
  type CreateAcquisitionJobRequest,
  type UpdateAcquisitionJobRequest,
  type ArtifactSourceLinkage,
  type ArtifactDeploymentLinkage,
} from "../artifactAcquisitionService";

describe("ArtifactAcquisitionService", () => {
  let service: ArtifactAcquisitionService;

  beforeEach(() => {
    service = new ArtifactAcquisitionService();
    service.clearAllJobs();
  });

  describe("createJob", () => {
    it("should create a new acquisition job", () => {
      const request: CreateAcquisitionJobRequest = {
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      };

      const job = service.createJob(request);

      expect(job.jobId).toBeDefined();
      expect(job.jobId).toMatch(/^job-\d+-[a-z0-9]+$/);
      expect(job.jobType).toBe("import");
      expect(job.status).toBe("pending");
      expect(job.priority).toBe("high");
      expect(job.artifactRef).toBe("artifact-123");
      expect(job.productUnitId).toBe("product-unit-1");
      expect(job.phase).toBe("build");
      expect(job.progress).toBe(0);
      expect(job.createdAt).toBeDefined();
    });

    it("should create job with optional fields", () => {
      const request: CreateAcquisitionJobRequest = {
        jobType: "decompile",
        priority: "medium",
        artifactRef: "artifact-456",
        artifactManifestRef: "manifest-789",
        sourceRef: "source-123",
        productUnitId: "product-unit-2",
        phase: "package",
        metadata: { key: "value" },
      };

      const job = service.createJob(request);

      expect(job.artifactManifestRef).toBe("manifest-789");
      expect(job.sourceRef).toBe("source-123");
      expect(job.metadata).toEqual({ key: "value" });
    });
  });

  describe("getJob", () => {
    it("should retrieve a job by ID", () => {
      const request: CreateAcquisitionJobRequest = {
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      };

      const created = service.createJob(request);
      const retrieved = service.getJob(created.jobId);

      expect(retrieved).toBeDefined();
      expect(retrieved?.jobId).toBe(created.jobId);
    });

    it("should return undefined for non-existent job", () => {
      const retrieved = service.getJob("non-existent-job");
      expect(retrieved).toBeUndefined();
    });
  });

  describe("getJobsForArtifact", () => {
    it("should retrieve all jobs for a specific artifact", () => {
      service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.createJob({
        jobType: "decompile",
        priority: "medium",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.createJob({
        jobType: "analyze",
        priority: "low",
        artifactRef: "artifact-456",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const jobs = service.getJobsForArtifact("artifact-123");
      expect(jobs).toHaveLength(2);
      expect(jobs.every((job) => job.artifactRef === "artifact-123")).toBe(true);
    });
  });

  describe("getJobsForProductUnit", () => {
    it("should retrieve all jobs for a specific product unit", () => {
      service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.createJob({
        jobType: "decompile",
        priority: "medium",
        artifactRef: "artifact-456",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.createJob({
        jobType: "analyze",
        priority: "low",
        artifactRef: "artifact-789",
        productUnitId: "product-unit-2",
        phase: "build",
      });

      const jobs = service.getJobsForProductUnit("product-unit-1");
      expect(jobs).toHaveLength(2);
      expect(jobs.every((job) => job.productUnitId === "product-unit-1")).toBe(true);
    });
  });

  describe("getPendingJobs", () => {
    it("should retrieve pending jobs sorted by priority", () => {
      const criticalJob = service.createJob({
        jobType: "import",
        priority: "critical",
        artifactRef: "artifact-1",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const lowJob = service.createJob({
        jobType: "import",
        priority: "low",
        artifactRef: "artifact-2",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const highJob = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-3",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const jobs = service.getPendingJobs();
      expect(jobs).toHaveLength(3);
      expect(jobs[0].jobId).toBe(criticalJob.jobId);
      expect(jobs[1].jobId).toBe(highJob.jobId);
      expect(jobs[2].jobId).toBe(lowJob.jobId);
    });

    it("should filter by priority when specified", () => {
      service.createJob({
        jobType: "import",
        priority: "critical",
        artifactRef: "artifact-1",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-2",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const jobs = service.getPendingJobs("critical");
      expect(jobs).toHaveLength(1);
      expect(jobs[0].priority).toBe("critical");
    });
  });

  describe("updateJob", () => {
    it("should update job status and progress", () => {
      const job = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const update: UpdateAcquisitionJobRequest = {
        status: "running",
        progress: 50,
      };

      const updated = service.updateJob(job.jobId, update);

      expect(updated).toBeDefined();
      expect(updated?.status).toBe("running");
      expect(updated?.progress).toBe(50);
      expect(updated?.startedAt).toBeDefined();
    });

    it("should set completedAt when status is succeeded", () => {
      const job = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const update: UpdateAcquisitionJobRequest = {
        status: "succeeded",
        progress: 100,
        completedAt: new Date().toISOString(),
      };

      const updated = service.updateJob(job.jobId, update);

      expect(updated?.status).toBe("succeeded");
      expect(updated?.completedAt).toBeDefined();
    });

    it("should set error message when status is failed", () => {
      const job = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const update: UpdateAcquisitionJobRequest = {
        status: "failed",
        error: "Import failed due to network error",
      };

      const updated = service.updateJob(job.jobId, update);

      expect(updated?.status).toBe("failed");
      expect(updated?.error).toBe("Import failed due to network error");
    });

    it("should return undefined for non-existent job", () => {
      const updated = service.updateJob("non-existent-job", { status: "running" });
      expect(updated).toBeUndefined();
    });
  });

  describe("cancelJob", () => {
    it("should cancel a pending job", () => {
      const job = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const cancelled = service.cancelJob(job.jobId);

      expect(cancelled).toBeDefined();
      expect(cancelled?.status).toBe("cancelled");
      expect(cancelled?.completedAt).toBeDefined();
    });

    it("should not cancel a succeeded job", () => {
      const job = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.updateJob(job.jobId, { status: "succeeded" });
      const cancelled = service.cancelJob(job.jobId);

      expect(cancelled).toBeUndefined();
    });

    it("should not cancel a failed job", () => {
      const job = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.updateJob(job.jobId, { status: "failed" });
      const cancelled = service.cancelJob(job.jobId);

      expect(cancelled).toBeUndefined();
    });
  });

  describe("deleteJob", () => {
    it("should delete a job", () => {
      const job = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const deleted = service.deleteJob(job.jobId);
      const retrieved = service.getJob(job.jobId);

      expect(deleted).toBe(true);
      expect(retrieved).toBeUndefined();
    });

    it("should return false for non-existent job", () => {
      const deleted = service.deleteJob("non-existent-job");
      expect(deleted).toBe(false);
    });
  });

  describe("verifyProvenance", () => {
    it("should verify valid provenance", () => {
      const sourceLinkage: ArtifactSourceLinkage = {
        gitCommit: "abc123def4567890123456789012345678901234",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const provenance = {
        source: sourceLinkage,
        build: {
          buildNumber: "123",
          buildId: "build-456",
          buildTool: "gradle",
          buildTimestamp: "2024-01-01T00:00:00.000Z",
        },
        acquisitionJobs: ["job-1"],
        verificationStatus: "unverified" as const,
      };

      const result = service.verifyProvenance(provenance);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it("should reject provenance with missing git commit", () => {
      const sourceLinkage: ArtifactSourceLinkage = {
        gitCommit: "",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const provenance = {
        source: sourceLinkage,
        build: {
          buildNumber: "123",
          buildId: "build-456",
          buildTool: "gradle",
          buildTimestamp: "2024-01-01T00:00:00.000Z",
        },
        acquisitionJobs: ["job-1"],
        verificationStatus: "unverified" as const,
      };

      const result = service.verifyProvenance(provenance);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain("Git commit is required");
    });

    it("should reject provenance with invalid git repository URL", () => {
      const sourceLinkage: ArtifactSourceLinkage = {
        gitCommit: "abc123def4567890123456789012345678901234",
        gitBranch: "main",
        gitRepository: "not-a-url",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const provenance = {
        source: sourceLinkage,
        build: {
          buildNumber: "123",
          buildId: "build-456",
          buildTool: "gradle",
          buildTimestamp: "2024-01-01T00:00:00.000Z",
        },
        acquisitionJobs: ["job-1"],
        verificationStatus: "unverified" as const,
      };

      const result = service.verifyProvenance(provenance);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain("Invalid git repository URL");
    });

    it("should reject provenance with missing build number", () => {
      const sourceLinkage: ArtifactSourceLinkage = {
        gitCommit: "abc123def4567890123456789012345678901234",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const provenance = {
        source: sourceLinkage,
        build: {
          buildNumber: "",
          buildId: "build-456",
          buildTool: "gradle",
          buildTimestamp: "2024-01-01T00:00:00.000Z",
        },
        acquisitionJobs: ["job-1"],
        verificationStatus: "unverified" as const,
      };

      const result = service.verifyProvenance(provenance);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain("Build number is required");
    });
  });

  describe("getProvenance", () => {
    it("should retrieve provenance for an artifact", () => {
      const sourceLinkage: ArtifactSourceLinkage = {
        gitCommit: "abc123def4567890123456789012345678901234",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const job = service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-123",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const provenance = {
        source: sourceLinkage,
        build: {
          buildNumber: "123",
          buildId: "build-456",
          buildTool: "gradle",
          buildTimestamp: "2024-01-01T00:00:00.000Z",
        },
        acquisitionJobs: [job.jobId],
        verificationStatus: "unverified" as const,
      };

      service.updateJob(job.jobId, { status: "succeeded", provenance });

      const retrieved = service.getProvenance("artifact-123");
      expect(retrieved).toBeDefined();
      expect(retrieved?.source.gitCommit).toBe("abc123def4567890123456789012345678901234");
    });

    it("should return undefined for artifact without provenance", () => {
      service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-456",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const retrieved = service.getProvenance("artifact-456");
      expect(retrieved).toBeUndefined();
    });
  });

  describe("buildProvenance", () => {
    it("should build provenance from parameters", () => {
      const sourceLinkage: ArtifactSourceLinkage = {
        gitCommit: "abc123def4567890123456789012345678901234",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const provenance = service.buildProvenance({
        sourceLinkage,
        artifactRef: "artifact-123",
        buildNumber: "123",
        buildId: "build-456",
        buildTool: "gradle",
        buildTimestamp: "2024-01-01T00:00:00.000Z",
        acquisitionJobIds: ["job-1"],
      });

      expect(provenance.source.gitCommit).toBe("abc123def4567890123456789012345678901234");
      expect(provenance.build.buildNumber).toBe("123");
      expect(provenance.acquisitionJobs).toEqual(["job-1"]);
      expect(provenance.verificationStatus).toBe("unverified");
    });

    it("should build provenance with deployment linkage", () => {
      const sourceLinkage: ArtifactSourceLinkage = {
        gitCommit: "abc123def4567890123456789012345678901234",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const deploymentLinkage: ArtifactDeploymentLinkage = {
        deploymentId: "deploy-123",
        environment: "production",
        artifactRef: "artifact-123",
        status: "deployed",
        deployedAt: "2024-01-01T00:00:00.000Z",
      };

      const provenance = service.buildProvenance({
        sourceLinkage,
        artifactRef: "artifact-123",
        buildNumber: "123",
        buildId: "build-456",
        buildTool: "gradle",
        buildTimestamp: "2024-01-01T00:00:00.000Z",
        deploymentLinkage,
        acquisitionJobIds: ["job-1"],
      });

      expect(provenance.deployment).toBeDefined();
      expect(provenance.deployment?.deploymentId).toBe("deploy-123");
    });
  });

  describe("getJobStats", () => {
    it("should return job statistics", () => {
      service.createJob({
        jobType: "import",
        priority: "high",
        artifactRef: "artifact-1",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.createJob({
        jobType: "decompile",
        priority: "medium",
        artifactRef: "artifact-2",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      const job1 = service.createJob({
        jobType: "analyze",
        priority: "low",
        artifactRef: "artifact-3",
        productUnitId: "product-unit-1",
        phase: "build",
      });

      service.updateJob(job1.jobId, { status: "succeeded" });

      const stats = service.getJobStats();
      expect(stats.total).toBe(3);
      expect(stats.pending).toBe(2);
      expect(stats.succeeded).toBe(1);
      expect(stats.running).toBe(0);
      expect(stats.failed).toBe(0);
    });
  });
});
