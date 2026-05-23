import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import type { KernelLifecycleService } from "../../service/KernelLifecycleService.js";
import {
  FileSystemStudioSourceAcquisitionJobStore,
  FileSystemStudioSourceAcquisitionPayloadStore,
  FileSystemStudioSourceInventoryStore,
  InMemoryStudioSourceWorkspaceWriter,
  StudioSourceAcquisitionQueueRunner,
  StudioSourceAcquisitionWorker,
} from "../../acquisition/StudioSourceAcquisitionWorker.js";
import { KernelLifecycleApiHandlers } from "../KernelLifecycleApiHandlers.js";

const scopeHeaders = {
  "X-Ghatana-Tenant-Id": "tenant-api-worker",
  "X-Ghatana-Workspace-Id": "workspace-api-worker",
  "X-Ghatana-Project-Id": "project-api-worker",
};

describe("KernelLifecycleApiHandlers source acquisition API-to-worker flow", () => {
  it("executes archive acquisition from API request through queue worker to materialized inventory", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-api-worker-acquisition-"));
    try {
      const stores = createStores(root);
      const handlers = createHandlers(stores);
      const archiveBytes = makeTar([
        {
          path: "src/App.tsx",
          bytes: new TextEncoder().encode("export const App = true;"),
        },
      ]);

      const createResponse = await handlers.createStudioArchiveSourceAcquisition({
        headers: {
          ...scopeHeaders,
          "X-Correlation-Id": "corr-archive-create",
        },
        body: {
          input: {
            kind: "archive-upload",
            file: {
              name: "source.tar",
              size: archiveBytes.byteLength,
              contentBase64: Buffer.from(archiveBytes).toString("base64"),
            },
          },
        },
      });

      expect(createResponse.statusCode).toBe(202);
      const jobId = extractJobId(createResponse.body);

      const runner = createRunner(stores);
      const workerResult = await runner.runNext({
        workerId: "worker-archive",
        archivePayloadStore: stores.payloadStore,
        repositoryFetcher: unreachableRepositoryFetcher(),
      });

      expect(workerResult?.job).toMatchObject({
        jobId,
        status: "complete",
        fileCount: 1,
      });

      const statusResponse = await handlers.getStudioSourceAcquisitionJob({
        headers: {
          ...scopeHeaders,
          "X-Correlation-Id": "corr-archive-status",
        },
        params: { jobId },
      });
      const inventoryResponse = await handlers.getStudioSourceAcquisitionInventory({
        headers: {
          ...scopeHeaders,
          "X-Correlation-Id": "corr-archive-inventory",
        },
        params: { jobId },
      });

      expect(statusResponse.statusCode).toBe(200);
      expect(statusResponse.body).toMatchObject({ jobId, status: "complete" });
      expect(inventoryResponse.statusCode).toBe(200);
      expect(inventoryResponse.body).toMatchObject({
        jobId,
        scope: {
          tenantId: "tenant-api-worker",
          workspaceId: "workspace-api-worker",
          projectId: "project-api-worker",
        },
        files: [
          {
            relativePath: "src/App.tsx",
            size: 24,
          },
        ],
      });
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  it("executes repository acquisition through the API boundary with an injected server-side fetcher", async () => {
    const root = await mkdtemp(join(tmpdir(), "ghatana-api-worker-repository-"));
    try {
      const stores = createStores(root);
      const handlers = createHandlers(stores);
      const createResponse = await handlers.createStudioRepositorySourceAcquisition({
        headers: {
          ...scopeHeaders,
          "X-Correlation-Id": "corr-repo-create",
        },
        body: {
          input: {
            kind: "github-repository",
            repositoryUrl: "https://github.com/acme/app",
            ref: "main",
          },
        },
      });

      expect(createResponse.statusCode).toBe(202);
      const jobId = extractJobId(createResponse.body);
      const repositoryArchive = makeTar([
        {
          path: "repo/src/Button.tsx",
          bytes: new TextEncoder().encode("export const Button = true;"),
        },
      ]);

      const runner = createRunner(stores);
      const workerResult = await runner.runNext({
        workerId: "worker-repository",
        archivePayloadStore: stores.payloadStore,
        repositoryFetcher: {
          fetchArchive: async (request) => {
            expect(request).toMatchObject({
              repositoryUrl: "https://github.com/acme/app",
              ref: "main",
            });
            return {
              fileName: "repo.tar",
              bytes: repositoryArchive,
            };
          },
        },
      });

      expect(workerResult?.job.status).toBe("complete");

      const inventoryResponse = await handlers.getStudioSourceAcquisitionInventory({
        headers: {
          ...scopeHeaders,
          "X-Correlation-Id": "corr-repo-inventory",
        },
        params: { jobId },
      });

      expect(inventoryResponse.statusCode).toBe(200);
      expect(inventoryResponse.body).toMatchObject({
        jobId,
        files: [
          {
            relativePath: "repo/src/Button.tsx",
            size: 27,
          },
        ],
      });
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });
});

function createStores(root: string): {
  readonly jobStore: FileSystemStudioSourceAcquisitionJobStore;
  readonly payloadStore: FileSystemStudioSourceAcquisitionPayloadStore;
  readonly inventoryStore: FileSystemStudioSourceInventoryStore;
} {
  return {
    jobStore: new FileSystemStudioSourceAcquisitionJobStore(join(root, "jobs")),
    payloadStore: new FileSystemStudioSourceAcquisitionPayloadStore(join(root, "payloads")),
    inventoryStore: new FileSystemStudioSourceInventoryStore(join(root, "inventory")),
  };
}

function createHandlers(stores: ReturnType<typeof createStores>): KernelLifecycleApiHandlers {
  return new KernelLifecycleApiHandlers({
    service: {} as KernelLifecycleService,
    requireAuthentication: false,
    studioSourceAcquisitionJobStore: stores.jobStore,
    studioSourceAcquisitionPayloadStore: stores.payloadStore,
    studioSourceInventoryStore: stores.inventoryStore,
  });
}

function createRunner(stores: ReturnType<typeof createStores>): StudioSourceAcquisitionQueueRunner {
  const writer = new InMemoryStudioSourceWorkspaceWriter(stores.inventoryStore);
  const worker = new StudioSourceAcquisitionWorker(stores.jobStore, writer);
  return new StudioSourceAcquisitionQueueRunner(stores.jobStore, worker);
}

function extractJobId(body: unknown): string {
  if (
    typeof body === "object" &&
    body !== null &&
    "acquisitionJob" in body &&
    typeof body.acquisitionJob === "object" &&
    body.acquisitionJob !== null &&
    "jobId" in body.acquisitionJob &&
    typeof body.acquisitionJob.jobId === "string"
  ) {
    return body.acquisitionJob.jobId;
  }
  throw new Error("Expected source acquisition response body to include acquisitionJob.jobId");
}

function unreachableRepositoryFetcher(): Parameters<StudioSourceAcquisitionQueueRunner["runNext"]>[0]["repositoryFetcher"] {
  return {
    fetchArchive: async () => {
      throw new Error("Repository fetcher should not be called for archive jobs");
    },
  };
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
  }[],
): Uint8Array {
  const chunks: Uint8Array[] = [];
  for (const entry of entries) {
    const header = new Uint8Array(512);
    writeTarString(header, 0, 100, entry.path);
    writeTarString(header, 100, 8, "0000644");
    writeTarString(header, 108, 8, "0000000");
    writeTarString(header, 116, 8, "0000000");
    writeTarString(header, 124, 12, entry.bytes.byteLength.toString(8).padStart(11, "0"));
    writeTarString(header, 136, 12, "00000000000");
    writeTarString(header, 156, 1, "0");
    writeTarString(header, 257, 6, "ustar");
    chunks.push(header, entry.bytes);
    const padding = (512 - (entry.bytes.byteLength % 512)) % 512;
    if (padding > 0) {
      chunks.push(new Uint8Array(padding));
    }
  }
  chunks.push(new Uint8Array(1024));
  const total = chunks.reduce((sum, chunk) => sum + chunk.byteLength, 0);
  const tar = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    tar.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return tar;
}
