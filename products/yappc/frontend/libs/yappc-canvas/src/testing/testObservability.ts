/**
 * Test Observability
 * 
 * Provides test debugging infrastructure including debug logging,
 * artifact retention, failure investigation guides, and CI integration.
 * 
 * Features:
 * - Structured debug logging
 * - Test artifact management
 * - Failure investigation guides
 * - Performance tracking
 * - CI integration and reporting
 * 
 * @module testing/testObservability
 */

/**
 * Log level
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

/**
 * Log entry
 */
export interface LogEntry {
  /**
   * Log timestamp
   */
  timestamp: number;

  /**
   * Log level
   */
  level: LogLevel;

  /**
   * Log message
   */
  message: string;

  /**
   * Additional context
   */
  context?: Record<string, unknown>;

  /**
   * Test name
   */
  testName?: string;

  /**
   * Error stack trace
   */
  stack?: string;
}

/**
 * Test artifact
 */
export interface TestArtifact {
  /**
   * Artifact type
   */
  type: 'screenshot' | 'trace' | 'video' | 'log' | 'coverage' | 'profiling';

  /**
   * Artifact path
   */
  path: string;

  /**
   * Test name
   */
  testName: string;

  /**
   * Artifact size (bytes)
   */
  size?: number;

  /**
   * Creation timestamp
   */
  timestamp: number;

  /**
   * Additional metadata
   */
  metadata?: Record<string, unknown>;
}

/**
 * Test failure information
 */
export interface TestFailure {
  /**
   * Test name
   */
  testName: string;

  /**
   * Failure message
   */
  message: string;

  /**
   * Stack trace
   */
  stack?: string;

  /**
   * Failure timestamp
   */
  timestamp: number;

  /**
   * Test duration (ms)
   */
  duration: number;

  /**
   * Related artifacts
   */
  artifacts: TestArtifact[];

  /**
   * Failure context
   */
  context?: Record<string, unknown>;

  /**
   * Investigation guide
   */
  investigationGuide?: string[];
}

/**
 * Performance metrics
 */
export interface PerformanceMetrics {
  /**
   * Test name
   */
  testName: string;

  /**
   * Total duration (ms)
   */
  duration: number;

  /**
   * Setup duration (ms)
   */
  setupDuration?: number;

  /**
   * Teardown duration (ms)
   */
  teardownDuration?: number;

  /**
   * Memory usage (bytes)
   */
  memoryUsage?: {
    heapUsed: number;
    heapTotal: number;
    external: number;
  };

  /**
   * Custom metrics
   */
  customMetrics?: Record<string, number>;

  /**
   * Timestamp
   */
  timestamp: number;
}

/**
 * Observability configuration
 */
export interface ObservabilityConfig {
  /**
   * Whether debug logging is enabled
   */
  debugEnabled?: boolean;

  /**
   * Minimum log level to capture
   */
  logLevel?: LogLevel;

  /**
   * Artifact retention days
   */
  artifactRetentionDays?: number;

  /**
   * Artifact output directory
   */
  artifactDir?: string;

  /**
   * Whether to capture performance metrics
   */
  capturePerformance?: boolean;

  /**
   * Whether to auto-generate investigation guides
   */
  autoInvestigationGuides?: boolean;

  /**
   * Maximum log entries to retain
   */
  maxLogEntries?: number;

  /**
   * CI integration options
   */
  ci?: {
    /**
     * Whether running in CI
     */
    enabled: boolean;

    /**
     * CI provider
     */
    provider?: 'github' | 'gitlab' | 'jenkins' | 'other';

    /**
     * Artifact upload command
     */
    uploadCommand?: string;
  };
}

/**
 * Investigation guide template
 */
export interface InvestigationGuide {
  /**
   * Failure pattern (regex)
   */
  pattern: RegExp;

  /**
   * Guide title
   */
  title: string;

  /**
   * Investigation steps
   */
  steps: string[];

