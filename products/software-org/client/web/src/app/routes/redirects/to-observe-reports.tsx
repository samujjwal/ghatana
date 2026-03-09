/**
 * Redirect to Observe Reports
 */
import { Navigate } from 'react-router';

export default function RedirectToObserveReports() {
    return <Navigate to="/observe/reports" replace />;
}
