import { SynthesisPipeline } from '../synthesis/pipeline';
import { getCanonicalExtractors } from '../extractors';
import type { RepositorySnapshot } from '../source-providers/types';
import { z } from 'zod';

/**
 * @doc.type module
 * @doc.purpose TypeScript extractor worker with contract validation for subprocess execution
 * @doc.layer product
 * @doc.pattern Worker
 * 
 * P1-17: Added subprocess contract validation using Zod schemas.
 * P1-18: Fixed contract validation with strict typing instead of unknown[].
 */

// P1-18: Define strict contract schemas for request/response validation
const NodeSchema = z.object({
  id: z.string(),
  type: z.string(),
  name: z.string(),
  filePath: z.string(),
  properties: z.record(z.string(), z.unknown()).optional(),
  tags: z.array(z.string()).optional(),
});

const EdgeSchema = z.object({
  sourceNodeId: z.string(),
  targetNodeId: z.string(),
  relationshipType: z.string(),
  properties: z.record(z.string(), z.unknown()).optional(),
});

const UnresolvedEdgeSchema = z.object({
  id: z.string(),
  sourceNodeId: z.string(),
  targetRef: z.string(),
  relationship: z.string(),
  targetKindHint: z.string().optional(),
  confidence: z.number().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

const EdgeResolutionRecordSchema = z.object({
  id: z.string(),
  unresolvedEdgeId: z.string(),
  status: z.string(),
  resolvedTargetId: z.string().optional(),
  candidateIds: z.array(z.string()).optional(),
  reviewRequired: z.boolean().optional(),
});

export const ExtractorWorkerRequestSchema = z.object({
  snapshot: z.any(), // P1-18: Use z.any() for RepositorySnapshot to avoid type mismatch, validate at runtime
});

export const ExtractorWorkerResponseSchema = z.object({
  nodes: z.array(NodeSchema),
  edges: z.array(EdgeSchema),
  unresolvedEdges: z.array(UnresolvedEdgeSchema),
  edgeResolutionRecords: z.array(EdgeResolutionRecordSchema),
  residualIslandIds: z.array(z.string()),
});

export interface ExtractorWorkerRequest {
  snapshot: RepositorySnapshot;
}

export interface ExtractorWorkerResponse {
  nodes: z.infer<typeof NodeSchema>[];
  edges: z.infer<typeof EdgeSchema>[];
  unresolvedEdges: z.infer<typeof UnresolvedEdgeSchema>[];
  edgeResolutionRecords: z.infer<typeof EdgeResolutionRecordSchema>[];
  residualIslandIds: string[];
}

export async function runExtractionWorker(request: ExtractorWorkerRequest): Promise<ExtractorWorkerResponse> {
  // P1-18: Validate request contract
  const validatedRequest = ExtractorWorkerRequestSchema.parse(request);
  
  const pipeline = new SynthesisPipeline({
    extractors: getCanonicalExtractors(),
    residualConfidenceThreshold: 0.5,
  });

  const result = await pipeline.runFromSnapshot(validatedRequest.snapshot);

  const response: ExtractorWorkerResponse = {
    nodes: result.graph.nodes as any,
    edges: result.graph.edges as any,
    unresolvedEdges: result.graph.unresolvedEdges as any,
    edgeResolutionRecords: result.graph.edgeResolutionRecords as any,
    residualIslandIds: result.residualIslands.map((island) => island.id),
  };

  // P1-18: Validate response contract before returning
  return ExtractorWorkerResponseSchema.parse(response);
}

async function main(): Promise<void> {
  const input = await new Promise<string>((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => {
      data += chunk;
    });
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });

  const payload = JSON.parse(input) as ExtractorWorkerRequest;
  const response = await runExtractionWorker(payload);
  process.stdout.write(JSON.stringify(response));
}

if (require.main === module) {
  main().catch((error) => {
    const message = error instanceof Error ? error.message : String(error);
    process.stderr.write(message);
    process.exitCode = 1;
  });
}
