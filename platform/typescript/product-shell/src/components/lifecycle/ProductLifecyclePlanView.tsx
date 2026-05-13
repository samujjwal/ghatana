/**
 * ProductLifecyclePlanView
 * 
 * Displays a lifecycle plan including phase, surfaces, mode, and steps.
 * 
 * @doc.type component
 * @doc.purpose Display product lifecycle plan
 * @doc.layer platform
 */

import type { ProductLifecyclePlan } from '../../contracts/product-lifecycle';
import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

interface ProductLifecyclePlanViewProps {
  readonly plan: ProductLifecyclePlan;
}

export function ProductLifecyclePlanView({ plan }: ProductLifecyclePlanViewProps) {
  const { phase, surfaces, mode, steps, dryRun } = plan;

  const getStepStatusColor = (status: string) => {
    switch (status) {
      case 'completed':
        return 'green';
      case 'in-progress':
        return 'blue';
      case 'failed':
        return 'red';
      case 'pending':
        return 'gray';
      case 'skipped':
        return 'yellow';
      default:
        return 'gray';
    }
  };

  return (
    <Card>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold">Lifecycle Plan: {phase}</h3>
        {dryRun && <Badge color="blue">Dry Run</Badge>}
      </div>

      <div className="grid grid-cols-2 gap-4 mb-6">
        <div>
          <div className="text-sm text-gray-600 mb-1">Mode</div>
          <div className="text-base font-medium capitalize">{mode}</div>
        </div>
        <div>
          <div className="text-sm text-gray-600 mb-1">Surfaces</div>
          <div className="text-base font-medium">{surfaces.join(', ')}</div>
        </div>
      </div>

      <div>
        <div className="text-sm text-gray-600 mb-2">Steps</div>
        <div className="space-y-2">
          {steps.map((step, index) => (
            <div key={step.id} className="flex items-center gap-3 p-2 bg-gray-50 rounded">
              <div className="text-sm text-gray-500 w-6">{index + 1}.</div>
              <div className="flex-1">
                <div className="text-sm font-medium">{step.name}</div>
                {step.error && (
                  <div className="text-xs text-red-600 mt-1">{step.error}</div>
                )}
              </div>
              <Badge color={getStepStatusColor(step.status)} size="sm">
                {step.status}
              </Badge>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
}
