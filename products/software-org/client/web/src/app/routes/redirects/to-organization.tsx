/**
 * Redirect to Organization
 */
import { Navigate } from 'react-router';

export default function RedirectToOrganization() {
    return <Navigate to="/admin/organization" replace />;
}
