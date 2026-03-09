/**
 * Redirect to Admin Security
 */
import { Navigate } from 'react-router';

export default function RedirectToAdminSecurity() {
    return <Navigate to="/admin/security" replace />;
}
