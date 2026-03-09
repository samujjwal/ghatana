/**
 * @fileoverview Process Contract System
 *
 * Defines a powerful, expressive, and secure contract for custom processes.
 * Processes follow a standard pipeline: Collect → Analyze → Report
 *
 * Features:
 * - Declarative process definition
 * - Type-safe with Zod validation
 * - Sandboxed execution
 * - Resource limits
 * - Data privacy controls
 * - Conditional execution
 * - Composable stages
 *
 * @module contracts/process
 * @since 2.0.0
 */

import { z } from 'zod';
import type { Event } from '@ghatana/dcmaar-connectors';

// ============================================================================
// Core Process Types
// ============================================================================

/**
 * Process execution stages
 */
export type ProcessStage = 'collect' | 'analyze' | 'report';

/**
 * Process execution status
 */
export type ProcessStatus = 'idle' | 'running' | 'paused' | 'completed' | 'error';

/**
 * Data collection strategies
 */
export type CollectionStrategy =
  | 'polling'      // Poll at regular intervals
  | 'streaming'    // Continuous stream
  | 'event-driven' // Triggered by events
  | 'scheduled'    // Cron-like scheduling
  | 'manual';      // User-triggered

/**
 * Analysis strategies
 */
export type AnalysisStrategy =
  | 'aggregate'    // Aggregate data over time
  | 'filter'       // Filter based on conditions
  | 'transform'    // Transform data shape
  | 'enrich'       // Add contextual data
  | 'classify'     // Categorize data
  | 'anomaly'      // Detect anomalies
  | 'custom';      // Custom analysis logic

/**
 * Report delivery methods
 */
export type ReportDelivery =
  | 'store'        // Store locally
  | 'push'         // Push to remote
  | 'webhook'      // HTTP webhook
  | 'notification' // User notification
  | 'custom';      // Custom delivery

// ============================================================================
// Security & Privacy
// ============================================================================

/**
 * Security constraints for process execution
 */
export const SecurityConstraintsSchema = z.object({
  /**
   * Maximum execution time per stage (milliseconds)
   * @default 30000 (30 seconds)
   */
  maxExecutionTimeMs: z.number().int().positive().default(30000),

  /**
   * Maximum memory usage (bytes)
   * @default 50MB
   */
  maxMemoryBytes: z.number().int().positive().default(50 * 1024 * 1024),

  /**
   * Maximum events processed per batch
   * @default 1000
   */
  maxEventsPerBatch: z.number().int().positive().default(1000),

  /**
   * Allowed network access patterns
   */
  allowedNetworkPatterns: z.array(z.string()).default([]),

  /**
   * Denied network access patterns
   */
  deniedNetworkPatterns: z.array(z.string()).default([
    'file://*',
    'chrome://*',
    'chrome-extension://*',
  ]),

  /**
   * Allowed browser APIs
   */
  allowedBrowserAPIs: z.array(z.string()).default([
    'storage',
    'tabs',
    'runtime',
  ]),

  /**
   * Enable sandboxed execution
   * @default true
   */
  sandboxed: z.boolean().default(true),
});

export type SecurityConstraints = z.infer<typeof SecurityConstraintsSchema>;

export const DEFAULT_SECURITY_CONSTRAINTS: SecurityConstraints =
  SecurityConstraintsSchema.parse({});

/**
 * Privacy controls for data handling
 */
export const PrivacyControlsSchema = z.object({
  /**
   * PII redaction rules
   */
  redactionRules: z.array(z.object({
    /** Pattern to match (regex) */
    pattern: z.string(),
    /** Replacement string */
    replacement: z.string().default('[REDACTED]'),
    /** Fields to apply redaction to */
    fields: z.array(z.string()).optional(),
  })).default([
    {
      pattern: '\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b',
      replacement: '[EMAIL]',
    },
    {
      pattern: '\\b\\d{3}-\\d{2}-\\d{4}\\b',
      replacement: '[SSN]',
    },
  ]),

  /**
   * Fields to encrypt
   */
  encryptFields: z.array(z.string()).default([]),

  /**
   * Data retention period (milliseconds)
   */
  retentionPeriodMs: z.number().int().positive().optional(),

  /**
   * Enable data anonymization
   */
  anonymize: z.boolean().default(false),

  /**
   * Consent required before processing
   */
  requireConsent: z.boolean().default(false),
});

export type PrivacyControls = z.infer<typeof PrivacyControlsSchema>;

export const DEFAULT_PRIVACY_CONTROLS: PrivacyControls = PrivacyControlsSchema.parse({});

