import { gzipSync } from "node:zlib";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { describe, expect, it } from "vitest";

import type {
  StudioSourceAcquisitionJob,
  StudioSourceAcquisitionJobStore,
  StudioSourceAcquisitionJobUpdate,
  StudioWorkflowStoreScope,
} from "../../api/KernelLifecycleApiHandlers.js";
import {
  FileSystemStudioSourceAcquisitionJobStore,
  FileSystemStudioSourceAcquisitionPayloadStore,
  FileSystemStudioSourceWorkspaceWriter,
  HttpStudioRepositoryArchiveFetcher,
  InMemoryStudioSourceWorkspaceWriter,
  StudioSourceAcquisitionQueueRunner,
  StudioSourceAcquisitionWorker,
} from "../StudioSourceAcquisitionWorker.js";

const scope: StudioWorkflowStoreScope = {
  tenantId: "tenant-1",
  workspaceId: "workspace-1",
  projectId: "project-1",
};

describe("StudioSourceAcquisitionWorker", () => {
  it("materializes a TAR archive and marks the job complete", async () => {
    const jobStore = new TestJobStore();
    await jobStore.putJob(createJob("job-1", "archive"));
    const writer = new InMemoryStudioSourceWorkspaceWriter();
    const worker = new StudioSourceAcquisitionWorker(
      jobStore,
      writer,
      fixedClock(),
    );

    const result = await worker.executeArchive({
      scope,
      jobId: "job-1",
      fileName: "source.tar",
      bytes: makeTar([
        {
          path: "src/App.tsx",
          bytes: new TextEncoder().encode("export const App = true;"),
        },
        {
          path: "src/.hidden.tsx",
          bytes: new TextEncoder().encode("export const Hidden = true;"),
        },
      ]),
    });

    expect(result.job).toMatchObject({
      jobId: "job-1",
      status: "complete",
      startedAt: "2026-05-21T00:00:00.000Z",
      completedAt: "2026-05-21T00:00:00.000Z",
      fileCount: 1,
      totalBytes: 24,
    });
    expect(result.files.map((file) => file.relativePath)).toEqual([
      "src/App.tsx",
    ]);
    expect(
      writer.filesByJob.get("job-1")?.map((file) => file.relativePath),
    ).toEqual(["src/App.tsx"]);
  });

  it("materializes a repository archive fetched by an injected server-side fetcher", async () => {
    const jobStore = new TestJobStore();
    await jobStore.putJob(createJob("job-2", "github"));
    const writer = new InMemoryStudioSourceWorkspaceWriter();
    const worker = new StudioSourceAcquisitionWorker(
      jobStore,
      writer,
      fixedClock(),
    );

    const result = await worker.executeRepository({
      scope,
      jobId: "job-2",
      repositoryUrl: "https://github.com/samujjwal/ghatana",
      ref: "main",
      fetcher: {
        fetchArchive: async (request) => {
          expect(request).toMatchObject({
            repositoryUrl: "https://github.com/samujjwal/ghatana",
            ref: "main",
          });
          return {
            fileName: "repo.tar.gz",
            bytes: gzipSync(
              makeTar([
                {
                  path: "repo/src/App.tsx",
                  bytes: new TextEncoder().encode("export const App = true;"),
                },
              ]),
            ),
          };
        },
      },
    });

    expect(result.job.status).toBe("complete");
    expect(result.files.map((file) => file.relativePath)).toEqual([
      "repo/src/App.tsx",
    ]);
  });

  it("marks the job failed when archive entries attempt path traversal", async () => {
    const jobStore = new TestJobStore();
    await jobStore.putJob(createJob("job-3", "archive"));
    const worker = new StudioSourceAcquisitionWorker(
      jobStore,
      new InMemoryStudioSourceWorkspaceWriter(),
      fixedClock(),
    );

    const result = await worker.executeArchive({
      scope,
      jobId: "job-3",
      fileName: "source.tar",
      bytes: makeTar([
        {
          path: "../escape.tsx",
          bytes: new TextEncoder().encode("export const Escape = true;"),
        },
      ]),
    });

    expect(result.files).toEqual([]);
    expect(result.job).toMatchObject({
      status: "failed",
      errorMessage: expect.stringContaining("Unsafe source path rejected"),
    });
  });

  it("marks the job failed when archive entries contain duplicate source paths", async () => {
    const jobStore = new TestJobStore();
    await jobStore.putJob(createJob("job-duplicate", "archive"));
    const worker = new StudioSourceAcquisitionWorker(
      jobStore,
      new InMemoryStudioSourceWorkspaceWriter(),
      fixedClock(),
    );

    const result = await worker.executeArchive({
      scope,
      jobId: "job-duplicate",
      fileName: "source.tar",
      bytes: makeTar([
        {
          path: "src/App.tsx",
          bytes: new TextEncoder().encode("export const App = true;"),
        },
        {
          path: "src/app.tsx",
          bytes: new TextEncoder().encode("export const Other = true;"),
        },
      ]),
    });

    expect(result.files).toEqual([]);
    expect(result.job).toMatchObject({
      status: "failed",
      errorMessage: expect.stringContaining("duplicate source path"),
    });
  });

  it("marks the job failed when archive entries exceed nested path depth", async () => {
    const jobStore = new TestJobStore();
    await jobStore.putJob(createJob("job-depth", "archive"));
    const worker = new StudioSourceAcquisitionWorker(
      jobStore,
      new InMemoryStudioSourceWorkspaceWriter(),
      fixedClock(),
    );

    const result = await worker.executeArchive({
      scope,
      jobId: "job-depth",
      fileName: "source.tar",
      bytes: makeTar([
        {
          path: "a/b/c/d/App.tsx",
          bytes: new TextEncoder().encode("export const App = true;"),
        },
      ]),
      options: { maxNestedPathDepth: 3 },
    });

    expect(result.files).toEqual([]);
    expect(result.job).toMatchObject({
      status: "failed",
      errorMessage: expect.stringContaining("nested path depth limit"),
    });
  });

  it("marks the job failed when TAR archives contain symlink entries", async () => {
    const jobStore = new TestJobStore();
    await jobStore.putJob(createJob("job-symlink", "archive"));
    const worker = new StudioSourceAcquisitionWorker(
      jobStore,
      new InMemoryStudioSourceWorkspaceWriter(),
      fixedClock(),
    );

    const result = await worker.executeArchive({
      scope,
      jobId: "job-symlink",
      fileName: "source.tar",
      bytes: makeTar([
        {
          path: "src/Linked.tsx",
          bytes: new TextEncoder().encode("src/App.tsx"),
          typeFlag: "2",
        },
      ]),
    });

    expect(result.files).toEqual([]);
    expect(result.job).toMatchObject({
      status: "failed",
      errorMessage: expect.stringContaining("link entry"),
    });
  });

  it("marks the job failed when archive limits are exceeded", async () => {
    const jobStore = new TestJobStore();
    await jobStore.putJob(createJob("job-4", "archive"));
    const worker = new StudioSourceAcquisitionWorker(
      jobStore,
      new InMemoryStudioSourceWorkspaceWriter(),
      fixedClock(),
    );

    const result = await worker.executeArchive({
      scope,
      jobId: "job-4",
      fileName: "source.tar",
      bytes: makeTar([
        {
          path: "src/Large.tsx",
          bytes: new TextEncoder().encode("x".repeat(32)),
        },
      ]),
      options: { maxFileBytes: 16 },
    });

    expect(result.job).toMatchObject({
      status: "failed",
      errorMessage: expect.stringContaining("exceeds file size limit"),
    });
  });
});

