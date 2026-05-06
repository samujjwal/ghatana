/**
 * Growth goal widget — placeholder-backed summary.
 *
 * @doc.type component
 * @doc.purpose Dashboard card for marketing growth goal status
 * @doc.layer frontend
 */
import React from 'react';
import { FEATURE_FLAGS, isFeatureEnabled } from '@/lib/feature-flags';

export const GrowthGoalWidget: React.FC = () => {
  if (!isFeatureEnabled(FEATURE_FLAGS.DASHBOARD_GROWTH_METRICS)) {
    return (
      <article
        aria-labelledby="growth-goal-title"
        data-testid="growth-goal-widget"
        className="border rounded-lg p-4 opacity-60"
      >
        <h2
          id="growth-goal-title"
          className="text-sm font-semibold text-gray-700"
        >
          Growth Goals
        </h2>
        <p className="text-xs text-gray-400 mt-2">Coming soon</p>
      </article>
    );
  }

  return (
    <article
      aria-labelledby="growth-goal-title"
      data-testid="growth-goal-widget"
      className="border rounded-lg p-4"
    >
      <h2
        id="growth-goal-title"
        className="text-sm font-semibold text-gray-700"
      >
        Growth Goals
      </h2>
      <p className="text-xs text-gray-400 mt-2">Metrics loading…</p>
    </article>
  );
};
