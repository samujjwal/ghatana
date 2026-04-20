/**
 * Exponential Backoff Retry Service
 *
 * Manages job retry logic with exponential backoff including:
 * - Configurable max retries and backoff strategy
 * - Jitter to prevent thundering herd
 * - Circuit breaker pattern for failing services
 * - Dead letter queue for permanently failed jobs
 *
 * @doc.type service
 * @doc.purpose Retry failed jobs with exponential backoff
 * @doc.layer product
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export interface RetryConfig {
  maxRetries: number;
  baseDelayMs: number;
  maxDelayMs: number;
  backoffMultiplier: number;
  jitterFactor: number;
  retryableErrors: string[];
  nonRetryableErrors: string[];
}

export interface CircuitBreakerConfig {
  failureThreshold: number;
  resetTimeoutMs: number;
  halfOpenMaxCalls: number;
}

export interface RetryAttempt {
  attemptNumber: number;
  scheduledAt: Date;
  executedAt?: Date;
  error?: string;
  result?: "success" | "failure" | "timeout";
}

export interface JobRetryState {
  jobId: string;
  retryCount: number;
  nextRetryAt?: Date;
  attempts: RetryAttempt[];
  circuitBreakerState: "closed" | "open" | "half-open";
  consecutiveFailures: number;
  lastFailureAt?: Date;
  deadLetteredAt?: Date;
}

export const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxRetries: 5,
  baseDelayMs: 1000,
  maxDelayMs: 300000, // 5 minutes
  backoffMultiplier: 2,
  jitterFactor: 0.1,
  retryableErrors: [
    "ECONNRESET",
    "ETIMEDOUT",
    "ENOTFOUND",
    "EAI_AGAIN",
    "NETWORK_ERROR",
    "RATE_LIMIT",
    "SERVICE_UNAVAILABLE",
    "TIMEOUT",
  ],
  nonRetryableErrors: [
    "VALIDATION_ERROR",
    "UNAUTHORIZED",
    "FORBIDDEN",
    "NOT_FOUND",
    "INVALID_INPUT",
    "BUSINESS_RULE_VIOLATION",
  ],
};

export const DEFAULT_CIRCUIT_BREAKER_CONFIG: CircuitBreakerConfig = {
  failureThreshold: 5,
  resetTimeoutMs: 60000, // 1 minute
  halfOpenMaxCalls: 3,
};

export class RetryService {
  private retryStates: Map<string, JobRetryState> = new Map();
  private circuitBreakers: Map<string, {
    state: "closed" | "open" | "half-open";
    failures: number;
    lastFailure: Date;
    halfOpenCalls: number;
  }> = new Map();

  constructor(
    private readonly prisma: PrismaClient,
    private config: RetryConfig = DEFAULT_RETRY_CONFIG,
    private circuitBreakerConfig: CircuitBreakerConfig = DEFAULT_CIRCUIT_BREAKER_CONFIG,
  ) {}

  /**
   * Calculate next retry time with exponential backoff and jitter
   */
  calculateNextRetryTime(retryCount: number, baseDelay?: number): Date {
    const delay = this.calculateDelay(retryCount, baseDelay);
    return new Date(Date.now() + delay);
  }

  /**
   * Calculate delay in milliseconds
   */
  calculateDelay(retryCount: number, baseDelay?: number): number {
    const base = baseDelay ?? this.config.baseDelayMs;

    // Exponential backoff: base * (multiplier ^ retryCount)
    const exponentialDelay = base * Math.pow(this.config.backoffMultiplier, retryCount);

    // Cap at max delay
    const cappedDelay = Math.min(exponentialDelay, this.config.maxDelayMs);

    // Add jitter to prevent thundering herd
    const jitter = cappedDelay * this.config.jitterFactor * (Math.random() * 2 - 1);

    return Math.floor(cappedDelay + jitter);
  }

  /**
   * Check if error is retryable
   */
  isRetryableError(error: Error | string): boolean {
    const errorMessage = typeof error === "string" ? error : error.message;
    const errorCode = typeof error === "string" ? error : (error as { code?: string }).code;

    // Check non-retryable errors first (explicit failures)
    for (const nonRetryable of this.config.nonRetryableErrors) {
      if (errorMessage.includes(nonRetryable) || errorCode === nonRetryable) {
        return false;
      }
    }

    // Check retryable errors
    for (const retryable of this.config.retryableErrors) {
      if (errorMessage.includes(retryable) || errorCode === retryable) {
        return true;
      }
    }

    // Default: retry on unknown errors (conservative)
    return true;
  }

  /**
   * Check if job can be retried
   */
  canRetry(jobId: string): { canRetry: boolean; reason?: string; nextRetryAt?: Date } {
    const state = this.retryStates.get(jobId);

    if (!state) {
      return { canRetry: true };
    }

    // Check max retries
    if (state.retryCount >= this.config.maxRetries) {
      return {
        canRetry: false,
        reason: `Maximum retry attempts (${this.config.maxRetries}) exceeded`,
      };
    }

    // Check circuit breaker
    const serviceKey = this.getServiceKey(jobId);
    const circuitState = this.circuitBreakers.get(serviceKey);

    if (circuitState?.state === "open") {
      const timeSinceLastFailure = Date.now() - circuitState.lastFailure.getTime();

      if (timeSinceLastFailure < this.circuitBreakerConfig.resetTimeoutMs) {
        const resetAt = new Date(circuitState.lastFailure.getTime() + this.circuitBreakerConfig.resetTimeoutMs);
        return {
          canRetry: false,
          reason: "Circuit breaker is open - service temporarily unavailable",
          nextRetryAt: resetAt,
        };
      }

      // Transition to half-open
      circuitState.state = "half-open";
      circuitState.halfOpenCalls = 0;
    }

    if (circuitState?.state === "half-open" && circuitState.halfOpenCalls >= this.circuitBreakerConfig.halfOpenMaxCalls) {
      return {
        canRetry: false,
        reason: "Circuit breaker half-open limit reached",
        nextRetryAt: new Date(Date.now() + this.circuitBreakerConfig.resetTimeoutMs),
      };
    }

    return {
      canRetry: true,
      ...(state.nextRetryAt ? { nextRetryAt: state.nextRetryAt } : {}),
    };
  }

  /**
   * Record a retry attempt
   */
  async recordRetryAttempt(
    jobId: string,
    attempt: Omit<RetryAttempt, "attemptNumber">,
  ): Promise<JobRetryState> {
    let state = this.retryStates.get(jobId);

    if (!state) {
      state = this.createInitialState(jobId);
      this.retryStates.set(jobId, state);
    }

    const attemptNumber = state.attempts.length + 1;
    const fullAttempt: RetryAttempt = { ...attempt, attemptNumber };
    state.attempts.push(fullAttempt);

    if (attempt.result === "failure") {
      state.retryCount++;
      state.consecutiveFailures++;
      state.lastFailureAt = new Date();

      // Update circuit breaker
      this.recordFailure(jobId);

      // Schedule next retry if allowed
      if (state.retryCount < this.config.maxRetries) {
        state.nextRetryAt = this.calculateNextRetryTime(state.retryCount);
      }
    } else if (attempt.result === "success") {
      state.consecutiveFailures = 0;
      this.recordSuccess(jobId);
    }

    // Persist state
    await this.persistRetryState(state);

    return state;
  }

  /**
   * Move job to dead letter queue after max retries
   */
  async moveToDeadLetter(jobId: string, reason: string): Promise<void> {
    const state = this.retryStates.get(jobId);
    if (!state) return;

    state.deadLetteredAt = new Date();

    // Persist to dead letter queue
    await this.prisma.$executeRaw`
      INSERT INTO "DeadLetterJob" (
        job_id, retry_state, failure_reason, dead_lettered_at, created_at
      ) VALUES (
        ${jobId},
        ${JSON.stringify(state)}::jsonb,
        ${reason},
        ${state.deadLetteredAt},
        NOW()
      )
    `.catch(() => {
      console.error(`[DEAD LETTER] Job ${jobId}: ${reason}`);
    });

    // Clean up retry state
    this.retryStates.delete(jobId);
  }

  /**
   * Get retry state for job
   */
  getRetryState(jobId: string): JobRetryState | undefined {
    return this.retryStates.get(jobId);
  }

  /**
   * Get all pending retries
   */
  getPendingRetries(): Array<{ jobId: string; nextRetryAt: Date; retryCount: number }> {
    const pending: Array<{ jobId: string; nextRetryAt: Date; retryCount: number }> = [];

    for (const [jobId, state] of this.retryStates) {
      if (state.nextRetryAt && !state.deadLetteredAt) {
        pending.push({
          jobId,
          nextRetryAt: state.nextRetryAt,
          retryCount: state.retryCount,
        });
      }
    }

    return pending.sort((a, b) => a.nextRetryAt.getTime() - b.nextRetryAt.getTime());
  }

  /**
   * Get jobs ready for retry
   */
  getReadyRetries(): string[] {
    const now = Date.now();
    const ready: string[] = [];

    for (const [jobId, state] of this.retryStates) {
      if (state.nextRetryAt && state.nextRetryAt.getTime() <= now && !state.deadLetteredAt) {
        const canRetry = this.canRetry(jobId);
        if (canRetry.canRetry) {
          ready.push(jobId);
        }
      }
    }

    return ready;
  }

  /**
   * Reset retry state for a job
   */
  resetRetryState(jobId: string): void {
    this.retryStates.delete(jobId);
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<RetryConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   */
  getConfig(): RetryConfig {
    return { ...this.config };
  }

  /**
   * Get circuit breaker status
   */
  getCircuitBreakerStatus(): Array<{
    service: string;
    state: "closed" | "open" | "half-open";
    failures: number;
    lastFailure?: Date;
  }> {
    const status: Array<{
      service: string;
      state: "closed" | "open" | "half-open";
      failures: number;
      lastFailure?: Date;
    }> = [];

    for (const [service, breaker] of this.circuitBreakers) {
      status.push({
        service,
        state: breaker.state,
        failures: breaker.failures,
        lastFailure: breaker.lastFailure,
      });
    }

    return status;
  }

  /**
   * Create initial retry state
   */
  private createInitialState(jobId: string): JobRetryState {
    return {
      jobId,
      retryCount: 0,
      attempts: [],
      circuitBreakerState: "closed",
      consecutiveFailures: 0,
    };
  }

  /**
   * Record a failure in circuit breaker
   */
  private recordFailure(jobId: string): void {
    const serviceKey = this.getServiceKey(jobId);
    let breaker = this.circuitBreakers.get(serviceKey);

    if (!breaker) {
      breaker = {
        state: "closed",
        failures: 0,
        lastFailure: new Date(),
        halfOpenCalls: 0,
      };
      this.circuitBreakers.set(serviceKey, breaker);
    }

    breaker.failures++;
    breaker.lastFailure = new Date();

    if (breaker.state === "half-open") {
      // Failure in half-open state goes back to open
      breaker.state = "open";
      breaker.halfOpenCalls = 0;
    } else if (breaker.failures >= this.circuitBreakerConfig.failureThreshold) {
      // Threshold exceeded, open circuit
      breaker.state = "open";
    }
  }

  /**
   * Record a success in circuit breaker
   */
  private recordSuccess(jobId: string): void {
    const serviceKey = this.getServiceKey(jobId);
    const breaker = this.circuitBreakers.get(serviceKey);

    if (!breaker) return;

    if (breaker.state === "half-open") {
      breaker.halfOpenCalls++;

      // If enough successes in half-open, close the circuit
      if (breaker.halfOpenCalls >= this.circuitBreakerConfig.halfOpenMaxCalls) {
        breaker.state = "closed";
        breaker.failures = 0;
        breaker.halfOpenCalls = 0;
      }
    } else if (breaker.state === "closed") {
      // Reset failures on success
      breaker.failures = 0;
    }
  }

  /**
   * Get service key for circuit breaker
   */
  private getServiceKey(jobId: string): string {
    // Extract service type from jobId or use default
    // Format: jobType_timestamp_random
    const parts = jobId.split("_");
    return parts[0] ?? "default";
  }

  /**
   * Persist retry state to database
   */
  private async persistRetryState(state: JobRetryState): Promise<void> {
    await this.prisma.$executeRaw`
      INSERT INTO "JobRetryState" (
        job_id, retry_count, next_retry_at, attempts,
        circuit_breaker_state, consecutive_failures, last_failure_at, updated_at
      ) VALUES (
        ${state.jobId},
        ${state.retryCount},
        ${state.nextRetryAt ?? null},
        ${JSON.stringify(state.attempts)}::jsonb,
        ${state.circuitBreakerState},
        ${state.consecutiveFailures},
        ${state.lastFailureAt ?? null},
        NOW()
      )
      ON CONFLICT (job_id)
      DO UPDATE SET
        retry_count = ${state.retryCount},
        next_retry_at = ${state.nextRetryAt ?? null},
        attempts = ${JSON.stringify(state.attempts)}::jsonb,
        circuit_breaker_state = ${state.circuitBreakerState},
        consecutive_failures = ${state.consecutiveFailures},
        last_failure_at = ${state.lastFailureAt ?? null},
        updated_at = NOW()
    `.catch(() => {
      // Table might not exist, state is in memory only
    });
  }

  /**
   * Load retry states from database on startup
   */
  async loadRetryStates(): Promise<void> {
    const states = await this.prisma.$queryRaw<Array<{
      job_id: string;
      retry_count: number;
      next_retry_at: Date | null;
      attempts: string;
      circuit_breaker_state: string;
      consecutive_failures: number;
      last_failure_at: Date | null;
    }>>`
      SELECT 
        job_id,
        retry_count,
        next_retry_at,
        attempts::text,
        circuit_breaker_state,
        consecutive_failures,
        last_failure_at
      FROM "JobRetryState"
      WHERE dead_lettered_at IS NULL
    `.catch(() => []);

    for (const state of states) {
      const retryState: JobRetryState = {
        jobId: state.job_id,
        retryCount: state.retry_count,
        attempts: JSON.parse(state.attempts) as RetryAttempt[],
        circuitBreakerState: state.circuit_breaker_state as JobRetryState["circuitBreakerState"],
        consecutiveFailures: state.consecutive_failures,
        ...(state.next_retry_at ? { nextRetryAt: state.next_retry_at } : {}),
        ...(state.last_failure_at ? { lastFailureAt: state.last_failure_at } : {}),
      };

      this.retryStates.set(state.job_id, retryState);
    }

    console.log(`Loaded ${this.retryStates.size} retry states from database`);
  }
}