describe("HttpStudioRepositoryArchiveFetcher", () => {
  it("fetches GitHub tarballs with bearer token injection and size checks", async () => {
    let capturedUrl = "";
    let capturedInit: RequestInit | undefined;
    const fetcher = new HttpStudioRepositoryArchiveFetcher({
      githubApiUrl: "https://github-api.local",
      tokenProvider: {
        getToken: async (request) => {
          expect(request.provider).toBe("github");
          return "github-token";
        },
      },
      fetchFn: async (url, init) => {
        capturedUrl = String(url);
        capturedInit = init;
        return new Response(new Uint8Array([1, 2, 3]), {
          status: 200,
          headers: { "content-length": "3" },
        });
      },
    });

    const archive = await fetcher.fetchArchive({
      repositoryUrl: "https://github.com/samujjwal/ghatana.git",
      ref: "main",
      maxBytes: 10,
    });

    expect(capturedUrl).toBe(
      "https://github-api.local/repos/samujjwal/ghatana/tarball/main",
    );
    expect(capturedInit?.headers).toMatchObject({
      Authorization: "Bearer github-token",
      Accept: "application/octet-stream",
    });
    expect(archive.fileName).toBe("samujjwal-ghatana-main.tar.gz");
    expect([...archive.bytes]).toEqual([1, 2, 3]);
  });

  it("fetches GitLab archive tarballs through the project archive endpoint", async () => {
    let capturedUrl = "";
    const fetcher = new HttpStudioRepositoryArchiveFetcher({
      gitlabApiUrl: "https://gitlab-api.local/api/v4",
      fetchFn: async (url) => {
        capturedUrl = String(url);
        return new Response(new Uint8Array([4, 5, 6]), { status: 200 });
      },
    });

    const archive = await fetcher.fetchArchive({
      repositoryUrl: "https://gitlab.com/group/project",
      ref: "release",
      maxBytes: 10,
    });

    expect(capturedUrl).toBe(
      "https://gitlab-api.local/api/v4/projects/group%2Fproject/repository/archive.tar.gz?sha=release",
    );
    expect(archive.fileName).toBe("group-project-release.tar.gz");
  });

  it("rejects unsupported or credentialed repository URLs before fetching", async () => {
    const fetcher = new HttpStudioRepositoryArchiveFetcher({
      fetchFn: async () => {
        throw new Error("fetch should not be called");
      },
    });

    await expect(
      fetcher.fetchArchive({
        repositoryUrl: "https://example.com/samujjwal/ghatana",
        maxBytes: 10,
      }),
    ).rejects.toThrow(/only supports github.com and gitlab.com/);

    await expect(
      fetcher.fetchArchive({
        repositoryUrl: "https://token@github.com/samujjwal/ghatana",
        maxBytes: 10,
      }),
    ).rejects.toThrow(/without embedded credentials/);
  });

  it("fails closed for HTTP errors and oversized responses without exposing tokens", async () => {
    const failingFetcher = new HttpStudioRepositoryArchiveFetcher({
      tokenProvider: { getToken: async () => "secret-token" },
      fetchFn: async () =>
        new Response("rate limited secret-token", { status: 429 }),
    });
    await expect(
      failingFetcher.fetchArchive({
        repositoryUrl: "https://github.com/samujjwal/ghatana",
        maxBytes: 10,
      }),
    ).rejects.toThrow("github archive fetch failed (429)");

    const oversizedByHeader = new HttpStudioRepositoryArchiveFetcher({
      fetchFn: async () =>
        new Response(new Uint8Array([1]), {
          status: 200,
          headers: { "content-length": "11" },
        }),
    });
    await expect(
      oversizedByHeader.fetchArchive({
        repositoryUrl: "https://github.com/samujjwal/ghatana",
        maxBytes: 10,
      }),
    ).rejects.toThrow(/exceeds maximum size/);

    const oversizedByBody = new HttpStudioRepositoryArchiveFetcher({
      fetchFn: async () =>
        new Response(new Uint8Array([1, 2, 3]), { status: 200 }),
    });
    await expect(
      oversizedByBody.fetchArchive({
        repositoryUrl: "https://github.com/samujjwal/ghatana",
        maxBytes: 2,
      }),
    ).rejects.toThrow(/exceeds maximum size/);
  });
});

