/**
 * @fileoverview Enhanced Process Contract System
 *
 * Extends the base process contract with additional capabilities for
 * advanced analytics, continuous collection, data transformation, and
 * complex analysis scenarios.
 *
 * @module contracts/process-enhanced
 * @since 2.1.0
 */

import { z } from 'zod';
import {
  ProcessContractSchema,
  DataSourceSchema,
  AnalysisOperationSchema,
  ReportDestinationSchema,
  ConditionSchema,
  ConditionGroupSchema,
} from './process';

// ============================================================================
// Extended Collection Features
// ============================================================================

/**
 * Continuous collection configuration
 * For long-running, always-on data collection
 */
export const ContinuousCollectionSchema = z.object({
  /** Enable continuous collection */
  enabled: z.boolean().default(false),
  /** Collection interval (milliseconds) */
  intervalMs: z.number().int().positive().default(5000),
  /** Buffer size before flush */
  bufferSize: z.number().int().positive().default(100),
  /** Auto-flush interval (milliseconds) */
  autoFlushMs: z.number().int().positive().default(30000),
  /** Pause on idle (no user activity) */
  pauseOnIdle: z.boolean().default(false),
  /** Idle threshold (milliseconds) */
  idleThresholdMs: z.number().int().positive().default(60000),
});

export type ContinuousCollection = z.infer<typeof ContinuousCollectionSchema>;

/**
 * Data preprocessing configuration
 * Transform/filter data before collection
 */
export const PreprocessingSchema = z.object({
  /** Enable preprocessing */
  enabled: z.boolean().default(false),
  /** Preprocessing steps */
  steps: z.array(z.object({
    /** Step type */
    type: z.enum(['filter', 'transform', 'validate', 'deduplicate', 'enrich']),
    /** Step configuration */
    config: z.record(z.string(), z.any()),
    /** Order of execution */
    order: z.number().int().default(100),
  })).default([]),
});

export type Preprocessing = z.infer<typeof PreprocessingSchema>;

/**
 * Enhanced data source with continuous collection and preprocessing
 */
export const EnhancedDataSourceSchema = DataSourceSchema.extend({
  /** Continuous collection settings */
  continuous: ContinuousCollectionSchema.optional(),
  /** Preprocessing configuration */
  preprocessing: PreprocessingSchema.optional(),
  /** Data quality thresholds */
  quality: z.object({
    /** Minimum completeness (0.0 to 1.0) */
    minCompleteness: z.number().min(0).max(1).optional(),
    /** Maximum staleness (milliseconds) */
    maxStalenessMs: z.number().int().positive().optional(),
    /** Require validation */
    requireValidation: z.boolean().default(false),
  }).optional(),
  /** Caching configuration */
  cache: z.object({
    /** Enable caching */
    enabled: z.boolean().default(false),
    /** Cache TTL (milliseconds) */
    ttlMs: z.number().int().positive().default(60000),
    /** Cache key strategy */
    keyStrategy: z.enum(['url', 'domain', 'custom']).default('url'),
  }).optional(),
});

export type EnhancedDataSource = z.infer<typeof EnhancedDataSourceSchema>;

// ============================================================================
// Extended Analysis Features
// ============================================================================

/**
 * Time-series analysis configuration
 */
export const TimeSeriesAnalysisSchema = z.object({
  /** Time window duration (milliseconds) */
  windowDurationMs: z.number().int().positive(),
  /** Window slide interval (milliseconds) */
  slideIntervalMs: z.number().int().positive().optional(),
  /** Aggregation functions */
  aggregations: z.array(z.enum(['count', 'sum', 'avg', 'min', 'max', 'stddev', 'percentile'])),
  /** Grouping fields */
  groupBy: z.array(z.string()).optional(),
  /** Percentiles to calculate (e.g., [50, 90, 99]) */
  percentiles: z.array(z.number().min(0).max(100)).optional(),
});

export type TimeSeriesAnalysis = z.infer<typeof TimeSeriesAnalysisSchema>;

/**
 * Statistical analysis configuration
 */
