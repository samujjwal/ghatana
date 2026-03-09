/**
 * @fileoverview Processor module exports.
 *
 * Provides processor types, registry, and built-in processors.
 *
 * @module processors
 * @since 1.1.0
 */

export { ProcessorRegistry } from './ProcessorRegistry';
export type {
  Processor,
  ProcessorConfig,
  ProcessorContext,
  ProcessorFactory,
  ProcessorResult,
} from './types';

// Export built-in processors
export {
  ValidateProcessor,
  TransformProcessor,
  FilterProcessor,
  EnrichProcessor,
  RedactionProcessor,
} from './built-in';

// Export built-in processor configs
export type {
  ValidateProcessorConfig,
  TransformProcessorConfig,
  TransformOperation,
  FilterProcessorConfig,
  FilterRule,
  FilterCondition,
  ComparisonOperator,
  EnrichProcessorConfig,
  EnrichmentOperation,
  RedactionProcessorConfig,
  RedactionRule,
  RedactionRuleType,
} from './built-in';
