/**
 * ProductConformanceSummaryCard
 * 
 * Displays a summary of product conformance status.
 * 
 * @doc.type component
 * @doc.purpose Display product conformance summary
 * @doc.layer platform
 */

import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

interface ConformanceCheck {
  readonly name: string;
  readonly status: 'pass' | 'fail' | 'pending';
  readonly message?: string;
}

interface ProductConformanceSummaryCardProps {
  readonly checks: readonly ConformanceCheck[];
  readonly onViewDetails?: () => void;
}

export function ProductConformanceSummaryCard({ checks, onViewDetails }: ProductConformanceSummaryCardProps) {
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'pass':
        return 'green';
      case 'fail':
        return 'red';
      case 'pending':
        return 'gray';
      default:
        return 'gray';
    }
  };

  const passedCount = checks.filter((c) => c.status === 'pass').length;
  const failedCount = checks.filter((c) => c.status === 'fail').length;
  const pendingCount = checks.filter((c) => c.status === 'pending').length;

  const overallStatus = failedCount > 0 ? 'fail' : pendingCount > 0 ? 'pending' : 'pass';

  return (
    <Card>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold">Conformance Status</h3>
        <Badge color={getStatusColor(overallStatus)}>{overallStatus}</Badge>
      </div>

      <div className="grid grid-cols-3 gap-4 mb-4">
        <div className="text-center">
          <div className="text-2xl font-semibold text-green-600">{passedCount}</div>
          <div className="text-xs text-gray-600">Passed</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-semibold text-red-600">{failedCount}</div>
          <div className="text-xs text-gray-600">Failed</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-semibold text-gray-600">{pendingCount}</div>
          <div className="text-xs text-gray-600">Pending</div>
        </div>
      </div>

      <div className="space-y-2">
        {checks.map((check) => (
          <div key={check.name} className="flex items-center justify-between p-2 bg-gray-50 rounded">
            <span className="text-sm font-medium">{check.name}</span>
            <Badge color={getStatusColor(check.status)} size="sm">
              {check.status}
            </Badge>
          </div>
        ))}
      </div>

      {onViewDetails && (
        <div className="border-t pt-4 mt-4">
          <button
            onClick={onViewDetails}
            className="text-sm text-blue-600 hover:text-blue-700"
          >
            View Details
          </button>
        </div>
      )}
    </Card>
  );
}