  /**
   * Related documentation
   */
  documentation?: string[];

  /**
   * Common causes
   */
  commonCauses?: string[];
}

/**
 * Test Observability Manager
 * 
 * Manages test debugging infrastructure including logging, artifacts,
 * failure investigation, and CI integration.
 */
export class TestObservabilityManager {
  private config: Required<ObservabilityConfig>;
  private logs: LogEntry[] = [];
  private artifacts = new Map<string, TestArtifact[]>();
  private failures = new Map<string, TestFailure>();
  private performance = new Map<string, PerformanceMetrics>();
  private investigationGuides: InvestigationGuide[] = [];

  /**
   *
   */
  constructor(config: ObservabilityConfig = {}) {
    this.config = {
      debugEnabled: config.debugEnabled ?? false,
      logLevel: config.logLevel ?? 'info',
      artifactRetentionDays: config.artifactRetentionDays ?? 30,
      artifactDir: config.artifactDir ?? './test-artifacts',
      capturePerformance: config.capturePerformance ?? true,
      autoInvestigationGuides: config.autoInvestigationGuides ?? true,
      maxLogEntries: config.maxLogEntries ?? 10000,
      ci: {
        enabled: config.ci?.enabled ?? false,
        provider: config.ci?.provider ?? 'github',
        uploadCommand: config.ci?.uploadCommand,
      },
    };

    this.initializeInvestigationGuides();
  }

  /**
   * Get current configuration
   */
  getConfig(): Readonly<Required<ObservabilityConfig>> {
    return { ...this.config, ci: { ...this.config.ci } };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<ObservabilityConfig>): void {
    this.config = {
      ...this.config,
      ...updates,
      ci: {
        ...this.config.ci,
        ...(updates.ci ?? {}),
      },
    };
  }

  /**
   * Log message
   */
  log(level: LogLevel, message: string, options?: {
    context?: Record<string, unknown>;
    testName?: string;
    stack?: string;
  }): void {
    // Check if logging should be captured
    const levels: LogLevel[] = ['debug', 'info', 'warn', 'error'];
    const configLevelIndex = levels.indexOf(this.config.logLevel);
    const messageLevelIndex = levels.indexOf(level);

    if (messageLevelIndex < configLevelIndex) {
      return;
    }

    const entry: LogEntry = {
      timestamp: Date.now(),
      level,
      message,
      context: options?.context,
      testName: options?.testName,
      stack: options?.stack,
    };

    this.logs.push(entry);

    // Trim logs if exceeding max
    if (this.logs.length > this.config.maxLogEntries) {
      this.logs = this.logs.slice(-this.config.maxLogEntries);
    }

    // Output to console if debug enabled
    if (this.config.debugEnabled) {
      const prefix = `[${level.toUpperCase()}]`;
      const testPrefix = options?.testName ? ` [${options.testName}]` : '';
      console.log(`${prefix}${testPrefix} ${message}`);
      if (options?.context) {
        console.log('Context:', options.context);
      }
      if (options?.stack) {
        console.error('Stack:', options.stack);
      }
    }
  }

  /**
   * Debug log
   */
  debug(message: string, options?: Parameters<TestObservabilityManager['log']>[2]): void {
    this.log('debug', message, options);
  }

  /**
   * Info log
   */
  info(message: string, options?: Parameters<TestObservabilityManager['log']>[2]): void {
    this.log('info', message, options);
  }

  /**
   * Warn log
   */
  warn(message: string, options?: Parameters<TestObservabilityManager['log']>[2]): void {
    this.log('warn', message, options);
  }

  /**
   * Error log
   */
  error(message: string, options?: Parameters<TestObservabilityManager['log']>[2]): void {
    this.log('error', message, options);
  }

