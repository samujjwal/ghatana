/**
 * Semantic Chunk Extraction Service
 *
 * Extracts semantically meaningful text chunks from canonical content
 * assets. Chunks are persisted and flagged for embedding generation
 * by Java heavy-processing services.
 *
 * @doc.type class
 * @doc.purpose Extract semantic chunks from content assets
 * @doc.layer product
 * @doc.pattern Service
 */

import { createHash } from "crypto";
import type { PrismaClient } from "@tutorputor/core/db";
import type { SemanticIndexResult } from "@tutorputor/contracts/v1/content-studio";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Approximate max tokens per chunk (GPT-style ~4 chars/token) */
const MAX_CHUNK_TOKENS = 512;
const CHARS_PER_TOKEN = 4;
const MAX_CHUNK_CHARS = MAX_CHUNK_TOKENS * CHARS_PER_TOKEN;

// ---------------------------------------------------------------------------
// Chunk descriptor (before persistence)
// ---------------------------------------------------------------------------

interface RawChunk {
  chunkRef: string;
  source: "BLOCK" | "CLAIM" | "MANIFEST" | "METADATA";
  sourceRef: string;
  sequenceIdx: number;
  text: string;
  tokenCount: number;
  contentHash: string;
  domain?: string;
  claimRefs?: string[];
  tags?: string[];
}

// ---------------------------------------------------------------------------
// Text utilities
// ---------------------------------------------------------------------------

function estimateTokens(text: string): number {
  return Math.ceil(text.length / CHARS_PER_TOKEN);
}

function hashText(text: string): string {
  return createHash("sha256").update(text).digest("hex");
}

/**
 * Splits long text into sentence-boundary-aware chunks.
 */
function splitText(text: string, maxChars: number): string[] {
  if (text.length <= maxChars) return [text];

  const chunks: string[] = [];
  let remaining = text;

  while (remaining.length > 0) {
    if (remaining.length <= maxChars) {
      chunks.push(remaining);
      break;
    }
    // Find sentence boundary near maxChars
    let splitIdx = remaining.lastIndexOf(". ", maxChars);
    if (splitIdx === -1 || splitIdx < maxChars * 0.5) {
      splitIdx = remaining.lastIndexOf(" ", maxChars);
    }
    if (splitIdx === -1) {
      splitIdx = maxChars;
    } else {
      splitIdx += 1; // Include the space/period
    }
    chunks.push(remaining.slice(0, splitIdx).trim());
    remaining = remaining.slice(splitIdx).trim();
  }

  return chunks.filter((c) => c.length > 0);
}

/**
 * Extracts plain text from a JSON payload (recursively).
 */
