/**
 * Next Action Dashboard
 *
 * Dashboard-first next-action system showing primary, secondary, and tertiary actions
 * along with quiet health indicators.
 *
 * @doc.type component
 * @doc.purpose Dashboard with next-action focus
 * @doc.layer product
 * @doc.pattern Dashboard
 */

import React from 'react';
import { ArrowRight, AlertCircle, Plus, CheckCircle, Activity } from 'lucide-react';

export interface NextAction {
  id: string;
  title: string;
  description: string;
  priority: 'primary' | 'secondary' | 'tertiary';
  action: () => void;
  icon?: React.ReactNode;
}

export interface HealthIndicator {
  id: string;
  label: string;
  status: 'healthy' | 'warning' | 'critical';
  value?: string;
}

export interface NextActionDashboardProps {
  /** Primary next action */
  primaryAction: NextAction;
  /** Secondary action (e.g., review blocker) */
  secondaryAction?: NextAction;
  /** Tertiary action (e.g., create new project) */
  tertiaryAction?: NextAction;
  /** Quiet health indicators */
  healthIndicators?: HealthIndicator[];
  /** Loading state */
  loading?: boolean;
}

export const NextActionDashboard: React.FC<NextActionDashboardProps> = ({
  primaryAction,
  secondaryAction,
  tertiaryAction,
  healthIndicators = [],
  loading = false,
}) => {
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
      </div>
    );
  }

  const getHealthIcon = (status: HealthIndicator['status']) => {
    switch (status) {
      case 'healthy':
        return <CheckCircle className="w-4 h-4 text-green-500" />;
      case 'warning':
        return <AlertCircle className="w-4 h-4 text-yellow-500" />;
      case 'critical':
        return <AlertCircle className="w-4 h-4 text-red-500" />;
    }
  };

  const getHealthColor = (status: HealthIndicator['status']) => {
    switch (status) {
      case 'healthy':
        return 'text-green-600';
      case 'warning':
        return 'text-yellow-600';
      case 'critical':
        return 'text-red-600';
    }
  };

  return (
    <div className="space-y-6">
      {/* Primary Next Action */}
      <div
        data-testid="primary-next-action"
        className="bg-primary-50 border border-primary-200 rounded-lg p-6 hover:shadow-md transition-shadow cursor-pointer"
        onClick={primaryAction.action}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            primaryAction.action();
          }
        }}
      >
        <div className="flex items-start gap-4">
          <div className="flex-shrink-0 mt-1">
            {primaryAction.icon || <ArrowRight className="w-6 h-6 text-primary-600" />}
          </div>
          <div className="flex-1">
            <h2 className="text-lg font-semibold text-primary-900 mb-2">
              Continue: {primaryAction.title}
            </h2>
            <p className="text-primary-700">{primaryAction.description}</p>
          </div>
        </div>
      </div>

      {/* Secondary Action */}
      {secondaryAction && (
        <div
          data-testid="secondary-action"
          className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer"
          onClick={secondaryAction.action}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              secondaryAction.action();
            }
          }}
        >
          <div className="flex items-start gap-3">
            <div className="flex-shrink-0 mt-0.5">
              {secondaryAction.icon || <AlertCircle className="w-5 h-5 text-yellow-600" />}
            </div>
            <div className="flex-1">
              <h3 className="text-base font-medium text-yellow-900 mb-1">
                Review: {secondaryAction.title}
              </h3>
              <p className="text-sm text-yellow-700">{secondaryAction.description}</p>
            </div>
          </div>
        </div>
      )}

      {/* Tertiary Action */}
      {tertiaryAction && (
        <div
          data-testid="tertiary-action"
          className="bg-gray-50 border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer"
          onClick={tertiaryAction.action}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              tertiaryAction.action();
            }
          }}
        >
          <div className="flex items-start gap-3">
            <div className="flex-shrink-0 mt-0.5">
              {tertiaryAction.icon || <Plus className="w-5 h-5 text-gray-600" />}
            </div>
            <div className="flex-1">
              <h3 className="text-base font-medium text-gray-900 mb-1">
                {tertiaryAction.title}
              </h3>
              <p className="text-sm text-gray-600">{tertiaryAction.description}</p>
            </div>
          </div>
        </div>
      )}

      {/* Quiet Health Indicators */}
      {healthIndicators.length > 0 && (
        <div
          data-testid="health-indicators"
          className="bg-white border border-gray-200 rounded-lg p-4"
        >
          <h4 className="text-sm font-medium text-gray-500 mb-3 flex items-center gap-2">
            <Activity className="w-4 h-4" />
            System Health
          </h4>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {healthIndicators.map((indicator) => (
              <div
                key={indicator.id}
                className="flex items-center gap-2 text-sm"
              >
                {getHealthIcon(indicator.status)}
                <span className="text-gray-600">{indicator.label}:</span>
                {indicator.value && (
                  <span className={`font-medium ${getHealthColor(indicator.status)}`}>
                    {indicator.value}
                  </span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default NextActionDashboard;
