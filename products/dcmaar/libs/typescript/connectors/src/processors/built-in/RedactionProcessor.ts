/**
 * @fileoverview PII redaction processor.
 *
 * Redacts personally identifiable information (PII) from event payloads
 * using pattern matching and configurable rules.
 *
 * @module processors/built-in/RedactionProcessor
 * @since 1.1.0
 */

import { Processor, ProcessorContext } from '../types';
import { Event } from '../../types';

/**
 * Redaction rule type.
 */
export type RedactionRuleType =
  | 'email'
  | 'phone'
  | 'ssn'
  | 'credit_card'
  | 'ip_address'
  | 'custom_regex'
  | 'field_path';

/**
 * Redaction rule configuration.
 */
export interface RedactionRule {
  /**
   * Rule type.
   */
  type: RedactionRuleType;

  /**
   * Field path for field_path type (dot notation).
   */
  path?: string;

  /**
   * Custom regex pattern for custom_regex type.
   */
  pattern?: string;

  /**
   * Replacement strategy.
   * - 'mask': Replace with mask character (e.g., ****)
   * - 'hash': Replace with hash
   * - 'remove': Remove the field entirely
   * - 'partial': Show first/last N characters
   * - 'token': Replace with generated token
   * @default 'mask'
   */
  replacement?: 'mask' | 'hash' | 'remove' | 'partial' | 'token';

  /**
   * Mask character for 'mask' replacement.
   * @default '*'
   */
  maskChar?: string;

  /**
   * Number of characters to show for 'partial' replacement.
   * @default 4
   */
  partialLength?: number;

  /**
   * Position for 'partial' replacement.
   * @default 'end'
   */
  partialPosition?: 'start' | 'end' | 'both';
}

/**
 * Configuration for RedactionProcessor.
 */
export interface RedactionProcessorConfig {
  /**
   * Array of redaction rules to apply.
   */
  rules: RedactionRule[];

  /**
   * Whether to recursively process nested objects.
   * @default true
   */
  recursive?: boolean;

  /**
   * Whether to add redaction metadata.
   * @default false
   */
  addMetadata?: boolean;

  /**
   * Fields to exclude from redaction.
   */
  exclude?: string[];
}

/**
 * PII redaction processor.
 *
 * Automatically detects and redacts personally identifiable information
 * from event payloads. Supports multiple redaction strategies and
 * custom patterns.
 *
 * **Features**:
 * - Built-in PII patterns (email, phone, SSN, credit card, IP)
 * - Custom regex patterns
 * - Field path redaction
 * - Multiple replacement strategies (mask, hash, remove, partial, token)
 * - Recursive object processing
 * - Configurable exclusions
 * - Redaction metadata tracking
 *
 * **Usage**:
 * ```typescript
 * const processor = new RedactionProcessor({
 *   id: 'redact-pii',
 *   type: 'redact',
 *   config: {
 *     rules: [
 *       { type: 'email', replacement: 'mask' },
 *       { type: 'phone', replacement: 'partial', partialLength: 4 },
 *       { type: 'field_path', path: 'user.ssn', replacement: 'hash' },
 *       { type: 'credit_card', replacement: 'partial', partialPosition: 'end' }
 *     ],
 *     recursive: true,
 *     addMetadata: true
 *   }
 * });
 * ```
 */
export class RedactionProcessor implements Processor {
  readonly id: string;
  readonly type = 'redact';
  readonly name?: string;

  private config: RedactionProcessorConfig;
  private patterns: Map<RedactionRuleType, RegExp> = new Map();

  constructor(processorConfig: {
    id: string;
    type: string;
    name?: string;
    config: RedactionProcessorConfig;
  }) {
    this.id = processorConfig.id;
    this.name = processorConfig.name;
    this.config = {
      recursive: true,
      addMetadata: false,
      exclude: [],
      ...processorConfig.config,
    };

    this.initializePatterns();
  }

