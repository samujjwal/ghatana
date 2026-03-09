/**
 * Redirect to Operate Queue
 */
import { Navigate } from 'react-router';

export default function RedirectToOperateQueue() {
    return <Navigate to="/operate/queue" replace />;
}