  /**
   * Get logs
   */
  getLogs(filter?: {
    level?: LogLevel;
    testName?: string;
    since?: number;
  }): LogEntry[] {
    let filtered = [...this.logs];

    if (filter?.level) {
      filtered = filtered.filter((log) => log.level === filter.level);
    }

    if (filter?.testName) {
      filtered = filtered.filter((log) => log.testName === filter.testName);
    }

    if (filter?.since) {
      filtered = filtered.filter((log) => log.timestamp >= filter.since);
    }

    return filtered;
  }

  /**
   * Register artifact
   */
  registerArtifact(artifact: TestArtifact): void {
    const artifacts = this.artifacts.get(artifact.testName) ?? [];
    artifacts.push(artifact);
    this.artifacts.set(artifact.testName, artifacts);

    this.log('debug', `Artifact registered: ${artifact.type} at ${artifact.path}`, {
      testName: artifact.testName,
      context: { artifactType: artifact.type, path: artifact.path },
    });
  }

  /**
   * Get artifacts for test
   */
  getArtifacts(testName?: string): TestArtifact[] {
    if (testName) {
      return this.artifacts.get(testName) ?? [];
    }

    return Array.from(this.artifacts.values()).flat();
  }

  /**
   * Clean up old artifacts
   */
  cleanupArtifacts(olderThanDays?: number): number {
    const days = olderThanDays ?? this.config.artifactRetentionDays;
    const cutoffTime = Date.now() - days * 24 * 60 * 60 * 1000;
    let removedCount = 0;

    for (const [testName, artifacts] of this.artifacts) {
      const filtered = artifacts.filter((artifact) => {
        const shouldRemove = artifact.timestamp < cutoffTime;
        if (shouldRemove) removedCount++;
        return !shouldRemove;
      });

      if (filtered.length === 0) {
        this.artifacts.delete(testName);
      } else {
        this.artifacts.set(testName, filtered);
      }
    }

    this.log('info', `Cleaned up ${removedCount} old artifact(s)`);
    return removedCount;
  }

  /**
   * Record test failure
   */
  recordFailure(failure: TestFailure): void {
    this.failures.set(failure.testName, failure);

    this.log('error', `Test failed: ${failure.testName}`, {
      testName: failure.testName,
      context: {
        message: failure.message,
        duration: failure.duration,
        artifactCount: failure.artifacts.length,
      },
      stack: failure.stack,
    });

    // Auto-generate investigation guide if enabled
    if (this.config.autoInvestigationGuides) {
      const guide = this.generateInvestigationGuide(failure);
      if (guide) {
        failure.investigationGuide = guide;
      }
    }
  }

  /**
   * Get test failure
   */
  getFailure(testName: string): TestFailure | undefined {
    return this.failures.get(testName);
  }

  /**
   * Get all failures
   */
  getAllFailures(): TestFailure[] {
    return Array.from(this.failures.values());
  }

  /**
   * Record performance metrics
   */
  recordPerformance(metrics: PerformanceMetrics): void {
    this.performance.set(metrics.testName, metrics);

    this.log('debug', `Performance metrics recorded for ${metrics.testName}`, {
      testName: metrics.testName,
      context: {
        duration: metrics.duration,
        setupDuration: metrics.setupDuration,
        teardownDuration: metrics.teardownDuration,
      },
    });

    // Check for slow tests
    if (metrics.duration > 10000) {
      this.log('warn', `Slow test detected: ${metrics.testName} (${metrics.duration}ms)`, {
        testName: metrics.testName,
      });
    }
  }

  /**
   * Get performance metrics
   */
  getPerformance(testName?: string): PerformanceMetrics[] {
    if (testName) {
      const metrics = this.performance.get(testName);
      return metrics ? [metrics] : [];
    }

    return Array.from(this.performance.values());
  }

  /**
   * Register investigation guide
   */
  registerInvestigationGuide(guide: InvestigationGuide): void {
    this.investigationGuides.push(guide);
  }

