/**
 * Sentry Error Tracking Integration
 *
 * <p><b>Purpose</b><br>
 * Production error tracking and performance monitoring.
 * Captures frontend errors, performance metrics, and user sessions.
 *
 * <p><b>Features</b><br>
 * - Automatic error capture and reporting
 * - Performance monitoring (Web Vitals)
 * - Session replay for debugging
 * - Release tracking and source maps
 * - User context and breadcrumbs
 *
 * @doc.type service
 * @doc.purpose Error tracking and monitoring
 * @doc.layer platform
 * @doc.pattern Observability
 */

// Sentry types (install @sentry/react for production)
interface SentryConfig {
    dsn: string;
    environment: string;
    release?: string;
    tracesSampleRate?: number;
    replaysSessionSampleRate?: number;
    replaysOnErrorSampleRate?: number;
}

/**
 * Initialize Sentry
 * Call this once at app startup
 */
export function initSentry(config?: SentryConfig): void {
    // Only initialize in production
    if (process.env.NODE_ENV !== 'production') {
        console.log('[Sentry] Skipping initialization in development');
        return;
    }

    // Check if Sentry is available (requires @sentry/react package)
    if (typeof window === 'undefined') {
        console.warn('[Sentry] Not running in browser environment');
        return;
    }

    const dsn = config?.dsn || process.env.VITE_SENTRY_DSN;
    if (!dsn) {
        console.warn('[Sentry] DSN not configured, skipping initialization');
        return;
    }

    try {
        // TODO: Uncomment when @sentry/react is installed
        /*
        import * as Sentry from '@sentry/react';
        
        Sentry.init({
            dsn,
            environment: config?.environment || process.env.NODE_ENV || 'production',
            release: config?.release || process.env.VITE_APP_VERSION,
            
            // Performance Monitoring
            integrations: [
                new Sentry.BrowserTracing({
                    // Set sampling rate for performance monitoring
                    tracePropagationTargets: [
                        'localhost',
                        /^https:\/\/yourserver\.io\/api/,
                    ],
                }),
                new Sentry.Replay({
                    // Mask all text and images by default
                    maskAllText: true,
                    blockAllMedia: true,
                }),
            ],
            
            // Performance Monitoring sample rate (0.0 - 1.0)
            tracesSampleRate: config?.tracesSampleRate || 0.1,
            
            // Session Replay sample rates
            replaysSessionSampleRate: config?.replaysSessionSampleRate || 0.1,
            replaysOnErrorSampleRate: config?.replaysOnErrorSampleRate || 1.0,
            
            // Don't send errors in development
            beforeSend(event, hint) {
                if (process.env.NODE_ENV === 'development') {
                    console.log('[Sentry] Would send error:', event);
                    return null;
                }
                return event;
            },
        });
        
        console.log('[Sentry] Initialized successfully');
        */
        console.log('[Sentry] Mock initialization (install @sentry/react to enable)');
    } catch (error) {
        console.error('[Sentry] Failed to initialize:', error);
    }
}

/**
 * Capture an error manually
 */
export function captureError(error: Error, context?: Record<string, any>): void {
    if (process.env.NODE_ENV !== 'production') {
        console.error('[Sentry Mock] Error:', error, context);
        return;
    }

    try {
        // TODO: Uncomment when @sentry/react is installed
        /*
        import * as Sentry from '@sentry/react';
        Sentry.captureException(error, {
            contexts: context ? { extra: context } : undefined,
        });
        */
        console.error('[Sentry Mock] Would capture:', error, context);
    } catch (e) {
        console.error('[Sentry] Failed to capture error:', e);
    }
}

/**
 * Set user context for error reports
 */
export function setUser(user: { id: string; email?: string; username?: string }): void {
    if (process.env.NODE_ENV !== 'production') {
        console.log('[Sentry Mock] Set user:', user);
        return;
    }

    try {
        // TODO: Uncomment when @sentry/react is installed
        /*
        import * as Sentry from '@sentry/react';
        Sentry.setUser(user);
        */
        console.log('[Sentry Mock] Would set user:', user);
    } catch (error) {
        console.error('[Sentry] Failed to set user:', error);
    }
}

/**
 * Add breadcrumb for debugging
 */
export function addBreadcrumb(message: string, category?: string, data?: Record<string, any>): void {
    if (process.env.NODE_ENV !== 'production') {
        console.log('[Sentry Mock] Breadcrumb:', { message, category, data });
        return;
    }

    try {
        // TODO: Uncomment when @sentry/react is installed
        /*
        import * as Sentry from '@sentry/react';
        Sentry.addBreadcrumb({
            message,
            category: category || 'custom',
            data,
            level: 'info',
        });
        */
        console.log('[Sentry Mock] Would add breadcrumb:', { message, category, data });
    } catch (error) {
        console.error('[Sentry] Failed to add breadcrumb:', error);
    }
}

/**
 * Capture a custom message
 */
export function captureMessage(message: string, level: 'info' | 'warning' | 'error' = 'info'): void {
    if (process.env.NODE_ENV !== 'production') {
        console.log('[Sentry Mock] Message:', message, level);
        return;
    }

    try {
        // TODO: Uncomment when @sentry/react is installed
        /*
        import * as Sentry from '@sentry/react';
        Sentry.captureMessage(message, level);
        */
        console.log('[Sentry Mock] Would capture message:', message, level);
    } catch (error) {
        console.error('[Sentry] Failed to capture message:', error);
    }
}

/**
 * Start a performance transaction
 */
export function startTransaction(name: string, op: string): any {
    if (process.env.NODE_ENV !== 'production') {
        const startTime = Date.now();
        return {
            finish: () => {
                const duration = Date.now() - startTime;
                console.log('[Sentry Mock] Transaction:', { name, op, duration });
            },
        };
    }

    try {
        // TODO: Uncomment when @sentry/react is installed
        /*
        import * as Sentry from '@sentry/react';
        return Sentry.startTransaction({ name, op });
        */
        return {
            finish: () => console.log('[Sentry Mock] Would finish transaction:', name),
        };
    } catch (error) {
        console.error('[Sentry] Failed to start transaction:', error);
        return { finish: () => {} };
    }
}
