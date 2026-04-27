/**
 * P1-1: Provenance Graph Tests
 *
 * Verifies that the ProvenanceGraphService correctly records and retrieves
 * provenance nodes, enforces tenant isolation, and logs structured data.
 */

import { beforeEach, describe, expect, it, vi } from "vitest";
import type { FastifyBaseLogger } from "fastify";

import {
  InMemoryProvenanceRepository,
  ProvenanceGraphService,
} from "../provenance-graph";
import type {
  AssertionSource,
  RecordAssertionInput,
} from "../provenance-graph";

// ─── Test Fixtures ─────────────────────────────────────────────────────────────

const makeSource = (overrides: Partial<AssertionSource> = {}): AssertionSource => ({
  model: "content-gen-v2",
  modelVersion: "2.1.0",
  promptHash: "sha256-abc123def456",
  context: { domain: "physics", gradeLevel: "grade-9" },
  ...overrides,
});

const makeInput = (overrides: Partial<RecordAssertionInput> = {}): RecordAssertionInput => ({
  generationRequestId: "req-test-1",
  tenantId: "tenant-physics",
  claimRef: "claim-newton-1",
  kind: "claim",
  assertionText: "An object in motion stays in motion unless acted upon by an external force",
  source: makeSource(),
  ...overrides,
});