  /**
   * Generate investigation guide for failure
   */
  generateInvestigationGuide(failure: TestFailure): string[] | undefined {
    for (const guide of this.investigationGuides) {
      if (guide.pattern.test(failure.message) || (failure.stack && guide.pattern.test(failure.stack))) {
        const steps: string[] = [
          `# Investigation Guide: ${guide.title}`,
          '',
          '## Steps to Investigate:',
          ...guide.steps.map((step, i) => `${i + 1}. ${step}`),
        ];

        if (guide.commonCauses) {
          steps.push('', '## Common Causes:');
          steps.push(...guide.commonCauses.map((cause) => `- ${cause}`));
        }

        if (guide.documentation) {
          steps.push('', '## Documentation:');
          steps.push(...guide.documentation.map((doc) => `- ${doc}`));
        }

        return steps;
      }
    }

    return undefined;
  }

  /**
   * Export observability report as JSON
   */
  exportReportJSON(): string {
    return JSON.stringify(
      {
        config: this.config,
        logs: this.logs,
        artifacts: Object.fromEntries(this.artifacts),
        failures: Object.fromEntries(this.failures),
        performance: Object.fromEntries(this.performance),
        summary: {
          totalLogs: this.logs.length,
          totalArtifacts: Array.from(this.artifacts.values()).flat().length,
          totalFailures: this.failures.size,
          totalTests: this.performance.size,
        },
      },
      null,
      2
    );
  }

  /**
   * Export observability report as Markdown
   */
  exportReportMarkdown(): string {
    const lines: string[] = [];

    lines.push('# Test Observability Report\n');
    lines.push(`Generated: ${new Date().toISOString()}\n`);

    // Summary
    lines.push('## Summary\n');
    lines.push(`- Total Logs: ${this.logs.length}`);
    lines.push(`- Total Artifacts: ${Array.from(this.artifacts.values()).flat().length}`);
    lines.push(`- Total Failures: ${this.failures.size}`);
    lines.push(`- Total Tests: ${this.performance.size}\n`);

    // Failures
    if (this.failures.size > 0) {
      lines.push('## Failures\n');
      for (const [testName, failure] of this.failures) {
        lines.push(`### ❌ ${testName}\n`);
        lines.push(`- **Message**: ${failure.message}`);
        lines.push(`- **Duration**: ${failure.duration}ms`);
        lines.push(`- **Artifacts**: ${failure.artifacts.length}\n`);

        if (failure.investigationGuide) {
          lines.push('#### Investigation Guide\n');
          lines.push(...failure.investigationGuide);
          lines.push('');
        }

        if (failure.stack) {
          lines.push('#### Stack Trace\n');
          lines.push('```');
          lines.push(failure.stack);
          lines.push('```\n');
        }
      }
    }

    // Performance
    if (this.performance.size > 0) {
      lines.push('## Performance\n');
      const sorted = Array.from(this.performance.values()).sort(
        (a, b) => b.duration - a.duration
      );

      lines.push('| Test | Duration | Setup | Teardown |');
      lines.push('|------|----------|-------|----------|');
      for (const metrics of sorted) {
        lines.push(
          `| ${metrics.testName} | ${metrics.duration}ms | ${metrics.setupDuration ?? '-'}ms | ${metrics.teardownDuration ?? '-'}ms |`
        );
      }
      lines.push('');
    }

    // Recent logs
    if (this.logs.length > 0) {
      lines.push('## Recent Logs (Last 50)\n');
      const recentLogs = this.logs.slice(-50);
      for (const log of recentLogs) {
        const time = new Date(log.timestamp).toISOString();
        const level = log.level.toUpperCase().padEnd(5);
        const test = log.testName ? ` [${log.testName}]` : '';
        lines.push(`- \`${time}\` **${level}**${test}: ${log.message}`);
      }
      lines.push('');
    }

    return lines.join('\n');
  }

