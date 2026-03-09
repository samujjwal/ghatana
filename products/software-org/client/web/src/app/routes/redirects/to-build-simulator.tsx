/**
 * Redirect to Build Simulator
 */
import { Navigate } from 'react-router';

export default function RedirectToBuildSimulator() {
    return <Navigate to="/build/simulator" replace />;
}
