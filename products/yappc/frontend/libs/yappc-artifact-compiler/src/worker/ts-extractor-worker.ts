import { SynthesisPipeline } from '../synthesis/pipeline';
import { getCanonicalExtractors } from '../extractors';
import type { RepositorySnapshot } from '../source-providers/types';

export interface ExtractorWorkerRequest {
  snapshot: RepositorySnapshot;
}

export interface ExtractorWorkerResponse {
  nodes: unknown[];
  edges: unknown[];
  unresolvedEdges: unknown[];
  edgeResolutionRecords: unknown[];
  residualIslandIds: string[];
}

export async function runExtractionWorker(request: ExtractorWorkerRequest): Promise<ExtractorWorkerResponse> {
  const pipeline = new SynthesisPipeline({
    extractors: getCanonicalExtractors(),
    residualConfidenceThreshold: 0.5,
  });

  const result = await pipeline.runFromSnapshot(request.snapshot);

  return {
    nodes: result.graph.nodes,
    edges: result.graph.edges,
    unresolvedEdges: result.graph.unresolvedEdges,
    edgeResolutionRecords: result.graph.edgeResolutionRecords,
    residualIslandIds: result.residualIslands.map((island) => island.id),
  };
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
