/**
 * ProductPromotionAction
 * 
 * Displays an action button for promoting a product between environments.
 * 
 * @doc.type component
 * @doc.purpose Display product promotion action
 * @doc.layer platform
 */

import type { ProductPromotion } from '../../contracts/product-deployment';
import { Badge } from '../ui/Badge';

interface ProductPromotionActionProps {
  readonly promotion: ProductPromotion;
  readonly onPromote?: () => void;
  readonly onApprove?: () => void;
  readonly onReject?: () => void;
}

export function ProductPromotionAction({ promotion, onPromote, onApprove, onReject }: ProductPromotionActionProps) {
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'deployed':
        return 'green';
      case 'deploying':
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
    <div className="flex items-center justify-between p-4 bg-white border border-gray-200 rounded-lg dark:bg-gray-900 dark:border-gray-700">
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">
            {promotion.sourceEnvironment} → {promotion.targetEnvironment}
          </span>
          <Badge color={getStatusColor(promotion.status)} size="sm">
            {promotion.status}
          </Badge>
        </div>
        <div className="text-xs text-gray-500 mt-1">
          Version {promotion.version}
          {promotion.approvalRequired && ' • Approval Required'}
        </div>
      </div>

      <div className="flex items-center gap-2">
        {promotion.approvalRequired && promotion.status === 'pending' && (
          <>
            {onApprove && (
              <button
                onClick={onApprove}
                className="px-3 py-1.5 text-xs font-medium text-white bg-green-600 rounded hover:bg-green-700"
              >
                Approve
              </button>
            )}
            {onReject && (
              <button
                onClick={onReject}
                className="px-3 py-1.5 text-xs font-medium text-white bg-red-600 rounded hover:bg-red-700"
              >
                Reject
              </button>
            )}
          </>
        )}
        {onPromote && promotion.status === 'pending' && !promotion.approvalRequired && (
          <button
            onClick={onPromote}
            className="px-3 py-1.5 text-xs font-medium text-white bg-blue-600 rounded hover:bg-blue-700"
          >
            Promote
          </button>
        )}
      </div>
    </div>
  );
}
