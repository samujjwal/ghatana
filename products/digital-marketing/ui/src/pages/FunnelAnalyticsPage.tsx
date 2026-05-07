/**
 * Funnel Analytics Page — reporting capability placeholder.
 *
 * <p>P2-006: Route is capability-gated via {@code dmos.reporting}. Shows a
 * coming-soon view until the reporting backend is available.</p>
 *
 * @doc.type page
 * @doc.purpose Funnel analytics reporting view
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

export function FunnelAnalyticsPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <section data-testid="funnel-analytics-page" className="max-w-6xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-2">Funnel Analytics</h1>
      <p className="text-gray-500 text-sm mb-6">
        Workspace: <span className="font-mono">{workspaceId}</span>
      </p>
      <div className="border border-dashed border-gray-300 rounded-lg p-12 text-center text-gray-400">
        <p className="text-lg font-medium">Funnel Analytics Coming Soon</p>
        <p className="text-sm mt-2">Full-funnel conversion reporting will be available here.</p>
      </div>
    </section>
  );
}
