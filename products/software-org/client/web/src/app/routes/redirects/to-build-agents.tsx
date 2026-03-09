/**
 * Redirect to Build Agents
 */
import { Navigate } from 'react-router';

export default function RedirectToBuildAgents() {
    return <Navigate to="/build/agents" replace />;
}
