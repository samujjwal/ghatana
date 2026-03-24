/**
 * Collaboration Security
 *
 * Provides rate limiting and payload validation for collaboration events.
 * Features:
 * - Token bucket rate limiting per user
 * - Schema-based payload validation
 * - Malformed message detection
 * - Event throttling and logging
 * - Connection management
 *
 * @module collabSecurity
 */

/**
 * Rate limit configuration
 */
export interface RateLimitConfig {
  /** Maximum events per window */
  maxEvents: number;
  /** Time window in ms */
  windowMs: number;
  /** Refill rate (events per second) */
  refillRate: number;
  /** Burst capacity */
  burstCapacity: number;
}

/**
 * Rate limit state
 */
export interface RateLimitState {
  /** Available tokens */
  tokens: number;
  /** Last refill timestamp */
  lastRefill: number;
  /** Total requests */
  totalRequests: number;
  /** Throttled requests */
  throttledRequests: number;
}

/**
 * Validation error
 */
export interface ValidationError {
  /** Error code */
  code: string;
  /** Error message */
  message: string;
  /** Field path (if applicable) */
  field?: string;
  /** Expected type/value */
  expected?: string;
  /** Actual type/value */
  actual?: string;
}

/**
 * Validation result
 */
export interface ValidationResult {
  /** Whether validation passed */
  valid: boolean;
  /** Validation errors */
  errors: ValidationError[];
  /** Warnings (non-fatal) */
  warnings: string[];
}

/**
 * Security event
 */
export interface SecurityEvent {
  /** Event type */
  type: 'rate_limit' | 'validation_error' | 'malformed_payload' | 'connection_closed';
  /** User ID */
  userId: string;
  /** Event timestamp */
  timestamp: number;
  /** Event details */
  details: Record<string, unknown>;
  /** Severity */
  severity: 'low' | 'medium' | 'high' | 'critical';
}

/**
 * Collaboration payload schema
 */
export interface CollabPayloadSchema {
  /** Required fields */
  required: string[];
  /** Field types */
  types: Record<string, 'string' | 'number' | 'boolean' | 'object' | 'array'>;
  /** Nested object schemas */
  nested?: Record<string, CollabPayloadSchema>;
  /** Maximum string length */
  maxStringLength?: number;
  /** Maximum array length */
  maxArrayLength?: number;
}

/**
 * Default rate limit configuration
 */
const DEFAULT_RATE_LIMIT: RateLimitConfig = {
  maxEvents: 100, // 100 events
  windowMs: 60000, // per minute
  refillRate: 2, // 2 events per second
  burstCapacity: 20, // burst of 20
};

/**
 * Default payload schema
 */
const DEFAULT_SCHEMA: CollabPayloadSchema = {
  required: ['type', 'userId', 'timestamp'],
  types: {
    type: 'string',
    userId: 'string',
    timestamp: 'number',
    data: 'object',
  },
  maxStringLength: 1000,
  maxArrayLength: 100,
};

/**
 * Collaboration security manager
 *
 * Manages rate limiting, payload validation, and security logging.
 */
export class CollabSecurityManager {
  private rateLimits = new Map<string, RateLimitState>();
  private rateLimitConfig: RateLimitConfig;
  private schema: CollabPayloadSchema;
  private securityEvents: SecurityEvent[] = [];
  private maxSecurityEvents = 1000;
  private listeners: Array<(event: SecurityEvent) => void> = [];

  /**
   *
   */
  constructor(
    rateLimitConfig: Partial<RateLimitConfig> = {},
    schema: CollabPayloadSchema = DEFAULT_SCHEMA
  ) {
    this.rateLimitConfig = { ...DEFAULT_RATE_LIMIT, ...rateLimitConfig };
    this.schema = schema;
  }

  /**
   * Check rate limit for user
   */
  checkRateLimit(userId: string): { allowed: boolean; retryAfter?: number } {
    const now = Date.now();
    let state = this.rateLimits.get(userId);

    if (!state) {
      state = {
        tokens: this.rateLimitConfig.burstCapacity,
        lastRefill: now,
        totalRequests: 0,
        throttledRequests: 0,
      };
      this.rateLimits.set(userId, state);
    }

    // Refill tokens based on time elapsed
    const elapsed = now - state.lastRefill;
    const tokensToAdd = (elapsed / 1000) * this.rateLimitConfig.refillRate;
    state.tokens = Math.min(state.tokens + tokensToAdd, this.rateLimitConfig.burstCapacity);
    state.lastRefill = now;

    state.totalRequests++;

    // Check if tokens available
    if (state.tokens >= 1) {
      state.tokens -= 1;
      return { allowed: true };
    }

    // Rate limited
    state.throttledRequests++;

    // Calculate retry after
    const tokensNeeded = 1 - state.tokens;
    const retryAfter = Math.ceil((tokensNeeded / this.rateLimitConfig.refillRate) * 1000);

    // Log security event
    this.logSecurityEvent({
      type: 'rate_limit',
      userId,
      timestamp: now,
      severity: 'medium',
      details: {
        tokensAvailable: state.tokens,
        totalRequests: state.totalRequests,
        throttledRequests: state.throttledRequests,
        retryAfter,
      },
    });

    return { allowed: false, retryAfter };
  }

