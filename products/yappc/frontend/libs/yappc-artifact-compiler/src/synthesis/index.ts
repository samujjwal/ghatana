/**
 * @fileoverview Synthesis barrel export.
 */

export type {
  SynthesisResult,
  GraphSynthesizer,
  MultiSourceSynthesisRequest,
  MultiSourceSynthesizer,
} from './types';

export {
  SynthesisEngine,
  type SynthesisEngineOptions,
  type AnalysisResult,
  type QueryResult,
} from './engine';

export { SynthesisPipeline } from "./pipeline";
export type {
  SynthesisPipelineConfig,
  SynthesisPipelineResult,
  PipelineError,
  PipelineWarning,
  PipelineStats,
} from "./pipeline";
export { resolveSymbols } from "./symbol-resolver";
export type { SymbolResolutionResult } from "./symbol-resolver";
