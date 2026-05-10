/**
 * Deploy Route - Redirect to Run Phase
 *
 * This route is deprecated. Deployment planning and execution controls are now part of Run.
 * Redirects to the canonical run phase cockpit.
 *
 * @doc.type route
 * @doc.purpose Redirect legacy deploy route to canonical phase
 * @doc.layer product
 * @doc.pattern Redirect Component
 */

import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';

// TODO-009: Redirect legacy deploy route to canonical run phase
export default function DeployRedirectRoute() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (projectId) {
      navigate(`/p/${projectId}/run`, { replace: true });
    }
  }, [projectId, navigate]);

  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <p className="text-sm text-fg-muted">Redirecting to Run phase...</p>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  return null;
}
