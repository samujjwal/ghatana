/**
 * Copyright (c) 2025 Ghatana Technologies
 * Knowledge Graph Components
 *
 * @packageDocumentation
 * @module knowledge-graph
 */

export { KnowledgeGraphPanel } from './KnowledgeGraphPanel';
export { knowledgeGraphApi } from './knowledgeGraphApi';
export {
  runSemanticSearchBenchmark,
  DEFAULT_BENCHMARK_THRESHOLDS,
} from './knowledgeGraphBenchmark';
export type {
  KnowledgeNode,
  KnowledgeEdge,
  KnowledgeGraphResult,
  SemanticSearchResult,
} from './knowledgeGraphApi';
export type {
  BenchmarkCase,
  SearchHit,
  BenchmarkRunResult,
  BenchmarkThresholds,
} from './knowledgeGraphBenchmark';
