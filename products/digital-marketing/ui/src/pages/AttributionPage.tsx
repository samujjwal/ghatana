/**
 * Attribution Reporting Page — reporting capability placeholder.
 *
 * <p>P0-003: Route is capability-gated via {@code dmos.reporting}. Shows a
 * feature unavailable view until the reporting backend is available.</p>
 *
 * @doc.type page
 * @doc.purpose Multi-touch attribution reporting view
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

export function AttributionPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <section data-testid="attribution-page" className="max-w-6xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-2">Attribution Reporting</h1>
      <p className="text-gray-500 text-sm mb-6">
        Workspace: <span className="font-mono">{workspaceId}</span>
      </p>
      <div className="border border-dashed border-gray-300 rounded-lg p-12 text-center text-gray-400">
        <p className="text-lg font-medium">Feature Not Available</p>
        <p className="text-sm mt-2">Multi-touch attribution models and channel credit reporting require the dmos.reporting capability.</p>
        <p className="text-sm mt-2">Contact your administrator to enable this feature.</p>
      </div>
    </section>
  );
}
