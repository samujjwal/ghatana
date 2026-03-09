/**
 * Redirect to Dashboard
 */
import { Navigate } from 'react-router';

export default function RedirectToDashboard() {
    return <Navigate to="/" replace />;
}
