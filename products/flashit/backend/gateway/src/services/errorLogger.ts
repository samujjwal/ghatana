/**
 * Error Logger Service for Flashit Web API
 * Centralized error logging and monitoring
 *
 * @doc.type service
 * @doc.purpose Error logging, aggregation, and monitoring
 * @doc.layer product
 * @doc.pattern ErrorLogger
 */

import { FastifyRequest } from 'fastify';
import { PrismaClient } from '../../generated/prisma/index.js';

// ============================================================================
// Types & Interfaces
// ============================================================================

export type ErrorSeverity = 'low' | 'medium' | 'high' | 'critical';
export type ErrorCategory =
  | 'validation'
  | 'authentication'
  | 'authorization'
  | 'database'
  | 'external_api'
  | 'business_logic'
  | 'system'
  | 'unknown';

export interface ErrorContext {
  userId?: string;
  requestId?: string;
  url?: string;
  method?: string;
  userAgent?: string;
  ip?: string;
  additionalData?: Record<string, unknown>;
}

export interface ErrorLogEntry {
  id: string;
  timestamp: Date;
  message: string;
  stack?: string;
  severity: ErrorSeverity;
  category: ErrorCategory;
  context: ErrorContext;
  fingerprint: string;
  count: number;
  firstSeen: Date;
  lastSeen: Date;
}

export interface ErrorStats {
  total: number;
  byCategory: Record<ErrorCategory, number>;
  bySeverity: Record<ErrorSeverity, number>;
  topErrors: Array<{ fingerprint: string; count: number; message: string }>;
  recentErrors: ErrorLogEntry[];
}

export interface ErrorAlert {
  id: string;
  fingerprint: string;
  message: string;
  count: number;
  threshold: number;
  timeWindow: number;
  triggered: boolean;
}

// ============================================================================
// Constants
// ============================================================================

const ERROR_FINGERPRINT_CACHE = new Map<string, string>();
const ERROR_COUNTS = new Map<string, { count: number; firstSeen: number }>();
const ERROR_ALERTS: ErrorAlert[] = [];

// Alert thresholds
const ALERT_THRESHOLDS: Record<ErrorSeverity, { count: number; window: number }> = {
  low: { count: 100, window: 3600000 }, // 100 in 1 hour
  medium: { count: 50, window: 1800000 }, // 50 in 30 min
  high: { count: 20, window: 600000 }, // 20 in 10 min
  critical: { count: 5, window: 300000 }, // 5 in 5 min
};

// ============================================================================
// Error Logger Service
// ============================================================================

/**
 * ErrorLoggerService handles error logging and monitoring
 */
class ErrorLoggerService {
  private static instance: ErrorLoggerService | null = null;
  private prisma: PrismaClient;
  private alertCallbacks: Array<(alert: ErrorAlert) => void> = [];

  private constructor(prisma: PrismaClient) {
    this.prisma = prisma;
    this.startCleanupInterval();
  }

  /**
   * Get singleton instance
   */
  static getInstance(prisma: PrismaClient): ErrorLoggerService {
    if (!this.instance) {
      this.instance = new ErrorLoggerService(prisma);
    }
    return this.instance;
  }

  /**
   * Log an error
   */
  async logError(
    error: Error,
    severity: ErrorSeverity,
    category: ErrorCategory,
    context: ErrorContext = {}
  ): Promise<string> {
    const fingerprint = this.getErrorFingerprint(error, category);
    const now = Date.now();

    // Update in-memory counts
    const existing = ERROR_COUNTS.get(fingerprint);
    if (existing) {
      existing.count++;
      ERROR_COUNTS.set(fingerprint, existing);
    } else {
      ERROR_COUNTS.set(fingerprint, { count: 1, firstSeen: now });
    }

    // Check if should trigger alert
    this.checkAlert(fingerprint, error.message, severity);

    // Log to console in development
    if (process.env.NODE_ENV === 'development') {
      console.error(`[${severity}] [${category}]`, error.message, context);
    }

    // Store in database (async, don't block)
    this.storeError(error, fingerprint, severity, category, context).catch((err) => {
      console.error('Failed to store error:', err);
    });

    return fingerprint;
  }

