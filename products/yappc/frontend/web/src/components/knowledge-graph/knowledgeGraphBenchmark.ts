/**
 * Knowledge Graph Semantic Search Benchmark Utilities
 *
 * Provides deterministic scoring helpers for validating semantic-search quality.
 */

export interface BenchmarkCase {
  query: string;
  expectedNodeIds: string[];
}

export interface SearchHit {
  nodeId: string;
  score: number;
}

export interface BenchmarkRunResult {
  caseCount: number;
  precisionAt3: number;
  recallAt3: number;
  meanReciprocalRank: number;
  passed: boolean;
}

export interface BenchmarkThresholds {
  precisionAt3: number;
  recallAt3: number;
  meanReciprocalRank: number;
}

export const DEFAULT_BENCHMARK_THRESHOLDS: BenchmarkThresholds = {
  precisionAt3: 0.45,
  recallAt3: 0.6,
  meanReciprocalRank: 0.7,
};

function safeDivide(numerator: number, denominator: number): number {
  return denominator === 0 ? 0 : numerator / denominator;
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values));
}

function scoreCaseAtK(
  benchmarkCase: BenchmarkCase,
  hits: SearchHit[],
  k: number
): { precision: number; recall: number; reciprocalRank: number } {
  const expected = unique(benchmarkCase.expectedNodeIds);
  const topK = hits.slice(0, k).map((hit) => hit.nodeId);
  const relevantInTopK = topK.filter((nodeId) => expected.includes(nodeId)).length;

  let reciprocalRank = 0;
  for (let index = 0; index < hits.length; index += 1) {
    if (expected.includes(hits[index].nodeId)) {
      reciprocalRank = 1 / (index + 1);
      break;
    }
  }

  return {
    precision: safeDivide(relevantInTopK, k),
    recall: safeDivide(relevantInTopK, expected.length),
    reciprocalRank,
  };
}

export function runSemanticSearchBenchmark(
  cases: BenchmarkCase[],
  resultMap: Record<string, SearchHit[]>,
  thresholds: BenchmarkThresholds = DEFAULT_BENCHMARK_THRESHOLDS
): BenchmarkRunResult {
  if (cases.length === 0) {
    return {
      caseCount: 0,
      precisionAt3: 0,
      recallAt3: 0,
      meanReciprocalRank: 0,
      passed: false,
    };
  }

  const caseScores = cases.map((benchmarkCase) => {
    const hits = resultMap[benchmarkCase.query] ?? [];
    return scoreCaseAtK(benchmarkCase, hits, 3);
  });

  const precisionAt3 =
    caseScores.reduce((sum, score) => sum + score.precision, 0) / caseScores.length;
  const recallAt3 =
    caseScores.reduce((sum, score) => sum + score.recall, 0) / caseScores.length;
  const meanReciprocalRank =
    caseScores.reduce((sum, score) => sum + score.reciprocalRank, 0) / caseScores.length;

  const passed =
    precisionAt3 >= thresholds.precisionAt3 &&
    recallAt3 >= thresholds.recallAt3 &&
    meanReciprocalRank >= thresholds.meanReciprocalRank;

  return {
    caseCount: caseScores.length,
    precisionAt3,
    recallAt3,
    meanReciprocalRank,
    passed,
  };
}
