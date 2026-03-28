/**
 * Worker Error Recovery Utility
 * 
 * Provides retry logic, exponential backoff, and dead letter queue
 * functionality for background job processing.
 * 
 * @doc.type utility
 * @doc.purpose Worker error recovery and resilience
 * @doc.layer platform
 */

import { createStandaloneLogger } from './logger';

const logger = createStandaloneLogger({ component: 'WorkerErrorRecovery' });

export interface RetryConfig {
  maxRetries: number;
  initialDelayMs: number;
  maxDelayMs: number;
  backoffMultiplier: number;
  retryableErrors?: string[];
}

export interface JobMetadata {
  jobId: string;
  jobType: string;
  tenantId: string;
  attemptNumber: number;
  firstAttemptAt: Date;
  lastAttemptAt: Date;
  error?: string;
  payload: unknown;
}

export interface DeadLetterQueueEntry {
  id: string;
  jobMetadata: JobMetadata;
  finalError: string;
  failedAt: Date;
  canRetry: boolean;
}

const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxRetries: 3,
  initialDelayMs: 1000,
  maxDelayMs: 60000,
  backoffMultiplier: 2,
};

/**
 * Calculates exponential backoff delay
 */
export function calculateBackoffDelay(
  attemptNumber: number,
  config: RetryConfig = DEFAULT_RETRY_CONFIG,
): number {
  const delay = config.initialDelayMs * Math.pow(config.backoffMultiplier, attemptNumber - 1);
  return Math.min(delay, config.maxDelayMs);
}

/**
 * Determines if an error is retryable
 */
export function isRetryableError(
  error: Error,
  config: RetryConfig = DEFAULT_RETRY_CONFIG,
): boolean {
  // Network errors are typically retryable
  const networkErrors = ['ECONNREFUSED', 'ETIMEDOUT', 'ENOTFOUND', 'ECONNRESET'];
  
  // Temporary errors are retryable
  const temporaryErrors = ['UNAVAILABLE', 'DEADLINE_EXCEEDED', 'RESOURCE_EXHAUSTED'];
  
  // Check if error message contains retryable patterns
  const errorMessage = error.message.toUpperCase();
  const isNetworkError = networkErrors.some(code => errorMessage.includes(code));
  const isTemporaryError = temporaryErrors.some(code => errorMessage.includes(code));
  
  // Check custom retryable errors from config
  const isCustomRetryable = config.retryableErrors?.some(pattern => 
    errorMessage.includes(pattern.toUpperCase())
  ) ?? false;
  
  return isNetworkError || isTemporaryError || isCustomRetryable;
}

/**
 * Executes a job with retry logic and exponential backoff
 */
export async function executeWithRetry<T>(
  jobFn: () => Promise<T>,
  metadata: Omit<JobMetadata, 'attemptNumber' | 'firstAttemptAt' | 'lastAttemptAt'>,
  config: RetryConfig = DEFAULT_RETRY_CONFIG,
): Promise<T> {
  const fullMetadata: JobMetadata = {
    ...metadata,
    attemptNumber: 1,
    firstAttemptAt: new Date(),
    lastAttemptAt: new Date(),
  };

  let lastError: Error | null = null;

  for (let attempt = 1; attempt <= config.maxRetries + 1; attempt++) {
    try {
      fullMetadata.attemptNumber = attempt;
      fullMetadata.lastAttemptAt = new Date();

      logger.info({
        message: 'Executing job',
        jobId: fullMetadata.jobId,
        jobType: fullMetadata.jobType,
        attempt,
        maxRetries: config.maxRetries,
      });

      const result = await jobFn();

      if (attempt > 1) {
        logger.info({
          message: 'Job succeeded after retry',
          jobId: fullMetadata.jobId,
          jobType: fullMetadata.jobType,
          attempt,
          totalAttempts: attempt,
        });
      }

      return result;
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      fullMetadata.error = lastError.message;

      const isLastAttempt = attempt > config.maxRetries;
      const canRetry = isRetryableError(lastError, config);

      logger.warn({
        message: 'Job execution failed',
        jobId: fullMetadata.jobId,
        jobType: fullMetadata.jobType,
        attempt,
        maxRetries: config.maxRetries,
        error: lastError.message,
        canRetry,
        isLastAttempt,
      });

      if (isLastAttempt || !canRetry) {
        // Send to dead letter queue
        await sendToDeadLetterQueue(fullMetadata, lastError, canRetry);
        throw lastError;
      }

      // Calculate backoff delay and wait
      const delayMs = calculateBackoffDelay(attempt, config);
      logger.info({
        message: 'Retrying job after backoff',
        jobId: fullMetadata.jobId,
        delayMs,
        nextAttempt: attempt + 1,
      });

      await sleep(delayMs);
    }
  }

  // Should never reach here, but TypeScript needs this
  throw lastError || new Error('Job failed with unknown error');
}

/**
 * Sleep utility for backoff delays
 */
function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Sends failed job to dead letter queue
 * In production, this should write to a database or message queue
 */