function extractText(value: unknown): string {
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean")
    return String(value);
  if (Array.isArray(value))
    return value.map(extractText).filter(Boolean).join(" ");
  if (value && typeof value === "object") {
    return Object.values(value as Record<string, unknown>)
      .map(extractText)
      .filter(Boolean)
      .join(" ");
  }
  return "";
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class SemanticChunkService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Extract and persist semantic chunks for a published asset.
   * Returns indexing metrics.
   */
  async indexAsset(
    assetId: string,
    options?: { force?: boolean },
  ): Promise<SemanticIndexResult> {
    const asset = await (this.prisma as any).contentAsset.findFirst({
      where: { id: assetId },
      include: { blocks: true, artifactManifests: true },
    });

    if (!asset) {
      return {
        assetId,
        chunksCreated: 0,
        chunksUpdated: 0,
        chunksStale: 0,
        embeddingsPending: 0,
      };
    }

    const rawChunks: RawChunk[] = [];

    // 1. Metadata chunk (title + tags + searchableText)
    const metaText = [
      asset.title,
      asset.domain,
      ...(asset.tags ?? []),
      asset.searchableText ?? "",
    ]
      .filter(Boolean)
      .join(" ");

    if (metaText.trim()) {
      rawChunks.push({
        chunkRef: "meta:0",
        source: "METADATA",
        sourceRef: assetId,
        sequenceIdx: 0,
        text: metaText.trim(),
        tokenCount: estimateTokens(metaText),
        contentHash: hashText(metaText),
        domain: asset.domain,
        tags: asset.tags,
      });
    }

    // 2. Block chunks
    const blocks = (asset.blocks ?? []).sort(
      (a: any, b: any) => a.orderIndex - b.orderIndex,
    );

    for (const block of blocks) {
      const blockText = extractText(block.payload);
      if (!blockText.trim()) continue;

      const segments = splitText(blockText, MAX_CHUNK_CHARS);
      for (let i = 0; i < segments.length; i++) {
        const text = segments[i];
        if (!text) continue;
        rawChunks.push({
          chunkRef: `${block.blockRef}:${i}`,
          source: "BLOCK",
          sourceRef: block.id,
          sequenceIdx: i,
          text,
          tokenCount: estimateTokens(text),
          contentHash: hashText(text),
          domain: asset.domain,
          claimRefs: block.claimRefs ?? undefined,
        });
      }
    }

    // 3. Manifest chunks (extract key textual fields)
    const manifests = asset.artifactManifests ?? [];
    for (const manifest of manifests) {
      const manifestText = extractText(manifest.manifest);
      if (!manifestText.trim()) continue;

      const segments = splitText(manifestText, MAX_CHUNK_CHARS);
      for (let i = 0; i < segments.length; i++) {
        const text = segments[i];
        if (!text) continue;
        rawChunks.push({
          chunkRef: `manifest-${manifest.id}:${i}`,
          source: "MANIFEST",
          sourceRef: manifest.id,
          sequenceIdx: i,
          text,
          tokenCount: estimateTokens(text),
          contentHash: hashText(text),
          domain: asset.domain,
          claimRefs: manifest.claimRef ? [manifest.claimRef] : undefined,
        });
      }
    }

    // 4. Persist chunks, detect staleness
    const existingChunks = await (this.prisma as any).semanticChunk.findMany({
      where: { assetId },
    });

    const existingMap = new Map<string, any>();
    for (const ec of existingChunks) {
      existingMap.set(ec.chunkRef, ec);
    }

    let chunksCreated = 0;
    let chunksUpdated = 0;
    let chunksStale = 0;
    const processedRefs = new Set<string>();

    for (const raw of rawChunks) {
      processedRefs.add(raw.chunkRef);
      const existing = existingMap.get(raw.chunkRef);

      if (!existing) {
        // New chunk
        await (this.prisma as any).semanticChunk.create({
          data: {
            assetId,
            chunkRef: raw.chunkRef,
            source: raw.source,
            sourceRef: raw.sourceRef,
            sequenceIdx: raw.sequenceIdx,
            text: raw.text,
            tokenCount: raw.tokenCount,
            contentHash: raw.contentHash,
            embeddingStatus: "PENDING",
            domain: raw.domain,
            claimRefs: raw.claimRefs,
            tags: raw.tags,
          },
        });
        chunksCreated++;
      } else if (existing.contentHash !== raw.contentHash || options?.force) {
        // Content changed — update and mark stale
        await (this.prisma as any).semanticChunk.update({
          where: { id: existing.id },
          data: {
            text: raw.text,
            tokenCount: raw.tokenCount,
            contentHash: raw.contentHash,
            embeddingStatus: "STALE",
            domain: raw.domain,
            claimRefs: raw.claimRefs,
            tags: raw.tags,
          },
        });
        chunksUpdated++;
      }
      // else: same hash, no update needed
    }

    // Mark orphaned chunks as stale
    for (const [ref, existing] of existingMap) {
      if (!processedRefs.has(ref) && existing.embeddingStatus !== "STALE") {
        await (this.prisma as any).semanticChunk.update({
          where: { id: existing.id },
          data: { embeddingStatus: "STALE" },
        });
        chunksStale++;
      }
    }

    // Update asset semantic index status
    await (this.prisma as any).contentAsset.update({
      where: { id: assetId },
      data: { semanticIndexStatus: "INDEXED" },
    });

    const pendingCount = await (this.prisma as any).semanticChunk.count({
      where: {
        assetId,
        embeddingStatus: { in: ["PENDING", "STALE"] },
      },
    });

    return {
      assetId,
      chunksCreated,
      chunksUpdated,
      chunksStale,
      embeddingsPending: pendingCount,
    };
  }

  /**
   * Mark chunks as stale when an asset is updated.
   */
  async markAssetStale(assetId: string): Promise<void> {
    await (this.prisma as any).semanticChunk.updateMany({
      where: { assetId, embeddingStatus: "READY" },
      data: { embeddingStatus: "STALE" },
    });

    await (this.prisma as any).contentAsset.update({
      where: { id: assetId },
      data: { semanticIndexStatus: "stale" },
    });
  }

  /**
   * Get chunks that need embedding generation (for Java service dispatch).
   */
  async getPendingChunks(
    tenantId: string,
    limit = 100,
  ): Promise<
    Array<{ id: string; assetId: string; chunkRef: string; text: string }>
  > {
    return (this.prisma as any).semanticChunk.findMany({
      where: {
        asset: { tenantId },
        embeddingStatus: { in: ["PENDING", "STALE"] },
      },
      select: { id: true, assetId: true, chunkRef: true, text: true },
      take: limit,
      orderBy: { createdAt: "asc" },
    });
  }
}
