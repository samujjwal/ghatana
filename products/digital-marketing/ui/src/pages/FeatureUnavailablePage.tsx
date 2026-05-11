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
import { AlertTriangle, ArrowLeft, LayoutDashboard } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

interface FeatureUnavailablePageProps {
  featureName?: string;
  reason?: string;
  capability?: string;
  tier?: string;
  connector?: string;
  lifecycle?: string;
  productionGate?: string;
  remediation?: string;
}

export function FeatureUnavailablePage({
  featureName = 'This feature',
  reason = 'is currently unavailable.',
  capability,
  tier,
  connector,
  lifecycle = 'Boundary',
  productionGate = 'Locked until the backend capability returns production data.',
  remediation = 'Use the command center for ready workflows while this surface remains gated.',
}: FeatureUnavailablePageProps): React.ReactElement {
  const navigate = useNavigate();
  const { workspaceId } = useAuth();
  const details = [
    { label: 'Lifecycle', value: lifecycle },
    { label: 'Capability', value: capability },
    { label: 'Tier', value: tier },
    { label: 'Connector', value: connector },
    { label: 'Production gate', value: productionGate },
  ].filter((item): item is { label: string; value: string } => Boolean(item.value));

  return (
    <section
      data-testid="feature-unavailable-page"
      className="min-h-[70vh] bg-gray-50 px-4 py-10"
    >
      <div className="mx-auto w-full max-w-3xl">
        <div className="mb-5 flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded border border-yellow-300 bg-yellow-50 text-yellow-700">
            <AlertTriangle aria-hidden="true" size={22} />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-950">Feature Unavailable</h1>
            <p className="text-sm text-gray-600">
              {featureName} remains visible as a product boundary, not a production workflow.
            </p>
          </div>
        </div>

        <p className="mb-5 text-sm text-gray-700">
          {featureName} {reason}
        </p>

        <dl className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-2" data-testid="feature-unavailable-details">
          {details.map((detail) => (
            <div key={detail.label} className="rounded border border-gray-200 bg-white p-3">
              <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">{detail.label}</dt>
              <dd className="mt-1 text-sm text-gray-900">{detail.value}</dd>
            </div>
          ))}
        </dl>

        <p className="mb-6 rounded border border-gray-200 bg-white p-3 text-sm text-gray-700" data-testid="feature-unavailable-remediation">
          {remediation}
        </p>

        <div className="flex flex-col gap-3 sm:flex-row">
          <button
            onClick={() => navigate(`/workspaces/${workspaceId}/dashboard`)}
            className="inline-flex items-center justify-center gap-2 rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700"
            data-testid="back-to-dashboard"
          >
            <LayoutDashboard aria-hidden="true" size={16} />
            Return to Dashboard
          </button>

          <button
            onClick={() => navigate(-1)}
            className="inline-flex items-center justify-center gap-2 rounded bg-gray-100 px-4 py-2 text-sm text-gray-700 hover:bg-gray-200"
            data-testid="go-back"
          >
            <ArrowLeft aria-hidden="true" size={16} />
            Go Back
          </button>
        </div>
      </div>
    </section>
  );
}
