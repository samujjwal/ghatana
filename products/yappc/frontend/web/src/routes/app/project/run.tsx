/**
 * Run Phase — Execute pipelines and deployments
 *
 * Pipeline execution, deployment management, and agent workflow runs.
 * Admin tools (prompt versions, A/B testing) surface as context-sensitive
 * panels within this phase via the header actions menu.
 *
 * NOTE: This phase is gated behind the PHASE_RUN feature flag until GitHub Actions
 * CI/CD integration is complete. See R-6 in the audit document.
 *
 * @doc.type route
 * @doc.purpose Run phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { useFeatureFlag, FeatureFlag } from '@/providers/FeatureFlagProvider';

function RunPhaseDisabled() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-zinc-950">
      <div className="max-w-md text-center space-y-4">
        <div className="text-6xl">🚧</div>
        <h1 className="text-2xl font-bold text-white">Run Phase Temporarily Disabled</h1>
        <p className="text-zinc-400">
          The Run phase (deployment and CI/CD execution) is currently disabled pending
          completion of the GitHub Actions integration. This is a temporary measure per
          the product correctness audit (R-6).
        </p>
        <div className="text-sm text-zinc-500 space-y-2">
          <p><strong>What's disabled:</strong></p>
          <ul className="list-disc list-inside text-left">
            <li>Deployment workflows</li>
            <li>CI/CD pipeline execution</li>
            <li>Run phase actions (deploy, test, monitor logs)</li>
          </ul>
        </div>
        <div className="text-xs text-zinc-600">
          To enable: Set the PHASE_RUN feature flag to true after completing
          GitHub Actions integration in GitHubActionsCiCdAdapter.java
        </div>
      </div>
    </div>
  );
}

export function Component() {
  const { isFeatureEnabled } = useFeatureFlag();
  const isRunPhaseEnabled = isFeatureEnabled(FeatureFlag.PHASE_RUN);

  if (!isRunPhaseEnabled) {
    return <RunPhaseDisabled />;
  }

  // Import the actual run/deploy component when enabled
  // Note: The deploy directory doesn't exist yet, so this will fail
  // until the Run phase is properly implemented
  return (
    <div className="p-8">
      <div className="bg-yellow-900/20 border border-yellow-800 rounded-lg p-4 text-yellow-400">
        <h2 className="font-semibold mb-2">Run Phase Under Construction</h2>
        <p className="text-sm">
          The Run phase is enabled but the deployment component is not yet implemented.
          Please complete the deployment UI implementation.
        </p>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  return (
    <div className="p-8">
      <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
        <h2 className="font-semibold mb-2">Run Phase Error</h2>
        <p className="text-sm">
          An error occurred in the Run phase. Please check the console for details.
        </p>
      </div>
    </div>
  );
}