  /**
   * Log from Fastify request
   */
  async logRequestError(
    error: Error,
    request: FastifyRequest,
    severity: ErrorSeverity = 'medium',
    category: ErrorCategory = 'unknown'
  ): Promise<string> {
    const context: ErrorContext = {
      userId: (request as unknown as { userId?: string }).userId,
      requestId: request.id,
      url: request.url,
      method: request.method,
      userAgent: request.headers['user-agent'],
      ip: request.ip,
    };

    return this.logError(error, severity, category, context);
  }

  /**
   * Get error statistics
   */
  async getStats(period: 'hour' | 'day' | 'week' = 'day'): Promise<ErrorStats> {
    const since = this.getPeriodStart(period);

    // This would query the database in a real implementation
    // For now, return from in-memory data
    const stats: ErrorStats = {
      total: 0,
      byCategory: {} as Record<ErrorCategory, number>,
      bySeverity: {} as Record<ErrorSeverity, number>,
      topErrors: [],
      recentErrors: [],
    };

    // Calculate from in-memory counts
    for (const [fingerprint, data] of ERROR_COUNTS.entries()) {
      if (data.firstSeen >= since.getTime()) {
        stats.total += data.count;
      }
    }

    // Get top errors
    const sorted = Array.from(ERROR_COUNTS.entries())
      .filter(([, data]) => data.firstSeen >= since.getTime())
      .sort((a, b) => b[1].count - a[1].count)
      .slice(0, 10);

    stats.topErrors = sorted.map(([fingerprint, data]) => ({
      fingerprint,
      count: data.count,
      message: fingerprint.split(':')[0], // Extract message from fingerprint
    }));

    return stats;
  }

  /**
   * Get errors by fingerprint
   */
  async getErrorsByFingerprint(
    fingerprint: string,
    limit: number = 100
  ): Promise<ErrorLogEntry[]> {
    // In a real implementation, query the database
    // For now, return empty array
    return [];
  }

  /**
   * Mark error as resolved
   */
  async resolveError(fingerprint: string): Promise<void> {
    ERROR_COUNTS.delete(fingerprint);
    
    // In a real implementation, update database
    // await this.prisma.errorLog.updateMany({
    //   where: { fingerprint },
    //   data: { resolved: true, resolvedAt: new Date() },
    // });
  }

  /**
   * Register alert callback
   */
  onAlert(callback: (alert: ErrorAlert) => void): void {
    this.alertCallbacks.push(callback);
  }

  /**
   * Get active alerts
   */
  getAlerts(): ErrorAlert[] {
    return ERROR_ALERTS.filter((alert) => alert.triggered);
  }

  /**
   * Clear alert
   */
  clearAlert(id: string): void {
    const index = ERROR_ALERTS.findIndex((alert) => alert.id === id);
    if (index !== -1) {
      ERROR_ALERTS.splice(index, 1);
    }
  }

  // ============================================================================
  // Private Methods
  // ============================================================================

  /**
   * Generate error fingerprint
   */
  private getErrorFingerprint(error: Error, category: ErrorCategory): string {
    const key = `${error.message}:${category}`;
    
    // Check cache
    if (ERROR_FINGERPRINT_CACHE.has(key)) {
      return ERROR_FINGERPRINT_CACHE.get(key)!;
    }

    // Generate fingerprint from stack trace
    const stackLines = error.stack?.split('\n').slice(0, 3) || [];
    const stackSignature = stackLines.join('|');
    const fingerprint = `${error.name}:${error.message}:${this.hashCode(stackSignature)}`;

    // Cache it
    ERROR_FINGERPRINT_CACHE.set(key, fingerprint);

    return fingerprint;
  }

