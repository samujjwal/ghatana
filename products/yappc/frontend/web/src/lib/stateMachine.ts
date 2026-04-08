/**
 * UI State Machine
 *
 * Comprehensive state management for UI states with circuit breaker patterns
 * and exponential backoff retry logic.
 *
 * @doc.type library
 * @doc.purpose UI state machine with circuit breaker and retry logic
 * @doc.layer product
 * @doc.pattern State Machine
 */

// ============================================================================
// UI State Types
// ============================================================================

/**
 * All possible UI states for the application
 */
export type UiState =
  | 'idle'
  | 'loading'
  | 'success'
  | 'empty'
  | 'partial'
  | 'stale'
  | 'validation_error'
  | 'permission_denied'
  | 'auth_failure'
  | 'conflict'
  | 'timeout'
  | 'rate_limit'
  | 'server_error'
  | 'offline'
  | 'retry'
  | 'background_refresh';

/**
 * State transition context
 */
export interface StateTransition {
  from: UiState;
  to: UiState;
  reason?: string;
  timestamp: number;
}

/**
 * Circuit breaker state
 */
export type CircuitBreakerState = 'closed' | 'open' | 'half-open';

/**
 * Circuit breaker configuration
 */
export interface CircuitBreakerConfig {
  failureThreshold: number;
  recoveryTimeout: number;
  onStateChange?: (state: CircuitBreakerState) => void;
}

/**
 * Circuit breaker state change listener
 */
export type CircuitBreakerListener = (state: CircuitBreakerState) => void;

/**
 * Retry configuration
 */
export interface RetryConfig {
  attempts: number;
  backoff: 'exponential' | 'linear';
  baseDelay: number;
  maxDelay?: number;
  onRetry?: (attempt: number, error: Error) => void;
}

// ============================================================================
// Circuit Breaker
// ============================================================================

/**
 * Circuit breaker implementation for API calls
 *
 * Prevents cascading failures by stopping requests when a service is failing.
 * Automatically transitions between closed, open, and half-open states.
 */
export class CircuitBreaker {
  private state: CircuitBreakerState = 'closed';
  private failureCount = 0;
  private lastFailureTime = 0;
  private nextAttemptTime = 0;
  private listeners: Set<CircuitBreakerListener> = new Set();

  constructor(private config: CircuitBreakerConfig) {}