export const StatisticalAnalysisSchema = z.object({
  /** Statistical tests to perform */
  tests: z.array(z.enum([
    'mean', 'median', 'mode',
    'variance', 'stddev',
    'correlation', 'regression',
    'distribution', 'outliers'
  ])),
  /** Confidence level (0.0 to 1.0) */
  confidenceLevel: z.number().min(0).max(1).default(0.95),
  /** Fields to analyze */
  fields: z.array(z.string()),
});

export type StatisticalAnalysis = z.infer<typeof StatisticalAnalysisSchema>;

/**
 * Pattern detection configuration
 */
export const PatternDetectionSchema = z.object({
  /** Pattern types to detect */
  patterns: z.array(z.enum([
    'trend', 'seasonality', 'cycle',
    'spike', 'dip', 'anomaly',
    'correlation', 'causation'
  ])),
  /** Sensitivity (0.0 to 1.0) */
  sensitivity: z.number().min(0).max(1).default(0.5),
  /** Minimum pattern length */
  minLength: z.number().int().positive().default(3),
  /** Fields to analyze */
  fields: z.array(z.string()),
});

export type PatternDetection = z.infer<typeof PatternDetectionSchema>;

/**
 * Machine learning analysis configuration
 */
export const MLAnalysisSchema = z.object({
  /** ML algorithm */
  algorithm: z.enum([
    'clustering', 'classification', 'regression',
    'anomaly-detection', 'forecasting', 'recommendation'
  ]),
  /** Model configuration */
  model: z.object({
    /** Model type/variant */
    type: z.string(),
    /** Hyperparameters */
    parameters: z.record(z.string(), z.any()).default({}),
    /** Training configuration */
    training: z.object({
      /** Training data size */
      dataSize: z.number().int().positive().default(1000),
      /** Validation split (0.0 to 1.0) */
      validationSplit: z.number().min(0).max(1).default(0.2),
      /** Number of epochs */
      epochs: z.number().int().positive().default(10),
    }).optional(),
  }),
  /** Features to use */
  features: z.array(z.string()),
  /** Target variable (for supervised learning) */
  target: z.string().optional(),
});

export type MLAnalysis = z.infer<typeof MLAnalysisSchema>;

/**
 * Enhanced analysis operation with advanced analytics
 */
export const EnhancedAnalysisOperationSchema = AnalysisOperationSchema.extend({
  /** Time-series analysis */
  timeSeries: TimeSeriesAnalysisSchema.optional(),
  /** Statistical analysis */
  statistical: StatisticalAnalysisSchema.optional(),
  /** Pattern detection */
  patterns: PatternDetectionSchema.optional(),
  /** Machine learning */
  ml: MLAnalysisSchema.optional(),
  /** Output transformation */
  outputTransform: z.object({
    /** Transform type */
    type: z.enum(['map', 'reduce', 'filter', 'flatten', 'group']),
    /** Transform configuration */
    config: z.record(z.string(), z.any()),
  }).optional(),
  /** Caching configuration */
  cache: z.object({
    /** Enable result caching */
    enabled: z.boolean().default(false),
    /** Cache TTL (milliseconds) */
    ttlMs: z.number().int().positive().default(300000),
    /** Cache invalidation rules */
    invalidateOn: z.array(z.string()).optional(),
  }).optional(),
});

export type EnhancedAnalysisOperation = z.infer<typeof EnhancedAnalysisOperationSchema>;

// ============================================================================
// Extended Reporting Features
// ============================================================================

/**
 * Report scheduling configuration
 */
export const ReportSchedulingSchema = z.object({
  /** Enable scheduled reports */
  enabled: z.boolean().default(false),
  /** Schedule (cron expression) */
  schedule: z.string(),
  /** Report type */
  type: z.enum(['summary', 'detailed', 'dashboard', 'alert', 'custom']),
  /** Recipients (for notifications/emails) */
  recipients: z.array(z.string()).optional(),
  /** Time window for report */
  timeWindow: z.object({
    /** Window type */
    type: z.enum(['last-hour', 'last-day', 'last-week', 'last-month', 'custom']),
    /** Custom duration (milliseconds) */
    durationMs: z.number().int().positive().optional(),
  }).optional(),
});

export type ReportScheduling = z.infer<typeof ReportSchedulingSchema>;

/**
 * Data visualization configuration
 */
