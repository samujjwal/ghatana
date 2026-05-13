/**
 * ProductRollbackAction
 * 
 * Displays an action button for rolling back a product deployment.
 * 
 * @doc.type component
 * @doc.purpose Display product rollback action
 * @doc.layer platform
 */

import type { RollbackPlan } from '../../contracts/product-deployment';

interface ProductRollbackActionProps {
  readonly rollbackPlan: RollbackPlan;
  readonly onRollback?: () => void;
}

export function ProductRollbackAction({ rollbackPlan, onRollback }: ProductRollbackActionProps) {
  return (
    <div className="flex items-center justify-between p-4 bg-white border border-gray-200 rounded-lg dark:bg-gray-900 dark:border-gray-700">
      <div className="flex-1">
        <div className="text-sm font-medium mb-1">Rollback Plan</div>
        <div className="text-xs text-gray-500">
          Strategy: {rollbackPlan.strategy}
          {rollbackPlan.targetVersion && ` • Target: ${rollbackPlan.targetVersion}`}
        </div>
        {rollbackPlan.reason && (
          <div className="text-xs text-gray-600 mt-1">Reason: {rollbackPlan.reason}</div>
        )}
      </div>

      {onRollback && (
        <button
          onClick={onRollback}
          className="px-3 py-1.5 text-xs font-medium text-white bg-orange-600 rounded hover:bg-orange-700"
        >
          Rollback
        </button>
      )}
    </div>
  );
}
