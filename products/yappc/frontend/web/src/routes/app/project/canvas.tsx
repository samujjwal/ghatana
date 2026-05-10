/**
 * Canvas Route - Redirect to Shape Phase
 *
 * This route is deprecated. Canvas design work is now part of Shape and Generate phase surfaces.
 * Redirects to the canonical shape phase cockpit.
 *
 * @doc.type route
 * @doc.purpose Redirect legacy canvas route to canonical phase
 * @doc.layer product
 * @doc.pattern Redirect Component
 */
import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';

// TODO-009: Redirect legacy canvas route to canonical shape phase
export default function CanvasRedirectRoute() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (projectId) {
      navigate(`/p/${projectId}/shape`, { replace: true });
    }
  }, [projectId, navigate]);

  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <p className="text-sm text-fg-muted">Redirecting to Shape phase...</p>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  return null;
}