  /**
   * Generate CI report
   */
  generateCIReport(): {
    exitCode: number;
    summary: string;
    details: string;
  } {
    const failureCount = this.failures.size;
    const testCount = this.performance.size;
    const passCount = testCount - failureCount;

    const summary = failureCount === 0
      ? `✅ All ${testCount} tests passed`
      : `❌ ${failureCount}/${testCount} tests failed`;

    const details = [
      'Test Results:',
      `- Passed: ${passCount}`,
      `- Failed: ${failureCount}`,
      `- Total: ${testCount}`,
      '',
      'Artifacts:',
      `- Total: ${Array.from(this.artifacts.values()).flat().length}`,
      `- Location: ${this.config.artifactDir}`,
    ].join('\n');

    return {
      exitCode: failureCount > 0 ? 1 : 0,
      summary,
      details,
    };
  }

  /**
   * Clear logs
   */
  clearLogs(): void {
    this.logs = [];
  }

  /**
   * Clear artifacts
   */
  clearArtifacts(testName?: string): void {
    if (testName) {
      this.artifacts.delete(testName);
    } else {
      this.artifacts.clear();
    }
  }

  /**
   * Clear failures
   */
  clearFailures(testName?: string): void {
    if (testName) {
      this.failures.delete(testName);
    } else {
      this.failures.clear();
    }
  }

  /**
   * Clear performance metrics
   */
  clearPerformance(testName?: string): void {
    if (testName) {
      this.performance.delete(testName);
    } else {
      this.performance.clear();
    }
  }

  /**
   * Reset manager (preserves config and investigation guides)
   */
  reset(): void {
    this.logs = [];
    this.artifacts.clear();
    this.failures.clear();
    this.performance.clear();
  }

  /**
   * Initialize default investigation guides
   */
  private initializeInvestigationGuides(): void {
    this.investigationGuides = [
      {
        pattern: /timeout|timed out|exceeded.*timeout/i,
        title: 'Timeout Error',
        steps: [
          'Check if the test is waiting for an element that never appears',
          'Verify network requests are completing successfully',
          'Look for slow operations or infinite loops',
          'Consider increasing timeout if operation is legitimately slow',
        ],
        commonCauses: [
          'Element selector is incorrect',
          'API request is hanging',
          'Animation or transition is taking longer than expected',
        ],
        documentation: [
          'Playwright Timeouts: https://playwright.dev/docs/test-timeouts',
        ],
      },
      {
        pattern: /cannot find element|element not found|no such element/i,
        title: 'Element Not Found',
        steps: [
          'Verify the selector is correct',
          'Check if element is rendered conditionally',
          'Ensure page has fully loaded before querying',
          'Check if element is in shadow DOM or iframe',
        ],
        commonCauses: [
          'Incorrect CSS selector',
          'Element not yet rendered',
          'Element is in different frame',
        ],
        documentation: [
          'Playwright Selectors: https://playwright.dev/docs/selectors',
        ],
      },
      {
        pattern: /assertion.*failed|expected.*to be|to equal/i,
        title: 'Assertion Failure',
        steps: [
          'Check the actual vs expected values in the error',
          'Verify test data is correct',
          'Check if there are race conditions',
          'Review recent code changes',
        ],
        commonCauses: [
          'Incorrect test expectations',
          'Data has changed',
          'Race condition in async code',
        ],
      },
      {
        pattern: /network error|fetch failed|xhr error/i,
        title: 'Network Error',
        steps: [
          'Check if API endpoint is accessible',
          'Verify network mocks are configured correctly',
          'Check for CORS issues',
          'Review network logs',
        ],
        commonCauses: [
          'API is down',
          'Mock server not running',
          'Incorrect API URL',
        ],
      },
    ];
  }
}

/**
 * Create test observability manager
 */
export function createTestObservabilityManager(
  config?: ObservabilityConfig
): TestObservabilityManager {
  return new TestObservabilityManager(config);
}
