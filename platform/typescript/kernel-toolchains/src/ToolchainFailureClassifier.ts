/**
 * ToolchainFailureClassifier - classifies toolchain adapter failures for observability and remediation.
 *
 * This module provides a default implementation of LifecycleFailureClassifier for toolchain adapters,
 * categorizing failures into actionable categories for monitoring and remediation.
 *
 * @doc.type module
 * @doc.purpose Failure classification for toolchain adapters
 * @doc.layer kernel-toolchains
 * @doc.pattern Classifier
 */

import type {
  LifecycleFailureClassifier,
  ProductLifecyclePhase,
} from '@ghatana/kernel-product-contracts';
import type { ToolchainAdapterContext } from './ToolchainAdapter.js';

/**
 * Default failure classifier configuration.
 */
export interface FailureClassifierConfig {
  /**
   * Default timeout in milliseconds before classifying as timeout.
   */
  readonly defaultTimeoutMs: number;

  /**
   * Custom error patterns for classification.
   */
  readonly errorPatterns: ReadonlyMap<RegExp, LifecycleFailureClassifier['category']>;
}

/**
 * Default error patterns for common toolchain failures.
 */
const DEFAULT_ERROR_PATTERNS: ReadonlyMap<RegExp, LifecycleFailureClassifier['category']> = new Map([
  [/artifact not found/i, 'artifact'],
  [/build failed|compilation error/i, 'adapter'],
  [/test failed|assertion failed/i, 'adapter'],
  [/deploy|deployment failed/i, 'adapter'],
  [/dependency|cannot find module/i, 'dependency'],
  [/config|configuration/i, 'config'],
  [/validation|invalid/i, 'config'],
  [/ECONNREFUSED|ETIMEDOUT|ENOTFOUND/i, 'environment'],
  [/EACCES|EPERM|permission denied/i, 'environment'],
  [/ENOENT|no such file/i, 'environment'],
  [/EMFILE|ENFILE|too many open files/i, 'infrastructure'],
  [/ENOMEM|out of memory/i, 'infrastructure'],
  [/ENOSPC|no space left/i, 'infrastructure'],
  [/timeout|timed out/i, 'environment'],
]);

/**
 * Default failure classifier configuration.
 */
export const DEFAULT_FAILURE_CLASSIFIER_CONFIG: FailureClassifierConfig = {
  defaultTimeoutMs: 30000,
  errorPatterns: DEFAULT_ERROR_PATTERNS,
} as const satisfies FailureClassifierConfig;

/**
 * Toolchain failure classifier.
 */
export class ToolchainFailureClassifier {
  private readonly config: FailureClassifierConfig;

  constructor(config: Partial<FailureClassifierConfig> = {}) {
    this.config = {
      ...DEFAULT_FAILURE_CLASSIFIER_CONFIG,
      ...config,
      errorPatterns: config.errorPatterns ?? DEFAULT_ERROR_PATTERNS,
    };
  }

  /**
   * Classify a toolchain adapter failure.
   */
  async classify(
    error: Error,
    context: ToolchainAdapterContext,
  ): Promise<LifecycleFailureClassifier> {
    const errorMessage = error.message.toLowerCase();
    const errorStack = error.stack?.toLowerCase() ?? '';

    // Determine category based on error patterns
    let category: LifecycleFailureClassifier['category'] = 'unknown';
    for (const [pattern, cat] of this.config.errorPatterns) {
      if (pattern.test(errorMessage) || pattern.test(errorStack)) {
        category = cat;
        break;
      }
    }

    // Determine severity based on category and phase
    const severity = this.determineSeverity(category, context.phase);

    // Determine if retryable
    const retryable = this.isRetryable(category);

    // Determine if requires human intervention
    const requiresHumanIntervention = !retryable || severity === 'critical';

    // Generate remediation steps
    const remediationSteps = this.generateRemediation(category);

    // Extract error codes if available
    const relatedFailureCodes = this.extractErrorCodes(error);

    return {
      category,
      severity,
      retryable,
      requiresHumanIntervention,
      remediationSteps,
      relatedFailureCodes,
      component: context.surface.id as string,
    };
  }

  /**
   * Determine severity based on category and phase.
   */
  private determineSeverity(
    category: LifecycleFailureClassifier['category'],
    phase: ProductLifecyclePhase,
  ): LifecycleFailureClassifier['severity'] {
    // Critical failures that block deployment
    if (category === 'adapter' || category === 'gate') {
      return 'critical';
    }

    // High severity for deployment and promotion phases
    if ((phase === 'deploy' || phase === 'promote') && 
        (category === 'artifact' || category === 'environment')) {
      return 'critical';
    }

    // Medium severity for most failures
    if (category === 'dependency' || category === 'config') {
      return 'high';
    }

    // Low severity for transient issues
    if (category === 'environment' || category === 'infrastructure') {
      return 'medium';
    }

    return 'medium';
  }

  /**
   * Determine if failure is retryable.
   */
  private isRetryable(category: LifecycleFailureClassifier['category']): boolean {
    // Environment and infrastructure errors are typically retryable
    if (category === 'environment' || category === 'infrastructure') {
      return true;
    }

    // Adapter failures are typically not retryable without code changes
    if (category === 'adapter') {
      return false;
    }

    // Config errors are not retryable
    if (category === 'config') {
      return false;
    }

    return false;
  }

  /**
   * Generate remediation steps based on category.
   */
  private generateRemediation(
    category: LifecycleFailureClassifier['category'],
  ): string[] {
    const remediation: string[] = [];

    switch (category) {
      case 'adapter':
        remediation.push('Review adapter execution logs');
        remediation.push('Check for syntax errors or type mismatches');
        remediation.push('Verify all dependencies are available');
        break;

      case 'dependency':
        remediation.push('Run dependency installation (e.g., pnpm install)');
        remediation.push('Check lockfile for dependency conflicts');
        remediation.push('Verify package registry accessibility');
        break;

      case 'config':
        remediation.push('Review toolchain configuration files');
        remediation.push('Check environment variables and settings');
        remediation.push('Verify configuration schema is valid');
        break;

      case 'environment':
        remediation.push('Check environment setup and prerequisites');
        remediation.push('Verify required tools are installed');
        remediation.push('Check environment variables are set correctly');
        remediation.push('Retry the operation after checking network');
        break;

      case 'infrastructure':
        remediation.push('Check available disk space');
        remediation.push('Verify memory usage and limits');
        remediation.push('Close unnecessary processes to free resources');
        break;

      case 'artifact':
        remediation.push('Check artifact build process completed successfully');
        remediation.push('Verify artifact paths and naming conventions');
        remediation.push('Rebuild artifacts if necessary');
        break;

      case 'gate':
        remediation.push('Review gate evaluation criteria');
        remediation.push('Check gate configuration and thresholds');
        remediation.push('Verify gate prerequisites are met');
        break;

      default:
        remediation.push('Review error logs for additional context');
        remediation.push('Check system logs for related issues');
        remediation.push('Contact support if issue persists');
    }

    return remediation;
  }

  /**
   * Extract error codes from error message or stack.
   */
  private extractErrorCodes(error: Error): string[] {
    const codes: string[] = [];
    const message = error.message;

    // Extract error codes like "ECONNREFUSED", "ENOENT", etc.
    const errorCodePattern = /\b[A-Z]{3,}\b/g;
    const matches = message.match(errorCodePattern);
    if (matches) {
      codes.push(...matches);
    }

    return codes;
  }
}
