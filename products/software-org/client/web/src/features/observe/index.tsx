import { lazy, Suspense } from 'react';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { PageLoader } from '@/components/LoadingState';

/**
 * Lazy-loaded Observe Features
 *
 * <p><b>Purpose</b><br>
 * Code-split observe features for better initial load performance.
 * Only loads monitoring components when user navigates to observe routes.
 *
 * <p><b>Features</b><br>
 * - Code splitting for reduced bundle size
 * - Error boundary for graceful failures
 * - Loading states during chunk download
 * - Automatic retry on chunk load failure
 *
 * @doc.type component
 * @doc.purpose Code-split lazy loading wrapper
 * @doc.layer product
 * @doc.pattern LazyLoader
 */

// Lazy load components
const AlertsMonitor = lazy(() => 
    import('./AlertsMonitor').then(module => ({ default: module.AlertsMonitor }))
);

/**
 * Lazy-loaded Alerts Monitor
 */
export function LazyAlertsMonitor() {
    return (
        <ErrorBoundary>
            <Suspense fallback={<PageLoader message="Loading Alerts Monitor..." />}>
                <AlertsMonitor />
            </Suspense>
        </ErrorBoundary>
    );
}
