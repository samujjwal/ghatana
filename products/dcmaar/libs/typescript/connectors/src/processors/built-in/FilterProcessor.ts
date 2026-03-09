/**
 * @fileoverview Event filtering processor using conditional expressions.
 *
 * Filters events based on configurable conditions and rules.
 * Supports comparison operators, logical operators, and JSONPath.
 *
 * @module processors/built-in/FilterProcessor
 * @since 1.1.0
 */

import { Processor, ProcessorContext } from '../types';
import { Event } from '../../types';

/**
 * Comparison operators.
 */
export type ComparisonOperator =
  | 'eq'
  | 'ne'
  | 'gt'
  | 'gte'
  | 'lt'
  | 'lte'
  | 'in'
  | 'nin'
  | 'contains'
  | 'exists'
  | 'regex';

/**
 * Filter condition.
 */
export interface FilterCondition {
  /**
   * Path to the field to evaluate (dot notation).
   */
  field: string;

  /**
   * Comparison operator.
   */
  operator: ComparisonOperator;

  /**
   * Value to compare against.
   */
  value?: unknown;
}

/**
 * Logical rule combining multiple conditions.
 */
export interface FilterRule {
  /**
   * Logical operator (AND/OR).
   * @default 'and'
   */
  logic?: 'and' | 'or';

  /**
   * Conditions to evaluate.
   */
  conditions: FilterCondition[];

  /**
   * Nested rules (for complex logic).
   */
  rules?: FilterRule[];
}

/**
 * Configuration for FilterProcessor.
 */
export interface FilterProcessorConfig {
  /**
   * Filter rule to apply.
   */
  rule: FilterRule;

  /**
   * Filter mode.
   * - 'include': Keep events matching the rule
   * - 'exclude': Drop events matching the rule
   * @default 'include'
   */
  mode?: 'include' | 'exclude';

  /**
   * Whether to add filter metadata to passing events.
   * @default false
   */
  addMetadata?: boolean;
}

/**
 * Event filtering processor.
 *
 * Filters events based on conditional rules. Supports complex
 * logic with nested conditions and multiple operators.
 *
 * **Features**:
 * - Comparison operators (eq, ne, gt, gte, lt, lte)
 * - Set operators (in, nin, contains)
 * - Existence check (exists)
 * - Regex matching
 * - Logical operators (AND, OR)
 * - Nested rules
 * - Include/exclude modes
 * - JSONPath field access
 * - Metadata tagging
 *
 * **Usage**:
 * ```typescript
 * const processor = new FilterProcessor({
 *   id: 'filter-adult-users',
 *   type: 'filter',
 *   config: {
 *     mode: 'include',
 *     rule: {
 *       logic: 'and',
 *       conditions: [
 *         { field: 'age', operator: 'gte', value: 18 },
 *         { field: 'verified', operator: 'eq', value: true }
 *       ]
 *     }
 *   }
 * });
 * ```
 */
export class FilterProcessor implements Processor {
  readonly id: string;
  readonly type = 'filter';
  readonly name?: string;

  private config: FilterProcessorConfig;

  constructor(processorConfig: {
    id: string;
    type: string;
    name?: string;
    config: FilterProcessorConfig;
  }) {
    this.id = processorConfig.id;
    this.name = processorConfig.name;
    this.config = {
      mode: 'include',
      addMetadata: false,
      ...processorConfig.config,
    };
  }

  validateConfig(config: unknown): { valid: boolean; error?: string } {
    if (!config.rule) {
      return { valid: false, error: 'rule is required' };
    }

    if (!config.rule.conditions || !Array.isArray(config.rule.conditions)) {
      return { valid: false, error: 'rule.conditions must be an array' };
    }

    // Validate mode
    if (config.mode && !['include', 'exclude'].includes(config.mode)) {
      return { valid: false, error: "mode must be 'include' or 'exclude'" };
    }

    return { valid: true };
  }

