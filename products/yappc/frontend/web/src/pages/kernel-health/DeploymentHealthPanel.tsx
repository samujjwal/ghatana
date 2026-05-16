/**
 * Deployment Health Panel
 *
 * Displays deployment status, target environment, health checks,
 * rollback status, and deployment history for a ProductUnit.
 */

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/badge';
import { CheckCircle, XCircle, Server, Clock, RotateCcw } from 'lucide-react';

export interface HealthCheck {
  name: string;
  status: 'pass' | 'fail' | 'pending';
  lastChecked: string;
}

export interface Deployment {
  id: string;
  status: 'deployed' | 'failed' | 'pending' | 'rolling_back' | 'not_deployed';
  target: string;
  environment: 'dev' | 'staging' | 'production' | 'preview';
  deployedAt?: string;
  artifactId: string;
  healthChecks: HealthCheck[];
  rollbackAvailable: boolean;
  rollbackStatus?: 'available' | 'not_available' | 'in_progress';
}

interface DeploymentHealthPanelProps {
  productUnitId: string;
  deployment: Deployment;
}

export const DeploymentHealthPanel: React.FC<DeploymentHealthPanelProps> = ({
  productUnitId,
  deployment,
}) => {
  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'deployed':
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'failed':
        return <XCircle className="h-4 w-4 text-red-500" />;
      case 'rolling_back':
        return <RotateCcw className="h-4 w-4 text-orange-500" />;
      default:
        return <Clock className="h-4 w-4 text-gray-500" />;
    }
  };

  const getStatusBadgeVariant = (status: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (status) {
      case 'deployed':
        return 'default';
      case 'failed':
        return 'destructive';
      case 'rolling_back':
        return 'secondary';
      default:
        return 'outline';
    }
  };

  const getEnvironmentBadgeVariant = (environment: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (environment) {
      case 'production':
        return 'destructive';
      case 'staging':
        return 'secondary';
      case 'dev':
        return 'outline';
      default:
        return 'outline';
    }
  };

  const getHealthCheckIcon = (status: string) => {
    switch (status) {
      case 'pass':
        return <CheckCircle className="h-3 w-3 text-green-500" />;
      case 'fail':
        return <XCircle className="h-3 w-3 text-red-500" />;
      default:
        return <Clock className="h-3 w-3 text-gray-500" />;
    }
  };

  if (!deployment) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Deployment Health</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">
            No deployment data available for {productUnitId}
          </p>
        </CardContent>
      </Card>
    );
  }

  const passedHealthChecks = deployment.healthChecks.filter(h => h.status === 'pass').length;
  const failedHealthChecks = deployment.healthChecks.filter(h => h.status === 'fail').length;
  const pendingHealthChecks = deployment.healthChecks.filter(h => h.status === 'pending').length;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Deployment Health</h3>
        <div className="flex items-center gap-2">
          <Badge variant={getStatusBadgeVariant(deployment.status)}>
            {deployment.status}
          </Badge>
          <Badge variant={getEnvironmentBadgeVariant(deployment.environment)}>
            {deployment.environment}
          </Badge>
        </div>
      </div>

      <div className="p-4 border rounded-lg space-y-4">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <Server className="h-4 w-4" />
            <div>
              <h4 className="font-semibold">{deployment.target}</h4>
              <p className="text-sm text-muted-foreground">
                {deployment.deployedAt ? `Deployed at ${new Date(deployment.deployedAt).toLocaleString()}` : 'Not deployed'}
              </p>
            </div>
          </div>
          {getStatusIcon(deployment.status)}
        </div>

        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-muted-foreground">Artifact ID</p>
            <p className="font-mono text-xs">{deployment.artifactId}</p>
          </div>
          <div>
            <p className="text-muted-foreground">Rollback</p>
            <p className="font-medium">
              {deployment.rollbackAvailable ? 'Available' : 'Not Available'}
            </p>
          </div>
        </div>

        {deployment.healthChecks.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <p className="font-medium">Health Checks</p>
              <div className="flex gap-2">
                <Badge variant="default">{passedHealthChecks} passed</Badge>
                {failedHealthChecks > 0 && <Badge variant="destructive">{failedHealthChecks} failed</Badge>}
                {pendingHealthChecks > 0 && <Badge variant="outline">{pendingHealthChecks} pending</Badge>}
              </div>
            </div>

            <div className="space-y-2">
              {deployment.healthChecks.map((check, index) => (
                <div key={index} className="flex items-center justify-between p-2 bg-muted rounded">
                  <div className="flex items-center gap-2">
                    {getHealthCheckIcon(check.status)}
                    <span className="text-sm font-medium">{check.name}</span>
                  </div>
                  <div className="text-xs text-muted-foreground">
                    {check.lastChecked ? new Date(check.lastChecked).toLocaleString() : 'Not checked'}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {deployment.rollbackAvailable && deployment.rollbackStatus && (
          <div className="flex items-center gap-2 p-3 bg-muted rounded">
            <RotateCcw className="h-4 w-4" />
            <div>
              <p className="text-sm font-medium">Rollback Status</p>
              <p className="text-sm text-muted-foreground">{deployment.rollbackStatus}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default DeploymentHealthPanel;
