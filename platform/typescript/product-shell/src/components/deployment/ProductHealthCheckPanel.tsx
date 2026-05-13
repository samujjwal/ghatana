/**
 * ProductHealthCheckPanel
 * 
 * Displays health check results for a product.
 * 
 * @doc.type component
 * @doc.purpose Display product health checks
 * @doc.layer platform
 */

import type { ProductHealthSummary } from '../../contracts/product-health';
import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

interface ProductHealthCheckPanelProps {
  readonly health: ProductHealthSummary;
}

export function ProductHealthCheckPanel({ health }: ProductHealthCheckPanelProps) {
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'healthy':
        return 'green';
      case 'degraded':
        return 'yellow';
      case 'unhealthy':
        return 'red';
      default:
        return 'gray';
    }
  };

  return (
    <Card>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold">Health Checks: {health.environment}</h3>
        <Badge color={getStatusColor(health.overallStatus)}>{health.overallStatus}</Badge>
      </div>

      <div className="space-y-2">
        {health.checks.map((check) => (
          <div key={check.checkId} className="flex items-center justify-between p-3 bg-gray-50 rounded">
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium">{check.surface}</span>
                <Badge color={getStatusColor(check.status)} size="sm">
                  {check.status}
                </Badge>
              </div>
              <div className="text-xs text-gray-500 mt-1">
                {check.type} • {new Date(check.lastChecked).toLocaleString()}
              </div>
              {check.message && (
                <div className="text-xs text-gray-600 mt-1">{check.message}</div>
              )}
            </div>
            {check.responseTimeMs && (
              <div className="text-xs text-gray-500">{check.responseTimeMs}ms</div>
            )}
          </div>
        ))}
      </div>

      <div className="border-t pt-4 mt-4">
        <div className="text-xs text-gray-500">Last updated: {new Date(health.lastUpdated).toLocaleString()}</div>
      </div>
    </Card>
  );
}
