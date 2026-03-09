/**
 * Sentry Configuration
 * Error tracking and performance monitoring
 */

import * as Sentry from '@sentry/react';

/**
 * Initialize Sentry for error tracking and performance monitoring
 */
export function initSentry(): void {
  // Only initialize in production or if explicitly enabled
  const isProduction = import.meta.env.PROD;
  const sentryDsn = import.meta.env.VITE_SENTRY_DSN;
  const enableSentry = import.meta.env.VITE_ENABLE_SENTRY === 'true';

  if (!sentryDsn || (!isProduction && !enableSentry)) {
    console.log('Sentry not initialized (no DSN or not in production)');
    return;
  }

  Sentry.init({
    dsn: sentryDsn,
    
    // Environment
    environment: import.meta.env.MODE || 'development',
    
    // Release tracking
    release: `guardian-dashboard@${import.meta.env.VITE_APP_VERSION || '1.0.0'}`,
    
    // Performance Monitoring
    integrations: [
      // Browser tracing for performance
      Sentry.browserTracingIntegration(),
      
      // Replay sessions for debugging
      Sentry.replayIntegration({
        maskAllText: true, // Privacy: mask user text
        blockAllMedia: true, // Privacy: block images/videos
      }),
      
      // Breadcrumbs for user actions
      Sentry.breadcrumbsIntegration({
        console: true,
        dom: true,
        fetch: true,
        history: true,
        xhr: true,
      }),
    ],
    
    // Performance Monitoring Sample Rates
    tracesSampleRate: isProduction ? 0.1 : 1.0, // 10% in production, 100% in dev
    
    // Session Replay Sample Rates
    replaysSessionSampleRate: 0.1, // 10% of sessions
    replaysOnErrorSampleRate: 1.0, // 100% of error sessions
    
    // Error Filtering
    beforeSend(event, hint) {
      // Filter out certain errors
      if (event.exception) {
        const error = hint.originalException;
        
        // Ignore network errors (handle separately)
        if (error && typeof error === 'object' && 'message' in error) {
          const message = String(error.message);
          if (message.includes('Network Error') || message.includes('fetch failed')) {
            return null;
          }
        }
        
        // Ignore ResizeObserver errors (browser quirks)
        if (event.message?.includes('ResizeObserver')) {
          return null;
        }
      }
      
      return event;
    },
    
    // Additional Configuration
    maxBreadcrumbs: 50,
    attachStacktrace: true,
    
    // Debug (only in development)
    debug: !isProduction,
  });

  // Set user context if available
  const user = getUserContext();
  if (user) {
    Sentry.setUser(user);
  }

  console.log('Sentry initialized successfully');
}

/**
 * Get user context for Sentry
 */
function getUserContext(): Sentry.User | null {
  try {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      const user = JSON.parse(userStr);
      return {
        id: user.id,
        email: user.email,
        username: user.username,
      };
    }
  } catch (error) {
    console.error('Failed to get user context:', error);
  }
  return null;
}

/**
 * Manually capture an exception
 */
export function captureException(error: Error, context?: Record<string, unknown>): void {
  if (context) {
    Sentry.withScope((scope) => {
      Object.keys(context).forEach((key) => {
        scope.setExtra(key, context[key]);
      });
      Sentry.captureException(error);
    });
  } else {
    Sentry.captureException(error);
  }
}

/**
 * Manually capture a message
 */
export function captureMessage(message: string, level: Sentry.SeverityLevel = 'info'): void {
  Sentry.captureMessage(message, level);
}

/**
 * Add breadcrumb for debugging
 */
export function addBreadcrumb(breadcrumb: Sentry.Breadcrumb): void {
  Sentry.addBreadcrumb(breadcrumb);
}

/**
 * Set custom user context
 */
export function setUserContext(user: { id: string; email?: string; username?: string } | null): void {
  if (user) {
    Sentry.setUser({
      id: user.id,
      email: user.email,
      username: user.username,
    });
  } else {
    Sentry.setUser(null);
  }
}

/**
 * Set custom context
 */
export function setContext(name: string, context: Record<string, unknown>): void {
  Sentry.setContext(name, context);
}

/**
 * Create a span for performance monitoring
 */
export function startSpan<T>(
  context: { name: string; op: string },
  callback: () => T
): T {
  return Sentry.startSpan(context, callback);
}

/**
 * Error Boundary fallback component
 */
export const SentryErrorBoundary = Sentry.ErrorBoundary;