  validateConfig(config: unknown): { valid: boolean; error?: string } {
    if (!config.rules) {
      return { valid: false, error: 'rules array is required' };
    }

    if (!Array.isArray(config.rules)) {
      return { valid: false, error: 'rules must be an array' };
    }

    if (config.rules.length === 0) {
      return { valid: false, error: 'rules array must not be empty' };
    }

    // Validate each rule
    for (const rule of config.rules) {
      if (!rule.type) {
        return { valid: false, error: 'rule type is required' };
      }

      if (rule.type === 'field_path' && !rule.path) {
        return { valid: false, error: 'path is required for field_path rule' };
      }

      if (rule.type === 'custom_regex' && !rule.pattern) {
        return { valid: false, error: 'pattern is required for custom_regex rule' };
      }
    }

    return { valid: true };
  }

  /**
   * Initializes built-in PII detection patterns.
   */
  private initializePatterns(): void {
    this.patterns.set('email', /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/g);
    this.patterns.set('phone', /\b(\+\d{1,3}[- ]?)?\(?\d{3}\)?[- ]?\d{3}[- ]?\d{4}\b/g);
    this.patterns.set('ssn', /\b\d{3}-\d{2}-\d{4}\b/g);
    this.patterns.set('credit_card', /\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b/g);
    this.patterns.set('ip_address', /\b(?:\d{1,3}\.){3}\d{1,3}\b/g);
  }

  async process(event: Event, context: ProcessorContext): Promise<Event> {
    const startTime = Date.now();

    try {
      const redactionStats = {
        fieldsRedacted: 0,
        patternsMatched: 0,
      };

      const redactedPayload = this.redactObject(
        JSON.parse(JSON.stringify(event.payload)),
        '',
        redactionStats,
        context
      );

      context.metrics?.increment('processor.redact.success', 1, {
        processorId: this.id,
        connectorId: context.connectorId,
      });

      context.metrics?.increment('processor.redact.fields', redactionStats.fieldsRedacted, {
        processorId: this.id,
        connectorId: context.connectorId,
      });

      const metadata = this.config.addMetadata
        ? {
            ...event.metadata,
            redaction: {
              processorId: this.id,
              fieldsRedacted: redactionStats.fieldsRedacted,
              patternsMatched: redactionStats.patternsMatched,
            },
          }
        : event.metadata;

      return {
        ...event,
        payload: redactedPayload,
        metadata,
      };
    } catch (error) {
      context.metrics?.increment('processor.redact.failure', 1, {
        processorId: this.id,
        connectorId: context.connectorId,
      });

      context.logger?.error('Redaction error', {
        processorId: this.id,
        eventId: event.id,
        error,
      });

      throw error;
    } finally {
      const duration = Date.now() - startTime;
      context.metrics?.histogram('processor.redact.duration', duration, {
        processorId: this.id,
        connectorId: context.connectorId,
      });
    }
  }

  /**
   * Recursively redacts an object.
   */
  private redactObject(obj: unknown, currentPath: string, stats: unknown, context: ProcessorContext): unknown {
    if (obj === null || obj === undefined) {
      return obj;
    }

    // Check if current path is excluded
    if (this.config.exclude?.includes(currentPath)) {
      return obj;
    }

    // Handle strings
    if (typeof obj === 'string') {
      return this.redactString(obj, currentPath, stats, context);
    }

    // Handle arrays
    if (Array.isArray(obj)) {
      return obj.map((item, index) => {
        const itemPath = currentPath ? `${currentPath}[${index}]` : `[${index}]`;
        return this.config.recursive ? this.redactObject(item, itemPath, stats, context) : item;
      });
    }

    // Handle objects
    if (typeof obj === 'object') {
      const redacted: unknown = {};

      for (const key in obj) {
        const fieldPath = currentPath ? `${currentPath}.${key}` : key;

        // Check field path rules
        const fieldPathRule = this.config.rules.find(
          (r) => r.type === 'field_path' && r.path === fieldPath
        );

        if (fieldPathRule) {
          // Redact entire field
          const redactedValue = this.applyRedaction(obj[key], fieldPathRule, context);

          if (fieldPathRule.replacement !== 'remove') {
            redacted[key] = redactedValue;
          }

          stats.fieldsRedacted++;
        } else {
          // Recursively process
          redacted[key] = this.config.recursive
            ? this.redactObject(obj[key], fieldPath, stats, context)
            : obj[key];
        }
      }

      return redacted;
    }

    return obj;
  }