describe("file-backed Studio source acquisition adapters", () => {
  it("persists acquisition jobs and materialized files across adapter instances", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      const writer = new FileSystemStudioSourceWorkspaceWriter(
        join(root, "workspaces"),
      );
      await jobStore.putJob(
        createJob("studio-acquisition:archive:durable", "archive"),
      );
      const worker = new StudioSourceAcquisitionWorker(
        jobStore,
        writer,
        fixedClock(),
      );

      const result = await worker.executeArchive({
        scope,
        jobId: "studio-acquisition:archive:durable",
        fileName: "source.tar",
        bytes: makeTar([
          {
            path: "src/App.tsx",
            bytes: new TextEncoder().encode("export const App = true;"),
          },
        ]),
      });
      const reloadedStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      const reloadedJob = await reloadedStore.getJob(
        scope,
        "studio-acquisition:archive:durable",
      );
      const writtenFile = await readFile(
        join(result.job.localWorkspacePath ?? "", "src", "App.tsx"),
        "utf8",
      );

      expect(reloadedJob).toMatchObject({
        status: "complete",
        fileCount: 1,
      });
      expect(writtenFile).toBe("export const App = true;");
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("executes stored archive payloads from durable scoped payload storage", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      const payloadStore = new FileSystemStudioSourceAcquisitionPayloadStore(
        join(root, "payloads"),
      );
      const writer = new FileSystemStudioSourceWorkspaceWriter(
        join(root, "workspaces"),
      );
      const jobId = "studio-acquisition:archive:payload";
      const archiveBytes = makeTar([
        {
          path: "src/App.tsx",
          bytes: new TextEncoder().encode("export const App = true;"),
        },
      ]);

      await jobStore.putJob(createJob(jobId, "archive"));
      await payloadStore.putArchivePayload({
        scope,
        jobId,
        fileName: "source.tar",
        size: archiveBytes.byteLength,
        contentBase64: Buffer.from(archiveBytes).toString("base64"),
        receivedAt: "2026-05-21T00:00:00.000Z",
        contentType: "application/x-tar",
      });

      const reloadedPayloadStore =
        new FileSystemStudioSourceAcquisitionPayloadStore(
          join(root, "payloads"),
        );
      const worker = new StudioSourceAcquisitionWorker(
        jobStore,
        writer,
        fixedClock(),
      );
      const result = await worker.executeStoredArchive({
        scope,
        jobId,
        payloadStore: reloadedPayloadStore,
      });
      const writtenFile = await readFile(
        join(result.job.localWorkspacePath ?? "", "src", "App.tsx"),
        "utf8",
      );
      const deletedPayload = await reloadedPayloadStore.getArchivePayload(
        scope,
        jobId,
      );
      const crossScopePayload = await payloadStore.getArchivePayload(
        { ...scope, tenantId: "tenant-2" },
        jobId,
      );

      expect(result.job).toMatchObject({ status: "complete", fileCount: 1 });
      expect(writtenFile).toBe("export const App = true;");
      expect(deletedPayload).toBeNull();
      expect(crossScopePayload).toBeNull();
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("marks stored archive jobs failed when decoded payload size does not match metadata", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      const payloadStore = new FileSystemStudioSourceAcquisitionPayloadStore(
        join(root, "payloads"),
      );
      const worker = new StudioSourceAcquisitionWorker(
        jobStore,
        new InMemoryStudioSourceWorkspaceWriter(),
        fixedClock(),
      );
      await jobStore.putJob(createJob("payload-size-mismatch", "archive"));
      await payloadStore.putArchivePayload({
        scope,
        jobId: "payload-size-mismatch",
        fileName: "source.tar",
        size: 999,
        contentBase64: Buffer.from(
          makeTar([
            {
              path: "src/App.tsx",
              bytes: new TextEncoder().encode("export const App = true;"),
            },
          ]),
        ).toString("base64"),
        receivedAt: "2026-05-21T00:00:00.000Z",
      });

      const result = await worker.executeStoredArchive({
        scope,
        jobId: "payload-size-mismatch",
        payloadStore,
      });

      expect(result.job).toMatchObject({
        status: "failed",
        errorMessage: expect.stringContaining("payload size mismatch"),
      });
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("claims the oldest pending archive job and executes it through the queue runner", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      const payloadStore = new FileSystemStudioSourceAcquisitionPayloadStore(
        join(root, "payloads"),
      );
      const writer = new FileSystemStudioSourceWorkspaceWriter(
        join(root, "workspaces"),
      );
      const worker = new StudioSourceAcquisitionWorker(
        jobStore,
        writer,
        fixedClock(),
      );
      const runner = new StudioSourceAcquisitionQueueRunner(
        jobStore,
        worker,
        fixedClock(),
      );
      const jobId = "studio-acquisition:archive:queued";
      const archiveBytes = makeTar([
        {
          path: "src/Queued.tsx",
          bytes: new TextEncoder().encode("export const Queued = true;"),
        },
      ]);

      await jobStore.putJob(createJob(jobId, "archive"));
      await payloadStore.putArchivePayload({
        scope,
        jobId,
        fileName: "queued.tar",
        size: archiveBytes.byteLength,
        contentBase64: Buffer.from(archiveBytes).toString("base64"),
        receivedAt: "2026-05-21T00:00:00.000Z",
      });

      const result = await runner.runNext({
        workerId: "worker-1",
        leaseMs: 60_000,
        archivePayloadStore: payloadStore,
        repositoryFetcher: {
          fetchArchive: async () => {
            throw new Error("repository fetch should not be called");
          },
        },
      });
      const completedJob = await jobStore.getJob(scope, jobId);
      const writtenFile = await readFile(
        join(result?.job.localWorkspacePath ?? "", "src", "Queued.tsx"),
        "utf8",
      );

      expect(result?.job.status).toBe("complete");
      expect(completedJob).toMatchObject({
        status: "complete",
        leasedBy: "worker-1",
        attemptCount: 1,
      });
      expect(writtenFile).toBe("export const Queued = true;");
      expect(
        await runner.runNext({
          workerId: "worker-1",
          archivePayloadStore: payloadStore,
          repositoryFetcher: {
            fetchArchive: async () => {
              throw new Error("repository fetch should not be called");
            },
          },
        }),
      ).toBeNull();
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("claims queued repository jobs and executes them with the injected fetcher", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      const payloadStore = new FileSystemStudioSourceAcquisitionPayloadStore(
        join(root, "payloads"),
      );
      const writer = new FileSystemStudioSourceWorkspaceWriter(
        join(root, "workspaces"),
      );
      const worker = new StudioSourceAcquisitionWorker(
        jobStore,
        writer,
        fixedClock(),
      );
      const runner = new StudioSourceAcquisitionQueueRunner(
        jobStore,
        worker,
        fixedClock(),
      );
      const jobId = "studio-acquisition:github:queued";

      await jobStore.putJob({
        ...createJob(jobId, "github"),
        descriptor: {
          kind: "github",
          uri: "https://github.com/samujjwal/ghatana",
          label: "https://github.com/samujjwal/ghatana",
          ref: "main",
        },
      });

      const result = await runner.runNext({
        workerId: "worker-2",
        archivePayloadStore: payloadStore,
        repositoryFetcher: {
          fetchArchive: async (request) => {
            expect(request.repositoryUrl).toBe(
              "https://github.com/samujjwal/ghatana",
            );
            expect(request.ref).toBe("main");
            return {
              fileName: "repo.tar",
              bytes: makeTar([
                {
                  path: "repo/src/App.tsx",
                  bytes: new TextEncoder().encode("export const App = true;"),
                },
              ]),
            };
          },
        },
      });

      expect(result?.job).toMatchObject({
        status: "complete",
        leasedBy: "worker-2",
        attemptCount: 1,
      });
      expect(result?.files.map((file) => file.relativePath)).toEqual([
        "repo/src/App.tsx",
      ]);
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("reclaims expired running jobs with bounded retry attempts", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      const payloadStore = new FileSystemStudioSourceAcquisitionPayloadStore(
        join(root, "payloads"),
      );
      const writer = new FileSystemStudioSourceWorkspaceWriter(
        join(root, "workspaces"),
      );
      const worker = new StudioSourceAcquisitionWorker(
        jobStore,
        writer,
        fixedClock(),
      );
      const runner = new StudioSourceAcquisitionQueueRunner(
        jobStore,
        worker,
        () => "2026-05-21T00:10:00.000Z",
      );
      const jobId = "studio-acquisition:archive:expired";
      const archiveBytes = makeTar([
        {
          path: "src/Recovered.tsx",
          bytes: new TextEncoder().encode("export const Recovered = true;"),
        },
      ]);

      await jobStore.putJob({
        ...createJob(jobId, "archive"),
        status: "running",
        startedAt: "2026-05-21T00:00:00.000Z",
        leasedBy: "dead-worker",
        leaseExpiresAt: "2026-05-21T00:05:00.000Z",
        attemptCount: 1,
      });
      await payloadStore.putArchivePayload({
        scope,
        jobId,
        fileName: "expired.tar",
        size: archiveBytes.byteLength,
        contentBase64: Buffer.from(archiveBytes).toString("base64"),
        receivedAt: "2026-05-21T00:00:00.000Z",
      });

      const result = await runner.runNext({
        workerId: "worker-retry",
        leaseMs: 60_000,
        maxAttempts: 3,
        archivePayloadStore: payloadStore,
        repositoryFetcher: {
          fetchArchive: async () => {
            throw new Error("repository fetch should not be called");
          },
        },
      });

      expect(result?.job).toMatchObject({
        status: "complete",
        leasedBy: "worker-retry",
        attemptCount: 2,
      });
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("fails expired running jobs that exceed retry attempts and continues to the next claimable job", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      const payloadStore = new FileSystemStudioSourceAcquisitionPayloadStore(
        join(root, "payloads"),
      );
      const writer = new FileSystemStudioSourceWorkspaceWriter(
        join(root, "workspaces"),
      );
      const worker = new StudioSourceAcquisitionWorker(
        jobStore,
        writer,
        fixedClock(),
      );
      const runner = new StudioSourceAcquisitionQueueRunner(
        jobStore,
        worker,
        () => "2026-05-21T00:10:00.000Z",
      );
      const exhaustedJobId = "studio-acquisition:archive:exhausted";
      const nextJobId = "studio-acquisition:archive:next";
      const archiveBytes = makeTar([
        {
          path: "src/Next.tsx",
          bytes: new TextEncoder().encode("export const Next = true;"),
        },
      ]);

      await jobStore.putJob({
        ...createJob(exhaustedJobId, "archive"),
        status: "running",
        startedAt: "2026-05-21T00:00:00.000Z",
        leasedBy: "dead-worker",
        leaseExpiresAt: "2026-05-21T00:05:00.000Z",
        attemptCount: 3,
      });
      await jobStore.putJob({
        ...createJob(nextJobId, "archive"),
        createdAt: "2026-05-21T00:01:00.000Z",
      });
      await payloadStore.putArchivePayload({
        scope,
        jobId: nextJobId,
        fileName: "next.tar",
        size: archiveBytes.byteLength,
        contentBase64: Buffer.from(archiveBytes).toString("base64"),
        receivedAt: "2026-05-21T00:01:00.000Z",
      });

      const result = await runner.runNext({
        workerId: "worker-next",
        maxAttempts: 3,
        archivePayloadStore: payloadStore,
        repositoryFetcher: {
          fetchArchive: async () => {
            throw new Error("repository fetch should not be called");
          },
        },
      });
      const exhaustedJob = await jobStore.getJob(scope, exhaustedJobId);

      expect(exhaustedJob).toMatchObject({
        status: "failed",
        errorMessage: expect.stringContaining(
          "exceeded maximum retry attempts",
        ),
      });
      expect(result?.job).toMatchObject({
        jobId: nextJobId,
        status: "complete",
      });
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("reports queue health snapshots for production monitoring", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      await jobStore.putJob({
        ...createJob("studio-acquisition:archive:pending-old", "archive"),
        createdAt: "2026-05-21T00:00:00.000Z",
      });
      await jobStore.putJob({
        ...createJob("studio-acquisition:archive:pending-new", "archive"),
        createdAt: "2026-05-21T00:02:00.000Z",
      });
      await jobStore.putJob({
        ...createJob("studio-acquisition:archive:expired", "archive"),
        status: "running",
        leasedBy: "worker-expired",
        leaseExpiresAt: "2026-05-21T00:05:00.000Z",
      });
      await jobStore.putJob({
        ...createJob("studio-acquisition:archive:running", "archive"),
        status: "running",
        leasedBy: "worker-live",
        leaseExpiresAt: "2026-05-21T00:20:00.000Z",
      });
      await jobStore.putJob({
        ...createJob("studio-acquisition:archive:complete", "archive"),
        status: "complete",
        completedAt: "2026-05-21T00:03:00.000Z",
      });
      await jobStore.putJob({
        ...createJob("studio-acquisition:archive:failed", "archive"),
        status: "failed",
        completedAt: "2026-05-21T00:04:00.000Z",
        errorMessage: "boom",
      });

      const snapshot = await jobStore.getQueueSnapshot(
        "2026-05-21T00:10:00.000Z",
      );

      expect(snapshot).toEqual({
        total: 6,
        pending: 2,
        running: 2,
        expiredRunning: 1,
        complete: 1,
        failed: 1,
        cancelled: 0,
        oldestPendingCreatedAt: "2026-05-21T00:00:00.000Z",
        oldestExpiredLeaseAt: "2026-05-21T00:05:00.000Z",
      });
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("rejects workspace writes that would escape the scoped job directory", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const writer = new FileSystemStudioSourceWorkspaceWriter(
        join(root, "workspaces"),
      );

      await expect(
        writer.writeFiles({
          scope,
          jobId: "job-escape",
          files: [
            {
              relativePath: "../escape.tsx",
              bytes: new TextEncoder().encode("export const Escape = true;"),
            },
          ],
        }),
      ).rejects.toThrow(/escapes source acquisition workspace/);
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("encodes hostile scope and job path segments before writing files", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const writer = new FileSystemStudioSourceWorkspaceWriter(
        join(root, "workspaces"),
      );
      const result = await writer.writeFiles({
        scope: {
          tenantId: "..",
          workspaceId: "workspace/one",
          projectId: "project:one",
        },
        jobId: "job:one",
        files: [
          {
            relativePath: "src/App.tsx",
            bytes: new TextEncoder().encode("export const App = true;"),
          },
        ],
      });
      const writtenFile = await readFile(
        join(result.localWorkspacePath, "src", "App.tsx"),
        "utf8",
      );

      expect(result.localWorkspacePath).toContain("%2E%2E");
      expect(result.localWorkspacePath).toContain("workspace%2Fone");
      expect(result.localWorkspacePath).toContain("job%3Aone");
      expect(writtenFile).toBe("export const App = true;");
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("enforces lifecycle transitions in the file-backed job store", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-source-acquisition-"));
    try {
      const jobStore = new FileSystemStudioSourceAcquisitionJobStore(
        join(root, "jobs"),
      );
      await jobStore.putJob({
        ...createJob("job-terminal", "archive"),
        status: "complete",
        completedAt: "2026-05-21T00:00:00.000Z",
      });

      await expect(
        jobStore.updateJob(scope, "job-terminal", {
          status: "running",
          startedAt: "2026-05-21T00:01:00.000Z",
        }),
      ).rejects.toThrow(
        /Cannot transition Studio source acquisition job from complete to running/,
      );
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });
});

class TestJobStore implements StudioSourceAcquisitionJobStore {
  private readonly jobs = new Map<string, StudioSourceAcquisitionJob>();

  async putJob(
    job: StudioSourceAcquisitionJob,
  ): Promise<StudioSourceAcquisitionJob> {
    this.jobs.set(job.jobId, job);
    return job;
  }

  async getJob(
    _scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<StudioSourceAcquisitionJob | null> {
    return this.jobs.get(jobId) ?? null;
  }

  async updateJob(
    _scope: StudioWorkflowStoreScope,
    jobId: string,
    patch: StudioSourceAcquisitionJobUpdate,
  ): Promise<StudioSourceAcquisitionJob | null> {
    const existing = this.jobs.get(jobId);
    if (existing === undefined) return null;
    const updated: StudioSourceAcquisitionJob = {
      ...existing,
      status: patch.status,
      ...(patch.startedAt === undefined ? {} : { startedAt: patch.startedAt }),
      ...(patch.completedAt === undefined
        ? {}
        : { completedAt: patch.completedAt }),
      ...(patch.totalBytes === undefined
        ? {}
        : { totalBytes: patch.totalBytes }),
      ...(patch.fileCount === undefined ? {} : { fileCount: patch.fileCount }),
      ...(patch.localWorkspacePath === undefined
        ? {}
        : { localWorkspacePath: patch.localWorkspacePath }),
      ...(patch.errorMessage === undefined
        ? {}
        : { errorMessage: patch.errorMessage }),
    };
    this.jobs.set(jobId, updated);
    return updated;
  }
}

function createJob(
  jobId: string,
  kind: "github" | "gitlab" | "archive",
): StudioSourceAcquisitionJob {
  return {
    jobId,
    status: "pending",
    descriptor: {
      kind,
      uri:
        kind === "archive"
          ? "archive://source.tar"
          : "https://github.com/samujjwal/ghatana",
      label:
        kind === "archive"
          ? "source.tar"
          : "https://github.com/samujjwal/ghatana",
    },
    createdAt: "2026-05-21T00:00:00.000Z",
    scope,
  };
}

function fixedClock(): () => string {
  return () => "2026-05-21T00:00:00.000Z";
}

function writeTarString(
  target: Uint8Array,
  offset: number,
  length: number,
  value: string,
): void {
  target.set(new TextEncoder().encode(value).slice(0, length), offset);
}

function makeTar(
  entries: readonly {
    readonly path: string;
    readonly bytes: Uint8Array;
    readonly typeFlag?: string;
  }[],
): Uint8Array {
  const chunks: Uint8Array[] = [];
  for (const entry of entries) {
    const header = new Uint8Array(512);
    writeTarString(header, 0, 100, entry.path);
    writeTarString(header, 100, 8, "0000644");
    writeTarString(header, 108, 8, "0000000");
    writeTarString(header, 116, 8, "0000000");
    writeTarString(
      header,
      124,
      12,
      entry.bytes.byteLength.toString(8).padStart(11, "0"),
    );
    writeTarString(header, 136, 12, "00000000000");
    writeTarString(header, 156, 1, entry.typeFlag ?? "0");
    writeTarString(header, 257, 6, "ustar");
    chunks.push(header, entry.bytes);
    const padding = (512 - (entry.bytes.byteLength % 512)) % 512;
    if (padding > 0) chunks.push(new Uint8Array(padding));
  }
  chunks.push(new Uint8Array(1024));
  const totalLength = chunks.reduce((sum, chunk) => sum + chunk.byteLength, 0);
  const tar = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    tar.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return tar;
}