const makeLogger = (): FastifyBaseLogger =>
  ({
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
    trace: vi.fn(),
    fatal: vi.fn(),
    child: vi.fn().mockReturnThis(),
    level: "info",
    silent: vi.fn(),
  }) as unknown as FastifyBaseLogger;

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("P1-1: ProvenanceGraphService", () => {
  let repository: InMemoryProvenanceRepository;
  let logger: FastifyBaseLogger;
  let service: ProvenanceGraphService;
  let idCounter: number;

  beforeEach(() => {
    repository = new InMemoryProvenanceRepository();
    logger = makeLogger();
    idCounter = 0;
    service = new ProvenanceGraphService(
      repository,
      logger,
      () => `provenance-id-${++idCounter}`,
    );
  });

  // ─── 1. Recording assertions ─────────────────────────────────────────────

  describe("1. recordAssertion", () => {
    it("returns a provenance node with generated id and ISO timestamp", async () => {
      const node = await service.recordAssertion(makeInput());

      expect(node.id).toBe("provenance-id-1");
      expect(node.generationRequestId).toBe("req-test-1");
      expect(node.tenantId).toBe("tenant-physics");
      expect(node.claimRef).toBe("claim-newton-1");
      expect(node.kind).toBe("claim");
      expect(node.assertionText).toContain("object in motion");
      expect(node.schemaVersion).toBe("1.0");
      expect(typeof node.generatedAt).toBe("string");
      expect(new Date(node.generatedAt).toISOString()).toBe(node.generatedAt);
    });

    it("persists the node in the repository", async () => {
      await service.recordAssertion(makeInput());

      expect(repository.nodes).toHaveLength(1);
      expect(repository.nodes[0]?.claimRef).toBe("claim-newton-1");
    });

    it("records source model and promptHash on the node", async () => {
      const node = await service.recordAssertion(makeInput({
        source: makeSource({ model: "gpt-4o", promptHash: "sha256-xyz789" }),
      }));

      expect(node.source.model).toBe("gpt-4o");
      expect(node.source.promptHash).toBe("sha256-xyz789");
    });

    it("logs structured info after recording", async () => {
      await service.recordAssertion(makeInput());

      expect(logger.info).toHaveBeenCalledWith(
        expect.objectContaining({
          provenanceId: "provenance-id-1",
          generationRequestId: "req-test-1",
          tenantId: "tenant-physics",
          claimRef: "claim-newton-1",
          kind: "claim",
          model: "content-gen-v2",
          promptHash: "sha256-abc123def456",
        }),
        "Provenance node recorded",
      );
    });

    it("supports all assertion kinds", async () => {
      const kinds = [
        "claim",
        "example",
        "simulation",
        "animation",
        "evidence_item",
        "validation_result",
      ] as const;

      for (const kind of kinds) {
        await service.recordAssertion(makeInput({ kind, claimRef: `ref-${kind}` }));
      }

      expect(repository.nodes).toHaveLength(kinds.length);
      const storedKinds = repository.nodes.map((n) => n.kind);
      expect(storedKinds).toEqual(expect.arrayContaining([...kinds]));
    });

    it("records sourceDocumentRef when provided", async () => {
      const node = await service.recordAssertion(makeInput({
        source: makeSource({ sourceDocumentRef: "isbn:978-0-321-97397-5" }),
      }));

      expect(node.source.sourceDocumentRef).toBe("isbn:978-0-321-97397-5");
    });

    it("multiple assertions in the same request produce distinct ids", async () => {
      const n1 = await service.recordAssertion(makeInput({ claimRef: "claim-1" }));
      const n2 = await service.recordAssertion(makeInput({ claimRef: "claim-2" }));
      const n3 = await service.recordAssertion(makeInput({ claimRef: "claim-3" }));

      expect(new Set([n1.id, n2.id, n3.id]).size).toBe(3);
    });
  });

  // ─── 2. Retrieving the provenance graph ──────────────────────────────────

  describe("2. getProvenanceGraph", () => {
    it("returns all nodes for a generation request", async () => {
      await service.recordAssertion(makeInput({ claimRef: "claim-1" }));
      await service.recordAssertion(makeInput({ claimRef: "claim-2" }));

      const graph = await service.getProvenanceGraph("req-test-1", "tenant-physics");

      expect(graph.generationRequestId).toBe("req-test-1");
      expect(graph.tenantId).toBe("tenant-physics");
      expect(graph.nodes).toHaveLength(2);
      expect(graph.nodes.map((n) => n.claimRef)).toEqual(
        expect.arrayContaining(["claim-1", "claim-2"]),
      );
    });

    it("returns an empty node list when no assertions recorded", async () => {
      const graph = await service.getProvenanceGraph("req-empty", "tenant-physics");

      expect(graph.nodes).toHaveLength(0);
      expect(graph.generationRequestId).toBe("req-empty");
    });

    it("includes capturedAt as an ISO timestamp", async () => {
      const graph = await service.getProvenanceGraph("req-test-1", "tenant-physics");

      expect(typeof graph.capturedAt).toBe("string");
      expect(new Date(graph.capturedAt).toISOString()).toBe(graph.capturedAt);
    });

    it("logs info with node count", async () => {
      await service.recordAssertion(makeInput());
      vi.clearAllMocks();

      await service.getProvenanceGraph("req-test-1", "tenant-physics");

      expect(logger.info).toHaveBeenCalledWith(
        expect.objectContaining({
          generationRequestId: "req-test-1",
          tenantId: "tenant-physics",
          nodeCount: 1,
        }),
        "Provenance graph retrieved",
      );
    });
  });

  // ─── 3. Tenant isolation ─────────────────────────────────────────────────

  describe("3. Tenant isolation", () => {
    it("does NOT return nodes from a different tenant for the same request id", async () => {
      await service.recordAssertion(makeInput({ tenantId: "tenant-A", claimRef: "claim-A" }));
      await service.recordAssertion(makeInput({ tenantId: "tenant-B", claimRef: "claim-B" }));

      const graphA = await service.getProvenanceGraph("req-test-1", "tenant-A");
      const graphB = await service.getProvenanceGraph("req-test-1", "tenant-B");

      expect(graphA.nodes).toHaveLength(1);
      expect(graphA.nodes[0]?.claimRef).toBe("claim-A");

      expect(graphB.nodes).toHaveLength(1);
      expect(graphB.nodes[0]?.claimRef).toBe("claim-B");
    });

    it("does NOT leak nodes from one request into another request's graph", async () => {
      await service.recordAssertion(makeInput({ generationRequestId: "req-1", claimRef: "claim-req1" }));
      await service.recordAssertion(makeInput({ generationRequestId: "req-2", claimRef: "claim-req2" }));

      const graph1 = await service.getProvenanceGraph("req-1", "tenant-physics");
      const graph2 = await service.getProvenanceGraph("req-2", "tenant-physics");

      expect(graph1.nodes).toHaveLength(1);
      expect(graph1.nodes[0]?.claimRef).toBe("claim-req1");

      expect(graph2.nodes).toHaveLength(1);
      expect(graph2.nodes[0]?.claimRef).toBe("claim-req2");
    });
  });

  // ─── 4. InMemoryProvenanceRepository contract ────────────────────────────

  describe("4. InMemoryProvenanceRepository", () => {
    it("save then findByGenerationRequest returns the stored node", async () => {
      const repo = new InMemoryProvenanceRepository();
      const node = {
        id: "prov-1",
        generationRequestId: "req-x",
        tenantId: "tenant-x",
        claimRef: "claim-x",
        kind: "claim" as const,
        assertionText: "test assertion",
        source: makeSource(),
        generatedAt: new Date().toISOString(),
        schemaVersion: "1.0" as const,
      };

      await repo.save(node);
      const results = await repo.findByGenerationRequest("req-x", "tenant-x");

      expect(results).toHaveLength(1);
      expect(results[0]).toStrictEqual(node);
    });

    it("nodes getter returns all stored nodes", async () => {
      const repo = new InMemoryProvenanceRepository();
      await repo.save({
        id: "p1",
        generationRequestId: "r1",
        tenantId: "t1",
        claimRef: "c1",
        kind: "claim",
        assertionText: "a",
        source: makeSource(),
        generatedAt: new Date().toISOString(),
        schemaVersion: "1.0",
      });

      expect(repo.nodes).toHaveLength(1);
    });
  });
});
