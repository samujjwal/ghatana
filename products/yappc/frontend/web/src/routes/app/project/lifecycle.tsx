/**
 * Lifecycle Route - Redirect to Intent Phase
 *
 * This route is deprecated. Lifecycle phase navigation is now through the canonical phase tabs.
 * Redirects to the canonical intent phase cockpit.
 *
 * @doc.type route
 * @doc.purpose Redirect legacy lifecycle route to canonical phase
 * @doc.layer product
 * @doc.pattern Redirect Component
 */

import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';

// TRACK-009: Redirect legacy lifecycle route to canonical intent phase
export default function LifecycleRedirectRoute() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (projectId) {
      navigate(`/p/${projectId}/intent`, { replace: true });
    }
  }, [projectId, navigate]);

  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <p className="text-sm text-fg-muted">Redirecting to Intent phase...</p>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  return null;
}
