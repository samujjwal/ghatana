/**
 * Feature Unavailable Page.
 *
 * <p>Displayed when a user attempts to access a feature that is disabled
 * via feature flags or backend capability checks. Provides clear messaging
 * and safe navigation options without logging the user out.</p>
 *
 * @doc.type page
 * @doc.purpose UX boundary for disabled/unavailable features (DMOS-P0-005)
 * @doc.layer frontend
 */
import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

interface FeatureUnavailablePageProps {
  featureName?: string;
  reason?: string;
}

export function FeatureUnavailablePage({
  featureName = 'This feature',
  reason = 'is currently unavailable.',
}: FeatureUnavailablePageProps): React.ReactElement {
  const navigate = useNavigate();
  const { workspaceId } = useAuth();

  return (
    <main
      data-testid="feature-unavailable-page"
      className="min-h-screen flex items-center justify-center bg-gray-50"
    >
      <div className="bg-white rounded-lg shadow p-8 w-full max-w-md text-center">
        <div className="mb-4">
          <svg
            className="mx-auto h-12 w-12 text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>

        <h1 className="text-xl font-bold mb-2">Feature Unavailable</h1>

        <p className="text-gray-600 mb-6">
          {featureName} {reason}
        </p>

        <div className="space-y-3">
          <button
            onClick={() => navigate(`/workspaces/${workspaceId}/dashboard`)}
            className="w-full bg-blue-600 text-white rounded px-4 py-2 text-sm hover:bg-blue-700"
            data-testid="back-to-dashboard"
          >
            Return to Dashboard
          </button>

          <button
            onClick={() => navigate(-1)}
            className="w-full bg-gray-100 text-gray-700 rounded px-4 py-2 text-sm hover:bg-gray-200"
            data-testid="go-back"
          >
            Go Back
          </button>
        </div>

        <p className="mt-6 text-xs text-gray-400">
          If you believe this is an error, please contact support.
        </p>
      </div>
    </main>
  );
}
