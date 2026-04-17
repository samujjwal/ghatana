import { describe, expect, it } from 'vitest';

import {
  DEFAULT_BENCHMARK_THRESHOLDS,
  runSemanticSearchBenchmark,
  type BenchmarkCase,
} from '../knowledgeGraphBenchmark';

describe('knowledge graph semantic-search benchmark harness', () => {
  it('meets the default quality thresholds for calibrated fixture cases', () => {
    const cases: BenchmarkCase[] = [
      {
        query: 'how do we deploy safely',
        expectedNodeIds: ['node-deploy-runbook', 'node-risk-policy'],
      },
      {
        query: 'which services talk to postgres',
        expectedNodeIds: ['node-collection-service', 'node-postgres-adapter'],
      },
      {
        query: 'find authentication and session flow',
        expectedNodeIds: ['node-auth-service', 'node-session-manager'],
      },
    ];

    const resultMap = {
      'how do we deploy safely': [
        { nodeId: 'node-risk-policy', score: 0.93 },
        { nodeId: 'node-deploy-runbook', score: 0.9 },
        { nodeId: 'node-ci-pipeline', score: 0.82 },
      ],
      'which services talk to postgres': [
        { nodeId: 'node-postgres-adapter', score: 0.91 },
        { nodeId: 'node-collection-service', score: 0.87 },
        { nodeId: 'node-query-planner', score: 0.73 },
      ],
      'find authentication and session flow': [
        { nodeId: 'node-auth-service', score: 0.95 },
        { nodeId: 'node-session-manager', score: 0.89 },
        { nodeId: 'node-route-guard', score: 0.84 },
      ],
    };

    const benchmarkResult = runSemanticSearchBenchmark(cases, resultMap);

    expect(benchmarkResult.caseCount).toBe(3);
    expect(benchmarkResult.precisionAt3).toBeGreaterThanOrEqual(
      DEFAULT_BENCHMARK_THRESHOLDS.precisionAt3
    );
    expect(benchmarkResult.recallAt3).toBeGreaterThanOrEqual(
      DEFAULT_BENCHMARK_THRESHOLDS.recallAt3
    );
    expect(benchmarkResult.meanReciprocalRank).toBeGreaterThanOrEqual(
      DEFAULT_BENCHMARK_THRESHOLDS.meanReciprocalRank
    );
    expect(benchmarkResult.passed).toBe(true);
  });

  it('fails the benchmark when retrieval quality drops below thresholds', () => {
    const cases: BenchmarkCase[] = [
      {
        query: 'auth flow',
        expectedNodeIds: ['node-auth-service'],
      },
    ];

    const resultMap = {
      'auth flow': [
        { nodeId: 'node-unrelated-1', score: 0.91 },
        { nodeId: 'node-unrelated-2', score: 0.87 },
        { nodeId: 'node-auth-service', score: 0.2 },
      ],
    };

    const benchmarkResult = runSemanticSearchBenchmark(cases, resultMap, {
      precisionAt3: 0.8,
      recallAt3: 0.9,
      meanReciprocalRank: 0.8,
    });

    expect(benchmarkResult.passed).toBe(false);
    expect(benchmarkResult.precisionAt3).toBeLessThan(0.8);
  });
});