export const VisualizationSchema = z.object({
  /** Enable visualizations */
  enabled: z.boolean().default(false),
  /** Chart types to generate */
  charts: z.array(z.object({
    /** Chart type */
    type: z.enum(['line', 'bar', 'pie', 'scatter', 'heatmap', 'histogram', 'table']),
    /** Data configuration */
    data: z.object({
      /** X-axis field */
      x: z.string().optional(),
      /** Y-axis field(s) */
      y: z.union([z.string(), z.array(z.string())]).optional(),
      /** Series grouping */
      series: z.string().optional(),
    }),
    /** Chart options */
    options: z.record(z.string(), z.any()).optional(),
  })).default([]),
  /** Layout configuration */
  layout: z.enum(['grid', 'stack', 'tabs', 'custom']).default('grid'),
});

export type Visualization = z.infer<typeof VisualizationSchema>;

/**
 * Export configuration
 */
export const ExportConfigSchema = z.object({
  /** Enable export */
  enabled: z.boolean().default(false),
  /** Export formats */
  formats: z.array(z.enum(['json', 'csv', 'xlsx', 'pdf', 'html'])),
  /** Include raw data */
  includeRaw: z.boolean().default(true),
  /** Include visualizations */
  includeVisualizations: z.boolean().default(false),
  /** Compression */
  compress: z.boolean().default(false),
  /** Export destination */
  destination: z.enum(['download', 'storage', 'cloud', 'email']).default('download'),
});

export type ExportConfig = z.infer<typeof ExportConfigSchema>;

/**
 * Enhanced report destination with scheduling and visualization
 */
export const EnhancedReportDestinationSchema = ReportDestinationSchema.extend({
  /** Report scheduling */
  scheduling: ReportSchedulingSchema.optional(),
  /** Data visualization */
  visualization: VisualizationSchema.optional(),
  /** Export configuration */
  export: ExportConfigSchema.optional(),
  /** Incremental updates */
  incremental: z.object({
    /** Enable incremental updates */
    enabled: z.boolean().default(false),
    /** Update frequency (milliseconds) */
    frequencyMs: z.number().int().positive().default(60000),
    /** Delta calculation strategy */
    deltaStrategy: z.enum(['append', 'merge', 'replace']).default('append'),
  }).optional(),
});

export type EnhancedReportDestination = z.infer<typeof EnhancedReportDestinationSchema>;

// ============================================================================
// Process Lifecycle & Orchestration
// ============================================================================

/**
 * Process dependencies configuration
 */
export const ProcessDependenciesSchema = z.object({
  /** Required processes (must complete before this runs) */
  requires: z.array(z.string()).default([]),
  /** Conflicting processes (cannot run concurrently) */
  conflicts: z.array(z.string()).default([]),
  /** Dependent processes (trigger after completion) */
  triggers: z.array(z.string()).default([]),
  /** Data dependencies */
  data: z.array(z.object({
    /** Source process ID */
    processId: z.string(),
    /** Data key to consume */
    key: z.string(),
    /** Required data freshness (milliseconds) */
    maxAgeMs: z.number().int().positive().optional(),
  })).default([]),
});

export type ProcessDependencies = z.infer<typeof ProcessDependenciesSchema>;

/**
 * Process state management
 */
export const StateManagementSchema = z.object({
  /** Enable state persistence */
  persist: z.boolean().default(false),
  /** State storage key */
  storageKey: z.string().optional(),
  /** State snapshot frequency (milliseconds) */
  snapshotFrequencyMs: z.number().int().positive().default(60000),
  /** Maximum state history */
  maxHistory: z.number().int().positive().default(10),
  /** State recovery strategy */
  recoveryStrategy: z.enum(['latest', 'checkpoint', 'replay']).default('latest'),
});

export type StateManagement = z.infer<typeof StateManagementSchema>;

/**
 * Process monitoring configuration
 */
