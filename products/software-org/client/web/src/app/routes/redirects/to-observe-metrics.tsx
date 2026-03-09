/**
 * Redirect to Observe Metrics
 */
import { Navigate } from 'react-router';

export default function RedirectToObserveMetrics() {
    return <Navigate to="/observe/metrics" replace />;
}