  /**
   * Execute a function with circuit breaker protection
   */
  async execute<T>(fn: () => Promise<T>): Promise<T> {
    if (this.state === 'open') {
      if (Date.now() < this.nextAttemptTime) {
        throw new Error('Circuit breaker is OPEN - requests blocked');
      }
      this.transitionTo('half-open');
    }

    try {
      const result = await fn();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }

  /**
   * Get current circuit breaker state
   */
  getState(): CircuitBreakerState {
    return this.state;
  }

  /**
   * Reset circuit breaker to closed state
   */
  reset(): void {
    this.state = 'closed';
    this.failureCount = 0;
    this.lastFailureTime = 0;
    this.nextAttemptTime = 0;
  }

  /**
   * Subscribe to circuit breaker state changes
   */
  subscribe(listener: CircuitBreakerListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private onSuccess(): void {
    this.failureCount = 0;
    if (this.state === 'half-open') {
      this.transitionTo('closed');
    }
  }

  private onFailure(): void {
    this.failureCount++;
    this.lastFailureTime = Date.now();

    if (this.failureCount >= this.config.failureThreshold) {
      this.transitionTo('open');
      this.nextAttemptTime = Date.now() + this.config.recoveryTimeout;
    }
  }

  private transitionTo(newState: CircuitBreakerState): void {
    if (this.state !== newState) {
      this.state = newState;
      this.config.onStateChange?.(newState);
    }
  }
}

// ============================================================================
// Retry Logic
// ============================================================================

/**
 * Retry logic with exponential backoff
 */
export class RetryHandler {
  constructor(private config: RetryConfig) {}

  /**
   * Execute a function with retry logic
   */
  async execute<T>(fn: () => Promise<T>): Promise<T> {
    let lastError: Error | null = null;

    for (let attempt = 1; attempt <= this.config.attempts; attempt++) {
      try {
        return await fn();
      } catch (error) {
        lastError = error as Error;

        if (attempt < this.config.attempts) {
          const delay = this.calculateDelay(attempt);
          this.config.onRetry?.(attempt, lastError);
          await this.sleep(delay);
        }
      }
    }

    throw lastError;
  }

  private calculateDelay(attempt: number): number {
    let delay: number;

    if (this.config.backoff === 'exponential') {
      delay = this.config.baseDelay * Math.pow(2, attempt - 1);
    } else {
      delay = this.config.baseDelay * attempt;
    }

    if (this.config.maxDelay && delay > this.config.maxDelay) {
      delay = this.config.maxDelay;
    }

    return delay;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

// ============================================================================
// State Machine
// ============================================================================

/**
 * State transition rules
 */
const STATE_TRANSITIONS: Record<UiState, UiState[]> = {
  idle: ['loading', 'stale'],
  loading: ['success', 'empty', 'partial', 'timeout', 'offline'],
  success: ['stale', 'loading', 'background_refresh'],
  empty: ['loading', 'stale'],
  partial: ['loading', 'success', 'stale'],
  stale: ['loading', 'background_refresh'],
  validation_error: ['idle', 'loading'],
  permission_denied: ['idle'],
  auth_failure: ['idle'],
  conflict: ['loading', 'idle'],
  timeout: ['loading', 'retry', 'offline'],
  rate_limit: ['loading', 'retry'],
  server_error: ['loading', 'retry'],
  offline: ['loading', 'idle'],
  retry: ['loading', 'idle', 'timeout'],
  background_refresh: ['success', 'partial', 'stale'],
} as const;

/**
 * UI State Machine
 *
 * Manages state transitions with validation and observability.
 */
export class UiStateMachine {
  private currentState: UiState = 'idle';
  private transitions: StateTransition[] = [];
  private listeners: Set<(state: UiState, transition: StateTransition) => void> = new Set();

  /**
   * Get current state
   */
  getState(): UiState {
    return this.currentState;
  }

  /**
   * Transition to a new state
   */
  transition(to: UiState, reason?: string): void {
    const allowedTransitions = STATE_TRANSITIONS[this.currentState] || [];
    
    if (!allowedTransitions.includes(to as UiState)) {
      throw new Error(
        `Invalid state transition from ${this.currentState} to ${to}. ` +
        `Allowed transitions: ${allowedTransitions.join(', ')}`
      );
    }

    const transition: StateTransition = {
      from: this.currentState,
      to,
      reason,
      timestamp: Date.now(),
    };

    this.transitions.push(transition);
    this.currentState = to;

    this.notifyListeners(to, transition);
  }

  /**
   * Subscribe to state changes
   */
  subscribe(listener: (state: UiState, transition: StateTransition) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Get transition history
   */
  getTransitions(): StateTransition[] {
    return [...this.transitions];
  }

  /**
   * Clear transition history
   */
  clearHistory(): void {
    this.transitions = [];
  }

  /**
   * Reset to initial state
   */
  reset(): void {
    this.transition('idle', 'State machine reset');
  }

  private notifyListeners(state: UiState, transition: StateTransition): void {
    this.listeners.forEach(listener => listener(state, transition));
  }
}

// ============================================================================
// State Utilities
// ============================================================================

/**
 * Check if state is a loading state
 */
export function isLoadingState(state: UiState): boolean {
  return state === 'loading' || state === 'background_refresh' || state === 'retry';
}

/**
 * Check if state is an error state
 */
export function isErrorState(state: UiState): boolean {
  return [
    'validation_error',
    'permission_denied',
    'auth_failure',
    'conflict',
    'timeout',
    'rate_limit',
    'server_error',
    'offline',
  ].includes(state);
}

/**
 * Check if state is a success state
 */
export function isSuccessState(state: UiState): boolean {
  return state === 'success' || state === 'partial';
}

/**
 * Check if state allows user interaction
 */
export function allowsInteraction(state: UiState): boolean {
  return !isLoadingState(state) && state !== 'offline';
}

/**
 * Get user-friendly message for state
 */
export function getStateMessage(state: UiState): string {
  const messages: Record<UiState, string> = {
    idle: 'Ready',
    loading: 'Loading...',
    success: 'Success',
    empty: 'No data available',
    partial: 'Partial data loaded',
    stale: 'Data may be outdated',
    validation_error: 'Invalid data',
    permission_denied: 'Permission denied',
    auth_failure: 'Authentication failed',
    conflict: 'Data conflict detected',
    timeout: 'Request timed out',
    rate_limit: 'Too many requests',
    server_error: 'Server error',
    offline: 'You are offline',
    retry: 'Retrying...',
    background_refresh: 'Updating...',
  };

  return messages[state] || 'Unknown state';
}