  /**
   * Validate collaboration payload
   */
  validatePayload(payload: unknown): ValidationResult {
    const errors: ValidationError[] = [];
    const warnings: string[] = [];

    // Check if object
    if (typeof payload !== 'object' || payload === null) {
      errors.push({
        code: 'INVALID_TYPE',
        message: 'Payload must be an object',
        expected: 'object',
        actual: typeof payload,
      });
      return { valid: false, errors, warnings };
    }

    const data = payload as Record<string, unknown>;

    // Check required fields
    for (const field of this.schema.required) {
      if (!(field in data)) {
        errors.push({
          code: 'MISSING_FIELD',
          message: `Required field '${field}' is missing`,
          field,
        });
      }
    }

    // Check field types
    for (const [field, expectedType] of Object.entries(this.schema.types)) {
      if (field in data) {
        const value = data[field];
        const actualType = Array.isArray(value) ? 'array' : typeof value;

        if (actualType !== expectedType) {
          errors.push({
            code: 'INVALID_TYPE',
            message: `Field '${field}' has invalid type`,
            field,
            expected: expectedType,
            actual: actualType,
          });
        }

        // Check string length
        if (expectedType === 'string' && typeof value === 'string') {
          if (this.schema.maxStringLength && value.length > this.schema.maxStringLength) {
            errors.push({
              code: 'STRING_TOO_LONG',
              message: `Field '${field}' exceeds maximum length`,
              field,
              expected: `<= ${this.schema.maxStringLength}`,
              actual: value.length.toString(),
            });
          }
        }

        // Check array length
        if (expectedType === 'array' && Array.isArray(value)) {
          if (this.schema.maxArrayLength && value.length > this.schema.maxArrayLength) {
            warnings.push(`Field '${field}' array length exceeds recommended size (${value.length})`);
          }
        }

        // Validate nested objects
        if (expectedType === 'object' && this.schema.nested && field in this.schema.nested) {
          const nestedResult = this.validateNested(value, this.schema.nested[field], field);
          errors.push(...nestedResult.errors);
          warnings.push(...nestedResult.warnings);
        }
      }
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Validate nested object
   */
  private validateNested(
    value: unknown,
    schema: CollabPayloadSchema,
    parentField: string
  ): ValidationResult {
    const errors: ValidationError[] = [];
    const warnings: string[] = [];

    if (typeof value !== 'object' || value === null) {
      errors.push({
        code: 'INVALID_TYPE',
        message: `Nested field '${parentField}' must be an object`,
        field: parentField,
        expected: 'object',
        actual: typeof value,
      });
      return { valid: false, errors, warnings };
    }

    const data = value as Record<string, unknown>;

    // Check required nested fields
    for (const field of schema.required || []) {
      const fieldPath = `${parentField}.${field}`;
      if (!(field in data)) {
        errors.push({
          code: 'MISSING_FIELD',
          message: `Required nested field '${fieldPath}' is missing`,
          field: fieldPath,
        });
      }
    }

    // Check nested field types
    for (const [field, expectedType] of Object.entries(schema.types || {})) {
      const fieldPath = `${parentField}.${field}`;
      if (field in data) {
        const fieldValue = data[field];
        const actualType = Array.isArray(fieldValue) ? 'array' : typeof fieldValue;

        if (actualType !== expectedType) {
          errors.push({
            code: 'INVALID_TYPE',
            message: `Nested field '${fieldPath}' has invalid type`,
            field: fieldPath,
            expected: expectedType,
            actual: actualType,
          });
        }
      }
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Detect malformed payload
   */
  detectMalformed(payload: unknown): { malformed: boolean; reason?: string } {
    try {
      // Check if payload can be serialized
      JSON.stringify(payload);

      // Check for circular references
      const seen = new WeakSet();
      const checkCircular = (obj: unknown): boolean => {
        if (obj !== null && typeof obj === 'object') {
          if (seen.has(obj)) {
            return true;
          }
          seen.add(obj);
          for (const value of Object.values(obj)) {
            if (checkCircular(value)) {
              return true;
            }
          }
        }
        return false;
      };

      if (checkCircular(payload)) {
        return { malformed: true, reason: 'Circular reference detected' };
      }

      // Check payload size
      const serialized = JSON.stringify(payload);
      if (serialized.length > 1024 * 1024) {
        // 1MB limit
        return { malformed: true, reason: 'Payload exceeds size limit (1MB)' };
      }

      return { malformed: false };
    } catch (error) {
      return {
        malformed: true,
        reason: error instanceof Error ? error.message : 'Serialization failed',
      };
    }
  }

  /**
   * Handle security violation
   */
  handleViolation(userId: string, violationType: 'rate_limit' | 'validation' | 'malformed'): {
    shouldDisconnect: boolean;
    reason: string;
  } {
    const violations = this.securityEvents.filter(
      (e) => e.userId === userId && Date.now() - e.timestamp < 60000
    ).length;

    // Disconnect after 3 violations in a minute
    const shouldDisconnect = violations >= 3;

    if (shouldDisconnect) {
      this.logSecurityEvent({
        type: 'connection_closed',
        userId,
        timestamp: Date.now(),
        severity: 'high',
        details: {
          reason: 'Multiple security violations',
          violationType,
          violationCount: violations,
        },
      });
    }

    return {
      shouldDisconnect,
      reason: shouldDisconnect
        ? `Connection closed due to ${violations} security violations`
        : 'Security violation detected',
    };
  }

  /**
   * Get rate limit state for user
   */
  getRateLimitState(userId: string): RateLimitState | undefined {
    return this.rateLimits.get(userId);
  }

  /**
   * Reset rate limit for user
   */
  resetRateLimit(userId: string): void {
    this.rateLimits.delete(userId);
  }

  /**
   * Get security events
   */
  getSecurityEvents(filter?: {
    userId?: string;
    type?: SecurityEvent['type'];
    severity?: SecurityEvent['severity'];
    since?: number;
  }): SecurityEvent[] {
    let events = this.securityEvents;

    if (filter) {
      if (filter.userId) {
        events = events.filter((e) => e.userId === filter.userId);
      }
      if (filter.type) {
        events = events.filter((e) => e.type === filter.type);
      }
      if (filter.severity) {
        events = events.filter((e) => e.severity === filter.severity);
      }
      if (filter.since !== undefined) {
        events = events.filter((e) => e.timestamp >= filter.since!);
      }
    }

    return events;
  }

  /**
   * Clear security events
   */
  clearSecurityEvents(): void {
    this.securityEvents = [];
  }

  /**
   * Subscribe to security events
   */
  subscribe(listener: (event: SecurityEvent) => void): () => void {
    this.listeners.push(listener);
    return () => {
      const index = this.listeners.indexOf(listener);
      if (index >= 0) {
        this.listeners.splice(index, 1);
      }
    };
  }

  /**
   * Get statistics
   */
  getStatistics(): {
    totalUsers: number;
    totalRequests: number;
    totalThrottled: number;
    securityEvents: number;
  } {
    let totalRequests = 0;
    let totalThrottled = 0;

    for (const state of this.rateLimits.values()) {
      totalRequests += state.totalRequests;
      totalThrottled += state.throttledRequests;
    }

    return {
      totalUsers: this.rateLimits.size,
      totalRequests,
      totalThrottled,
      securityEvents: this.securityEvents.length,
    };
  }

  /**
   * Update schema
   */
  updateSchema(schema: CollabPayloadSchema): void {
    this.schema = schema;
  }

  /**
   * Get current schema
   */
  getSchema(): CollabPayloadSchema {
    return { ...this.schema };
  }

  /**
   * Log security event
   */
  private logSecurityEvent(event: SecurityEvent): void {
    this.securityEvents.push(event);

    // Trim old events
    if (this.securityEvents.length > this.maxSecurityEvents) {
      this.securityEvents = this.securityEvents.slice(-this.maxSecurityEvents);
    }

    // Notify listeners
    for (const listener of this.listeners) {
      try {
        listener(event);
      } catch (error) {
        console.error('Security event listener error:', error);
      }
    }
  }
}

/**
 * Create collaboration security manager
 */
export function createCollabSecurityManager(
  rateLimitConfig?: Partial<RateLimitConfig>,
  schema?: CollabPayloadSchema
): CollabSecurityManager {
  return new CollabSecurityManager(rateLimitConfig, schema);
}
