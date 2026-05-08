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

import { ArrowRight, AlertCircle, Plus, CheckCircle, Activity } from 'lucide-react';
import React from 'react';

import { Alert, Button, Card, CardContent } from '@ghatana/design-system';

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
        <div
          aria-label="Loading dashboard"
          className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"
        />
      </div>
    );
  }

  const getHealthIcon = (status: HealthIndicator['status']): React.ReactNode => {
    switch (status) {
      case 'healthy':
        return <CheckCircle className="w-4 h-4 text-fg-muted" />;
      case 'warning':
        return <AlertCircle className="w-4 h-4 text-fg-muted" />;
      case 'critical':
        return <AlertCircle className="w-4 h-4 text-fg-muted" />;
    }
  };

  const getHealthTone = (status: HealthIndicator['status']): 'success' | 'warning' | 'error' => {
    switch (status) {
      case 'healthy':
        return 'success';
      case 'warning':
        return 'warning';
      case 'critical':
        return 'error';
    }
  };

  return (
    <div className="space-y-6">
      <Card data-testid="primary-next-action" variant="outlined">
        <CardContent className="space-y-4 p-6">
          <div className="flex items-start gap-4">
            <div className="flex-shrink-0 mt-1">
              {primaryAction.icon || <ArrowRight className="w-6 h-6 text-primary-600" />}
            </div>
            <div className="flex-1">
              <h2 className="text-lg font-semibold text-fg mb-2">
                Continue: {primaryAction.title}
              </h2>
              <p className="text-fg-muted">{primaryAction.description}</p>
            </div>
          </div>
          <Button onClick={primaryAction.action} rightIcon={<ArrowRight className="w-4 h-4" />}>
            Open next step
          </Button>
        </CardContent>
      </Card>

      {secondaryAction && (
        <Alert
          data-testid="secondary-action"
          severity="warning"
          title={`Review: ${secondaryAction.title}`}
          icon={secondaryAction.icon || <AlertCircle className="w-5 h-5" />}
          action={
            <Button size="sm" variant="outline" tone="warning" onClick={secondaryAction.action}>
              Review
            </Button>
          }
        >
          {secondaryAction.description}
        </Alert>
      )}

      {tertiaryAction && (
        <Card data-testid="tertiary-action" variant="outlined">
          <CardContent className="flex items-start justify-between gap-4 p-4">
            <div className="flex items-start gap-3">
              <div className="flex-shrink-0 mt-0.5">
                {tertiaryAction.icon || <Plus className="w-5 h-5 text-fg-muted" />}
              </div>
              <div className="flex-1">
                <h3 className="text-base font-medium text-fg mb-1">
                  {tertiaryAction.title}
                </h3>
                <p className="text-sm text-fg-muted">{tertiaryAction.description}</p>
              </div>
            </div>
            <Button size="sm" variant="ghost" tone="neutral" onClick={tertiaryAction.action}>
              Open
            </Button>
          </CardContent>
        </Card>
      )}

      {healthIndicators.length > 0 && (
        <Card data-testid="health-indicators" variant="outlined">
          <CardContent className="p-4">
            <h4 className="text-sm font-medium text-fg-muted mb-3 flex items-center gap-2">
              <Activity className="w-4 h-4" />
              Health
            </h4>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
              {healthIndicators.map((indicator) => (
                <Alert
                  key={indicator.id}
                  severity={getHealthTone(indicator.status)}
                  title={indicator.label}
                  className="h-full"
                >
                  <span className="flex items-center gap-2">
                    {getHealthIcon(indicator.status)}
                    <span>{indicator.value ?? 'No recent signal'}</span>
                  </span>
                </Alert>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default NextActionDashboard;
