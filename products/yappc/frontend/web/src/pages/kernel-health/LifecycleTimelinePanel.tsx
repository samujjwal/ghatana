/**
 * Lifecycle Timeline Panel
 *
 * Displays the lifecycle execution timeline with phase-by-phase status,
 * duration, and gate results for a ProductUnit.
 */

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/badge';
import { CheckCircle, XCircle, Clock, Loader2 } from 'lucide-react';

export interface LifecycleRunSummary {
  phase: string;
  status: 'succeeded' | 'failed' | 'running' | 'blocked' | 'pending';
  timestamp: string;
  duration?: number;
}

interface LifecycleTimelinePanelProps {
  productUnitId: string;
  runs: LifecycleRunSummary[];
}

export const LifecycleTimelinePanel: React.FC<LifecycleTimelinePanelProps> = ({
  productUnitId,
  runs,
}) => {
  const getPhaseIcon = (status: string) => {
    switch (status) {
      case 'succeeded':
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'failed':
        return <XCircle className="h-4 w-4 text-red-500" />;
      case 'running':
        return <Loader2 className="h-4 w-4 text-blue-500 animate-spin" />;
      case 'blocked':
        return <XCircle className="h-4 w-4 text-orange-500" />;
      default:
        return <Clock className="h-4 w-4 text-gray-500" />;
    }
  };

  const getStatusBadgeVariant = (status: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (status) {
      case 'succeeded':
        return 'default';
      case 'failed':
      case 'blocked':
        return 'destructive';
      case 'running':
        return 'secondary';
      default:
        return 'outline';
    }
  };

  if (runs.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Lifecycle Timeline</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">
            No lifecycle runs available for {productUnitId}
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Lifecycle Timeline</h3>
        <Badge variant="outline">{runs.length} phases</Badge>
      </div>

      <div className="space-y-3">
        {runs.map((run, index) => (
          <div key={index} className="flex items-start gap-4 p-4 border rounded-lg">
            <div className="mt-1">{getPhaseIcon(run.status)}</div>
            
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between mb-1">
                <h4 className="font-semibold">{run.phase}</h4>
                <Badge variant={getStatusBadgeVariant(run.status)}>
                  {run.status}
                </Badge>
              </div>
              
              <div className="flex items-center gap-4 text-sm text-muted-foreground">
                <span>{run.timestamp ? new Date(run.timestamp).toLocaleString() : 'Not started'}</span>
                {run.duration && <span>{run.duration}s</span>}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default LifecycleTimelinePanel;
