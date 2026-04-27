/**
 * Content Provenance Graph
 *
 * Captures every generated assertion and its source/context so that any
 * claim, example, simulation, or animation can be traced back to the
 * generation request, model, prompt version, and tenant that produced it.
 *
 * Design: lightweight in-process service that persists provenance nodes to
 * the database. Consumers call `recordAssertion` after each generation step;
 * `getProvenanceGraph` returns the full graph for a generation request.
 *
 * @doc.type class
 * @doc.purpose Captures provenance of generated content assertions for audit and quality loops.
 * @doc.layer product
 * @doc.pattern Service
 */

import type { FastifyBaseLogger } from "fastify";

// ─── Domain Types ─────────────────────────────────────────────────────────────

/** Tracks which kind of artifact was generated. */
export type AssertionKind =
  | "claim"
  | "example"
  | "simulation"
  | "animation"
  | "evidence_item"
  | "validation_result";

/** Source context for a generated assertion. */
export interface AssertionSource {
  /** Identifier of the gRPC or AI model used (e.g. "gpt-4o", "content-gen-v2"). */
  model: string;
  /** Version string of the model or prompt template. */
  modelVersion: string;
  /** SHA-256 or deterministic hash of the prompt used for generation. */
  promptHash: string;
  /** Optional reference to the external document/resource the model drew from. */
  sourceDocumentRef?: string | undefined;
  /** Any extra contextual key-value metadata (domain, gradeLevel, etc.). */
  context: Record<string, unknown>;
}

/** A single provenance node representing one generated assertion. */
export interface ProvenanceNode {
  /** Stable unique identifier for this provenance record. */
  id: string;
  /** The generation request that triggered this assertion. */
  generationRequestId: string;
  /** Tenant that owns this content. */
  tenantId: string;
  /** The specific artifact being traced (e.g. claim-newton-1). */
  claimRef: string;
  /** Type of assertion. */
  kind: AssertionKind;
  /** The concrete text or descriptor of the assertion. */
  assertionText: string;
  /** Source details (model, prompt hash, context). */
  source: AssertionSource;
  /** ISO timestamp of when the assertion was generated. */
  generatedAt: string;
  /** Schema version for forward compatibility. */
  schemaVersion: "1.0";
}

/** Full provenance graph for a generation request. */
export interface ProvenanceGraph {
  generationRequestId: string;
  tenantId: string;
  nodes: ProvenanceNode[];
  capturedAt: string;
}

/** Persistence port — implemented by the Prisma adapter (or an in-memory stub in tests). */
export interface ProvenanceRepository {
  save(node: ProvenanceNode): Promise<void>;
  findByGenerationRequest(
    generationRequestId: string,
    tenantId: string,
  ): Promise<ProvenanceNode[]>;
}

// ─── In-memory Repository (for testing / no-DB contexts) ──────────────────────

export class InMemoryProvenanceRepository implements ProvenanceRepository {
  private readonly _nodes: ProvenanceNode[] = [];

  async save(node: ProvenanceNode): Promise<void> {
    this._nodes.push(node);
  }

  async findByGenerationRequest(
    generationRequestId: string,
    tenantId: string,
  ): Promise<ProvenanceNode[]> {
    return this._nodes.filter(
      (n) =>
        n.generationRequestId === generationRequestId &&
        n.tenantId === tenantId,
    );
  }

  /** Test helper — direct access to all stored nodes. */
  get nodes(): ReadonlyArray<ProvenanceNode> {
    return this._nodes;
  }
}

// ─── Service ──────────────────────────────────────────────────────────────────

export interface RecordAssertionInput {
  generationRequestId: string;
  tenantId: string;
  claimRef: string;
  kind: AssertionKind;
  assertionText: string;
  source: AssertionSource;
}

export class ProvenanceGraphService {
  constructor(
    private readonly repository: ProvenanceRepository,
    private readonly logger: FastifyBaseLogger,
    private readonly idFactory: () => string = () =>
      crypto.randomUUID(),
  ) {}

  /**
   * Record a single generated assertion into the provenance graph.
   * Call this once per claim/example/simulation/animation produced during a
   * generation run.
   */
  async recordAssertion(input: RecordAssertionInput): Promise<ProvenanceNode> {
    const node: ProvenanceNode = {
      id: this.idFactory(),
      generationRequestId: input.generationRequestId,
      tenantId: input.tenantId,
      claimRef: input.claimRef,
      kind: input.kind,
      assertionText: input.assertionText,
      source: input.source,
      generatedAt: new Date().toISOString(),
      schemaVersion: "1.0",
    };

    await this.repository.save(node);

    this.logger.info(
      {
        provenanceId: node.id,
        generationRequestId: node.generationRequestId,
        tenantId: node.tenantId,
        claimRef: node.claimRef,
        kind: node.kind,
        model: node.source.model,
        promptHash: node.source.promptHash,
      },
      "Provenance node recorded",
    );

    return node;
  }

  /**
   * Retrieve the full provenance graph for a generation request.
   * Scoped to tenantId to enforce tenant isolation.
   */
  async getProvenanceGraph(
    generationRequestId: string,
    tenantId: string,
  ): Promise<ProvenanceGraph> {
    const nodes = await this.repository.findByGenerationRequest(
      generationRequestId,
      tenantId,
    );

    this.logger.info(
      {
        generationRequestId,
        tenantId,
        nodeCount: nodes.length,
      },
      "Provenance graph retrieved",
    );

    return {
      generationRequestId,
      tenantId,
      nodes,
      capturedAt: new Date().toISOString(),
    };
  }
}