  async process(event: Event, context: ProcessorContext): Promise<Event | null> {
    const startTime = Date.now();

    try {
      const matches = this.evaluateRule(this.config.rule, event.payload, context);
      const shouldPass = this.config.mode === 'include' ? matches : !matches;

      if (shouldPass) {
        context.metrics?.increment('processor.filter.passed', 1, {
          processorId: this.id,
          connectorId: context.connectorId,
        });

        const metadata = this.config.addMetadata
          ? {
              ...event.metadata,
              filterResult: { processorId: this.id, matches, mode: this.config.mode },
            }
          : event.metadata;

        return { ...event, metadata };
      } else {
        context.metrics?.increment('processor.filter.filtered', 1, {
          processorId: this.id,
          connectorId: context.connectorId,
        });

        context.logger?.debug('Event filtered', {
          processorId: this.id,
          eventId: event.id,
          matches,
          mode: this.config.mode,
        });

        return null;
      }
    } catch (error) {
      context.metrics?.increment('processor.filter.error', 1, {
        processorId: this.id,
        connectorId: context.connectorId,
      });

      context.logger?.error('Filter evaluation error', {
        processorId: this.id,
        eventId: event.id,
        error,
      });

      throw error;
    } finally {
      const duration = Date.now() - startTime;
      context.metrics?.histogram('processor.filter.duration', duration, {
        processorId: this.id,
        connectorId: context.connectorId,
      });
    }
  }

  /**
   * Evaluates a filter rule against a payload.
   */
  private evaluateRule(rule: FilterRule, payload: unknown, context: ProcessorContext): boolean {
    const logic = rule.logic || 'and';

    // Evaluate conditions
    const conditionResults = rule.conditions.map((condition) =>
      this.evaluateCondition(condition, payload, context)
    );

    // Evaluate nested rules
    const ruleResults = (rule.rules || []).map((nestedRule) =>
      this.evaluateRule(nestedRule, payload, context)
    );

    // Combine all results
    const allResults = [...conditionResults, ...ruleResults];

    // Apply logic
    return logic === 'and' ? allResults.every(Boolean) : allResults.some(Boolean);
  }

  /**
   * Evaluates a single condition.
   */
  private evaluateCondition(
    condition: FilterCondition,
    payload: unknown,
    context: ProcessorContext
  ): boolean {
    const actualValue = this.getValueByPath(payload, condition.field);

    try {
      switch (condition.operator) {
        case 'eq':
          return actualValue === condition.value;

        case 'ne':
          return actualValue !== condition.value;

        case 'gt':
          return actualValue > condition.value;

        case 'gte':
          return actualValue >= condition.value;

        case 'lt':
          return actualValue < condition.value;

        case 'lte':
          return actualValue <= condition.value;

        case 'in':
          return Array.isArray(condition.value) && condition.value.includes(actualValue);

        case 'nin':
          return Array.isArray(condition.value) && !condition.value.includes(actualValue);

        case 'contains':
          if (typeof actualValue === 'string') {
            return actualValue.includes(String(condition.value));
          }
          if (Array.isArray(actualValue)) {
            return actualValue.includes(condition.value);
          }
          return false;

        case 'exists':
          return condition.value ? actualValue !== undefined : actualValue === undefined;

        case 'regex':
          if (typeof actualValue !== 'string') return false;
          const regex = new RegExp(condition.value);
          return regex.test(actualValue);

        default:
          context.logger?.warn('Unknown operator', {
            processorId: this.id,
            operator: condition.operator,
          });
          return false;
      }
    } catch (error) {
      context.logger?.warn('Condition evaluation error', {
        processorId: this.id,
        condition,
        error,
      });
      return false;
    }
  }

  /**
   * Gets value from object by path (dot notation).
   */
  private getValueByPath(obj: unknown, path: string): unknown {
    const keys = path.split('.');
    let current = obj;

    for (const key of keys) {
      if (current == null) return undefined;
      current = current[key];
    }

    return current;
  }
}
