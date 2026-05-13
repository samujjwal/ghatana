import { ProductFailurePolicy } from '../domain/ProductLifecyclePhase.js';
import { ExecutionLogger } from '../domain/ProductLifecyclePhase.js';

/**
 * Execution failure handler
 */
export class ExecutionFailureHandler {
  private failurePolicy: ProductFailurePolicy;

  constructor(failurePolicy: ProductFailurePolicy) {
    this.failurePolicy = failurePolicy;
  }

  /**
   * Handle a step failure
   */
  async handleFailure(
    stepId: string,
    error: Error,
    logger: ExecutionLogger,
  ): Promise<FailureHandlingResult> {
    logger.error(`Step ${stepId} failed`, {
      error: error.message,
      stack: error.stack,
    });

    switch (this.failurePolicy.strategy) {
      case 'fail-closed':
        return {
          action: 'stop',
          reason: 'Fail-closed policy: stopping execution on first failure',
        };

      case 'fail-open':
        return {
          action: 'continue',
          reason: 'Fail-open policy: continuing execution despite failure',
        };

      case 'continue-on-error':
        return {
          action: 'continue',
          reason: 'Continue-on-error policy: continuing execution',
        };

      default:
        return {
          action: 'stop',
          reason: `Unknown failure policy: ${this.failurePolicy.strategy}`,
        };
    }
  }

  /**
   * Check if retry is possible
   */
  canRetry(attemptCount: number): boolean {
    if (!this.failurePolicy.retryConfig) {
      return false;
    }

    return attemptCount < this.failurePolicy.retryConfig.maxRetries;
  }

  /**
   * Get retry delay
   */
  getRetryDelay(attemptCount: number): number {
    if (!this.failurePolicy.retryConfig) {
      return 0;
    }

    const { maxRetries, backoffMs } = this.failurePolicy.retryConfig;
    return Math.min(backoffMs * Math.pow(2, attemptCount), backoffMs * maxRetries);
  }

  /**
   * Check if notification is required
   */
  shouldNotify(): boolean {
    return this.failurePolicy.notifyOnFailure === true;
  }
}

/**
 * Failure handling result
 */
export interface FailureHandlingResult {
  action: 'stop' | 'continue' | 'retry';
  reason: string;
}