export const ProcessMonitoringSchema = z.object({
  /** Enable detailed monitoring */
  enabled: z.boolean().default(true),
  /** Metrics to collect */
  metrics: z.array(z.enum([
    'duration', 'throughput', 'errors',
    'memory', 'cpu', 'latency',
    'cache-hits', 'data-quality'
  ])).default(['duration', 'throughput', 'errors']),
  /** Alerts configuration */
  alerts: z.array(z.object({
    /** Alert name */
    name: z.string(),
    /** Metric to monitor */
    metric: z.string(),
    /** Threshold condition */
    condition: ConditionSchema,
    /** Alert severity */
    severity: z.enum(['low', 'medium', 'high', 'critical']),
    /** Notification channels */
    channels: z.array(z.enum(['console', 'notification', 'webhook', 'email'])),
  })).default([]),
  /** Performance baselines */
  baselines: z.record(z.string(), z.object({
    /** Expected value */
    expected: z.number(),
    /** Acceptable variance (percentage) */
    variance: z.number().min(0).max(1).default(0.2),
  })).optional(),
});

export type ProcessMonitoring = z.infer<typeof ProcessMonitoringSchema>;

// ============================================================================
// Enhanced Process Contract
// ============================================================================

/**
 * Complete Enhanced Process Contract
 *
 * Extends the base process contract with advanced features for
 * analytics, continuous collection, ML analysis, and complex orchestration.
 */
export const EnhancedProcessContractSchema = ProcessContractSchema.extend({
  /** Process dependencies */
  dependencies: ProcessDependenciesSchema.optional(),
  /** State management */
  stateManagement: StateManagementSchema.optional(),
  /** Process monitoring */
  monitoring: ProcessMonitoringSchema.optional(),
  /** Extended collect stage with enhanced sources */
  collect: z.object({
    id: z.string().default('collect'),
    sources: z.array(EnhancedDataSourceSchema).min(1),
    when: z.union([ConditionSchema, ConditionGroupSchema]).optional(),
    mergeStrategy: z.enum(['union', 'intersection', 'priority']).default('union'),
    timeoutMs: z.number().int().positive().default(10000),
    retry: z.object({
      maxAttempts: z.number().int().nonnegative().default(3),
      backoffMs: z.number().int().positive().default(1000),
    }).optional(),
    /** Continuous collection mode */
    continuous: ContinuousCollectionSchema.optional(),
  }),
  /** Extended analyze stage with enhanced operations */
  analyze: z.object({
    id: z.string().default('analyze'),
    operations: z.array(EnhancedAnalysisOperationSchema).min(1),
    when: z.union([ConditionSchema, ConditionGroupSchema]).optional(),
    parallel: z.boolean().default(false),
    timeoutMs: z.number().int().positive().default(15000),
    /** Enable analysis caching */
    cache: z.object({
      enabled: z.boolean().default(false),
      strategy: z.enum(['memory', 'disk', 'hybrid']).default('memory'),
      ttlMs: z.number().int().positive().default(300000),
    }).optional(),
  }),
  /** Extended report stage with enhanced destinations */
  report: z.object({
    id: z.string().default('report'),
    destinations: z.array(EnhancedReportDestinationSchema).min(1),
    when: z.union([ConditionSchema, ConditionGroupSchema]).optional(),
    aggregate: z.boolean().default(false),
    includeMetadata: z.boolean().default(true),
    includeSummary: z.boolean().default(true),
    timeoutMs: z.number().int().positive().default(10000),
    /** Report deduplication */
    deduplicate: z.object({
      enabled: z.boolean().default(false),
      strategy: z.enum(['exact', 'fuzzy', 'hash']).default('exact'),
      windowMs: z.number().int().positive().default(3600000),
    }).optional(),
  }),
});

export type EnhancedProcessContract = z.infer<typeof EnhancedProcessContractSchema>;

// ============================================================================
// Validation Helpers
// ============================================================================

/**
 * Validates an enhanced process contract
 */
export function validateEnhancedProcessContract(contract: unknown): {
  valid: boolean;
  error?: string;
  data?: EnhancedProcessContract;
} {
  try {
    const data = EnhancedProcessContractSchema.parse(contract);
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
 * Migrates a base process contract to enhanced contract
 */
export function migrateToEnhanced(baseContract: any): EnhancedProcessContract {
  return {
    ...baseContract,
    // Add default enhanced features if not present
    dependencies: baseContract.dependencies || {
      requires: [],
      conflicts: [],
      triggers: [],
      data: [],
    },
    stateManagement: baseContract.stateManagement || {
      persist: false,
    },
    monitoring: baseContract.monitoring || {
      enabled: true,
      metrics: ['duration', 'throughput', 'errors'],
      alerts: [],
    },
  };
}