  /**
   * Simple hash function
   */
  private hashCode(str: string): string {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash; // Convert to 32bit integer
    }
    return Math.abs(hash).toString(36);
  }

  /**
   * Store error in database
   */
  private async storeError(
    error: Error,
    fingerprint: string,
    severity: ErrorSeverity,
    category: ErrorCategory,
    context: ErrorContext
  ): Promise<void> {
    try {
      // In a real implementation, store in database
      // For now, just log to console in production
      if (process.env.NODE_ENV === 'production') {
        console.error(JSON.stringify({
          fingerprint,
          severity,
          category,
          message: error.message,
          stack: error.stack,
          context,
          timestamp: new Date().toISOString(),
        }));
      }

      // Example Prisma implementation:
      // await this.prisma.errorLog.upsert({
      //   where: { fingerprint },
      //   update: {
      //     count: { increment: 1 },
      //     lastSeen: new Date(),
      //   },
      //   create: {
      //     fingerprint,
      //     message: error.message,
      //     stack: error.stack,
      //     severity,
      //     category,
      //     context: JSON.stringify(context),
      //     count: 1,
      //     firstSeen: new Date(),
      //     lastSeen: new Date(),
      //   },
      // });
    } catch (err) {
      console.error('Failed to store error in database:', err);
    }
  }

  /**
   * Check if error should trigger alert
   */
  private checkAlert(
    fingerprint: string,
    message: string,
    severity: ErrorSeverity
  ): void {
    const threshold = ALERT_THRESHOLDS[severity];
    const counts = ERROR_COUNTS.get(fingerprint);
    
    if (!counts) return;

    const timeWindow = Date.now() - counts.firstSeen;
    
    if (timeWindow <= threshold.window && counts.count >= threshold.count) {
      const alert: ErrorAlert = {
        id: `${fingerprint}:${Date.now()}`,
        fingerprint,
        message,
        count: counts.count,
        threshold: threshold.count,
        timeWindow: threshold.window,
        triggered: true,
      };

      ERROR_ALERTS.push(alert);

      // Notify callbacks
      this.alertCallbacks.forEach((callback) => {
        try {
          callback(alert);
        } catch (err) {
          console.error('Alert callback error:', err);
        }
      });
    }
  }

  /**
   * Get period start time
   */
  private getPeriodStart(period: 'hour' | 'day' | 'week'): Date {
    const now = new Date();
    switch (period) {
      case 'hour':
        return new Date(now.getTime() - 3600000);
      case 'day':
        return new Date(now.getTime() - 86400000);
      case 'week':
        return new Date(now.getTime() - 604800000);
    }
  }

  /**
   * Start cleanup interval
   */
  private startCleanupInterval(): void {
    setInterval(() => {
      const now = Date.now();
      const maxAge = 3600000; // 1 hour

      // Clean up old counts
      for (const [fingerprint, data] of ERROR_COUNTS.entries()) {
        if (now - data.firstSeen > maxAge) {
          ERROR_COUNTS.delete(fingerprint);
        }
      }

      // Clean up old alerts
      for (let i = ERROR_ALERTS.length - 1; i >= 0; i--) {
        const alert = ERROR_ALERTS[i];
        const counts = ERROR_COUNTS.get(alert.fingerprint);
        if (!counts) {
          ERROR_ALERTS.splice(i, 1);
        }
      }
    }, 300000); // Every 5 minutes
  }
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Categorize error automatically
 */
export function categorizeError(error: Error): ErrorCategory {
  const message = error.message.toLowerCase();
  
  if (message.includes('validation') || message.includes('invalid')) {
    return 'validation';
  }
  if (message.includes('unauthorized') || message.includes('authentication')) {
    return 'authentication';
  }
  if (message.includes('forbidden') || message.includes('permission')) {
    return 'authorization';
  }
  if (
    message.includes('database') ||
    message.includes('query') ||
    message.includes('prisma')
  ) {
    return 'database';
  }
  if (message.includes('fetch') || message.includes('api') || message.includes('network')) {
    return 'external_api';
  }
  if (message.includes('econnrefused') || message.includes('timeout')) {
    return 'system';
  }
  
  return 'unknown';
}

/**
 * Determine error severity
 */
export function determineErrorSeverity(error: Error): ErrorSeverity {
  const message = error.message.toLowerCase();
  
  if (
    message.includes('critical') ||
    message.includes('fatal') ||
    message.includes('crashed')
  ) {
    return 'critical';
  }
  if (
    message.includes('database') ||
    message.includes('authentication') ||
    message.includes('unauthorized')
  ) {
    return 'high';
  }
  if (message.includes('validation') || message.includes('invalid input')) {
    return 'low';
  }
  
  return 'medium';
}

/**
 * Create error logger instance
 */
export function createErrorLogger(prisma: PrismaClient): ErrorLoggerService {
  return ErrorLoggerService.getInstance(prisma);
}

/**
 * Global error handler middleware
 */
export function createErrorHandler(logger: ErrorLoggerService) {
  return async (error: Error, request: FastifyRequest): Promise<void> => {
    const category = categorizeError(error);
    const severity = determineErrorSeverity(error);
    
    await logger.logRequestError(error, request, severity, category);
  };
}

export default ErrorLoggerService;
