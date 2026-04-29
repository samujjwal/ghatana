/**
 * Prisma Adapter for Provenance Repository
 *
 * Implements ProvenanceRepository using Prisma for persistence.
 *
 * @doc.type class
 * @doc.purpose Persist provenance nodes to database via Prisma
 * @doc.layer product
 * @doc.pattern Repository Adapter
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type {
  ProvenanceNode,
  ProvenanceRepository,
} from "./provenance-graph.js";

export class PrismaProvenanceRepository implements ProvenanceRepository {
  constructor(private readonly prisma: PrismaClient) {}

  async save(node: ProvenanceNode): Promise<void> {
    await this.prisma.provenanceNode.create({
      data: {
        id: node.id,
        generationRequestId: node.generationRequestId,
        tenantId: node.tenantId,
        claimRef: node.claimRef,
        kind: node.kind,
        assertionText: node.assertionText,
        model: node.source.model,
        modelVersion: node.source.modelVersion,
        promptHash: node.source.promptHash,
        sourceDocumentRef: node.source.sourceDocumentRef,
        context: node.source.context as Record<string, unknown> | null,
        generatedAt: new Date(node.generatedAt),
        schemaVersion: node.schemaVersion,
      },
    });
  }

  async findByGenerationRequest(
    generationRequestId: string,
    tenantId: string,
  ): Promise<ProvenanceNode[]> {
    const nodes = await this.prisma.provenanceNode.findMany({
      where: {
        generationRequestId,
        tenantId,
      },
      orderBy: {
        createdAt: "asc",
      },
    });

    return nodes.map(
      (node): ProvenanceNode => ({
        id: node.id,
        generationRequestId: node.generationRequestId,
        tenantId: node.tenantId,
        claimRef: node.claimRef,
        kind: node.kind as ProvenanceNode["kind"],
        assertionText: node.assertionText,
        source: {
          model: node.model,
          modelVersion: node.modelVersion,
          promptHash: node.promptHash,
          sourceDocumentRef: node.sourceDocumentRef ?? undefined,
          context: (node.context as Record<string, unknown>) ?? {},
        },
        generatedAt: node.generatedAt.toISOString(),
        schemaVersion: node.schemaVersion as ProvenanceNode["schemaVersion"],
      }),
    );
  }
}
