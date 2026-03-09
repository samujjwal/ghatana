/**
 * Sentry error monitoring integration for exception tracking and performance monitoring.
 *
 * <p><b>Purpose</b><br>
 * Integrates Sentry for automatic error capture, reporting, and performance monitoring
 * in production. Captures exceptions with stack traces, user context, breadcrumbs,
 * and source maps for debugging. Supports release tracking and environment-based
 * sampling to control data volume.
 *
 * <p><b>Features</b><br>
 * - Automatic error capture: Unhandled exceptions and rejections
 * - Source maps: Production debugging with readable stack traces
 * - Performance monitoring: Transaction tracing with duration metrics
 * - User context: Associate errors with userId and session
 * - Breadcrumbs: Event timeline leading to errors
 * - Release tracking: Link errors to Git commits/versions
 * - Environment sampling: Control error/transaction sample rates
 *
 * <p><b>Configuration</b><br>
 * Requires SENTRY_DSN environment variable. Optional:
 * - SENTRY_ENVIRONMENT: production|staging|development
 * - SENTRY_RELEASE: Git commit SHA or version
 * - NODE_ENV: Controls sample rates and logging
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * initSentry(); // Call at app startup
 * 
 * captureException(error, { userId: '123', action: 'create_policy' });
 * 
 * const transaction = Sentry.startTransaction({ name: 'POST /api/policy' });
 * await processRequest();
 * transaction.finish();
 * }</pre>
 *
 * <p><b>Integration</b><br>
 * Integrated with logger and error middleware for automatic capture of all
 * application errors. Errors are logged locally and sent to Sentry in production.
 *
 * @doc.type class
 * @doc.purpose Error monitoring and performance tracking with Sentry
 * @doc.layer backend
 * @doc.pattern Utility
 */
import * as Sentry from '@sentry/node';
import { logger } from './logger';

/**
 * Initialize Sentry
 */
export function initSentry() {
  if (!process.env.SENTRY_DSN) {
    logger.warn('Sentry DSN not configured - error monitoring disabled');
    return;
  }

  Sentry.init({
    dsn: process.env.SENTRY_DSN,
    environment: process.env.NODE_ENV || 'development',
    release: process.env.npm_package_version || '1.0.0',
    
    // Performance monitoring
    tracesSampleRate: process.env.NODE_ENV === 'production' ? 0.1 : 1.0, // 10% in prod, 100% in dev
    
    // Filter out health checks and metrics from Sentry
    beforeSend(event) {
      const url = event.request?.url || '';
      if (url.includes('/health') || url.includes('/metrics')) {
        return null;
      }
      return event;
    },
    
    // Attach user context
    beforeBreadcrumb(breadcrumb) {
      // Filter out noisy breadcrumbs
      if (breadcrumb.category === 'console' && breadcrumb.level !== 'error') {
        return null;
      }
      return breadcrumb;
    },
  });

  logger.info('Sentry error monitoring initialized', {
    environment: process.env.NODE_ENV,
    release: process.env.npm_package_version,
  });
}

/**
 * Capture exception with context
 */
export function captureException(error: Error, context?: Record<string, any>) {
  logger.error('Captured exception', { error: error.message, stack: error.stack, ...context });
  
  if (context) {
    Sentry.setContext('additional', context);
  }
  
  Sentry.captureException(error);
}

/**
 * Capture message (non-error events)
 */
export function captureMessage(message: string, level: Sentry.SeverityLevel = 'info', context?: Record<string, any>) {
  if (context) {
    Sentry.setContext('additional', context);
  }
  
  Sentry.captureMessage(message, level);
}

/**
 * Set user context for error tracking
 */
export function setUser(userId: string, email?: string, username?: string) {
  Sentry.setUser({
    id: userId,
    email,
    username,
  });
}

/**
 * Clear user context (on logout)
 */
export function clearUser() {
  Sentry.setUser(null);
}

/**
 * Add breadcrumb for debugging
 */
export function addBreadcrumb(category: string, message: string, level: Sentry.SeverityLevel = 'info', data?: Record<string, any>) {
  Sentry.addBreadcrumb({
    category,
    message,
    level,
    data,
    timestamp: Date.now() / 1000,
  });
}

export default Sentry;
