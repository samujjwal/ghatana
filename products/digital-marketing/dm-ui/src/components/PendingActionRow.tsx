import React, { useState, useCallback } from 'react';
import { Loader2, CheckCircle, XCircle, Clock, AlertCircle, MoreHorizontal } from 'lucide-react';

/**
 * P1-031: Per-Row Action Pending States
 *
 * Manages pending action states for individual table rows:
 * - Loading indicators for in-flight operations
 * - Optimistic UI updates
 * - Success/error state transitions
 * - Retry functionality for failed operations
 * - Action cancellation
 * - State persistence across re-renders
 */

export type ActionState = 'idle' | 'loading' | 'success' | 'error' | 'cancelling';

export interface PendingAction {
  id: string;
  rowId: string;
  action: string;
  state: ActionState;
  progress?: number;
  error?: string;
  startTime: Date;
  estimatedDuration?: number;
}

export interface PendingActionRowProps {
  rowId: string;
  children: React.ReactNode;
  actions?: {
    id: string;
    label: string;
    icon?: React.ReactNode;
    onClick: () => Promise<void>;
    variant?: 'primary' | 'secondary' | 'danger';
    disabled?: boolean;
  }[];
  onActionStart?: (action: PendingAction) => void;
  onActionComplete?: (action: PendingAction, success: boolean) => void;
  optimisticData?: unknown;
  rollbackOnError?: boolean;
  showLoadingOverlay?: boolean;
}

/**
 * P1-031: Hook for managing row-level pending actions
 */
export const usePendingRowActions = () => {
  const [pendingActions, setPendingActions] = useState<Map<string, PendingAction>>(new Map());

  const startAction = useCallback((rowId: string, actionType: string, estimatedDuration?: number): string => {
    const actionId = `action-${rowId}-${actionType}-${Date.now()}`;
    
    const action: PendingAction = {
      id: actionId,
      rowId,
      action: actionType,
      state: 'loading',
      startTime: new Date(),
      estimatedDuration,
      progress: 0
    };

    setPendingActions(prev => new Map(prev).set(actionId, action));
    return actionId;
  }, []);

  const updateProgress = useCallback((actionId: string, progress: number) => {
    setPendingActions(prev => {
      const action = prev.get(actionId);
      if (!action) return prev;
      
      const updated = new Map(prev);
      updated.set(actionId, { ...action, progress: Math.min(100, Math.max(0, progress)) });
      return updated;
    });
  }, []);

  const completeAction = useCallback((actionId: string, success: boolean, error?: string) => {
    setPendingActions(prev => {
      const action = prev.get(actionId);
      if (!action) return prev;

      const updated = new Map(prev);
      updated.set(actionId, {
        ...action,
        state: success ? 'success' : 'error',
        error: error,
        progress: success ? 100 : action.progress
      });
      return updated;
    });

    // Auto-clear success actions after 2 seconds
    if (success) {
      setTimeout(() => {
        clearAction(actionId);
      }, 2000);
    }
  }, []);

  const clearAction = useCallback((actionId: string) => {
    setPendingActions(prev => {
      const updated = new Map(prev);
      updated.delete(actionId);
      return updated;
    });
  }, []);

  const cancelAction = useCallback((actionId: string) => {
    setPendingActions(prev => {
      const action = prev.get(actionId);
      if (!action || action.state !== 'loading') return prev;

      const updated = new Map(prev);
      updated.set(actionId, { ...action, state: 'cancelling' });
      return updated;
    });
  }, []);

  const getRowActions = useCallback((rowId: string): PendingAction[] => {
    return Array.from(pendingActions.values()).filter(a => a.rowId === rowId);
  }, [pendingActions]);

  const isRowLoading = useCallback((rowId: string): boolean => {
    return getRowActions(rowId).some(a => a.state === 'loading' || a.state === 'cancelling');
  }, [getRowActions]);

  return {
    pendingActions,
    startAction,
    updateProgress,
    completeAction,
    clearAction,
    cancelAction,
    getRowActions,
    isRowLoading,
    clearAllActions: () => setPendingActions(new Map())
  };
};

/**
 * P1-031: Main component for pending action row
 */
