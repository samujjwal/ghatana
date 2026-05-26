/**
 * Recommended Actions Panel
 *
 * Displays rule-based action recommendations derived from Kernel ProductUnit health state.
 * Recommendations are categorized by severity: critical, warning, or info.
 */

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { AlertCircle, AlertTriangle, Info, ArrowRight } from 'lucide-react';

export interface ActionRecommendation {
  severity: 'critical' | 'warning' | 'info';
  title: string;
  description: string;
  actionType: string;
  owner?: string;
  reason?: string;
  evidenceId?: string;
  nextAction?: string;
}

interface RecommendedActionsPanelProps {
  productUnitId: string;
  recommendations: ActionRecommendation[];
}

const getSeverityIcon = (severity: ActionRecommendation['severity']): React.ReactElement => {
  switch (severity) {
    case 'critical':
      return <AlertCircle className="h-4 w-4 text-red-500" />;
    case 'warning':
      return <AlertTriangle className="h-4 w-4 text-orange-500" />;
    default:
      return <Info className="h-4 w-4 text-blue-500" />;
  }
};

const getSeverityBadgeVariant = (
  severity: ActionRecommendation['severity']
): 'default' | 'secondary' | 'destructive' | 'outline' => {
  switch (severity) {
    case 'critical':
      return 'destructive';
    case 'warning':
      return 'secondary';
    default:
      return 'outline';
  }
};

const getSeverityAlertVariant = (
  severity: ActionRecommendation['severity']
): 'default' | 'destructive' => {
  return severity === 'critical' ? 'destructive' : 'default';
};

export const RecommendedActionsPanel: React.FC<RecommendedActionsPanelProps> = ({
  productUnitId,
  recommendations,
}) => {
  if (recommendations.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Recommended Actions</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">
            No recommendations for {productUnitId}. The ProductUnit appears healthy.
          </p>
        </CardContent>
      </Card>
    );
  }

  const criticalCount = recommendations.filter((r) => r.severity === 'critical').length;
  const warningCount = recommendations.filter((r) => r.severity === 'warning').length;

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Recommended Actions</CardTitle>
          <div className="flex gap-2">
            {criticalCount > 0 && (
              <Badge variant="destructive">{criticalCount} critical</Badge>
            )}
            {warningCount > 0 && (
              <Badge variant="secondary">{warningCount} warning</Badge>
            )}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {recommendations.map((rec, index) => (
            <Alert
              key={`${rec.actionType}-${index}`}
              variant={getSeverityAlertVariant(rec.severity)}
            >
              <div className="flex items-start gap-2">
                {getSeverityIcon(rec.severity)}
                <div className="flex-1 min-w-0">
                  <AlertTitle className="flex items-center gap-2">
                    {rec.title}
                    <Badge variant={getSeverityBadgeVariant(rec.severity)} className="text-xs">
                      {rec.severity}
                    </Badge>
                  </AlertTitle>
                  <AlertDescription className="mt-1">
                    {rec.description}
                  </AlertDescription>
                  {rec.actionType && (
                    <div className="mt-2 flex items-center gap-1 text-xs text-muted-foreground">
                      <ArrowRight className="h-3 w-3" />
                      <span>Action: {rec.actionType}</span>
                    </div>
                  )}
                  {(rec.owner || rec.reason || rec.evidenceId || rec.nextAction) && (
                    <dl className="mt-3 grid gap-2 text-xs text-muted-foreground sm:grid-cols-2">
                      {rec.owner && (
                        <div>
                          <dt className="font-medium text-foreground">Owner</dt>
                          <dd>{rec.owner}</dd>
                        </div>
                      )}
                      {rec.reason && (
                        <div>
                          <dt className="font-medium text-foreground">Reason</dt>
                          <dd>{rec.reason}</dd>
                        </div>
                      )}
                      {rec.evidenceId && (
                        <div>
                          <dt className="font-medium text-foreground">Evidence</dt>
                          <dd>{rec.evidenceId}</dd>
                        </div>
                      )}
                      {rec.nextAction && (
                        <div>
                          <dt className="font-medium text-foreground">Next action</dt>
                          <dd>{rec.nextAction}</dd>
                        </div>
                      )}
                    </dl>
                  )}
                </div>
              </div>
            </Alert>
          ))}
        </div>
      </CardContent>
    </Card>
  );
};
