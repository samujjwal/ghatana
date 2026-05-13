/**
 * ProductLifecycleStatusPanel
 * 
 * Displays the current lifecycle status of a product including active phase,
 * phase status, and recent runs.
 * 
 * @doc.type component
 * @doc.purpose Display product lifecycle status
 * @doc.layer platform
 */

import type { ProductLifecycleStatus } from '../../contracts/product-lifecycle';
import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

interface ProductLifecycleStatusPanelProps {
  readonly status: ProductLifecycleStatus;
  readonly onViewRun?: (runId: string) => void;
}

export function ProductLifecycleStatusPanel({ status, onViewRun }: ProductLifecycleStatusPanelProps) {
  const { currentPhase, phaseStatus, lastRun } = status;

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'completed':
        return 'green';
      case 'in-progress':
        return 'blue';
      case 'failed':
        return 'red';
      case 'pending':
        return 'gray';
      default:
        return 'gray';
    }
  };

  return (
    <Card>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold">Lifecycle Status</h3>
        {currentPhase && (
          <Badge color={getStatusColor(phaseStatus)}>{phaseStatus}</Badge>
        )}
      </div>

      {currentPhase && (
        <div className="mb-4">
          <div className="text-sm text-gray-600 mb-1">Current Phase</div>
          <div className="text-base font-medium">{currentPhase}</div>
        </div>
      )}

      {lastRun && (
        <div className="border-t pt-4">
          <div className="text-sm text-gray-600 mb-2">Last Run</div>
          <div className="flex items-center justify-between">
            <div>
              <div className="text-sm font-medium">{lastRun.phase}</div>
              <div className="text-xs text-gray-500">{new Date(lastRun.startedAt).toLocaleString()}</div>
            </div>
            {onViewRun && (
              <button
                onClick={() => onViewRun(lastRun.runId)}
                className="text-sm text-blue-600 hover:text-blue-700"
              >
                View Details
              </button>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}
