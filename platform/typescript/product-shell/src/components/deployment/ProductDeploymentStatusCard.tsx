/**
 * ProductDeploymentStatusCard
 * 
 * Displays the status of a product deployment.
 * 
 * @doc.type component
 * @doc.purpose Display product deployment status
 * @doc.layer platform
 */

import type { ProductDeployment } from '../../contracts/product-deployment';
import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

interface ProductDeploymentStatusCardProps {
  readonly deployment: ProductDeployment;
  readonly onViewDetails?: (deploymentId: string) => void;
}

export function ProductDeploymentStatusCard({ deployment, onViewDetails }: ProductDeploymentStatusCardProps) {
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'deployed':
        return 'green';
      case 'deploying':
        return 'blue';
      case 'failed':
        return 'red';
      case 'rolling-back':
        return 'orange';
      case 'rolled-back':
        return 'yellow';
      default:
        return 'gray';
    }
  };

  return (
    <Card>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold">Deployment: {deployment.environment}</h3>
        <Badge color={getStatusColor(deployment.status)}>{deployment.status}</Badge>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-4">
        <div>
          <div className="text-sm text-gray-600 mb-1">Version</div>
          <div className="text-base font-medium">{deployment.version}</div>
        </div>
        <div>
          <div className="text-sm text-gray-600 mb-1">Target</div>
          <div className="text-base font-medium capitalize">{deployment.target}</div>
        </div>
      </div>

      <div className="border-t pt-4">
        <div className="text-sm text-gray-600 mb-2">Surfaces</div>
        <div className="space-y-1">
          {deployment.surfaces.map((surface) => (
            <div key={surface.surface} className="flex items-center justify-between text-sm">
              <span className="font-medium">{surface.surface}</span>
              <Badge color={getStatusColor(surface.status)} size="sm">
                {surface.status}
              </Badge>
            </div>
          ))}
        </div>
      </div>

      {deployment.deployedAt && (
        <div className="border-t pt-4 mt-4">
          <div className="text-sm text-gray-600 mb-1">Deployed At</div>
          <div className="text-sm">{new Date(deployment.deployedAt).toLocaleString()}</div>
        </div>
      )}

      {onViewDetails && (
        <div className="border-t pt-4 mt-4">
          <button
            onClick={() => onViewDetails(deployment.deploymentId)}
            className="text-sm text-blue-600 hover:text-blue-700"
          >
            View Details
          </button>
        </div>
      )}
    </Card>
  );
}
