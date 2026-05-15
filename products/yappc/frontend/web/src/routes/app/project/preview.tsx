/**
 * Preview Route - Redirect to Observe Phase
 *
 * This route is deprecated. Preview and runtime signals are now part of Observe.
 * Redirects to the canonical observe phase cockpit.
 *
 * @doc.type route
 * @doc.purpose Redirect legacy preview route to canonical phase
 * @doc.layer product
 * @doc.pattern Redirect Component
 */

import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';

// TRACK-009: Redirect legacy preview route to canonical observe phase
export default function PreviewRedirectRoute() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (projectId) {
      navigate(`/p/${projectId}/observe`, { replace: true });
    }
  }, [projectId, navigate]);

  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <p className="text-sm text-fg-muted">Redirecting to Observe phase...</p>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  return null;
}
