/**
 * Growth goal widget — feature-flagged dashboard card.
 *
 * Disabled by default via {@code DMOS_DASHBOARD_GROWTH_METRICS_ENABLED}.
 * Shows "Unavailable" when the flag is off.
 *
 * @doc.type component
 * @doc.purpose Dashboard card for marketing growth goal status
 * @doc.layer frontend
 */
import React from 'react';
import { FEATURE_FLAGS, isFeatureEnabled } from '@/lib/feature-flags';
import { DashboardWidgetCard } from './DashboardWidgetCard';

export const GrowthGoalWidget: React.FC = () => {
  if (!isFeatureEnabled(FEATURE_FLAGS.DASHBOARD_GROWTH_METRICS)) {
    return (
      <DashboardWidgetCard
        testId="growth-goal-widget"
        title="Growth Goals"
        state="unavailable"
        message="Currently unavailable"
        stateMessageTestId="growth-goal-unavailable"
      />
    );
  }

  return (
    <DashboardWidgetCard
      testId="growth-goal-widget"
      title="Growth Goals"
      state="ready"
    >
      <p className="text-xs text-gray-700 mt-2" data-testid="growth-goal-loading-placeholder">
        Metrics loading…
      </p>
    </DashboardWidgetCard>
  );
};
