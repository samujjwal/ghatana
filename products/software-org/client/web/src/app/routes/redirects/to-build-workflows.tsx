/**
 * Redirect to Build Workflows
 */
import { Navigate, useParams } from 'react-router';

export default function RedirectToBuildWorkflows() {
    const { id } = useParams();
    const target = id ? `/build/workflows/${id}` : '/build/workflows';
    return <Navigate to={target} replace />;
}