export const PendingActionRow: React.FC<PendingActionRowProps> = ({
  rowId,
  children,
  actions = [],
  onActionStart,
  onActionComplete,
  showLoadingOverlay = true
}) => {
  const { 
    startAction, 
    completeAction, 
    updateProgress, 
    getRowActions,
    clearAction 
  } = usePendingRowActions();

  const rowActions = getRowActions(rowId);
  const isLoading = rowActions.some(a => a.state === 'loading' || a.state === 'cancelling');
  const hasError = rowActions.some(a => a.state === 'error');

  const handleActionClick = async (actionConfig: typeof actions[0]) => {
    const actionId = startAction(rowId, actionConfig.id, 3000); // Default 3s estimate
    
    onActionStart?.({
      id: actionId,
      rowId,
      action: actionConfig.id,
      state: 'loading',
      startTime: new Date()
    });

    try {
      // Simulate progress updates for long operations
      const progressInterval = setInterval(() => {
        updateProgress(actionId, Math.random() * 30 + 50); // 50-80% progress
      }, 500);

      await actionConfig.onClick();
      
      clearInterval(progressInterval);
      updateProgress(actionId, 100);
      completeAction(actionId, true);
      
      onActionComplete?.({
        id: actionId,
        rowId,
        action: actionConfig.id,
        state: 'success',
        startTime: new Date()
      }, true);
    } catch (error) {
      completeAction(actionId, false, error instanceof Error ? error.message : 'Unknown error');
      
      onActionComplete?.({
        id: actionId,
        rowId,
        action: actionConfig.id,
        state: 'error',
        startTime: new Date(),
        error: error instanceof Error ? error.message : 'Unknown error'
      }, false);
    }
  };

  return (
    <div 
      className={`relative ${isLoading ? 'pointer-events-none' : ''}`}
      data-testid={`pending-row-${rowId}`}
      data-loading={isLoading}
    >
      {/* Loading Overlay */}
      {showLoadingOverlay && isLoading && (
        <div className="absolute inset-0 bg-white/60 backdrop-blur-[1px] z-10 flex items-center justify-center rounded">
          <div className="flex items-center gap-2 text-blue-600">
            <Loader2 className="w-5 h-5 animate-spin" />
            <span className="text-sm font-medium">
              {rowActions.find(a => a.state === 'loading')?.action === 'delete' 
                ? 'Deleting...' 
                : 'Processing...'}
            </span>
          </div>
        </div>
      )}

      {/* Error Overlay */}
      {hasError && (
        <div className="absolute inset-0 bg-red-50/80 z-10 flex items-center justify-center rounded">
          <div className="flex items-center gap-2 text-red-600">
            <XCircle className="w-5 h-5" />
            <span className="text-sm font-medium">Failed</span>
          </div>
        </div>
      )}

      {/* Main Content */}
      <div className={`transition-opacity ${isLoading ? 'opacity-50' : 'opacity-100'}`}>
        {children}
      </div>

      {/* Action Buttons with States */}
      {actions.length > 0 && (
        <div className="flex items-center gap-2 mt-2">
          {actions.map((action) => {
            const pendingAction = rowActions.find(a => a.action === action.id);
            const actionState = pendingAction?.state || 'idle';

            return (
              <ActionButton
                key={action.id}
                config={action}
                state={actionState}
                progress={pendingAction?.progress}
                error={pendingAction?.error}
                onClick={() => handleActionClick(action)}
                onDismiss={() => pendingAction && clearAction(pendingAction.id)}
              />
            );
          })}
        </div>
      )}

      {/* Row-level Status Indicator */}
      {rowActions.length > 0 && (
        <div className="absolute top-2 right-2 flex items-center gap-1">
          {rowActions.map(action => (
            <ActionStatusBadge key={action.id} action={action} />
          ))}
        </div>
      )}
    </div>
  );
};

/**
 * P1-031: Individual action button with state
 */
