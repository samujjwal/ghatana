/**
 * Redirect to Observe ML Observatory
 */
import { Navigate } from 'react-router';

export default function RedirectToObserveML() {
    return <Navigate to="/observe/ml" replace />;
}