  /**
   * Redacts a string value.
   */
  private redactString(value: string, path: string, stats: unknown, context: ProcessorContext): string {
    let redacted = value;

    for (const rule of this.config.rules) {
      // Skip field_path rules (handled elsewhere)
      if (rule.type === 'field_path') continue;

      // Get pattern
      let pattern: RegExp | undefined;

      if (rule.type === 'custom_regex' && rule.pattern) {
        pattern = new RegExp(rule.pattern, 'g');
      } else {
        pattern = this.patterns.get(rule.type);
      }

      if (!pattern) continue;

      // Apply pattern matching
      const matches = redacted.match(pattern);
      if (matches && matches.length > 0) {
        stats.patternsMatched += matches.length;

        redacted = redacted.replace(pattern, (match) => {
          stats.fieldsRedacted++;
          return this.applyRedactionToString(match, rule, context);
        });

        context.logger?.debug('PII detected and redacted', {
          processorId: this.id,
          path,
          ruleType: rule.type,
          matchCount: matches.length,
        });
      }
    }

    return redacted;
  }

  /**
   * Applies redaction to a value based on rule.
   */
  private applyRedaction(value: unknown, rule: RedactionRule, context: ProcessorContext): unknown {
    if (typeof value === 'string') {
      return this.applyRedactionToString(value, rule, context);
    }

    // For non-strings, apply hash or token
    const replacement = rule.replacement || 'mask';

    switch (replacement) {
      case 'hash':
        return this.computeHash(JSON.stringify(value));
      case 'token':
        return this.generateToken();
      case 'remove':
        return undefined;
      default:
        return '****';
    }
  }

  /**
   * Applies redaction to a string value.
   */
  private applyRedactionToString(
    value: string,
    rule: RedactionRule,
    _context: ProcessorContext
  ): string {
    const replacement = rule.replacement || 'mask';

    switch (replacement) {
      case 'mask': {
        const maskChar = rule.maskChar || '*';
        return maskChar.repeat(value.length);
      }

      case 'hash':
        return this.computeHash(value);

      case 'partial': {
        const length = rule.partialLength || 4;
        const position = rule.partialPosition || 'end';
        const maskChar = rule.maskChar || '*';

        if (value.length <= length) {
          return value;
        }

        switch (position) {
          case 'start':
            return value.substring(0, length) + maskChar.repeat(value.length - length);
          case 'end':
            return maskChar.repeat(value.length - length) + value.substring(value.length - length);
          case 'both': {
            const each = Math.floor(length / 2);
            const start = value.substring(0, each);
            const end = value.substring(value.length - each);
            const middle = maskChar.repeat(value.length - 2 * each);
            return start + middle + end;
          }
        }
        break;
      }

      case 'token':
        return this.generateToken();

      case 'remove':
        return '';
    }

    return value;
  }

  /**
   * Computes a simple hash of a string.
   */
  private computeHash(input: string): string {
    let hash = 0;
    for (let i = 0; i < input.length; i++) {
      const char = input.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash;
    }
    return `[REDACTED:${Math.abs(hash).toString(16)}]`;
  }

  /**
   * Generates a random token.
   */
  private generateToken(): string {
    return `[TOKEN:${Math.random().toString(36).substring(2, 10).toUpperCase()}]`;
  }
}
