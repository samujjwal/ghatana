/**
 * Gate Health Panel
 *
 * Displays gate evaluation results, including gate status, criteria,
 * failure reasons, and recommended actions for a ProductUnit.
 */

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { CheckCircle, XCircle, AlertTriangle, Info } from 'lucide-react';

interface GateEvaluation {
  id: string;
  name: string;
  phase: string;
  status: 'passed' | 'failed' | 'blocked' | 'skipped';
  required: boolean;
  criteria: string[];
  reason?: string;
  evidence?: string;
}

interface GateHealthPanelProps {
  productUnitId: string;
  gates: GateEvaluation[];
}

export const GateHealthPanel: React.FC<GateHealthPanelProps> = ({
  productUnitId,
  gates,
}) => {
  const getGateIcon = (status: string) => {
    switch (status) {
      case 'passed':
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'failed':
        return <XCircle className="h-4 w-4 text-red-500" />;
      case 'blocked':
        return <AlertTriangle className="h-4 w-4 text-orange-500" />;
      default:
        return <Info className="h-4 w-4 text-gray-500" />;
    }
  };

  const getStatusBadgeVariant = (status: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (status) {
      case 'passed':
        return 'default';
      case 'failed':
      case 'blocked':
        return 'destructive';
      default:
        return 'outline';
    }
  };

  const failedGates = gates.filter(g => g.status === 'failed' || g.status === 'blocked');
  const passedGates = gates.filter(g => g.status === 'passed');
  const skippedGates = gates.filter(g => g.status === 'skipped');

  if (gates.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Gate Health</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">
            No gate evaluations available for {productUnitId}
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Gate Health</h3>
        <div className="flex gap-2">
          <Badge variant="destructive">{failedGates.length} failed</Badge>
          <Badge variant="default">{passedGates.length} passed</Badge>
          <Badge variant="outline">{skippedGates.length} skipped</Badge>
        </div>
      </div>

      {failedGates.length > 0 && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>Gate Failures Detected</AlertTitle>
          <AlertDescription>
            {failedGates.length} gate(s) are blocking lifecycle progression.
            Review the failed gates below and address the blocking criteria.
          </AlertDescription>
        </Alert>
      )}

      <div className="space-y-3">
        {gates.map((gate) => (
          <div key={gate.id} className="p-4 border rounded-lg space-y-3">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2">
                {getGateIcon(gate.status)}
                <h4 className="font-semibold">{gate.name}</h4>
                {gate.required && <Badge variant="outline" className="text-xs">Required</Badge>}
              </div>
              <Badge variant={getStatusBadgeVariant(gate.status)}>
                {gate.status}
              </Badge>
            </div>

            <div className="text-sm text-muted-foreground">
              <span>Phase: {gate.phase}</span>
            </div>

            {gate.criteria && gate.criteria.length > 0 && (
              <div className="space-y-1">
                <p className="text-sm font-medium">Criteria:</p>
                <ul className="list-disc list-inside text-sm text-muted-foreground space-y-1">
                  {gate.criteria.map((criterion, idx) => (
                    <li key={idx}>{criterion}</li>
                  ))}
                </ul>
              </div>
            )}

            {gate.reason && (
              <div className="bg-muted p-3 rounded-md">
                <p className="text-sm font-medium mb-1">Reason:</p>
                <p className="text-sm text-muted-foreground">{gate.reason}</p>
              </div>
            )}

            {gate.evidence && (
              <div className="text-sm text-muted-foreground">
                <span className="font-medium">Evidence:</span> {gate.evidence}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default GateHealthPanel;