// ============================================================================
// Condition System
// ============================================================================

/**
 * Condition operator for execution logic
 */
export type ConditionOperator =
  | 'eq'   // Equal
  | 'ne'   // Not equal
  | 'gt'   // Greater than
  | 'gte'  // Greater than or equal
  | 'lt'   // Less than
  | 'lte'  // Less than or equal
  | 'in'   // In array
  | 'nin'  // Not in array
  | 'regex' // Regex match
  | 'exists'; // Field exists

/**
 * Condition for conditional execution
 */
export const ConditionSchema = z.object({
  /** Field path (dot notation) */
  field: z.string(),
  /** Operator */
  operator: z.enum(['eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'in', 'nin', 'regex', 'exists']),
  /** Value to compare against */
  value: z.any().optional(),
});

export type Condition = z.infer<typeof ConditionSchema>;

/**
 * Logical condition group (AND/OR)
 */
export const ConditionGroupSchema: z.ZodType<any> = z.lazy(() =>
  z.object({
    /** Logical operator */
    operator: z.enum(['and', 'or', 'not']),
    /** Child conditions */
    conditions: z.array(z.union([ConditionSchema, ConditionGroupSchema])),
  })
);

export type ConditionGroup = {
  operator: 'and' | 'or' | 'not';
  conditions: (Condition | ConditionGroup)[];
};

// ============================================================================
// Stage Definitions
// ============================================================================

/**
 * Data source configuration for collection
 */
export const DataSourceSchema = z.object({
  /** Source identifier */
  id: z.string().min(1),
  /** Source type */
  type: z.enum([
    'browser-events',
    'network',
    'storage',
    'performance',
    'web-vitals',
    'page-visits',
    'engagement',
    'custom'
  ]),
  /** Collection strategy */
  strategy: z.enum(['polling', 'streaming', 'event-driven', 'scheduled', 'manual']),
  /** Source-specific configuration */
  config: z.record(z.string(), z.any()).default({}),
  /** Filters to apply at collection */
  filters: z.array(ConditionSchema).optional(),
  /** Sampling rate (0.0 to 1.0) */
  samplingRate: z.number().min(0).max(1).default(1.0),
  /** Batch size for collection */
  batchSize: z.number().int().positive().default(50),
  /** Schedule (cron expression) for scheduled strategy */
  schedule: z.string().optional(),
});

export type DataSource = z.infer<typeof DataSourceSchema>;

/**
 * Collect stage configuration
 */
export const CollectStageSchema = z.object({
  /** Stage identifier */
  id: z.string().default('collect'),
  /** Data sources to collect from */
  sources: z.array(DataSourceSchema).min(1),
  /** Execution conditions */
  when: z.union([ConditionSchema, ConditionGroupSchema]).optional(),
  /** Merge strategy for multiple sources */
  mergeStrategy: z.enum(['union', 'intersection', 'priority']).default('union'),
  /** Timeout for collection (milliseconds) */
  timeoutMs: z.number().int().positive().default(10000),
  /** Retry policy */
  retry: z.object({
    maxAttempts: z.number().int().nonnegative().default(3),
    backoffMs: z.number().int().positive().default(1000),
  }).optional(),
});

export type CollectStage = z.infer<typeof CollectStageSchema>;

/**
 * Analysis operation configuration
 */
export const AnalysisOperationSchema = z.object({
  /** Operation identifier */
  id: z.string().min(1),
  /** Analysis strategy */
  strategy: z.enum(['aggregate', 'filter', 'transform', 'enrich', 'classify', 'anomaly', 'custom']),
  /** Operation-specific configuration */
  config: z.record(z.string(), z.any()).default({}),
  /** Execution order */
  order: z.number().int().default(100),
  /** Skip on error */
  continueOnError: z.boolean().default(false),
});

export type AnalysisOperation = z.infer<typeof AnalysisOperationSchema>;

/**
 * Analyze stage configuration
 */
export const AnalyzeStageSchema = z.object({
  /** Stage identifier */
  id: z.string().default('analyze'),
  /** Analysis operations to perform */
  operations: z.array(AnalysisOperationSchema).min(1),
  /** Execution conditions */
  when: z.union([ConditionSchema, ConditionGroupSchema]).optional(),
  /** Enable parallel execution */
  parallel: z.boolean().default(false),
  /** Timeout for analysis (milliseconds) */
  timeoutMs: z.number().int().positive().default(15000),
});

export type AnalyzeStage = z.infer<typeof AnalyzeStageSchema>;

/**
 * Report destination configuration
 */
export const ReportDestinationSchema = z.object({
  /** Destination identifier */
  id: z.string().min(1),
  /** Destination type */
  type: z.enum(['store', 'push', 'webhook', 'notification', 'custom']),
  /** Delivery method */
  delivery: z.enum(['store', 'push', 'webhook', 'notification', 'custom']),
  /** Destination-specific configuration */
  config: z.record(z.string(), z.any()).default({}),
  /** Report format */
  format: z.enum(['json', 'csv', 'html', 'markdown', 'custom']).default('json'),
  /** Report template (for formatted reports) */
  template: z.string().optional(),
  /** Send on condition */
  when: z.union([ConditionSchema, ConditionGroupSchema]).optional(),
});

export type ReportDestination = z.infer<typeof ReportDestinationSchema>;

/**
 * Report stage configuration
 */
export const ReportStageSchema = z.object({
  /** Stage identifier */
  id: z.string().default('report'),
  /** Report destinations */
  destinations: z.array(ReportDestinationSchema).min(1),
  /** Execution conditions */
  when: z.union([ConditionSchema, ConditionGroupSchema]).optional(),
  /** Report aggregation */
  aggregate: z.boolean().default(false),
  /** Include metadata in report */
  includeMetadata: z.boolean().default(true),
  /** Include execution summary */
  includeSummary: z.boolean().default(true),
  /** Timeout for reporting (milliseconds) */
  timeoutMs: z.number().int().positive().default(10000),
});

export type ReportStage = z.infer<typeof ReportStageSchema>;

// ============================================================================
// Process Definition
// ============================================================================

/**
 * Process metadata
 */
export const ProcessMetadataSchema = z.object({
  /** Process name */
  name: z.string().min(1),
  /** Process description */
  description: z.string().optional(),
  /** Process version */
  version: z.string().default('1.0.0'),
  /** Author/creator */
  author: z.string().optional(),
  /** Tags for categorization */
  tags: z.array(z.string()).default([]),
  /** Creation timestamp */
  createdAt: z.number().default(() => Date.now()),
  /** Last update timestamp */
  updatedAt: z.number().default(() => Date.now()),
});

export type ProcessMetadata = z.infer<typeof ProcessMetadataSchema>;

/**
 * Complete Process Contract
 *
 * This is the main contract for defining custom processes.
 */
export const ProcessContractSchema = z.object({
  /** Unique process identifier */
  id: z.string().min(1),

  /** Process metadata */
  metadata: ProcessMetadataSchema,

  /** Collect stage */
  collect: CollectStageSchema,

  /** Analyze stage */
  analyze: AnalyzeStageSchema,

  /** Report stage */
  report: ReportStageSchema,

  /** Security constraints */
  security: SecurityConstraintsSchema.default(DEFAULT_SECURITY_CONSTRAINTS),

  /** Privacy controls */
  privacy: PrivacyControlsSchema.default(DEFAULT_PRIVACY_CONTROLS),

  /** Process enabled */
  enabled: z.boolean().default(true),

  /** Execution schedule (cron expression) */
  schedule: z.string().optional(),

  /** Execution priority (higher = more priority) */
  priority: z.number().int().default(100),

  /** Maximum concurrent executions */
  maxConcurrentExecutions: z.number().int().positive().default(1),

  /** Global timeout (milliseconds) */
  timeoutMs: z.number().int().positive().default(60000),

  /** Error handling strategy */
  onError: z.enum(['retry', 'skip', 'halt', 'continue']).default('retry'),

  /** Variables/parameters */
  variables: z.record(z.string(), z.any()).default({}),

  /** Required feature flags */
  requiredFeatures: z.array(z.string()).default([]),
});

export type ProcessContract = z.infer<typeof ProcessContractSchema>;

// ============================================================================
// Execution Context & Results
// ============================================================================

/**
 * Process execution context
 */
export interface ProcessExecutionContext {
  /** Process ID */
  processId: string;
  /** Execution ID (unique per run) */
  executionId: string;
  /** Current stage */
  currentStage: ProcessStage;
  /** Start time */
  startTime: number;
  /** Variables/state */
  variables: Record<string, any>;
  /** Collected data */
  collectedData?: Event[];
  /** Analysis results */
  analysisResults?: any;
  /** Logger */
  logger: {
    debug(message: string, meta?: any): void;
    info(message: string, meta?: any): void;
    warn(message: string, meta?: any): void;
    error(message: string, meta?: any): void;
  };
  /** Metrics */
  metrics: {
    increment(metric: string, value?: number, tags?: Record<string, string>): void;
    gauge(metric: string, value: number, tags?: Record<string, string>): void;
    timing(metric: string, value: number, tags?: Record<string, string>): void;
  };
  /** Abort signal */
  signal?: AbortSignal;
}

/**
 * Stage execution result
 */
export interface StageExecutionResult {
  /** Stage name */
  stage: ProcessStage;
  /** Execution status */
  status: 'success' | 'error' | 'skipped' | 'timeout';
  /** Start time */
  startTime: number;
  /** End time */
  endTime: number;
  /** Duration (milliseconds) */
  duration: number;
  /** Output data */
  data?: any;
  /** Error if failed */
  error?: Error;
  /** Metadata */
  metadata?: Record<string, any>;
}

/**
 * Process execution result
 */
export interface ProcessExecutionResult {
  /** Process ID */
  processId: string;
  /** Execution ID */
  executionId: string;
  /** Overall status */
  status: ProcessStatus;
  /** Start time */
  startTime: number;
  /** End time */
  endTime: number;
  /** Total duration (milliseconds) */
  duration: number;
  /** Stage results */
  stages: StageExecutionResult[];
  /** Final output */
  output?: any;
  /** Errors */
  errors: Error[];
  /** Execution summary */
  summary: {
    eventsCollected: number;
    eventsAnalyzed: number;
    reportsGenerated: number;
    errorsEncountered: number;
  };
  /** Metadata */
  metadata: Record<string, any>;
}

// ============================================================================
// Validation Helpers
// ============================================================================

/**
 * Validates a process contract
 *
 * @param contract - Process contract to validate
 * @returns Validation result
 */
export function validateProcessContract(contract: unknown): {
  valid: boolean;
  error?: string;
  data?: ProcessContract;
} {
  try {
    const data = ProcessContractSchema.parse(contract);
    return { valid: true, data };
  } catch (error) {
    const errorMessage =
      error instanceof z.ZodError
        ? error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`).join('; ')
        : String(error);
    return { valid: false, error: errorMessage };
  }
}

/**
 * Validates security constraints
 *
 * @param constraints - Security constraints to validate
 * @returns Validation result
 */
export function validateSecurityConstraints(constraints: unknown): {
  valid: boolean;
  error?: string;
  data?: SecurityConstraints;
} {
  try {
    const data = SecurityConstraintsSchema.parse(constraints);
    return { valid: true, data };
  } catch (error) {
    const errorMessage =
      error instanceof z.ZodError
        ? error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`).join('; ')
        : String(error);
    return { valid: false, error: errorMessage };
  }
}

/**
 * Evaluates a condition against data
 *
 * @param condition - Condition to evaluate
 * @param data - Data to evaluate against
 * @returns True if condition matches
 */
export function evaluateCondition(
  condition: Condition | ConditionGroup,
  data: any
): boolean {
  if ('operator' in condition && (condition.operator === 'and' || condition.operator === 'or' || condition.operator === 'not')) {
    // It's a ConditionGroup
    const group = condition as ConditionGroup;

    if (group.operator === 'and') {
      return group.conditions.every(c => evaluateCondition(c, data));
    } else if (group.operator === 'or') {
      return group.conditions.some(c => evaluateCondition(c, data));
    } else if (group.operator === 'not') {
      return !group.conditions.every(c => evaluateCondition(c, data));
    }
  }

  // It's a simple Condition
  const cond = condition as Condition;
  const value = getNestedValue(data, cond.field);

  switch (cond.operator) {
    case 'eq':
      return value === cond.value;
    case 'ne':
      return value !== cond.value;
    case 'gt':
      return value > cond.value;
    case 'gte':
      return value >= cond.value;
    case 'lt':
      return value < cond.value;
    case 'lte':
      return value <= cond.value;
    case 'in':
      return Array.isArray(cond.value) && cond.value.includes(value);
    case 'nin':
      return Array.isArray(cond.value) && !cond.value.includes(value);
    case 'regex':
      return typeof cond.value === 'string' && new RegExp(cond.value).test(String(value));
    case 'exists':
      return value !== undefined && value !== null;
    default:
      return false;
  }
}

/**
 * Gets nested value from object using dot notation
 *
 * @param obj - Object to extract from
 * @param path - Dot-notation path
 * @returns Value at path
 */
function getNestedValue(obj: any, path: string): any {
  return path.split('.').reduce((current, key) => current?.[key], obj);
}