async function sendToDeadLetterQueue(
  metadata: JobMetadata,
  error: Error,
  canRetry: boolean,
): Promise<void> {
  const dlqEntry: DeadLetterQueueEntry = {
    id: `dlq_${metadata.jobId}_${Date.now()}`,
    jobMetadata: metadata,
    finalError: error.message,
    failedAt: new Date(),
    canRetry,
  };

  logger.error({
    message: 'Job sent to dead letter queue',
    jobId: metadata.jobId,
    jobType: metadata.jobType,
    attempts: metadata.attemptNumber,
    error: error.message,
    canRetry,
    dlqId: dlqEntry.id,
  });

  // TODO: Implement actual DLQ persistence
  // For now, just log. In production, write to:
  // - Database table for DLQ entries
  // - Redis list for processing
  // - Message queue (RabbitMQ, SQS, etc.)
  
  // Example implementation:
  // await prisma.deadLetterQueue.create({ data: dlqEntry });
}

/**
 * Wrapper for content generation jobs with default retry config
 */
export async function executeContentJob<T>(
  jobFn: () => Promise<T>,
  metadata: Omit<JobMetadata, 'attemptNumber' | 'firstAttemptAt' | 'lastAttemptAt'>,
): Promise<T> {
  const contentJobConfig: RetryConfig = {
    maxRetries: 3,
    initialDelayMs: 2000,
    maxDelayMs: 30000,
    backoffMultiplier: 2,
    retryableErrors: ['AI_SERVICE_UNAVAILABLE', 'RATE_LIMIT_EXCEEDED'],
  };

  return executeWithRetry(jobFn, metadata, contentJobConfig);
}

/**
 * Wrapper for compliance jobs with conservative retry config
 */
export async function executeComplianceJob<T>(
  jobFn: () => Promise<T>,
  metadata: Omit<JobMetadata, 'attemptNumber' | 'firstAttemptAt' | 'lastAttemptAt'>,
): Promise<T> {
  const complianceJobConfig: RetryConfig = {
    maxRetries: 5,
    initialDelayMs: 1000,
    maxDelayMs: 60000,
    backoffMultiplier: 2,
    retryableErrors: ['DATABASE_LOCK', 'TRANSACTION_CONFLICT'],
  };

  return executeWithRetry(jobFn, metadata, complianceJobConfig);
}

/**
 * Circuit breaker state for job types
 */
class JobCircuitBreaker {
  private failureCount = 0;
  private lastFailureTime: number | null = null;
  private state: 'CLOSED' | 'OPEN' | 'HALF_OPEN' = 'CLOSED';

  constructor(
    private readonly threshold: number = 5,
    private readonly resetTimeoutMs: number = 60000,
  ) {}

  recordSuccess(): void {
    this.failureCount = 0;
    this.state = 'CLOSED';
  }

  recordFailure(): void {
    this.failureCount++;
    this.lastFailureTime = Date.now();

    if (this.failureCount >= this.threshold) {
      this.state = 'OPEN';
      logger.warn({
        message: 'Circuit breaker opened',
        failureCount: this.failureCount,
        threshold: this.threshold,
      });
    }
  }

  canExecute(): boolean {
    if (this.state === 'CLOSED') {
      return true;
    }

    if (this.state === 'OPEN') {
      const now = Date.now();
      if (this.lastFailureTime && now - this.lastFailureTime > this.resetTimeoutMs) {
        this.state = 'HALF_OPEN';
        logger.info({ message: 'Circuit breaker half-open, allowing test request' });
        return true;
      }
      return false;
    }

    // HALF_OPEN state - allow one request to test
    return true;
  }

  getState(): 'CLOSED' | 'OPEN' | 'HALF_OPEN' {
    return this.state;
  }
}

// Global circuit breakers for different job types
const circuitBreakers = new Map<string, JobCircuitBreaker>();

/**
 * Gets or creates a circuit breaker for a job type
 */
export function getCircuitBreaker(jobType: string): JobCircuitBreaker {
  if (!circuitBreakers.has(jobType)) {
    circuitBreakers.set(jobType, new JobCircuitBreaker());
  }
  return circuitBreakers.get(jobType)!;
}

/**
 * Executes a job with circuit breaker protection
 */
export async function executeWithCircuitBreaker<T>(
  jobFn: () => Promise<T>,
  metadata: Omit<JobMetadata, 'attemptNumber' | 'firstAttemptAt' | 'lastAttemptAt'>,
  config?: RetryConfig,
): Promise<T> {
  const breaker = getCircuitBreaker(metadata.jobType);

  if (!breaker.canExecute()) {
    const error = new Error(`Circuit breaker OPEN for job type: ${metadata.jobType}`);
    logger.error({
      message: 'Circuit breaker preventing execution',
      jobType: metadata.jobType,
      breakerState: breaker.getState(),
    });
    throw error;
  }

  try {
    const result = await executeWithRetry(jobFn, metadata, config);
    breaker.recordSuccess();
    return result;
  } catch (error) {
    breaker.recordFailure();
    throw error;
  }
}
