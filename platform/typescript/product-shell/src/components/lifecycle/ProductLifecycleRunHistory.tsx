/**
 * ProductLifecycleRunHistory
 * 
 * Displays the history of lifecycle runs for a product.
 * 
 * @doc.type component
 * @doc.purpose Display product lifecycle run history
 * @doc.layer platform
 */

import type { ProductLifecycleRun } from '../../contracts/product-lifecycle';
import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

interface ProductLifecycleRunHistoryProps {
  readonly runs: readonly ProductLifecycleRun[];
  readonly onViewRun?: (runId: string) => void;
}

export function ProductLifecycleRunHistory({ runs, onViewRun }: ProductLifecycleRunHistoryProps) {
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
      <h3 className="text-lg font-semibold mb-4">Lifecycle Run History</h3>

      {runs.length === 0 ? (
        <div className="text-sm text-gray-500 py-4">No runs yet</div>
      ) : (
        <div className="space-y-3">
          {runs.map((run) => (
            <div
              key={run.runId}
              className="flex items-center justify-between p-3 bg-gray-50 rounded hover:bg-gray-100"
            >
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium capitalize">{run.phase}</span>
                  <Badge color={getStatusColor(run.status)} size="sm">
                    {run.status}
                  </Badge>
                  {run.dryRun && <Badge color="blue" size="sm">Dry Run</Badge>}
                </div>
                <div className="text-xs text-gray-500 mt-1">
                  {new Date(run.startedAt).toLocaleString()} • by {run.triggeredBy}
                </div>
              </div>
              {onViewRun && (
                <button
                  onClick={() => onViewRun(run.runId)}
                  className="text-sm text-blue-600 hover:text-blue-700"
                >
                  View
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