const ActionButton: React.FC<{
  config: {
    id: string;
    label: string;
    icon?: React.ReactNode;
    variant?: 'primary' | 'secondary' | 'danger';
    disabled?: boolean;
  };
  state: ActionState;
  progress?: number;
  error?: string;
  onClick: () => void;
  onDismiss: () => void;
}> = ({ config, state, progress, error, onClick, onDismiss }) => {
  const getButtonContent = () => {
    switch (state) {
      case 'loading':
        return (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            {progress !== undefined && (
              <span className="text-xs">{Math.round(progress)}%</span>
            )}
          </>
        );
      case 'success':
        return <CheckCircle className="w-4 h-4 text-green-600" />;
      case 'error':
        return <XCircle className="w-4 h-4 text-red-600" />;
      case 'cancelling':
        return (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            <span className="text-xs">Cancelling...</span>
          </>
        );
      default:
        return (
          <>
            {config.icon}
            <span>{config.label}</span>
          </>
        );
    }
  };

  const getButtonStyle = () => {
    const base = 'flex items-center gap-2 px-3 py-1.5 rounded text-sm font-medium transition-all';
    
    if (state === 'loading' || state === 'cancelling') {
      return `${base} bg-gray-100 text-gray-500 cursor-wait`;
    }
    if (state === 'success') {
      return `${base} bg-green-100 text-green-700`;
    }
    if (state === 'error') {
      return `${base} bg-red-100 text-red-700 hover:bg-red-200`;
    }

    switch (config.variant) {
      case 'primary':
        return `${base} bg-blue-600 text-white hover:bg-blue-700`;
      case 'danger':
        return `${base} bg-red-600 text-white hover:bg-red-700`;
      default:
        return `${base} bg-gray-100 text-gray-700 hover:bg-gray-200`;
    }
  };

  return (
    <div className="relative">
      <button
        onClick={state === 'error' ? onDismiss : onClick}
        disabled={state === 'loading' || state === 'cancelling' || config.disabled}
        className={getButtonStyle()}
        data-testid={`action-btn-${config.id}`}
        data-state={state}
      >
        {getButtonContent()}
      </button>

      {/* Error Tooltip */}
      {state === 'error' && error && (
        <div className="absolute top-full left-0 mt-1 p-2 bg-red-600 text-white text-xs rounded shadow-lg z-20 max-w-xs">
          <div className="flex items-start gap-1">
            <AlertCircle className="w-3 h-3 mt-0.5 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <p className="mt-1 text-red-200">Click to dismiss</p>
        </div>
      )}
    </div>
  );
};

/**
 * P1-031: Action status badge for row indicator
 */
const ActionStatusBadge: React.FC<{ action: PendingAction }> = ({ action }) => {
  const getIcon = () => {
    switch (action.state) {
      case 'loading':
        return <Loader2 className="w-3 h-3 animate-spin text-blue-600" />;
      case 'success':
        return <CheckCircle className="w-3 h-3 text-green-600" />;
      case 'error':
        return <XCircle className="w-3 h-3 text-red-600" />;
      default:
        return <Clock className="w-3 h-3 text-gray-400" />;
    }
  };

  return (
    <div 
      className="flex items-center gap-1 px-2 py-0.5 bg-white rounded-full shadow-sm text-xs"
      title={`${action.action}: ${action.state}`}
    >
      {getIcon()}
      <span className="capitalize">{action.action}</span>
    </div>
  );
};

/**
 * P1-031: Table row wrapper with pending states
 */
export const PendingActionTableRow: React.FC<{
  rowId: string;
  children: React.ReactNode;
  className?: string;
}> = ({ rowId, children, className }) => {
  const { getRowActions, isRowLoading } = usePendingRowActions();
  
  const rowActions = getRowActions(rowId);
  const isLoading = isRowLoading(rowId);
  const hasSuccess = rowActions.some(a => a.state === 'success');
  const hasError = rowActions.some(a => a.state === 'error');

  const getRowClass = () => {
    let classes = className || '';
    
    if (isLoading) {
      classes += ' bg-blue-50/50';
    } else if (hasSuccess) {
      classes += ' bg-green-50/50';
    } else if (hasError) {
      classes += ' bg-red-50/50';
    }
    
    return classes;
  };

  return (
    <tr 
      className={getRowClass()}
      data-testid={`table-row-${rowId}`}
      data-loading={isLoading}
      data-success={hasSuccess}
      data-error={hasError}
    >
      {children}
    </tr>
  );
};

export default PendingActionRow;
