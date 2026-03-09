/**
 * @fileoverview Built-in processor implementations.
 *
 * Exports all built-in processor classes and their configurations.
 *
 * @module processors/built-in
 * @since 1.1.0
 */

export { ValidateProcessor } from './ValidateProcessor';
export type { ValidateProcessorConfig } from './ValidateProcessor';

export { TransformProcessor } from './TransformProcessor';
export type { TransformProcessorConfig, TransformOperation } from './TransformProcessor';

export { FilterProcessor } from './FilterProcessor';
export type {
  FilterProcessorConfig,
  FilterRule,
  FilterCondition,
  ComparisonOperator,
} from './FilterProcessor';

export { EnrichProcessor } from './EnrichProcessor';
export type { EnrichProcessorConfig, EnrichmentOperation } from './EnrichProcessor';

export { RedactionProcessor } from './RedactionProcessor';
export type {
  RedactionProcessorConfig,
  RedactionRule,
  RedactionRuleType,
} from './RedactionProcessor';
